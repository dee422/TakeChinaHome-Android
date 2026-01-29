package com.dee.android.pbl.takechinahome

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
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

/**
 * 雅鉴置换市集 - 核心逻辑说明：
 * 1. 检查当前用户的 VIP 身份（引荐人数 > 0）。
 * 2. 从本地 Room 数据库和远程加载置换品列表。
 * 3. 支持图片选取、本地私有目录保存及云端申请上传。
 */
class ExchangeActivity : AppCompatActivity() {

    private lateinit var rvExchange: RecyclerView
    private lateinit var tvVipHint: TextView
    private lateinit var fabUpload: FloatingActionButton
    private lateinit var adapter: ExchangeAdapter
    private val exchangeList = mutableListOf<ExchangeGift>()

    // 图片上传相关
    private var ivPreview: ImageView? = null
    private var selectedImageUri: Uri? = null

    // 图片选取启动器
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                // 申请持久化权限（防止重启后无法读取）
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
            selectedImageUri = it
            ivPreview?.setImageURI(it)
            ivPreview?.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exchange)

        // 1. 设置 Toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarExchange)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "雅鉴置换市集"
        }

        // 2. 初始化视图
        rvExchange = findViewById(R.id.rvExchange)
        tvVipHint = findViewById(R.id.tvVipHint)
        fabUpload = findViewById(R.id.fabUpload)

        // 使用瀑布流布局更显市集美感
        rvExchange.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)

        // 3. 检查权限并加载数据
        checkVipStatusAndLoadData()
    }

    /**
     * 检查当前用户是否有发布权限（VIP 逻辑）
     */
    private fun checkVipStatusAndLoadData() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ExchangeActivity)
            val user = withContext(Dispatchers.IO) { db.userDao().getCurrentUser() }

            if (user != null && user.referralCount >= 0) {
                tvVipHint.text = "雅鉴 VIP：已开启发布权限"
                tvVipHint.setTextColor("#4CAF50".toColorInt())
                fabUpload.setOnClickListener { showUploadDialog() }
            } else {
                tvVipHint.text = "当前身份：访客（邀请好友可开启发布）"
                fabUpload.setOnClickListener {
                    Toast.makeText(this@ExchangeActivity, "引荐人数不足，暂无发布权", Toast.LENGTH_SHORT).show()
                }
            }
            loadExchangeData()
        }
    }

    /**
     * 加载置换列表（先加载本地，再尝试同步云端）
     */
    private fun loadExchangeData() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ExchangeActivity)

            // 1. 先读本地数据库
            val localData = withContext(Dispatchers.IO) { db.exchangeDao().getAllExchangeGifts() }
            exchangeList.clear()
            exchangeList.addAll(localData)

            if (!::adapter.isInitialized) {
                adapter = ExchangeAdapter(exchangeList) { item -> showDetailDialog(item) }
                rvExchange.adapter = adapter
            } else {
                adapter.notifyDataSetChanged()
            }

            // 2. 尝试从网络同步（可选逻辑）
            try {
                val remoteData = withContext(Dispatchers.IO) { RetrofitClient.instance.getMarketGifts() }
                if (remoteData.isNotEmpty()) {
                    exchangeList.clear()
                    exchangeList.addAll(remoteData)
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Log.e("Exchange", "云端同步失败: ${e.message}")
            }
        }
    }

    /**
     * 显示上传弹窗
     */
    private fun showUploadDialog() {
        val v = layoutInflater.inflate(R.layout.dialog_upload_exchange, null)
        val etTitle = v.findViewById<EditText>(R.id.etUploadTitle)
        val etStory = v.findViewById<EditText>(R.id.etUploadStory)
        val btnSelect = v.findViewById<Button>(R.id.btnSelectImage)
        ivPreview = v.findViewById(R.id.ivUploadPreview)

        btnSelect.setOnClickListener { pickImageLauncher.launch("image/*") }

        MaterialAlertDialogBuilder(this)
            .setTitle("刊登雅鉴")
            .setView(v)
            .setPositiveButton("确认为发布") { _, _ ->
                val title = etTitle.text.toString().trim()
                val story = etStory.text.toString().trim()
                if (title.isNotEmpty()) {
                    saveExchangeItem(title, story, selectedImageUri)
                } else {
                    Toast.makeText(this, "品名不可为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("罢罢", null)
            .show()
    }

    /**
     * 保存置换品：保存图片到私有目录 + 存入本地数据库 + 调用接口
     */
    private fun saveExchangeItem(title: String, story: String, uri: Uri?) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ExchangeActivity)
            val user = withContext(Dispatchers.IO) { db.userDao().getCurrentUser() }

            // 1. 处理图片本地化保存
            val localImagePath = uri?.let { saveImageToInternal(it) } ?: ""

            // 2. 构建实体 (状态 1 表示待审核)
            val newItem = ExchangeGift(
                id = UUID.randomUUID().toString(),
                title = title,
                story = story,
                imageUrl = localImagePath,
                ownerEmail = user?.email ?: "anonymous",
                status = 1
            )

            // 3. 写入数据库
            withContext(Dispatchers.IO) { db.exchangeDao().insert(newItem) }

            // 4. 通知适配器
            exchangeList.add(0, newItem)
            adapter.notifyItemInserted(0)
            rvExchange.scrollToPosition(0)

            // 5. 提交服务器异步审核
            try {
                RetrofitClient.instance.applyExchangeReview(
                    newItem.id,
                    newItem.ownerEmail,
                    newItem.title,
                    newItem.story,
                    null // imageData 后期可扩展上传
                )
            } catch (e: Exception) {
                Log.e("Upload", "网络同步失败: ${e.message}")
            }
        }
    }

    /**
     * 显示置换详情弹窗
     */
    private fun showDetailDialog(item: ExchangeGift) {
        val v = layoutInflater.inflate(R.layout.dialog_exchange_detail, null)
        val ivDetail = v.findViewById<ImageView>(R.id.ivDetailImage)
        val tvTitle = v.findViewById<TextView>(R.id.tvDetailTitle)
        val tvStory = v.findViewById<TextView>(R.id.tvDetailStory)
        val tvContact = v.findViewById<TextView>(R.id.tvDetailContact)

        tvTitle.text = item.title
        tvStory.text = item.story
        tvContact.text = "持有者雅号：${item.ownerEmail.split("@")[0]}"

        Glide.with(this)
            .load(item.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(ivDetail)

        MaterialAlertDialogBuilder(this)
            .setView(v)
            .setPositiveButton("敬悉", null)
            .show()
    }

    /**
     * 辅助函数：将选取的 URI 图片复制到 App 私有目录
     */
    private suspend fun saveImageToInternal(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val file = File(filesDir, "exchange_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}