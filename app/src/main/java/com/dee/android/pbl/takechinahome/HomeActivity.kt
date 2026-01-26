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

    private var currentUser: User? = null // 在类顶部定义

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 统一在这里设置一次布局
        setContentView(R.layout.activity_home)

        // 调用统一初始化方法
        initHomeUI()

        // 数据库读取逻辑：更新欢迎语和头像
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@HomeActivity)
            currentUser = withContext(Dispatchers.IO) {
                db.userDao().getCurrentUser()
            }

            currentUser?.let {
                val nickname = it.account
                findViewById<TextView>(R.id.welcomeText).text = "尊驾 $nickname，别来无恙"
                findViewById<TextView>(R.id.userAvatarText).text = if (nickname.isNotEmpty()) nickname.take(1) else "佚"

                // 数据准备好后，如果是第一次进入，执行同步
                if (myGifts.isEmpty()) {
                    loadGiftsFromServer()
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

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        startBGM()
        tvEmptyHint = findViewById(R.id.tvEmptyHint)

        findViewById<View>(R.id.btnRegisterIntent).setOnClickListener { showWishFormDialog() }
        findViewById<View>(R.id.fabGenerate).setOnClickListener { generateOrderImage() }

        val recyclerView = findViewById<RecyclerView>(R.id.giftRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = GiftAdapter(myGifts, { gift, position ->
            showDeleteConfirmDialog(gift, position)
        }, { gift ->
            showGiftDetailDialog(gift)
        })
        recyclerView.adapter = adapter

        loadCachedGifts()
        if (myGifts.isEmpty()) loadGiftsFromServer()

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
        inputTime: String? = null
    ) {
        val activeGifts = myGifts.filter { it.isSaved }
        if (activeGifts.isEmpty()) {
            Toast.makeText(this, "画卷空空，请先「确入画卷」添加礼品", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            // 这里不需要再声明 val currentUser，直接用全局的
            val userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)

            // --- 核心修复：优先使用传入的参数，如果没有则用缓存 ---
            val accountOwner = currentUser?.account ?: "匿名官"

            // 如果传入了 inputName 就用它，否则用缓存或雅号
            val finalContactName = inputName ?: userPrefs.getString("saved_name", accountOwner) ?: accountOwner
            val contact = inputContact ?: userPrefs.getString("saved_contact", "未留联系方式") ?: "未留联系方式"
            val time = inputTime ?: userPrefs.getString("saved_comm_time", "随时可叙") ?: "随时可叙"

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

                // 最后的逻辑处理：是预览还是直接保存
                if (shouldSave) {
                    saveBitmapToGallery(bitmap)
                } else {
                    showImagePreviewDialog(bitmap)
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

        // --- 修改部分：自动填充登记时的雅号 ---
        val accountOwner = currentUser?.account ?: ""
        if (accountOwner.isNotEmpty()) {
            etName.setText(accountOwner)
            // 可选：将光标移至文字末尾，方便用户修改
            etName.setSelection(accountOwner.length)
        } else {
            // 如果没有获取到账号（例如本地缓存异常），则显示提示词
            etName.hint = "请输入联络人姓名"
        }

        val etContact = dialogView.findViewById<EditText>(R.id.etContact)
        val etCommTime = dialogView.findViewById<EditText>(R.id.etCommTime)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmitWish)

        val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        etName.setText(prefs.getString("saved_name", ""))
        etContact.setText(prefs.getString("saved_contact", ""))
        etCommTime.setText(prefs.getString("saved_comm_time", ""))

        // 修改弹窗标题
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("— 【投帖 · 联络官】  —") // 明确说明是下单联系人
            .setView(dialogView)
            .create()
        btnSubmit.setOnClickListener {
            val nameStr = etName.text.toString()
            val contactStr = etContact.text.toString()
            val timeStr = etCommTime.text.toString()

            if (nameStr.isBlank() || contactStr.isBlank()) {
                Toast.makeText(this, "请补全联络信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit {
                putString("saved_name", nameStr)
                putString("saved_contact", contactStr)
                putString("saved_comm_time", timeStr)
            }

            // --- 这里是关键：传参数给生成函数 ---
            generateOrderImage(false, nameStr, contactStr, timeStr)

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
            // 只需要吐司提示即可，不要再调用 showImagePreviewDialog 了
            Toast.makeText(this@HomeActivity, "画卷已存入相册", Toast.LENGTH_SHORT).show()
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

        MaterialAlertDialogBuilder(this)
            .setTitle("清单预览")
            .setView(scrollView)
            .setPositiveButton("存入相册") { _, _ ->
                saveBitmapToGallery(bitmap) // 在这里调用真正的保存逻辑
            }
            .setNegativeButton("返回", null)
            .show()
    }

    // --- 5. 数据加载与缓存 ---
    private fun loadGiftsFromServer() {
        // 1. 立即在主线程找到引用并开始动画
        val swipeLayout = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        swipeLayout.isRefreshing = true

        lifecycleScope.launch {
            try {
                // 2. 切换到 IO 线程请求数据
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.instance.getGifts()
                }

                // 3. 回到主线程处理 UI
                withContext(Dispatchers.Main) {
                    if (response != null) {
                        myGifts.clear()
                        myGifts.addAll(response)
                        adapter.notifyDataSetChanged()
                    }
                }
            } catch (e: Exception) {
                // 如果报错（如 404、超时、解析失败），这里会捕获
                Log.e("RETROFIT_ERROR", "请求失败: ${e.message}")
            } finally {
                // 4. 重点：无论如何，停止刷新动画并释放 UI
                withContext(Dispatchers.Main) {
                    swipeLayout.isRefreshing = false
                    updateEmptyView()
                }
            }
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
            val currentUser = db.userDao().getCurrentUser() ?: return@launch

            // 主容器：古风宣纸色
            val container = LinearLayout(this@HomeActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(80, 60, 80, 60)
                setBackgroundColor("#FBF8EF".toColorInt())
            }

            // 1. 展示登录邮箱（不可修改）
            val tvEmail = TextView(this@HomeActivity).apply {
                text = "登记邮箱：${currentUser.email}"
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
                setText(currentUser.account)
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
                    val inviteUrl = "$baseUrl?from=${currentUser.invitationCode}"
                    val qrBitmap = generateQRCode(inviteUrl, 600)
                    showQRCodeDialog(qrBitmap, currentUser.invitationCode)
                }
            }

            val tvCodeLabel = TextView(this@HomeActivity).apply { text = "我的引荐码：" }
            val tvCodeValue = TextView(this@HomeActivity).apply {
                text = currentUser.invitationCode
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
                    val clip = android.content.ClipData.newPlainText("InviteCode", currentUser.invitationCode)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this@HomeActivity, "引荐码已誊抄，可发给好友", Toast.LENGTH_SHORT).show()
                }
            }

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
                            currentUser.account = newName
                            db.userDao().updateUser(currentUser)
                            getSharedPreferences("UserPrefs", MODE_PRIVATE).edit { putString("saved_name", newName) }
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
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
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
                // 核心逻辑：将 container 转化为图片
                val imageBitmap = viewToBitmap(container)
                saveBitmapToGallery(imageBitmap) // 调用你之前写好的保存到 MediaStore 的方法
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
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 40, 80, 40)
            setBackgroundColor("#FBF8EF".toColorInt())
        }

        // 定义三个输入框
        val etOldPass = EditText(this).apply { hint = "原密信"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val etNewPass = EditText(this).apply { hint = "新密信"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val etConfirmPass = EditText(this).apply { hint = "确认新密信"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }

        container.addView(etOldPass)
        container.addView(etNewPass)
        container.addView(etConfirmPass)

        MaterialAlertDialogBuilder(this)
            .setTitle("— 【 修订密信 】 —")
            .setView(container)
            .setPositiveButton("重设") { _, _ ->
                val oldP = etOldPass.text.toString()
                val newP = etNewPass.text.toString()
                val confirmP = etConfirmPass.text.toString()

                if (newP != confirmP) {
                    Toast.makeText(this, "两次输入的密信不一", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // 发起网络请求
                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.instance.updatePassword(
                            currentUser?.email ?: "", oldP, newP
                        )
                        if (response.success) {
                            Toast.makeText(this@HomeActivity, "密信修订成功，请妥善保管", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@HomeActivity, "原密信有误：${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
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