package com.dee.android.pbl.takechinahome

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Random

class OrderHistoryActivity : AppCompatActivity() {

    private lateinit var adapter: OrderHistoryAdapter
    private val DEFAULT_MAX_WIDTH = 880f // 绘图文本换行宽度

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val recyclerView = RecyclerView(this).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -1)
            setBackgroundColor(Color.parseColor("#F4EFE2"))
            setPadding(32, 32, 32, 32)
            layoutManager = LinearLayoutManager(this@OrderHistoryActivity)
        }
        setContentView(recyclerView)
        supportActionBar?.title = "往期卷宗 · 溯源"

        adapter = OrderHistoryAdapter(emptyList()) { order ->
            // 点击卷宗，不再弹文本框，直接弹“画卷图片”
            renderHistoryImage(order)
        }
        recyclerView.adapter = adapter

        loadHistoryData()
    }

    private fun loadHistoryData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@OrderHistoryActivity)
            val user = db.userDao().getCurrentUser()

            if (user != null) {
                try {
                    // 1. 尝试从云端拉取最新卷宗
                    val cloudHistory = RetrofitClient.instance.getOrderHistory(user.email)

                    withContext(Dispatchers.Main) {
                        // 更新 UI 展示云端数据
                        adapter.updateData(cloudHistory)

                        // 2. 异步同步到本地数据库（可选，但推荐，方便离线查看）
                        syncToLocal(db, cloudHistory)

                        if (cloudHistory.isEmpty()) {
                            Toast.makeText(this@OrderHistoryActivity, "云端暂无您的卷宗", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    // 3. 网络失败时，读取本地 Room 缓存兜底
                    Log.e("Sync", "拉取云端失败，改用本地缓存: ${e.message}")
                    val localHistory = db.orderHistoryDao().getOrdersByUser(user.email)

                    withContext(Dispatchers.Main) {
                        adapter.updateData(localHistory)
                        if (localHistory.isEmpty()) {
                            Toast.makeText(this@OrderHistoryActivity, "本地亦无卷宗，请检查网络", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@OrderHistoryActivity, "已载入本地存卷（离线模式）", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // 辅助函数：将云端数据刷新到本地 Room
    private suspend fun syncToLocal(db: AppDatabase, cloudData: List<OrderHistory>) {
        withContext(Dispatchers.IO) {
            // 这里可以根据业务决定是全量覆盖还是增量插入
            // 为简单起见，可以先插入所有新数据
            cloudData.forEach { order ->
                db.orderHistoryDao().insertOrder(order)
            }
        }
    }

    /**
     * 核心函数：将历史 JSON 还原为礼品对象，并绘制长卷
     */
    private fun renderHistoryImage(order: OrderHistory) {
        lifecycleScope.launch {
            try {
                // 1. 数据还原：JSON -> List<Gift>
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val rawList: List<Map<String, Any>> = Gson().fromJson(order.detailsJson, type)
                val historyGifts = rawList.map { map ->
                    Gift(
                        name = map["name"].toString(),
                        spec = map["spec"].toString(),
                        isSaved = true
                    ).apply {
                        customQuantity = map["qty"].toString()
                        customNotes = map["note"].toString()
                    }
                }

                // 2. 准备画布参数
                val width = 1080
                val paint = Paint().apply { isAntiAlias = true }

                // 计算动态高度
                var totalHeight = 1100f
                val giftHeights = mutableListOf<Float>()
                historyGifts.forEach { gift ->
                    paint.textSize = 38f
                    val noteLines = splitTextIntoLines("特别叮嘱：${gift.customNotes}", DEFAULT_MAX_WIDTH, paint).size
                    val h = 320f + (noteLines * 60f)
                    giftHeights.add(h)
                    totalHeight += h
                }

                // 3. 开始绘图
                val bitmap = Bitmap.createBitmap(width, totalHeight.toInt(), Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor("#F4EFE2".toColorInt())

                // 纸张纹理效果 (简化逻辑)
                val random = Random()
                paint.style = Paint.Style.STROKE
                for (i in 0..200) {
                    paint.color = Color.argb(random.nextInt(30) + 10, 120, 100, 80)
                    val startX = random.nextFloat() * width
                    val startY = random.nextFloat() * totalHeight
                    canvas.drawLine(startX, startY, startX + random.nextFloat() * 50f, startY + random.nextFloat() * 50f, paint)
                }

                // 标题与身份信息
                paint.style = Paint.Style.FILL
                paint.textAlign = Paint.Align.CENTER
                paint.color = Color.BLACK
                paint.textSize = 80f
                paint.isFakeBoldText = true
                canvas.drawText("岁时礼序 · 往期卷宗", width / 2f, 180f, paint)

                paint.textAlign = Paint.Align.LEFT
                paint.textSize = 35f
                paint.color = Color.GRAY
                canvas.drawText("账户主 (雅号)：${order.accountOwner}", 130f, 320f, paint)

                paint.textSize = 42f
                paint.color = Color.BLACK
                canvas.drawText("联络人：${order.contactName}", 130f, 385f, paint)
                canvas.drawText("账户归属：${order.userEmail}", 130f, 445f, paint)

                canvas.drawLine(100f, 520f, width - 100f, 520f, paint.apply { strokeWidth = 2f })

                // 绘制历史礼品列表
                var currentY = 620f
                historyGifts.forEachIndexed { index, gift ->
                    paint.textSize = 48f
                    paint.isFakeBoldText = true
                    canvas.drawText("${index + 1}. ${gift.name}", 100f, currentY, paint)

                    paint.textSize = 38f
                    paint.isFakeBoldText = false
                    canvas.drawText("数量：${gift.customQuantity} | 规格：${gift.spec}", 130f, currentY + 70f, paint)

                    var textY = currentY + 130f
                    splitTextIntoLines("特别叮嘱：${gift.customNotes.ifEmpty { "随缘" }}", DEFAULT_MAX_WIDTH, paint).forEach {
                        canvas.drawText(it, 130f, textY, paint)
                        textY += 60f
                    }
                    currentY = textY + 40f
                }

                // 底部印章与日期
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("确入日期：${order.submitTime}", width - 100f, totalHeight - 100f, paint)

                // 4. 显示预览弹窗
                showImagePreview(bitmap)

            } catch (e: Exception) {
                Log.e("RenderError", "绘图失败: ${e.message}")
                Toast.makeText(this@OrderHistoryActivity, "画卷生成失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showImagePreview(bitmap: Bitmap) {
        // 1. 创建滑动容器
        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(-1, -2)
        }

        // 2. 创建并配置图片视图
        val imageView = ImageView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
            // 关键：保持宽度占满，高度按比例伸展
            scaleType = ImageView.ScaleType.FIT_START
            adjustViewBounds = true
            setImageBitmap(bitmap)
            // 增加一点边缘留白，更有画卷质感
            setPadding(10, 10, 10, 10)
        }

        // 3. 组装并弹出
        scrollView.addView(imageView)

        MaterialAlertDialogBuilder(this)
            .setTitle("— 溯源画卷 · 详情 —")
            .setView(scrollView) // 现在视图是可以滑动的了
            .setPositiveButton("阅毕归卷", null)
            .show()
    }

    // 辅助函数：处理长文本换行
    private fun splitTextIntoLines(text: String, maxWidth: Float, paint: Paint): List<String> {
        val lines = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val count = paint.breakText(text, start, text.length, true, maxWidth, null)
            lines.add(text.substring(start, start + count))
            start += count
        }
        return lines
    }
}