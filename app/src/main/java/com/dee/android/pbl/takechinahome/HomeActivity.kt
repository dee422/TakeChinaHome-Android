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
import android.view.Gravity
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
import androidx.appcompat.app.AlertDialog
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.async

class HomeActivity : AppCompatActivity() {

    companion object {
        private const val DEFAULT_MAX_WIDTH = 850
        private const val KEY_CONTACT_NAME = "final_contact_name"
        private const val KEY_CONTACT_PHONE = "final_contact_phone"
        private const val KEY_CONTACT_TIME = "final_contact_time"
    }

    private var isMusicPlaying = true
    private lateinit var adapter: GiftAdapter
    private val myGifts = mutableListOf<Gift>()
    private var mediaPlayer: MediaPlayer? = null
    private val gson = Gson()
    private var tvEmptyHint: TextView? = null

    private var currentUser: User? = null // 在类顶部定义

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 1. 设置布局
        setContentView(R.layout.activity_home)

        // 2. 初始化 UI 组件（包含按钮点击事件）
        initHomeUI()

        // 3. 数据库读取逻辑：更新欢迎语和头像
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@HomeActivity)
            currentUser = withContext(Dispatchers.IO) {
                db.userDao().getCurrentUser()
            }

            currentUser?.let { user ->
                val nickname = user.account
                findViewById<TextView>(R.id.welcomeText).text = "尊驾 $nickname，别来无恙"
                findViewById<TextView>(R.id.userAvatarText).text = if (nickname.isNotEmpty()) nickname.take(1) else "佚"

                // 4. 数据准备好后，如果是第一次进入（列表为空），执行同步
                if (myGifts.isEmpty()) {
                    loadAllGiftsFromServer()
                }
            }
        }
    }

    // 抽离出来的注销函数（放在 onCreate 外面）
    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("提示")
            .setMessage("确定要退出登录吗？")
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@HomeActivity).userDao().clearUsers()
                    withContext(Dispatchers.Main) {
                        val intent = Intent(this@HomeActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun initHomeUI() {
        // 1. Toolbar 与 基础 UI 设置
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        startBGM()
        tvEmptyHint = findViewById(R.id.tvEmptyHint)

        // 2. 头像现在只做展示，不加点击事件，避免混淆
        findViewById<View>(R.id.userAvatarText).setOnClickListener(null)

        // “登记名帖”按钮/文字：点击后应填写本次订单的联络人信息
        // 修正：调用 showWishFormDialog，而不是 showProfileEditDialog
        findViewById<View>(R.id.btnRegisterIntent).setOnClickListener {
            // 这里的 adapter 是你在 onCreate 中初始化的 GiftAdapter 实例
            showWishFormDialog()
        }

        // 3. 核心：右下角“生成清单”按钮逻辑
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabGenerate).setOnClickListener {
            // 过滤出已确入画轴的礼品
            val activeGifts = myGifts.filter { it.isSaved }
            if (activeGifts.isEmpty()) {
                Toast.makeText(this, "画轴空空，请先勾勒礼遇", Toast.LENGTH_SHORT).show()
            } else {
                // 调用生成图片并预览的逻辑
                generateOrderImage()
            }
        }

        // 4. 核心：右下角“往期卷宗”按钮逻辑
        findViewById<com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton>(R.id.fabHistory).setOnClickListener {
            // ✨ 修改：指向 IntentListActivity，并带上当前用户的 Email
            val intent = Intent(this@HomeActivity, IntentListActivity::class.java).apply {
                putExtra("USER_EMAIL", currentUser?.email ?: "")
            }
            startActivity(intent)
        }

        // 5. 列表与适配器初始化
        val recyclerView = findViewById<RecyclerView>(R.id.giftRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = GiftAdapter(myGifts, { gift, position ->
            showDeleteConfirmDialog(gift, position)
        }, { gift ->
            showGiftDetailDialog(gift)
        })
        recyclerView.adapter = adapter

        // 6. 数据加载逻辑
        loadCachedGifts()
        if (myGifts.isEmpty()) {
            loadAllGiftsFromServer()
        }

        // 7. 下拉刷新逻辑
        findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout).apply {
            setColorSchemeColors("#8B4513".toColorInt())
            setOnRefreshListener { refreshGifts(this) }
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

        etText.setText(gift.customText)
        etQuantity.setText(gift.customQuantity)
        etDate.setText(gift.customDeliveryDate)
        etNotes.setText(gift.customNotes)

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("确入画卷") { _, _ ->
                gift.apply {
                    customText = etText.text.toString()
                    customQuantity = etQuantity.text.toString()
                    customDeliveryDate = etDate.text.toString()
                    customNotes = etNotes.text.toString()
                    isSaved = true
                }
                cacheGiftsLocally()
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "已加入清单", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // --- 3. 生成订购清单图逻辑 ---
    // --- 3. 生成订购清单图逻辑 ---
    // --- 3. 生成订购清单图逻辑 ---
    private fun generateOrderImage(
        shouldSave: Boolean = false,
        inputName: String? = null,
        inputContact: String? = null,
        inputTime: String? = null,
        historyGifts: List<Gift>? = null, // 新增
        historyAccount: String? = null   // 新增
    ) {
        // 逻辑：如果是查看卷宗，则使用传入的列表；否则使用当前画轴
        val activeGifts = historyGifts ?: myGifts.filter { it.isSaved }

        if (activeGifts.isEmpty()) {
            Toast.makeText(this, "清单空空，无从勾勒", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)

            // 账号主优先级：历史记录中的名字 > 当前登录名
            val accountOwner = historyAccount ?: (currentUser?.account ?: "匿名官")

            // 联络官信息同理
            val finalContactName = inputName ?: userPrefs.getString(KEY_CONTACT_NAME, null) ?: accountOwner
            val contact = inputContact ?: userPrefs.getString(KEY_CONTACT_PHONE, null) ?: "未留联系方式"
            val time = inputTime ?: userPrefs.getString(KEY_CONTACT_TIME, null) ?: "随时可叙"

            // --- 以下 Canvas 绘图代码完全不用改 ---
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
                canvas.drawColor("#F4EFE2".toColorInt()) // 宣纸底色

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

                // --- 1. 标题 ---
                paint.textAlign = Paint.Align.LEFT
                paint.textSize = 45f
                paint.color = "#B22222".toColorInt()
                canvas.drawText("【 投帖 · 联络官 】", 100f, 300f, paint)

                // --- 2. 账户主 (斜体) ---
                paint.color = Color.GRAY
                paint.textSize = 35f
                paint.textSkewX = -0.25f
                canvas.drawText("账户主 (雅号)：$accountOwner", 130f, 360f, paint)

                // --- 3. 联络人 (加粗，来自登记名或输入) ---
                paint.color = Color.BLACK
                paint.textSize = 40f
                paint.textSkewX = 0f
                paint.isFakeBoldText = true
                canvas.drawText("联络人：$finalContactName", 130f, 415f, paint)

                // --- 4. 其他信息 ---
                paint.isFakeBoldText = false
                paint.textSize = 38f
                canvas.drawText("联系方式：$contact", 130f, 475f, paint)
                canvas.drawText("便利时间：$time", 130f, 535f, paint)

                // --- 5. 关键修改：将分隔线往下移动 ---
                // 原本可能在 500f 左右，现在移到 620f，确保不遮挡文字
                paint.color = "#D3D3D3".toColorInt() // 浅灰色线
                paint.strokeWidth = 2f
                canvas.drawLine(100f, 620f, width - 100f, 620f, paint)

                // --- 6. 统一间距起始位置 ---
                var currentY = 720f

                activeGifts.forEachIndexed { index, gift ->
                    // A. 绘制品名序号与名称 (例如: 壹. 官窑八角杯)
                    paint.textSize = 52f // 稍微加大一点
                    paint.isFakeBoldText = true
                    paint.color = Color.BLACK

                    // 使用中文数字或加粗的阿拉伯数字增加仪式感
                    val itemTitle = "第 ${index + 1} 选：${gift.name}"
                    canvas.drawText(itemTitle, 100f, currentY, paint)

                    // B. 绘制详情 (缩进一点，让编号更突出)
                    paint.isFakeBoldText = false
                    paint.textSize = 38f
                    paint.color = "#8B4513".toColorInt()
                    canvas.drawText("【 数量：${gift.customQuantity} 】   交货期：${gift.customDeliveryDate}", 130f, currentY + 85f, paint)

                    paint.color = Color.BLACK
                    var textY = currentY + 160f

                    // 绘制刻花和叮嘱 (保持原本逻辑)
                    splitTextIntoLines("刻花/底款：${gift.customText.ifEmpty { "随缘" }}", DEFAULT_MAX_WIDTH, paint).forEach {
                        canvas.drawText(it, 130f, textY, paint); textY += 65f
                    }
                    splitTextIntoLines("特别叮嘱：${gift.customNotes.ifEmpty { "无" }}", DEFAULT_MAX_WIDTH, paint).forEach {
                        canvas.drawText(it, 130f, textY, paint); textY += 65f
                    }

                    // C. 绘制该条目的装饰短线 (让每一项看起来更独立)
                    val lineY = textY + 30f
                    paint.color = "#338B4513".toColorInt()
                    paint.strokeWidth = 2f
                    canvas.drawLine(100f, lineY, width - 100f, lineY, paint)

                    // D. 更新下一个条目的起始 Y
                    currentY = lineY + 90f
                }

                // 底部日期
                paint.textAlign = Paint.Align.RIGHT
                paint.color = Color.BLACK
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
                canvas.drawText("生成日期：$today", width - 100f, totalHeight - 120f, paint)

                // 印章
                val sealX = width - 400f
                val sealY = totalHeight - 450f // 根据总高度动态调整 Y 轴

                paint.style = Paint.Style.FILL
                paint.color = "#B22222".toColorInt()
                canvas.drawRect(sealX, sealY, sealX + 160f, sealY + 160f, paint)
                paint.color = Color.WHITE
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = 45f
                canvas.drawText("岁时", sealX + 75f, sealY + 65f, paint)
                canvas.drawText("礼序", sealX + 75f, sealY + 125f, paint)

                // 找到最末尾的逻辑分支并修改：
                if (shouldSave) {
                    saveBitmapToGallery(bitmap) { success ->
                        if (success) uploadOrderToBackend(finalContactName, activeGifts)
                    }
                } else {
                    // 传入参数供弹窗内部使用
                    showImagePreviewDialog(bitmap, finalContactName, activeGifts)
                }

            } catch (e: Exception) {
                Log.e("Log", "绘制异常: ${e.message}")
            }
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

        // 统一存储文件
        val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)

        // --- 回显逻辑：从常量 Key 读取 ---
        val savedName = prefs.getString(KEY_CONTACT_NAME, "")
        val savedPhone = prefs.getString(KEY_CONTACT_PHONE, "")
        val savedTime = prefs.getString(KEY_CONTACT_TIME, "")

        if (!savedName.isNullOrEmpty()) {
            etName.setText(savedName)
            etContact.setText(savedPhone)
            etCommTime.setText(savedTime)
        } else {
            etName.setText(currentUser?.account ?: "") // 首次进入默认账号名
            etCommTime.setText("随时可叙")
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("— 登记 · 联络官 —")
            .setView(dialogView)
            .create()

        btnSubmit.setOnClickListener {
            val n = etName.text.toString().trim()
            val p = etContact.text.toString().trim()
            val t = etCommTime.text.toString().trim()

            if (n.isEmpty() || p.isEmpty()) {
                Toast.makeText(this, "请补全信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // --- 同步写入：确保磁盘数据更新 ---
            val isSuccess = prefs.edit().apply {
                putString(KEY_CONTACT_NAME, n)
                putString(KEY_CONTACT_PHONE, p)
                putString(KEY_CONTACT_TIME, t)
            }.commit()

            if (isSuccess) {
                // 立即刷新预览图，并将新录入的数据直接透传
                generateOrderImage(false, n, p, t)
                Toast.makeText(this, "名帖已锁定", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
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

    private fun saveBitmapToGallery(bitmap: Bitmap, onSaved: ((Boolean) -> Unit)? = null) {
        val filename = "Order_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/TakeChinaHome")
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        lifecycleScope.launch(Dispatchers.IO) {
            var isSuccess = false
            try {
                uri?.let { imageUri ->
                    contentResolver.openOutputStream(imageUri)?.use { stream ->
                        isSuccess = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    }
                }
            } catch (e: Exception) {
                Log.e("Gallery", "保存失败: ${e.message}")
            }

            withContext(Dispatchers.Main) {
                if (isSuccess) {
                    Toast.makeText(this@HomeActivity, "画卷已存入相册", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@HomeActivity, "存入失败，请检查存储权限", Toast.LENGTH_SHORT).show()
                }
                // 关键：在这里触发回调
                onSaved?.invoke(isSuccess)
            }
        }
    }

    private fun uploadOrderToBackend(contactName: String, giftList: List<Gift>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = currentUser ?: return@launch

                // 1. 序列化数据
                val orderDetailsJson = Gson().toJson(giftList.map {
                    mapOf(
                        "name" to it.name,
                        "qty" to it.customQuantity,
                        "spec" to it.spec,
                        "note" to it.customNotes
                    )
                })

                // 2. 云端同步 - 修改具名参数以匹配 ApiService 中的定义
                val response = RetrofitClient.instance.uploadOrderConfirm(
                    userEmail = user.email,        // 去掉下划线，改用 userEmail
                    contactName = contactName,     // 去掉下划线，改用 contactName
                    json = orderDetailsJson        // 注意：你的 ApiService 里的参数名是 json
                )

                withContext(Dispatchers.Main) {
                    if (response.success) {
                        Toast.makeText(this@HomeActivity, "订单已同步至云端", Toast.LENGTH_SHORT).show()

                        val historyDao = AppDatabase.getDatabase(this@HomeActivity).orderHistoryDao()
                        val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                        val currentTime = timeFormatter.format(Date())

                        // 3. 本地存卷：同时存入账号主(user.account)和联络人(contactName)
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val historyEntry = OrderHistory(
                                    submitTime = currentTime,
                                    userEmail = user.email,
                                    accountOwner = user.account, // 账号主：当时的雅号
                                    contactName = contactName,   // 联络官：填写的名帖
                                    detailsJson = orderDetailsJson
                                )
                                historyDao.insertOrder(historyEntry)

                                withContext(Dispatchers.Main) {
                                    MaterialAlertDialogBuilder(this@HomeActivity)
                                        .setTitle("— 确入归卷 · 成功 —")
                                        .setMessage("该清单已妥帖存入『往期卷宗』。\n是否清空当前画轴，以便重新勾勒新清单？")
                                        .setCancelable(false)
                                        .setPositiveButton("清空首页") { _, _ -> clearCurrentOrder() }
                                        .setNegativeButton("保留查看", null)
                                        .show()
                                }
                            } catch (dbError: Exception) {
                                Log.e("DatabaseError", "本地存卷失败: ${dbError.message}")
                            }
                        }
                    } else {
                        Toast.makeText(this@HomeActivity, "同步失败: ${response.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("SyncError", "原因: ${e.message}")
                    Toast.makeText(this@HomeActivity, "网络同步失败", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 增加参数传递，以便在点击保存时知道要上传什么数据
    private fun showImagePreviewDialog(bitmap: Bitmap, contactName: String, activeGifts: List<Gift>) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 20)
            setBackgroundColor("#FBF8EF".toColorInt())
        }

        // 1. 图片预览容器
        val scrollView = ScrollView(this).apply {
            // 保持权重 1f 占用剩余空间
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            // 隐藏滑动条更显古风
            isVerticalScrollBarEnabled = false
        }

        val imageView = ImageView(this).apply {
            // 关键点 1：宽度撑满，高度自适应内容
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            // 关键点 2：改为 FIT_START (从顶部开始绘制) 或 FIT_XY (配合 adjustViewBounds)
            // 配合 adjustViewBounds = true，图片会按比例拉伸直到占满宽度
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_START

            setImageBitmap(bitmap)
        }

        scrollView.addView(imageView)
        container.addView(scrollView)

        // 2. 按钮栏（保持原有逻辑，仅微调样式）
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            gravity = Gravity.CENTER
            setPadding(40, 20, 40, 10) // 缩减一点垂直边距
        }

        fun createStyledButton(txt: String, color: String) = com.google.android.material.button.MaterialButton(this).apply {
            text = txt
            textSize = 14f // 稍微加大一点字号
            setTextColor(Color.WHITE)
            setBackgroundColor(color.toColorInt())
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                setMargins(12, 0, 12, 0)
            }
            cornerRadius = 12
            insetTop = 0
            insetBottom = 0
            elevation = 4f // 增加一点阴影
        }

        val btnClear = createStyledButton("裁撤", "#757575")
        val btnSave = createStyledButton("存图", "#8B4513")
        val btnUpload = createStyledButton("下单", "#A52A2A")

        buttonLayout.addView(btnClear)
        buttonLayout.addView(btnSave)
        buttonLayout.addView(btnUpload)
        container.addView(buttonLayout)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("— 画卷预览 · 确入 —")
            .setView(container)
            .setCancelable(true) // 允许点击外部取消以便修改
            .create()

        // 按钮逻辑...
        btnClear.setOnClickListener { dialog.dismiss(); showClearConfirmDialog() }
        btnSave.setOnClickListener { saveBitmapToGallery(bitmap); dialog.dismiss() }
        btnUpload.setOnClickListener { uploadOrderToBackend(contactName, activeGifts); dialog.dismiss() }

        dialog.show()
    }

    // 2. 确认弹窗函数
    private fun showClearConfirmDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("裁撤提醒")
            .setMessage("确定要清空当前画卷中已选中的礼品吗？此操作不可撤销。")
            .setPositiveButton("确定") { _, _ ->
                clearCurrentOrder() // 用户确定后，再调用执行逻辑
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 辅助函数：清空当前已“确入画卷”的状态
    private fun clearCurrentOrder() {
        myGifts.forEach { it.isSaved = false }
        cacheGiftsLocally()
        adapter.notifyDataSetChanged()
        updateEmptyView()
        Toast.makeText(this, "清单已清空", Toast.LENGTH_SHORT).show()
    }

    // 将原有的 loadGiftsFromServer 和 refreshGifts 逻辑合并至此
    // 2. 修改加载逻辑：解决图片拼接与重复问题
    private fun loadAllGiftsFromServer(swipe: SwipeRefreshLayout? = null) {
        swipe?.isRefreshing = true

        lifecycleScope.launch {
            try {
                val officialDeferred = async(Dispatchers.IO) { RetrofitClient.instance.getGifts() }
                val marketDeferred = async(Dispatchers.IO) { RetrofitClient.instance.getMarketGifts() }

                val officialResponse = try { officialDeferred.await() } catch (e: Exception) { null }
                val marketResponse = try { marketDeferred.await() } catch (e: Exception) { null }

                withContext(Dispatchers.Main) {
                    val combinedMap = mutableMapOf<Int, Gift>()

                    // 优先加载官方数据
                    officialResponse?.forEach { gift ->
                        combinedMap[gift.id] = gift
                    }

                    // 加载置换市集数据（status=2 覆盖原数据实现去重替换）
                    marketResponse?.filter { it.status == 2 }?.forEach { item ->
                        val gift = Gift(
                            id = item.id,
                            name = item.itemName ?: "无名藏品",
                            spec = item.description ?: "暂无描述",
                            isFriendShare = true
                        ).apply {
                            // 【核心修正】图片地址拼接逻辑
                            this.imageUrl = item.imageUrl ?: ""

                            // 2. 传递意向标签
                            this.exchangeWish = item.exchangeWish
                        }
                        // 以 ID 为 Key 放入 Map，如果 ID 重复，市集数据会替换旧数据
                        combinedMap[item.id] = gift
                    }

                    // 更新 UI 列表
                    myGifts.clear()
                    myGifts.addAll(combinedMap.values)
                    adapter.notifyDataSetChanged()

                    cacheGiftsLocally()
                    updateEmptyView()

                    if (swipe != null) {
                        Toast.makeText(this@HomeActivity, "云端卷宗同步完毕", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncError", "原因: ${e.message}")
                if (swipe != null) Toast.makeText(this@HomeActivity, "同步受阻，请检查网络", Toast.LENGTH_SHORT).show()
            } finally {
                swipe?.isRefreshing = false
            }
        }
    }

    private fun loadCachedGifts() {
        val json = getSharedPreferences("DataCache", MODE_PRIVATE).getString("cached_gifts", null)
        if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<MutableList<Gift>>() {}.type
            val cachedList: MutableList<Gift> = gson.fromJson(json, type)

            myGifts.clear()
            myGifts.addAll(cachedList)
            // ❌ 此处已彻底删除 myGifts[0].isFriendShare = true

            adapter.notifyDataSetChanged()
        }
    }

    // 1. 修改刷新触发逻辑：增加弹窗确认
    private fun refreshGifts(swipe: SwipeRefreshLayout) {
        // 立即停止刷新动画，等待弹窗确认
        swipe.isRefreshing = false

        MaterialAlertDialogBuilder(this)
            .setTitle("— 卷宗同步 · 确认 —")
            .setMessage("是否连接云端，同步最新的岁时礼遇与市集置换？")
            .setPositiveButton("确入同步") { _, _ ->
                // 用户确认后，带上 swipe 触发加载
                loadAllGiftsFromServer(swipe)
            }
            .setNegativeButton("暂缓", null)
            .show()
    }

    private fun cacheGiftsLocally() {
        val json = gson.toJson(myGifts)
        getSharedPreferences("DataCache", MODE_PRIVATE).edit { putString("cached_gifts", json) }
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

    // 1. 创建菜单
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.home_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?) = super.onPrepareOptionsMenu(menu).also {
        menu?.findItem(R.id.action_toggle_music)?.title = if (isMusicPlaying) "音律：奏鸣" else "音律：暂歇"
    }

    // 2. 处理菜单点击事件
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            // 1. 雅鉴置换跳转
            R.id.action_exchange -> {
                lifecycleScope.launch {
                    try {
                        val db = AppDatabase.getDatabase(this@HomeActivity)
                        val user = db.userDao().getCurrentUser()
                        if (user != null && user.referralCount >= 0) {
                            val intent = Intent(this@HomeActivity, ExchangeActivity::class.java)
                            startActivity(intent)
                        } else {
                            Toast.makeText(this@HomeActivity, "请先完善名帖信息", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Exchange_Err", "跳转失败: ${e.message}")
                        Toast.makeText(this@HomeActivity, "系统洗炼中，请稍后再试", Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }

            // 2. 音乐开关
            R.id.action_toggle_music -> {
                toggleMusic()
                true
            }

            // 3. 退出登记 (新增部分)
            R.id.action_logout -> {
                showLogoutConfirmDialog()
                true
            }

            // 4. 其他原有菜单
            R.id.action_profile -> { showProfileEditDialog(); true }
            R.id.action_generate_order -> { generateOrderImage(); true }
            R.id.action_help -> { showHelpDialog(); true }

            else -> super.onOptionsItemSelected(item)
        }
    }

    // 3. 退出登录逻辑
    private fun showLogoutConfirmDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("退出登记")
            .setMessage("确定要注销名帖，重新开启画卷吗？")
            .setPositiveButton("确定") { _, _ ->
                performLogout()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performLogout() {
        lifecycleScope.launch {
            // 清理本地数据库
            val db = AppDatabase.getDatabase(this@HomeActivity)
            db.userDao().clearUsers()

            // 停止背景音乐（可选）
            // stopBGM()

            // 返回注册页并清空 Activity 栈
            val intent = Intent(this@HomeActivity, RegisterActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()

            Toast.makeText(this@HomeActivity, "已退出登记", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showProfileEditDialog() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@HomeActivity)
            // 获取当前标记为 isCurrentUser 的用户
            val userInDb = db.userDao().getCurrentUser() ?: return@launch

            // 主容器：古风宣纸色
            val container = LinearLayout(this@HomeActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(80, 60, 80, 60)
                setBackgroundColor("#FBF8EF".toColorInt())
            }

            // 1. 展示登录邮箱（不可修改）
            val tvEmail = TextView(this@HomeActivity).apply {
                text = "登记邮箱：${userInDb.email}"
                textSize = 13f
                setTextColor(Color.GRAY)
                setPadding(0, 0, 0, 30)
            }

            // 2. 修订雅号标题
            val tvNicknameLabel = TextView(this@HomeActivity).apply {
                text = "当前雅号 (App内称呼)："
                textSize = 14f
            }

            // 3. 修订雅号输入框
            val etNickname = EditText(this@HomeActivity).apply {
                hint = "请修订雅号"
                setText(userInDb.account)
                textSize = 18f
                setSingleLine(true)
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }

            // --- 新增：修改密码入口 ---
            val tvChangePassword = TextView(this@HomeActivity).apply {
                text = "👉 修订密信 (修改密码)"
                textSize = 14f
                paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG // 增加下划线
                setTextColor("#A52A2A".toColorInt()) // 深红色
                setPadding(0, 30, 0, 30)
                setOnClickListener {
                    showChangePasswordDialog() // 调用修改密码对话框
                }
            }

            // 4. 展示用户自己的邀请码（invitationCode）
            val inviteSection = LinearLayout(this@HomeActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 40, 0, 20)
            }

            val btnQRCode = com.google.android.material.button.MaterialButton(this@HomeActivity).apply {
                text = "出示邀约"
                textSize = 10f
                setOnClickListener {
                    val baseUrl = "https://www.ichessgeek.com/api/v1/download.html"
                    val inviteUrl = "$baseUrl?from=${userInDb.invitationCode}"
                    val qrBitmap = generateQRCode(inviteUrl, 600)
                    showQRCodeDialog(qrBitmap, userInDb.invitationCode)
                }
            }

            val tvCodeLabel = TextView(this@HomeActivity).apply { text = "我的引荐码：" }
            val tvCodeValue = TextView(this@HomeActivity).apply {
                text = userInDb.invitationCode
                textSize = 20f
                setTextColor("#A52A2A".toColorInt())
                setPadding(20, 0, 20, 0)
                typeface = Typeface.MONOSPACE
            }

            val btnCopy = com.google.android.material.button.MaterialButton(this@HomeActivity).apply {
                text = "誊抄"
                textSize = 10f
                setOnClickListener {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("InviteCode", userInDb.invitationCode)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this@HomeActivity, "引荐码已誊抄，可发给好友", Toast.LENGTH_SHORT).show()
                }
            }

            // 在对话框的布局或按钮逻辑中增加
            val btnHistory = com.google.android.material.button.MaterialButton(this@HomeActivity).apply {
                text = "往期清单"
                // 设置一个小图标更雅致
                icon = androidx.core.content.ContextCompat.getDrawable(this@HomeActivity, android.R.drawable.ic_menu_recent_history)
                setOnClickListener {
                    val intent = Intent(this@HomeActivity, OrderHistoryActivity::class.java)
                    startActivity(intent)
                }
            }
// 将这个按钮 addView 到你的 Profile 布局中

            inviteSection.addView(btnQRCode)
            inviteSection.addView(tvCodeLabel)
            inviteSection.addView(tvCodeValue)
            inviteSection.addView(btnCopy)

            // 5. VIP 激励说明
            val vipDesc = TextView(this@HomeActivity).apply {
                text = "💡 雅号传千家：将引荐码转送给十位好友登记，即可晋升『雅鉴VIP』，开启置换分享权限。"
                textSize = 11f
                setTextColor(android.graphics.Color.DKGRAY)
                setPadding(0, 20, 0, 0)
            }

            // 组合所有 UI 控件
            container.addView(tvEmail)
            container.addView(tvNicknameLabel)
            container.addView(etNickname)
            container.addView(tvChangePassword) // 放在雅号下方
            container.addView(btnHistory)
            container.addView(inviteSection)
            container.addView(vipDesc)

            // 弹出对话框
            MaterialAlertDialogBuilder(this@HomeActivity)
                .setTitle("— 【名帖 · 账户主】  —")
                .setView(container)
                .setPositiveButton("存入") { _, _ ->
                    val newName = etNickname.text.toString().trim()
                    if (newName.isNotEmpty()) {
                        lifecycleScope.launch {
                            userInDb.account = newName
                            db.userDao().updateUser(userInDb)
                            this@HomeActivity.currentUser = userInDb
                            getSharedPreferences("UserPrefs", MODE_PRIVATE).edit { putString("saved_name", newName) }
                            // 立即刷新首页 UI
                            findViewById<TextView>(R.id.welcomeText).text = "尊驾 $newName，别来无恙"
                            findViewById<TextView>(R.id.userAvatarText).text = newName.take(1)

                            Toast.makeText(this@HomeActivity, "名帖已更新", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun generateQRCode(text: String, size: Int = 500): Bitmap {
        val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(
            text, com.google.zxing.BarcodeFormat.QR_CODE, size, size
        )
        // 建议使用 ARGB_8888 保证兼容性
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                // 💡 视觉优化：将纯黑换成深褐色 (#3E2723)，更符合宣纸质感
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) "#3E2723".toColorInt() else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun showQRCodeDialog(qrBitmap: Bitmap, code: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(80, 80, 80, 80) // 增加内边距
            setBackgroundColor("#FBF8EF".toColorInt())
        }

        // 标题：雅致的衬线体
        val tvTitle = TextView(this).apply {
            text = "— 岁时邀约 —"
            textSize = 20f
            setTextColor("#3E2723".toColorInt())
            setPadding(0, 0, 0, 50)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }

        // 二维码容器：增加一个白色背板，方便识别
        val qrFrame = FrameLayout(this).apply {
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.WHITE) // 二维码背后的白色保护区
        }

        val ivQR = ImageView(this).apply {
            setImageBitmap(qrBitmap)
            layoutParams = FrameLayout.LayoutParams(600, 600)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        qrFrame.addView(ivQR)

        val tvHint = TextView(this).apply {
            text = "扫码共赏，引荐码：$code"
            textSize = 14f
            setTextColor("#8B4513".toColorInt())
            setPadding(0, 40, 0, 0)
        }

        container.addView(tvTitle)
        container.addView(qrFrame)
        container.addView(tvHint)

        MaterialAlertDialogBuilder(this)
            .setTitle("生成邀约图帖") // 增加标题提示
            .setView(container)
            .setPositiveButton("存入相册") { _, _ ->
                val imageBitmap = viewToBitmap(container)
                saveBitmapToGallery(imageBitmap) { success ->
                    if (success) {
                        Toast.makeText(this, "邀约图帖已封存至相册", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("隐去", null)
            .show()
    }

    private fun viewToBitmap(view: View): Bitmap {
        // 手动测量和布局，确保即使 view 还没显示在屏幕上也能生成图片
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun showChangePasswordDialog() {
        // 1. 安全拦截：确保用户非空
        val user = currentUser ?: run {
            Toast.makeText(this, "用户信息异常，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 40, 80, 40)
            setBackgroundColor("#FBF8EF".toColorInt())
        }

        // 定义输入框样式
        fun createPassET(hintStr: String) = EditText(this).apply {
            hint = hintStr
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(0, 40, 0, 40) // 增加上下间距，视觉更舒缓
        }

        val etOldPass = createPassET("原密信")
        val etNewPass = createPassET("新密信")
        val etConfirmPass = createPassET("确认新密信")

        container.addView(etOldPass)
        container.addView(etNewPass)
        container.addView(etConfirmPass)

        MaterialAlertDialogBuilder(this)
            .setTitle("— 【 修订密信 】 —")
            .setView(container)
            .setPositiveButton("重设") { _, _ ->
                val oldP = etOldPass.text.toString().trim()
                val newP = etNewPass.text.toString().trim()
                val confirmP = etConfirmPass.text.toString().trim()

                // 2. 基础校验
                if (newP != confirmP) {
                    Toast.makeText(this, "两次输入的密信不一", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newP.length < 6) {
                    Toast.makeText(this, "新密信过短", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // 3. 发起网络请求
                lifecycleScope.launch {
                    try {
                        // 显式指定参数名，防止传错位
                        val response = RetrofitClient.instance.updatePassword(
                            email = user.email,
                            oldPass = oldP,
                            newPass = newP
                        )

                        if (response.success) {
                            Toast.makeText(this@HomeActivity, "密信修订成功，请妥善保管", Toast.LENGTH_SHORT).show()
                        } else {
                            // 提示具体的失败原因（如原密码错误）
                            Toast.makeText(this@HomeActivity, "修订失败：${response.message}", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("UpdatePass", "Error: ${e.message}")
                        Toast.makeText(this@HomeActivity, "云端连接失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onResume() { super.onResume(); if (isMusicPlaying) mediaPlayer?.start() }
    override fun onPause() { super.onPause() }
    override fun onDestroy() { super.onDestroy(); mediaPlayer?.release() }
}