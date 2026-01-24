package com.dee.android.pbl.takechinahome

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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

class HomeActivity : AppCompatActivity() {

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

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayShowTitleEnabled(false)
        }

        startBGM()
        tvEmptyHint = findViewById(R.id.tvEmptyHint)

        val recyclerView = findViewById<RecyclerView>(R.id.giftRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        setupTouchHelper(recyclerView)

        adapter = GiftAdapter(myGifts, { viewHolder ->
            itemTouchHelper.startSwipe(viewHolder)
        }, { gift ->
            showGiftDetailDialog(gift)
        })
        recyclerView.adapter = adapter

        loadCachedGifts()

        val prefs = getSharedPreferences("DataCache", MODE_PRIVATE)
        if (prefs.getBoolean("is_first_run", true) && myGifts.isEmpty()) {
            loadGiftsFromServer(isInitial = true)
        }

        updateEmptyView()

        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setColorSchemeColors("#8B4513".toColorInt())
        swipeRefreshLayout.setOnRefreshListener {
            refreshGifts(swipeRefreshLayout)
        }

        findViewById<View>(R.id.fabGenerate).setOnClickListener {
            generateOrderImage()
        }
    }

    private fun showGiftDetailDialog(gift: Gift) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_gift_custom, null)
        val etText = dialogView.findViewById<TextInputEditText>(R.id.etCustomText)
        val etQuantity = dialogView.findViewById<TextInputEditText>(R.id.etCustomQuantity)
        val etDate = dialogView.findViewById<TextInputEditText>(R.id.etCustomDate)
        val etNotes = dialogView.findViewById<TextInputEditText>(R.id.etCustomNotes)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvCustomTitle)

        tvTitle.text = getString(R.string.custom_title_format, gift.name)
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

                gift.isSaved = true // 关键：标记该礼品已定制！

                cacheGiftsLocally()
                Toast.makeText(this, "定制信息已存入「${gift.name}」", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("暂不定制", null)
            .show()
    }

    private fun generateOrderImage() {
        val activeGifts = myGifts.filter { it.isSaved }

        if (activeGifts.isEmpty()) {
            Toast.makeText(this, "尚未确入任何礼品，画卷无从落笔", Toast.LENGTH_SHORT).show()
            return
        }

        val width = 1080
        var totalHeight = 800
        val paint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }

        val itemHeights = mutableListOf<Float>()

        // 1. 动态计算高度（由于增加了日期和备注，每个条目的基础高度稍微调大）
        activeGifts.forEach { gift ->
            paint.textSize = 40f
            val safeReq = gift.customText ?: ""
            val safeNotes = gift.customNotes ?: ""

            // 计算定制需求行数
            val reqLines = splitTextIntoLines("定制需求：$safeReq", 850, paint).size
            // 计算备注行数
            val noteLines = splitTextIntoLines("备注说明：$safeNotes", 850, paint).size

            // 基础高度(380f) + 定制行高 + 备注行高
            val h = 380f + (reqLines * 60f) + (noteLines * 60f)
            itemHeights.add(h)
            totalHeight += h.toInt()
        }

        try {
            val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.parseColor("#FDF5E6"))

            paint.color = Color.BLACK
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            canvas.drawText("岁时礼序 · 订购清单", width / 2f, 220f, paint)

            paint.textAlign = Paint.Align.LEFT
            var currentY = 450f

            activeGifts.forEachIndexed { index, gift ->
                val safeName = gift.name ?: "未知礼品"
                val safeSpec = gift.spec ?: "标准"
                val safeQty = gift.customQuantity ?: "1"
                val safeReq = gift.customText ?: ""
                val safeDate = gift.customDeliveryDate ?: "待定"
                val safeNotes = gift.customNotes ?: ""

                // A. 商品标题
                paint.textSize = 50f
                paint.isFakeBoldText = true
                paint.color = Color.BLACK
                canvas.drawText("${index + 1}. $safeName", 100f, currentY, paint)

                // B. 规格与数量
                paint.textSize = 38f
                paint.isFakeBoldText = false
                paint.color = Color.parseColor("#8B4513")
                canvas.drawText("规格：$safeSpec   数量：$safeQty", 120f, currentY + 80f, paint)

                // C. 意向登记（交货日期）
                paint.color = Color.parseColor("#B22222")
                canvas.drawText("意向日期：$safeDate", 120f, currentY + 140f, paint)

                // D. 定制需求（多行）
                paint.color = Color.BLACK
                var textY = currentY + 210f
                val reqLines = splitTextIntoLines("定制需求：${if(safeReq.isEmpty()) "无" else safeReq}", 850, paint)
                reqLines.forEach { line ->
                    canvas.drawText(line, 120f, textY, paint)
                    textY += 60f
                }

                // E. 备注说明（多行）
                if (safeNotes.isNotEmpty()) {
                    paint.color = Color.GRAY
                    val noteLines = splitTextIntoLines("备注说明：$safeNotes", 850, paint)
                    noteLines.forEach { line ->
                        canvas.drawText(line, 120f, textY, paint)
                        textY += 60f
                    }
                }

                currentY += itemHeights[index]
                paint.color = Color.parseColor("#DCDCDC")
                canvas.drawLine(100f, currentY - 50f, width - 100f, currentY - 50f, paint)
            }

            // 页脚落款
            val footerY = totalHeight - 250f
            paint.color = Color.BLACK
            paint.textSize = 38f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("落款：${getSavedContact()}", width - 120f, footerY, paint)

            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date())
            canvas.drawText("日期：$today", width - 120f, footerY + 80f, paint)

            // 官印
            val sealSize = 120f
            val sealX = width - 580f
            val sealY = footerY - 40f
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#B22222")
            canvas.drawRect(sealX, sealY, sealX + sealSize, sealY + sealSize, paint)
            paint.color = Color.WHITE
            paint.textSize = 35f
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            canvas.drawText("岁时", sealX + sealSize / 2, sealY + 50f, paint)
            canvas.drawText("礼序", sealX + sealSize / 2, sealY + 100f, paint)

            saveBitmapToGallery(bitmap)

        } catch (e: Exception) {
            Log.e("TakeChinaHome", "绘制异常: ${e.message}")
            Toast.makeText(this, "画卷绘制失败", Toast.LENGTH_SHORT).show()
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
            val success = try {
                uri?.let { imageUri ->
                    contentResolver.openOutputStream(imageUri)?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    }
                } != null
            } catch (e: Exception) {
                false
            }

            if (success) {
                showImagePreviewDialog(bitmap)
                Toast.makeText(this@HomeActivity, "清单已入画卷", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@HomeActivity, "画卷保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showImagePreviewDialog(bitmap: Bitmap) {
        // 创建一个滚动视图包裹图片，防止长图被截断
        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.6).toInt() // 对话框最高占屏幕60%
            )
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            gravity = android.view.Gravity.CENTER
        }

        val imageView = ImageView(this).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val hintView = TextView(this).apply {
            text = "「已存至相册」\n请移步系统相册查看完整画卷。"
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#8B4513"))
            setPadding(0, 40, 0, 0)
        }

        container.addView(imageView)
        container.addView(hintView)
        scrollView.addView(container)

        MaterialAlertDialogBuilder(this)
            .setTitle("岁时礼序 · 成单预览")
            .setView(scrollView)
            .setPositiveButton("确入", null)
            .show()
    }

    private fun getSavedContact(): String {
        return getSharedPreferences("UserPrefs", MODE_PRIVATE).getString("saved_contact", "匿名客户") ?: "匿名客户"
    }

    private fun updateEmptyView() {
        tvEmptyHint?.visibility = if (myGifts.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupTouchHelper(recyclerView: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun isItemViewSwipeEnabled(): Boolean = false
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val giftToDelete = myGifts[position]
                AlertDialog.Builder(this@HomeActivity)
                    .setTitle("移出画卷")
                    .setMessage("确定要将「${giftToDelete.name}」移出吗？")
                    .setPositiveButton("确定") { _, _ -> performDelete(position, giftToDelete) }
                    .setNegativeButton("取消") { dialog, _ ->
                        adapter.notifyItemChanged(position)
                        dialog.dismiss()
                    }
                    .setCancelable(false).show()
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                val itemView = vh.itemView
                if (dX < 0) {
                    c.drawRect(RectF(itemView.right.toFloat() + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat()), deletePaint)
                    ContextCompat.getDrawable(this@HomeActivity, R.drawable.ic_discard)?.let { icon ->
                        val iconSize = (itemView.height * 0.25).toInt()
                        val margin = (itemView.height - iconSize) / 2
                        icon.setBounds(itemView.right - margin - iconSize, itemView.top + margin, itemView.right - margin, itemView.bottom - margin)
                        icon.setTint(Color.WHITE)
                        icon.draw(c)
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
        Snackbar.make(findViewById(android.R.id.content), "已移出：${deletedGift.name}", Snackbar.LENGTH_LONG)
            .setAction("撤销") {
                myGifts.add(position, deletedGift)
                cacheGiftsLocally()
                adapter.notifyItemInserted(position)
                updateEmptyView()
            }.show()
    }

    private fun loadGiftsFromServer(isInitial: Boolean = false) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getGifts()
                myGifts.clear()
                myGifts.addAll(response)
                @SuppressLint("NotifyDataSetChanged")
                adapter.notifyDataSetChanged()
                cacheGiftsLocally()
                updateEmptyView()
                if (isInitial) {
                    getSharedPreferences("DataCache", MODE_PRIVATE).edit { putBoolean("is_first_run", false) }
                }
            } catch (e: Exception) {
                Log.e("TakeChinaHome", "API异常: ${e.localizedMessage}")
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadCachedGifts() {
        val json = getSharedPreferences("DataCache", MODE_PRIVATE).getString("cached_gifts", null)
        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<MutableList<Gift>>() {}.type
                val cachedList: MutableList<Gift> = gson.fromJson(json, type)
                myGifts.clear()
                myGifts.addAll(cachedList)
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Log.e("TakeChinaHome", "加载缓存失败: ${e.message}")
            }
        }
    }

    private fun refreshGifts(swipe: SwipeRefreshLayout) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getGifts()
                if (response.isNotEmpty()) {
                    myGifts.clear()
                    myGifts.addAll(response)
                    @SuppressLint("NotifyDataSetChanged")
                    adapter.notifyDataSetChanged()
                    cacheGiftsLocally()
                    updateEmptyView()
                    Toast.makeText(this@HomeActivity, "画卷已重新同步", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this@HomeActivity, "同步失败，请检查网络", Toast.LENGTH_SHORT).show()
            } finally { swipe.isRefreshing = false }
        }
    }

    private fun startBGM() {
        if (mediaPlayer != null) return
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.bg_music)
            mediaPlayer?.apply { isLooping = true; setVolume(0.6f, 0.6f); start() }
        } catch (e: Exception) { Log.e("TakeChinaHome", "BGM加载异常", e) }
    }

    private fun cacheGiftsLocally() {
        val json = gson.toJson(myGifts)
        getSharedPreferences("DataCache", MODE_PRIVATE).edit { putString("cached_gifts", json) }
    }

    override fun onResume() { super.onResume(); mediaPlayer?.start() }
    override fun onPause() { super.onPause(); mediaPlayer?.pause() }
    override fun onDestroy() { super.onDestroy(); mediaPlayer?.release(); mediaPlayer = null }
}