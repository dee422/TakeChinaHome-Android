package com.dee.android.pbl.takechinahome

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ExchangeActivity : AppCompatActivity() {

    private lateinit var rvExchange: RecyclerView
    private lateinit var tvVipHint: TextView
    private lateinit var fabUpload: FloatingActionButton
    private lateinit var adapter: ExchangeAdapter

    private val exchangeList = mutableListOf<ExchangeGift>()
    private var ivPreview: ImageView? = null
    private var selectedImageUri: Uri? = null

    // 相册启动器：增加 Uri 权限持久化，防止重启或刷新后图片变空白
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                // 关键：获取长期的 Uri 读取权限
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) { e.printStackTrace() }

            selectedImageUri = it
            ivPreview?.setImageURI(it)
            ivPreview?.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exchange)

        // 绑定自定义 Toolbar
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarExchange)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeButtonEnabled(true)
            title = "雅鉴置换市集"
        }

        rvExchange = findViewById(R.id.rvExchange)
        tvVipHint = findViewById(R.id.tvVipHint)
        fabUpload = findViewById(R.id.fabUpload)

        rvExchange.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)

        checkVipStatusAndLoadData()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun checkVipStatusAndLoadData() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ExchangeActivity)
            val currentUser = db.userDao().getCurrentUser() ?: return@launch

            val targetCount = 0
            if (currentUser.referralCount >= targetCount) {
                tvVipHint.text = "雅鉴 VIP：已开启置换分享权限"
                tvVipHint.setTextColor(Color.parseColor("#4CAF50"))
                fabUpload.isEnabled = true
                fabUpload.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#8B4513"))
                fabUpload.setOnClickListener { showUploadDialog() }
            } else {
                val diff = targetCount - currentUser.referralCount
                tvVipHint.text = "再引荐 $diff 位好友即可升级 VIP"
                tvVipHint.setTextColor(Color.parseColor("#8B4513"))
                fabUpload.isEnabled = false
                fabUpload.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BDBDBD"))
                fabUpload.setOnClickListener {
                    Toast.makeText(this@ExchangeActivity, "功德未满，暂无法刊登", Toast.LENGTH_SHORT).show()
                }
            }
            loadExchangeData()
        }
    }

    private fun loadExchangeData() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@ExchangeActivity)
                val data = db.exchangeDao().getAllExchangeGifts()

                exchangeList.clear()
                exchangeList.addAll(data)

                // 解决最后一张图消失的关键：重置跨度分配
                (rvExchange.layoutManager as? StaggeredGridLayoutManager)?.invalidateSpanAssignments()

                val tvEmpty = findViewById<TextView>(R.id.tvEmptyState)
                tvEmpty.visibility = if (exchangeList.isEmpty()) View.VISIBLE else View.GONE
                rvExchange.visibility = if (exchangeList.isEmpty()) View.GONE else View.VISIBLE

                if (!::adapter.isInitialized) {
                    adapter = ExchangeAdapter(exchangeList) { item -> showDetailDialog(item) }
                    rvExchange.adapter = adapter
                } else {
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun showUploadDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_upload_exchange, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etUploadTitle)
        val etStory = dialogView.findViewById<EditText>(R.id.etUploadStory)
        val etWant = dialogView.findViewById<EditText>(R.id.etUploadWant)
        val etContact = dialogView.findViewById<EditText>(R.id.etUploadContact)
        ivPreview = dialogView.findViewById(R.id.ivUploadPreview)
        val btnAddImg = dialogView.findViewById<Button>(R.id.btnSelectImage)

        btnAddImg.setOnClickListener { pickImageLauncher.launch("image/*") }

        MaterialAlertDialogBuilder(this)
            .setTitle("— 刊登雅鉴 —")
            .setView(dialogView)
            .setPositiveButton("发布") { _, _ ->
                val title = etTitle.text.toString().trim()
                if (title.isNotEmpty()) {
                    saveExchangeItem(
                        title,
                        etStory.text.toString(),
                        etWant.text.toString(),
                        etContact.text.toString(),
                        selectedImageUri
                    )
                }
            }
            .setNegativeButton("罢笔") { _, _ -> selectedImageUri = null }
            .show()
    }

    // 关键功能：将相册图片保存到 App 内部私有目录，确保路径永久有效
    private suspend fun saveImageToInternal(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(filesDir, "gift_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun showDetailDialog(item: ExchangeGift) {
        val detailView = layoutInflater.inflate(R.layout.dialog_exchange_detail, null)
        detailView.findViewById<TextView>(R.id.tvDetailTitle).text = item.title
        detailView.findViewById<TextView>(R.id.tvDetailStory).text = item.story
        detailView.findViewById<TextView>(R.id.tvDetailWant).text = "愿易：${item.want}"
        detailView.findViewById<TextView>(R.id.tvDetailContact).text = "暗号：${item.contact}"

        val ivDetail = detailView.findViewById<ImageView>(R.id.ivDetailImage)
        Glide.with(this)
            .load(item.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(ivDetail)

        MaterialAlertDialogBuilder(this)
            .setTitle("藏品详情")
            .setView(detailView)
            .setPositiveButton("知晓了", null)
            .setNeutralButton("下架此物") { _, _ -> confirmDelete(item.id) }
            .show()
    }

    private fun confirmDelete(giftId: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("下架确认")
            .setMessage("确定要将此物移出市集吗？")
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(this@ExchangeActivity)
                    db.exchangeDao().deleteExchangeGift(giftId)
                    loadExchangeData()
                    Toast.makeText(this@ExchangeActivity, "已下架", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("留着", null)
            .show()
    }

    private fun saveExchangeItem(title: String, story: String, want: String, contact: String, uri: Uri?) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ExchangeActivity)
            val currentUser = db.userDao().getCurrentUser()

            // 将选中的图片保存到本地路径
            val finalImagePath = uri?.let { saveImageToInternal(it) } ?: ""

            val newItem = ExchangeGift(
                id = UUID.randomUUID().toString(),
                ownerName = currentUser?.account ?: "匿名雅士",
                title = title,
                story = story,
                want = want,
                contact = contact,
                imageUrl = finalImagePath
            )
            db.exchangeDao().insertExchangeGift(newItem)
            loadExchangeData()
            Toast.makeText(this@ExchangeActivity, "宝贝已入市集", Toast.LENGTH_SHORT).show()
            selectedImageUri = null
        }
    }
}