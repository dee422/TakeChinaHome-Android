package com.dee.android.pbl.takechinahome

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.*
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
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

        // 绑定登记意向按钮
        findViewById<View>(R.id.btnRegisterIntent).setOnClickListener {
            showWishFormDialog()
        }

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
            Toast.makeText(this, "客户意向已登记", Toast.LENGTH_SHORT).show()
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
        var totalHeight = 1000
        val paint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
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
            canvas.drawColor(Color.parseColor("#FDF5E6")) // 宣纸背景

            // --- 标题 ---
            paint.color = Color.BLACK
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            canvas.drawText("岁时礼序 · 订购清单", width / 2f, 180f, paint)

            // --- 顶部名帖信息 ---
            paint.textAlign = Paint.Align.LEFT
            paint.isFakeBoldText = false
            paint.textSize = 45f
            paint.color = Color.parseColor("#B22222") // 朱红
            canvas.drawText("【名帖 · 登记意向】", 100f, 300f, paint)

            paint.color = Color.BLACK
            paint.textSize = 38f
            canvas.drawText("联系人：$name", 130f, 370f, paint)
            canvas.drawText("联系方式：$contact", 130f, 430f, paint)
            canvas.drawText("便宜时间：$time", 130f, 490f, paint)

            paint.color = Color.parseColor("#8B4513")
            canvas.drawLine(100f, 540f, width - 100f, 540f, paint)

            // --- 礼品细节 ---
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
                paint.color = Color.parseColor("#DCDCDC")
                canvas.drawLine(100f, currentY - 50f, width - 100f, currentY - 50f, paint)
            }

            // --- 落款区：阴文印章 + 日期 ---
            val dateY = totalHeight - 100f
            paint.textAlign = Paint.Align.RIGHT
            paint.color = Color.BLACK
            paint.textSize = 38f
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
            canvas.drawText("生成日期：$today", width - 100f, dateY, paint)

            // 阴文印章布局
            val sealSize = 150f
            val sealX = width - 480f
            val sealY = dateY - 240f // 在日期斜上方

            // 1. 实心朱红底座
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#B22222")
            canvas.drawRect(sealX, sealY, sealX + sealSize, sealY + sealSize, paint)

            // 2. 白色阴文字
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            paint.textSize = 45f
            canvas.drawText("岁时", sealX + sealSize/2, sealY + 65f, paint)
            canvas.drawText("礼序", sealX + sealSize/2, sealY + 125f, paint)

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
            layoutParams = FrameLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.6).toInt())
        }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40); gravity = android.view.Gravity.CENTER }
        val imageView = ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
        val hintView = TextView(this).apply { text = "「已存至相册」\n请移步系统相册查看完整画卷。"; textSize = 14f; gravity = android.view.Gravity.CENTER; setTextColor(Color.parseColor("#8B4513")); setPadding(0, 40, 0, 0) }
        container.addView(imageView)
        container.addView(hintView)
        scrollView.addView(container)
        MaterialAlertDialogBuilder(this).setTitle("成单预览").setView(scrollView).setPositiveButton("确入", null).show()
    }

    private fun updateEmptyView() { tvEmptyHint?.visibility = if (myGifts.isEmpty()) View.VISIBLE else View.GONE }

    private fun setupTouchHelper(recyclerView: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun isItemViewSwipeEnabled(): Boolean = false
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val gift = myGifts[position]
                AlertDialog.Builder(this@HomeActivity).setTitle("移出画卷").setMessage("确定要移出「${gift.name}」吗？")
                    .setPositiveButton("确定") { _, _ -> performDelete(position, gift) }
                    .setNegativeButton("取消") { d, _ -> adapter.notifyItemChanged(position); d.dismiss() }.show()
            }
            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                if (dX < 0) {
                    val itemView = vh.itemView
                    c.drawRect(RectF(itemView.right.toFloat() + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat()), deletePaint)
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
        if (mediaPlayer != null) return
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.bg_music)
            mediaPlayer?.apply { isLooping = true; setVolume(0.6f, 0.6f); start() }
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