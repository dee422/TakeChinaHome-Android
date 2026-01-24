package com.dee.android.pbl.takechinahome

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.*
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : AppCompatActivity() {

    private var isMusicPlaying = true // 默认播放
    private lateinit var adapter: GiftAdapter
    private val myGifts = mutableListOf<Gift>()
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var itemTouchHelper: ItemTouchHelper
    private val gson = Gson()
    private var tvEmptyHint: TextView? = null

    private val deletePaint = Paint().apply {
        color = "#B22222".toColorInt()
        isAntiAlias = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // --- 方案 A：标准 Toolbar 设置 ---
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
            // 允许显示标题，会自动应用 XML 中的 app:title
            supportActionBar?.setDisplayShowTitleEnabled(true)
        }

        startBGM()
        tvEmptyHint = findViewById(R.id.tvEmptyHint)

        // 名帖登记点击
        findViewById<View>(R.id.btnRegisterIntent).setOnClickListener {
            showWishFormDialog()
        }

        // RecyclerView 初始化
        val recyclerView = findViewById<RecyclerView>(R.id.giftRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        setupTouchHelper(recyclerView)

        adapter = GiftAdapter(myGifts, { viewHolder ->
            itemTouchHelper.startSwipe(viewHolder)
        }, { gift ->
            showGiftDetailDialog(gift)
        })
        recyclerView.adapter = adapter

        // 数据加载逻辑
        loadCachedGifts()

        val prefs = getSharedPreferences("DataCache", MODE_PRIVATE)
        if (prefs.getBoolean("is_first_run", true) && myGifts.isEmpty()) {
            loadGiftsFromServer(isInitial = true)
        }

        // 首次运行弹出帮助指南
        if (prefs.getBoolean("is_first_help", true)) {
            showHelpDialog()
            prefs.edit { putBoolean("is_first_help", false) }
        }

        updateEmptyView()

        // 下拉刷新
        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setColorSchemeColors("#8B4513".toColorInt())
        swipeRefreshLayout.setOnRefreshListener {
            refreshGifts(swipeRefreshLayout)
        }

        // FAB 生成清单
        findViewById<View>(R.id.fabGenerate).setOnClickListener {
            generateOrderImage()
        }
    }

    // --- 菜单栏：关联 home_menu.xml ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.home_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val musicItem = menu?.findItem(R.id.action_toggle_music)
        musicItem?.title = if (isMusicPlaying) "音律：奏鸣" else "音律：暂歇"
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_music -> {
                isMusicPlaying = !isMusicPlaying
                if (isMusicPlaying) {
                    mediaPlayer?.start()
                    Toast.makeText(this, "丝竹再起", Toast.LENGTH_SHORT).show()
                } else {
                    mediaPlayer?.pause()
                    Toast.makeText(this, "音律已歇", Toast.LENGTH_SHORT).show()
                }
                // 强制更新菜单文案
                invalidateOptionsMenu()
                true
            }
            R.id.action_generate_order -> {
                generateOrderImage()
                true
            }
            R.id.action_help -> {
                showHelpDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleMusic(item: MenuItem) {
        if (isMusicPlaying) {
            mediaPlayer?.pause()
            item.setIcon(R.drawable.ic_music_off) // 需确保有此图标
            Toast.makeText(this, "音律已歇", Toast.LENGTH_SHORT).show()
        } else {
            mediaPlayer?.start()
            item.setIcon(R.drawable.ic_music_note)
            Toast.makeText(this, "丝竹再起", Toast.LENGTH_SHORT).show()
        }
        isMusicPlaying = !isMusicPlaying

        getSharedPreferences("UserPrefs", MODE_PRIVATE).edit {
            putBoolean("music_enabled", isMusicPlaying)
        }
    }

    // --- 古风帮助弹窗：已修复 val 报错 ---
    private fun showHelpDialog() {
        val helpTips = listOf(
            "【回望】下拉画卷可同步云端数据，并重置画卷。",
            "【裁撤】长按品名并向左滑动，可移出该礼品项。",
            "【落款】下单前，请先在顶端栏登记名帖信息。",
            "【寻幽】画卷深远，向上滑动可查看更多礼品。",
            "【成画】按右下保存键或右上图标生成订购清单。",
            "【时限】每件礼品皆有时限，请留意订购截止日。"
        )

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(70, 50, 70, 70)
            setBackgroundColor(Color.parseColor("#F4EFE2"))
        }

        val titleView = TextView(this).apply {
            text = "— 岁时礼序 · 使用指南 —"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#8B4513"))
            setPadding(0, 0, 0, 40)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        container.addView(titleView)

        helpTips.forEach { tip ->
            val tv = TextView(this).apply {
                text = tip
                textSize = 15f
                setTextColor(Color.parseColor("#4A4A4A"))
                setPadding(0, 12, 0, 12)
                // 彻底修复：调用方法而非直接赋值
                setLineSpacing(0f, 1.3f)
                typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            }
            container.addView(tv)
        }

        MaterialAlertDialogBuilder(this)
            .setView(container)
            .setPositiveButton("敬悉", null)
            .show()
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
            Toast.makeText(this, "客户名帖已登记", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialog.show()
    }

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
                gift.customText = etText.text.toString()
                gift.customQuantity = etQuantity.text.toString()
                gift.customDeliveryDate = etDate.text.toString()
                gift.customNotes = etNotes.text.toString()
                gift.isSaved = true
                cacheGiftsLocally()
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "已加入清单", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun generateOrderImage() {
        val activeGifts = myGifts.filter { it.isSaved }
        if (activeGifts.isEmpty()) {
            Toast.makeText(this, "画卷空空，请先「确入画卷」添加礼品", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val name = prefs.getString("saved_name", "匿名官") ?: "匿名官"
        val contact = prefs.getString("saved_contact", "未留联系方式") ?: "未留联系方式"
        val time = prefs.getString("saved_comm_time", "随时可叙") ?: "随时可叙"

        val width = 1080
        var totalHeight = 1100
        val paint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            setShadowLayer(1.5f, 1f, 1f, Color.parseColor("#44000000"))
        }

        val itemHeights = mutableListOf<Float>()
        activeGifts.forEach { gift ->
            paint.textSize = 40f
            val reqLines = splitTextIntoLines("刻花/底款：${gift.customText ?: ""}", 850, paint).size
            val noteLines = splitTextIntoLines("特别叮嘱：${gift.customNotes ?: ""}", 850, paint).size
            val h = 420f + (reqLines * 60f) + (noteLines * 60f)
            itemHeights.add(h)
            totalHeight += h.toInt()
        }

        try {
            val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            canvas.drawColor(Color.parseColor("#F4EFE2"))

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
                val endX = startX + (Math.cos(angle) * length).toFloat()
                val endY = startY + (Math.sin(angle) * length).toFloat()

                canvas.drawLine(startX, startY, endX, endY, texturePaint)

                if (i % 10 == 0) {
                    val dotPaint = Paint(texturePaint).apply { style = Paint.Style.FILL }
                    canvas.drawCircle(startX, startY, random.nextFloat() * 3f + 1f, dotPaint)
                }
            }

            val radialGradient = RadialGradient(width/2f, totalHeight/2f, Math.max(width, totalHeight).toFloat(),
                intArrayOf(Color.TRANSPARENT, Color.parseColor("#12000000")), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width.toFloat(), totalHeight.toFloat(), Paint().apply { shader = radialGradient })

            paint.color = Color.BLACK
            paint.textSize = 85f
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            canvas.drawText("岁时礼序 · 订购清单", width / 2f, 180f, paint)

            paint.textAlign = Paint.Align.LEFT
            paint.isFakeBoldText = false
            paint.textSize = 45f
            paint.color = Color.parseColor("#B22222")
            canvas.drawText("【名帖 · 登记意向】", 100f, 300f, paint)

            paint.color = Color.BLACK
            paint.textSize = 38f
            canvas.drawText("联系人：$name", 130f, 370f, paint)
            canvas.drawText("联系方式：$contact", 130f, 430f, paint)
            canvas.drawText("便利时间：$time", 130f, 490f, paint)

            paint.color = Color.parseColor("#8B4513")
            paint.strokeWidth = 2f
            canvas.drawLine(100f, 540f, width - 100f, 540f, paint)

            var currentY = 650f
            activeGifts.forEachIndexed { index, gift ->
                paint.textSize = 50f
                paint.isFakeBoldText = true
                paint.color = Color.BLACK
                canvas.drawText("${index + 1}. ${gift.name}", 100f, currentY, paint)

                paint.isFakeBoldText = false
                paint.textSize = 38f
                paint.color = Color.parseColor("#8B4513")
                canvas.drawText("数量：${gift.customQuantity ?: "1"}   交货期：${gift.customDeliveryDate ?: "按约"}", 130f, currentY + 80f, paint)

                paint.color = Color.BLACK
                var textY = currentY + 150f
                val reqLines = splitTextIntoLines("刻花/底款：${if(gift.customText.isNullOrEmpty()) "随缘" else gift.customText}", 850, paint)
                reqLines.forEach { canvas.drawText(it, 130f, textY, paint); textY += 60f }
                val noteLines = splitTextIntoLines("特别叮嘱：${if(gift.customNotes.isNullOrEmpty()) "无" else gift.customNotes}", 850, paint)
                noteLines.forEach { canvas.drawText(it, 130f, textY, paint); textY += 60f }

                currentY += itemHeights[index]
                paint.color = Color.parseColor("#338B4513")
                canvas.drawLine(100f, currentY - 50f, width - 100f, currentY - 50f, paint)
            }

            val dateY = totalHeight - 120f
            paint.textAlign = Paint.Align.RIGHT
            paint.color = Color.BLACK
            paint.textSize = 38f
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
            canvas.drawText("生成日期：$today", width - 100f, dateY, paint)

            // 印章绘制
            val sealSize = 150f
            val sealX = width - 480f
            val sealY = dateY - 240f
            val sealPaint = Paint(paint).apply { clearShadowLayer() }
            sealPaint.style = Paint.Style.FILL
            sealPaint.color = Color.parseColor("#B22222")
            canvas.drawRect(sealX, sealY, sealX + sealSize, sealY + sealSize, sealPaint)
            sealPaint.color = Color.WHITE
            sealPaint.textAlign = Paint.Align.CENTER
            sealPaint.isFakeBoldText = true
            sealPaint.textSize = 45f
            canvas.drawText("岁时", sealX + sealSize/2, sealY + 65f, sealPaint)
            canvas.drawText("礼序", sealX + sealSize/2, sealY + 125f, sealPaint)

            saveBitmapToGallery(bitmap)

        } catch (e: Exception) {
            Log.e("Log", "绘制异常: ${e.message}")
        }
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

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 60)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#EEE9DE"))
        }

        val imageView = ImageView(this).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                elevation = 20f
            }
        }

        val hintView = TextView(this).apply {
            text = "「已存至相册」\n愿此礼心诚意满。"
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#8B4513"))
            setPadding(0, 50, 0, 0)
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }

        container.addView(imageView)
        container.addView(hintView)
        scrollView.addView(container)

        MaterialAlertDialogBuilder(this)
            .setTitle("清单预览")
            .setView(scrollView)
            .setPositiveButton("确入", null)
            .show()
    }

    private fun updateEmptyView() { tvEmptyHint?.visibility = if (myGifts.isEmpty()) View.VISIBLE else View.GONE }

    private fun setupTouchHelper(recyclerView: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            override fun isItemViewSwipeEnabled(): Boolean = true

            // --- 核心修复：提高灵敏度 ---

            // 1. 降低触发滑动的比例阈值（默认 0.5f，改为 0.3f 意味着滑出 30% 宽就触发）
            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.3f

            // 2. 提高“逃逸速度”（让快划更容易被捕捉）
            override fun getSwipeEscapeVelocity(defaultValue: Float): Float = defaultValue * 0.5f

            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val gift = myGifts[position]

                MaterialAlertDialogBuilder(this@HomeActivity)
                    .setTitle("裁撤项目")
                    .setMessage("确定要将「${gift.name}」移出画卷吗？")
                    .setPositiveButton("确定") { _, _ -> performDelete(position, gift) }
                    .setNegativeButton("取消") { d, _ ->
                        adapter.notifyItemChanged(position) // 重要：刷新以弹回 Item
                        d.dismiss()
                    }
                    .setCancelable(false)
                    .show()
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                    val itemView = vh.itemView

                    // 绘制红色底色
                    c.drawRect(
                        RectF(itemView.right.toFloat() + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat()),
                        deletePaint
                    )

                    // 绘制“弃”图标
                    val icon = ContextCompat.getDrawable(this@HomeActivity, R.drawable.ic_discard)
                    icon?.let {
                        it.setTint(Color.WHITE)
                        val iconSize = (itemView.height * 0.4).toInt()
                        val margin = (itemView.height - iconSize) / 2
                        val iconTop = itemView.top + margin
                        val iconBottom = itemView.bottom - margin
                        val iconRight = itemView.right - margin
                        val iconLeft = iconRight - iconSize

                        if (Math.abs(dX) > (iconSize + margin)) {
                            it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                            it.draw(c)
                        }
                    }
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun performDelete(position: Int, deletedGift: Gift) {
        myGifts.removeAt(position)
        cacheGiftsLocally()
        adapter.notifyItemRemoved(position)
        updateEmptyView()
    }

    private fun loadGiftsFromServer(isInitial: Boolean = false) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getGifts()
                myGifts.clear(); myGifts.addAll(response)
                adapter.notifyDataSetChanged(); cacheGiftsLocally(); updateEmptyView()
                if (isInitial) getSharedPreferences("DataCache", MODE_PRIVATE).edit { putBoolean("is_first_run", false) }
            } catch (e: Exception) { Log.e("Log", "API Err") }
        }
    }

    private fun loadCachedGifts() {
        val json = getSharedPreferences("DataCache", MODE_PRIVATE).getString("cached_gifts", null)
        if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<MutableList<Gift>>() {}.type
            myGifts.clear(); myGifts.addAll(gson.fromJson(json, type))
            adapter.notifyDataSetChanged()
        }
    }

    private fun refreshGifts(swipe: SwipeRefreshLayout) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getGifts()
                if (response.isNotEmpty()) {
                    myGifts.clear(); myGifts.addAll(response)
                    adapter.notifyDataSetChanged(); cacheGiftsLocally(); updateEmptyView()
                    Toast.makeText(this@HomeActivity, "同步成功", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) { } finally { swipe.isRefreshing = false }
        }
    }

    private fun startBGM() {
        // 从缓存读取用户是否开启了音乐
        isMusicPlaying = getSharedPreferences("UserPrefs", MODE_PRIVATE).getBoolean("music_enabled", true)

        if (mediaPlayer != null) return
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.bg_music)
            mediaPlayer?.apply {
                isLooping = true
                setVolume(0.6f, 0.6f)
                if (isMusicPlaying) start()
            }
        } catch (e: Exception) { }
    }

    private fun cacheGiftsLocally() {
        val json = gson.toJson(myGifts)
        getSharedPreferences("DataCache", MODE_PRIVATE).edit { putString("cached_gifts", json) }
    }

    override fun onResume() { super.onResume(); mediaPlayer?.start() }
    override fun onPause() { super.onPause(); mediaPlayer?.pause() }
    override fun onDestroy() { super.onDestroy(); mediaPlayer?.release(); mediaPlayer = null }
}