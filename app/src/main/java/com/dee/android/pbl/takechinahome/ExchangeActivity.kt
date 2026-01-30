package com.dee.android.pbl.takechinahome

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

        adapter = ExchangeAdapter(exchangeList) { item ->
            val intent = Intent(this, ExchangeDetailActivity::class.java)
            intent.putExtra("EXTRA_GIFT", item)
            startActivity(intent)
        }
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
                tvVipHint.text = "雅鉴 VIP：已开启发布权限"
                tvVipHint.setTextColor("#4CAF50".toColorInt())
                fabUpload.setOnClickListener { showUploadDialog() }
            }
            syncWithCloud()
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
                // 这里的 R.id.rbSell 必须在 dialog_upload_exchange.xml 中定义
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

            val localPath = saveImageToInternal(uri)

            // 使用最新的字段名：itemName, description, contactCode, exchangeWish
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
                val db = AppDatabase.getDatabase(this@ExchangeActivity)
                val base64Data = uriToBase64(Uri.fromFile(File(gift.imageUrl)))

                // 【核心对齐】这里的参数名必须和 ApiService 中的 @Field 一致
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

    private suspend fun saveImageToInternal(uri: Uri): String = withContext(Dispatchers.IO) {
        val file = File(filesDir, "ex_${System.currentTimeMillis()}.jpg")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (e: Exception) { "" }
    }

    private fun uriToBase64(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(inputStream)
            val out = ByteArrayOutputStream()
            original.compress(Bitmap.CompressFormat.JPEG, 70, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}