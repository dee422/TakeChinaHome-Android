package com.dee.android.pbl.takechinahome

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class ExchangeActivity : AppCompatActivity() {

    private lateinit var rvExchange: RecyclerView
    private lateinit var tvVipHint: TextView
    private lateinit var fabUpload: FloatingActionButton
    private lateinit var adapter: ExchangeAdapter
    private val exchangeList = mutableListOf<ExchangeGift>()
    private var currentUserEmail: String = "" // 新增：保存当前用户Email

    private var ivPreview: ImageView? = null
    private var selectedImageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            ivPreview?.setImageURI(it)
            ivPreview?.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exchange)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarExchange)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        rvExchange = findViewById(R.id.rvExchange)
        tvVipHint = findViewById(R.id.tvVipHint)
        fabUpload = findViewById(R.id.fabUpload)

        rvExchange.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)

        // 【核心修复】初始化 Adapter，传入 4 个参数
        adapter = ExchangeAdapter(
            exchangeList,
            currentUserEmail, // 初始为空，会在 checkVipStatus 中更新
            onItemClick = { item ->
                val intent = Intent(this, ExchangeDetailActivity::class.java)
                intent.putExtra("EXTRA_GIFT", item)
                startActivity(intent)
            },
            onItemLongClick = { item ->
                // 调用长按删除处理函数
                handleLongClickDelete(item)
            }
        )
        rvExchange.adapter = adapter

        checkVipStatusAndLoadData()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun checkVipStatusAndLoadData() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ExchangeActivity)
            val user = withContext(Dispatchers.IO) { db.userDao().getCurrentUser() }

            if (user != null) {
                currentUserEmail = user.email ?: ""
                // 更新 Adapter 里的 Email 权限信息
                adapter = ExchangeAdapter(exchangeList, currentUserEmail, { item ->
                    val intent = Intent(this@ExchangeActivity, ExchangeDetailActivity::class.java)
                    intent.putExtra("EXTRA_GIFT", item)
                    startActivity(intent)
                }, { item -> handleLongClickDelete(item) })
                rvExchange.adapter = adapter

                tvVipHint.text = "雅鉴 VIP：已开启发布权限"
                tvVipHint.setTextColor("#4CAF50".toColorInt())
                fabUpload.setOnClickListener { showUploadDialog() }
            }
            syncWithCloud()
        }
    }

    // --- 新增：处理长按删除的业务逻辑 ---
    private fun handleLongClickDelete(item: ExchangeGift) {
        // 权限硬检查
        if (item.ownerEmail != currentUserEmail) {
            Toast.makeText(this, "权限不足：您不是此物什的主人", Toast.LENGTH_SHORT).show()
            return
        }

        if (item.status == 2) {
            // 上架状态不可直接删
            MaterialAlertDialogBuilder(this)
                .setTitle("提示")
                .setMessage("此物什正在市集中，请先进入详情页将其【下架】后再行删除。")
                .setPositiveButton("去详情页") { _, _ ->
                    val intent = Intent(this, ExchangeDetailActivity::class.java)
                    intent.putExtra("EXTRA_GIFT", item)
                    startActivity(intent)
                }
                .setNegativeButton("罢罢", null)
                .show()
        } else {
            // 待审核或已下架，允许删除
            MaterialAlertDialogBuilder(this)
                .setTitle("彻底抹除")
                .setMessage("确定要将此物什从画卷及云端永久移除吗？")
                .setPositiveButton("确定") { _, _ ->
                    performDeleteSync(item)
                }
                .setNegativeButton("罢罢", null)
                .show()
        }
    }

    // --- 新增：执行云端和本地删除 ---
    private fun performDeleteSync(item: ExchangeGift) {
        lifecycleScope.launch {
            try {
                // 1. 同步云端删除
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.instance.deleteExchangeItem(item.id, item.ownerEmail)
                }

                // 2. 本地数据库删除
                withContext(Dispatchers.IO) {
                    // 明确获取 DAO 实例并执行删除
                    val dao = AppDatabase.getDatabase(this@ExchangeActivity).exchangeDao()
                    dao.delete(item)
                }

                Toast.makeText(this@ExchangeActivity, "已从画卷中抹除", Toast.LENGTH_SHORT).show()
                refreshList()
            } catch (e: Exception) {
                Log.e("DeleteError", "原因: ${e.message}")
                Toast.makeText(this@ExchangeActivity, "网络异常，本地已先清理", Toast.LENGTH_SHORT).show()

                withContext(Dispatchers.IO) {
                    // 同样在此处明确 DAO 实例
                    val dao = AppDatabase.getDatabase(this@ExchangeActivity).exchangeDao()
                    dao.delete(item)
                }
                refreshList()
            }
        }
    }

    private fun showUploadDialog() {
        val v = layoutInflater.inflate(R.layout.dialog_upload_exchange, null)
        val etName = v.findViewById<EditText>(R.id.etUploadTitle)
        val etStory = v.findViewById<EditText>(R.id.etUploadStory)
        val etContact = v.findViewById<EditText>(R.id.etUploadContact)
        val rgWish = v.findViewById<RadioGroup>(R.id.rgExchangeWish)
        val btnSelect = v.findViewById<Button>(R.id.btnSelectImage)
        ivPreview = v.findViewById(R.id.ivUploadPreview)

        btnSelect.setOnClickListener { pickImageLauncher.launch("image/*") }

        MaterialAlertDialogBuilder(this)
            .setTitle("刊登雅鉴")
            .setView(v)
            .setPositiveButton("确认为发布") { _, _ ->
                val name = etName.text.toString().trim()
                val story = etStory.text.toString().trim()
                val contact = etContact.text.toString().trim()
                val wish = if (rgWish?.checkedRadioButtonId == R.id.rbSell) 2 else 1

                if (name.isNotEmpty() && selectedImageUri != null) {
                    saveExchangeItem(name, story, contact, wish, selectedImageUri!!)
                } else {
                    Toast.makeText(this, "品名与图片不可为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("罢罢", null)
            .show()
    }

    private fun saveExchangeItem(name: String, story: String, contact: String, wish: Int, uri: Uri) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ExchangeActivity)
            val user = withContext(Dispatchers.IO) { db.userDao().getCurrentUser() }

            val compressedFile = withContext(Dispatchers.IO) {
                getCompressedImageFile(this@ExchangeActivity, uri)
            }
            val localPath = saveCompressedImageToInternal(compressedFile)

            val newItem = ExchangeGift(
                id = 0,
                ownerEmail = user?.email ?: "anonymous",
                itemName = name,
                description = story,
                imageUrl = localPath,
                status = 1,
                contactCode = contact,
                exchangeWish = wish
            )

            withContext(Dispatchers.IO) { db.exchangeDao().insert(newItem) }
            refreshList()
            saveAndSyncExchange(newItem)
        }
    }

    private fun saveAndSyncExchange(gift: ExchangeGift) {
        lifecycleScope.launch {
            try {
                val base64Data = fileToBase64(gift.imageUrl) ?: ""
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.instance.applyExchangeReview(
                        id = gift.id,
                        owner_email = gift.ownerEmail,
                        item_name = gift.itemName,
                        description = gift.description,
                        image_data = base64Data,
                        contact_code = gift.contactCode,
                        exchange_wish = gift.exchangeWish
                    )
                }

                if (response.success) {
                    Toast.makeText(this@ExchangeActivity, "已递交云端雅赏", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SyncError", "原因: ${e.message}")
            }
        }
    }

    private suspend fun saveCompressedImageToInternal(compressedFile: File): String = withContext(Dispatchers.IO) {
        val permanentFile = File(filesDir, "ex_${System.currentTimeMillis()}.jpg")
        try {
            compressedFile.renameTo(permanentFile)
            permanentFile.absolutePath
        } catch (e: Exception) { "" }
    }

    private fun fileToBase64(filePath: String): String? {
        return try {
            val file = File(filePath)
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }

    private fun syncWithCloud() {
        lifecycleScope.launch {
            try {
                val remoteData = withContext(Dispatchers.IO) { RetrofitClient.instance.getMarketGifts() }
                if (remoteData.isNotEmpty()) {
                    val db = AppDatabase.getDatabase(this@ExchangeActivity)
                    withContext(Dispatchers.IO) { db.exchangeDao().insertAll(remoteData) }
                    refreshList()
                }
            } catch (e: Exception) {
                refreshList()
            }
        }
    }

    private fun refreshList() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ExchangeActivity)
            val newList = withContext(Dispatchers.IO) { db.exchangeDao().getAllExchangeGifts() }
            exchangeList.clear()
            exchangeList.addAll(newList)
            adapter.updateData(exchangeList)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun getCompressedImageFile(context: Context, uri: Uri): File {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }

        val maxWidth = 720f // 进一步降低尺寸提高效率
        val maxHeight = 1280f
        var inSampleSize = 1
        if (options.outWidth > maxWidth || options.outHeight > maxHeight) {
            inSampleSize = Math.max(Math.round(options.outWidth / maxWidth), Math.round(options.outHeight / maxHeight))
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = inSampleSize
        val scaledBitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }

        val compressedFile = File(context.cacheDir, "post_${System.currentTimeMillis()}.jpg")
        FileOutputStream(compressedFile).use { out ->
            scaledBitmap?.compress(Bitmap.CompressFormat.JPEG, 60, out) // 60 质量
        }
        return compressedFile
    }
}