package com.dee.android.pbl.takechinahome

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

class HomeActivity : AppCompatActivity() {

    companion object {
        private const val DEFAULT_MAX_WIDTH = 850
    }

    private var isMusicPlaying = true
    private lateinit var adapter: GiftAdapter
    private val myGifts = mutableListOf<Gift>()
    private var mediaPlayer: MediaPlayer? = null
    private val gson = Gson()
    private var tvEmptyHint: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@HomeActivity)
            val currentUser = db.userDao().getCurrentUser()

            if (currentUser == null) {
                startActivity(Intent(this@HomeActivity, RegisterActivity::class.java))
                finish()
            } else {
                initHomeUI()
            }
        }
    }

    private fun initHomeUI() {
        setContentView(R.layout.activity_home)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        startBGM()
        tvEmptyHint = findViewById(R.id.tvEmptyHint)

        // 登记名帖按钮
        findViewById<View>(R.id.btnRegisterIntent).setOnClickListener {
            showWishFormDialog()
        }

        val recyclerView = findViewById<RecyclerView>(R.id.giftRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 初始化 Adapter：第一个 lambda 处理长按删除，第二个处理点击编辑
        adapter = GiftAdapter(myGifts, { gift, position ->
            showDeleteConfirmDialog(gift, position)
        }, { gift ->
            showGiftDetailDialog(gift)
        })
        recyclerView.adapter = adapter

        loadCachedGifts()

        val prefs = getSharedPreferences("DataCache", MODE_PRIVATE)
        if (prefs.getBoolean("is_first_run", true) && myGifts.isEmpty()) {
            loadGiftsFromServer(isInitial = true)
        }

        if (prefs.getBoolean("is_first_help", true)) {
            showHelpDialog()
            prefs.edit { putBoolean("is_first_help", false) }
        }

        updateEmptyView()

        findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout).apply {
            setColorSchemeColors("#8B4513".toColorInt())
            setOnRefreshListener { refreshGifts(this) }
        }

        // 生成清单悬浮按钮
        findViewById<View>(R.id.fabGenerate).setOnClickListener {
            generateOrderImage()
        }
    }

    // --- 1. 长按删除逻辑 ---
    private fun showDeleteConfirmDialog(gift: Gift, position: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("裁撤项目")
            .setMessage("确定要将「${gift.name}」移出画卷吗？")
            .setPositiveButton("确定") { _, _ ->
                performDelete(position, gift)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performDelete(position: Int, deletedGift: Gift) {
        if (position !in myGifts.indices) return

        myGifts.removeAt(position)
        adapter.notifyItemRemoved(position)
        cacheGiftsLocally()
        updateEmptyView()

        lifecycleScope.launch {
            try {
                // 请确保你的 Retrofit 定义中 deleteGift 接收的是 Gift.id 的类型
                val response = RetrofitClient.instance.deleteGift(deletedGift.id)
                if (response.isSuccessful) Log.d("Sync", "云端同步成功")
            } catch (e: Exception) {
                Log.e("Sync", "同步失败: ${e.message}")
            }
        }
    }

    // --- 2. 填写礼品详情并保存逻辑 ---
    private fun showGiftDetailDialog(gift: Gift) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_gift_custom, null)
        val etText = dialogView.findViewById<TextInputEditText>(R.id.etCustomText)
        val etQuantity = dialogView.findViewById<TextInputEditText>(R.id.etCustomQuantity)
        val etDate = dialogView.findViewById<TextInputEditText>(R.id.etCustomDate)
        val etNotes = dialogView.findViewById<TextInputEditText>(R.id.etCustomNotes)

        // 回显已有数据
        etText.setText(gift.customText)
        etQuantity.setText(gift.customQuantity)
        etDate.setText(gift.customDeliveryDate)
        etNotes.setText(gift.customNotes)

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("确入画卷") { _, _ ->
                // 更新内存中的对象
                gift.apply {
                    customText = etText.text.toString()
                    customQuantity = etQuantity.text.toString()
                    customDeliveryDate = etDate.text.toString()
                    customNotes = etNotes.text.toString()
                    isSaved = true // 关键：标记已保存，否则无法生成清单
                }

                // 持久化到本地缓存
                cacheGiftsLocally()

                // 刷新列表显示（改变按钮文字和图标）
                val index = myGifts.indexOf(gift)
                if (index != -1) {
                    adapter.notifyItemChanged(index)
                }
                Toast.makeText(this, "已加入清单", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // --- 3. 生成订购清单图逻辑 ---
    private fun generateOrderImage() {
        val activeGifts = myGifts.filter { it.isSaved }
        if (activeGifts.isEmpty()) {
            Toast.makeText(this, "画卷空空，请先「确入画卷」添加礼品", Toast.LENGTH_SHORT).show()
            return
        }

        val userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val name = userPrefs.getString("saved_name", "匿名官") ?: "匿名官"
        val contact = userPrefs.getString("saved_contact", "未留联系方式") ?: "未留联系方式"
        val time = userPrefs.getString("saved_comm_time", "随时可叙") ?: "随时可叙"

        val width = 1080
        var totalHeight = 1100
        val paint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            setShadowLayer(1.5f, 1f, 1f, "#44000000".toColorInt())
        }

        // 计算总高度
        val itemHeights = mutableListOf<Float>()
        activeGifts.forEach { gift ->
            paint.textSize = 40f
            val reqLines = splitTextIntoLines("刻花/底款：${gift.customText}", DEFAULT_MAX_WIDTH, paint).size
            val noteLines = splitTextIntoLines("特别叮嘱：${gift.customNotes}", DEFAULT_MAX_WIDTH, paint).size
            val h = 420f + (reqLines * 60f) + (noteLines * 60f)
            itemHeights.add(h)
            totalHeight += h.toInt()
        }

        try {
            val bitmap = createBitmap(width, totalHeight)
            val canvas = Canvas(bitmap)
            canvas.drawColor("#F4EFE2".toColorInt())

            // 绘制纸张纹理
            val random = Random()
            val texturePaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
            for (i in 0..300) {
                texturePaint.strokeWidth = random.nextFloat() * 2f + 1f
                val alpha = random.nextInt(40) + 20
                texturePaint.color = Color.argb(alpha, 120, 100, 80)
                val startX = random.nextFloat() * width
                val startY = random.nextFloat() * totalHeight
                val length = random.nextFloat() * 60f + 20f
                val angle = random.nextFloat() * Math.PI * 2
                canvas.drawLine(startX, startY, (startX + cos(angle) * length).toFloat(), (startY + sin(angle) * length).toFloat(), texturePaint)
            }

            // 标题
            paint.color = Color.BLACK
            paint.textSize = 85f
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            canvas.drawText("岁时礼序 · 订购清单", width / 2f, 180f, paint)

            // 名帖信息
            paint.textAlign = Paint.Align.LEFT
            paint.isFakeBoldText = false
            paint.textSize = 45f
            paint.color = "#B22222".toColorInt()
            canvas.drawText("【名帖 · 登记意向】", 100f, 300f, paint)

            paint.color = Color.BLACK
            paint.textSize = 38f
            canvas.drawText("联系人：$name", 130f, 370f, paint)
            canvas.drawText("联系方式：$contact", 130f, 430f, paint)
            canvas.drawText("便利时间：$time", 130f, 490f, paint)

            paint.color = "#8B4513".toColorInt()
            canvas.drawLine(100f, 540f, width - 100f, 540f, paint)

            // 循环绘制每个礼品
            var currentY = 650f
            activeGifts.forEachIndexed { index, gift ->
                paint.textSize = 50f
                paint.isFakeBoldText = true
                paint.color = Color.BLACK
                canvas.drawText("${index + 1}. ${gift.name}", 100f, currentY, paint)

                paint.isFakeBoldText = false
                paint.textSize = 38f
                paint.color = "#8B4513".toColorInt()
                canvas.drawText("数量：${gift.customQuantity}   交货期：${gift.customDeliveryDate}", 130f, currentY + 80f, paint)

                paint.color = Color.BLACK
                var textY = currentY + 150f
                splitTextIntoLines("刻花/底款：${gift.customText.ifEmpty { "随缘" }}", DEFAULT_MAX_WIDTH, paint).forEach {
                    canvas.drawText(it, 130f, textY, paint); textY += 60f
                }
                splitTextIntoLines("特别叮嘱：${gift.customNotes.ifEmpty { "无" }}", DEFAULT_MAX_WIDTH, paint).forEach {
                    canvas.drawText(it, 130f, textY, paint); textY += 60f
                }

                currentY += itemHeights[index]
                paint.color = "#338B4513".toColorInt()
                canvas.drawLine(100f, currentY - 50f, width - 100f, currentY - 50f, paint)
            }

            // 底部日期
            paint.textAlign = Paint.Align.RIGHT
            paint.color = Color.BLACK
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
            canvas.drawText("生成日期：$today", width - 100f, totalHeight - 120f, paint)

            // 印章
            val sealX = width - 480f
            val sealY = totalHeight - 360f
            paint.style = Paint.Style.FILL
            paint.color = "#B22222".toColorInt()
            canvas.drawRect(sealX, sealY, sealX + 150f, sealY + 150f, paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 45f
            canvas.drawText("岁时", sealX + 75f, sealY + 65f, paint)
            canvas.drawText("礼序", sealX + 75f, sealY + 125f, paint)

            saveBitmapToGallery(bitmap)

        } catch (e: Exception) {
            Log.e("Log", "绘制异常: ${e.message}")
        }
    }

    // --- 4. 辅助 UI 方法 ---
    private fun showHelpDialog() {
        val helpTips = listOf(
            "【回望】下拉画卷可同步云端数据。",
            "【裁撤】长按卡片区域可移出礼品。",
            "【落款】下单前请先登记名帖信息。",
            "【成画】点击下方保存键生成订购清单。"
        )
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(70, 50, 70, 70)
            setBackgroundColor("#F4EFE2".toColorInt())
        }
        helpTips.forEach { tip ->
            container.addView(TextView(this).apply {
                text = tip
                textSize = 15f
                setPadding(0, 15, 0, 15)
                setTextColor("#4A4A4A".toColorInt())
            })
        }
        MaterialAlertDialogBuilder(this).setView(container).setPositiveButton("敬悉", null).show()
    }

    private fun showWishFormDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_wish_form, null)
        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etContact = dialogView.findViewById<EditText>(R.id.etContact)
        val etCommTime = dialogView.findViewById<EditText>(R.id.etCommTime)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmitWish)

        val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        etName.setText(prefs.getString("saved_name", ""))
        etContact.setText(prefs.getString("saved_contact", ""))
        etCommTime.setText(prefs.getString("saved_comm_time", ""))

        val dialog = MaterialAlertDialogBuilder(this).setView(dialogView).create()
        btnSubmit.setOnClickListener {
            prefs.edit {
                putString("saved_name", etName.text.toString())
                putString("saved_contact", etContact.text.toString())
                putString("saved_comm_time", etCommTime.text.toString())
            }
            Toast.makeText(this, "名帖已登记", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun splitTextIntoLines(text: String, maxWidth: Int, paint: Paint): List<String> {
        val lines = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val count = paint.breakText(text, start, text.length, true, maxWidth.toFloat(), null)
            lines.add(text.substring(start, start + count))
            start += count
        }
        return lines
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename = "Order_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/TakeChinaHome")
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        lifecycleScope.launch {
            uri?.let { imageUri ->
                contentResolver.openOutputStream(imageUri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                }
            }
            showImagePreviewDialog(bitmap)
        }
    }

    private fun showImagePreviewDialog(bitmap: Bitmap) {
        val scrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.7).toInt())
        }
        val imageView = ImageView(this).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        scrollView.addView(imageView)
        MaterialAlertDialogBuilder(this).setTitle("清单预览").setView(scrollView).setPositiveButton("确认", null).show()
    }

    // --- 5. 数据加载与缓存 ---
    private fun loadGiftsFromServer(isInitial: Boolean = false) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getGifts()
                myGifts.clear()
                myGifts.addAll(response)
                adapter.notifyDataSetChanged()
                cacheGiftsLocally()
                updateEmptyView()
                if (isInitial) getSharedPreferences("DataCache", MODE_PRIVATE).edit { putBoolean("is_first_run", false) }
            } catch (e: Exception) { Log.e("Log", "API Err: ${e.message}") }
        }
    }

    private fun loadCachedGifts() {
        val json = getSharedPreferences("DataCache", MODE_PRIVATE).getString("cached_gifts", null)
        if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<MutableList<Gift>>() {}.type
            myGifts.clear()
            myGifts.addAll(gson.fromJson(json, type))
            adapter.notifyDataSetChanged()
        }
    }

    private fun cacheGiftsLocally() {
        val json = gson.toJson(myGifts)
        getSharedPreferences("DataCache", MODE_PRIVATE).edit { putString("cached_gifts", json) }
    }

    private fun refreshGifts(swipe: SwipeRefreshLayout) {
        // 弹出确认对话框，防止误操作清空已保存的订单
        MaterialAlertDialogBuilder(this)
            .setTitle("重新洗炼")
            .setMessage("同步云端将重置当前画卷的所有定制信息，是否继续？")
            .setPositiveButton("确定") { _, _ ->
                // 用户确认，开始同步
                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.instance.getGifts()
                        if (response.isNotEmpty()) {
                            myGifts.clear()
                            myGifts.addAll(response)
                            adapter.notifyDataSetChanged()
                            cacheGiftsLocally() // 同步后立即更新本地缓存
                            updateEmptyView()
                            Toast.makeText(this@HomeActivity, "画卷已焕然一新", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("Log", "同步失败: ${e.message}")
                        Toast.makeText(this@HomeActivity, "云端暂不可达，请稍后再试", Toast.LENGTH_SHORT).show()
                    } finally {
                        swipe.isRefreshing = false // 停止旋转动画
                    }
                }
            }
            .setNegativeButton("取消") { _, _ ->
                // 用户取消，直接停止刷新动画
                swipe.isRefreshing = false
            }
            .setCancelable(false) // 强制用户做出选择
            .show()
    }

    private fun updateEmptyView() {
        tvEmptyHint?.visibility = if (myGifts.isEmpty()) View.VISIBLE else View.GONE
    }

    // --- 6. 音乐控制与生命周期 ---
    private fun startBGM() {
        isMusicPlaying = getSharedPreferences("UserPrefs", MODE_PRIVATE).getBoolean("music_enabled", true)
        if (mediaPlayer != null) return
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.bg_music)
            mediaPlayer?.isLooping = true
            if (isMusicPlaying) mediaPlayer?.start()
        } catch (_: Exception) { }
    }

    private fun toggleMusic() {
        isMusicPlaying = !isMusicPlaying
        if (isMusicPlaying) mediaPlayer?.start() else mediaPlayer?.pause()
        invalidateOptionsMenu()
        getSharedPreferences("UserPrefs", MODE_PRIVATE).edit { putBoolean("music_enabled", isMusicPlaying) }
    }

    override fun onCreateOptionsMenu(menu: Menu?) = menuInflater.inflate(R.menu.home_menu, menu).let { true }
    override fun onPrepareOptionsMenu(menu: Menu?) = super.onPrepareOptionsMenu(menu).also {
        menu?.findItem(R.id.action_toggle_music)?.title = if (isMusicPlaying) "音律：奏鸣" else "音律：暂歇"
    }
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_toggle_music -> { toggleMusic(); true }
        R.id.action_generate_order -> { generateOrderImage(); true }
        R.id.action_help -> { showHelpDialog(); true }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onResume() { super.onResume(); if (isMusicPlaying) mediaPlayer?.start() }
    override fun onPause() { super.onPause(); mediaPlayer?.pause() }
    override fun onDestroy() { super.onDestroy(); mediaPlayer?.release() }
}