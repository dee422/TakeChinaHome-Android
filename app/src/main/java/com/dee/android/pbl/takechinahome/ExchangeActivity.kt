package com.dee.android.pbl.takechinahome

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class ExchangeActivity : AppCompatActivity() {

    private lateinit var rvExchange: RecyclerView
    private lateinit var tvVipHint: TextView
    private lateinit var fabUpload: FloatingActionButton
    private val exchangeList = mutableListOf<Gift>() // 暂时复用 Gift 模型进行演示

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exchange)

        // 1. 初始化视图
        rvExchange = findViewById(R.id.rvExchange)
        tvVipHint = findViewById(R.id.tvVipHint)
        fabUpload = findViewById(R.id.fabUpload)

        // 2. 配置瀑布流布局 (2列)
        rvExchange.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)

        // 3. 核心逻辑：检查 VIP 状态并更新 UI
        checkVipStatusAndLoadData()

        // 4. 悬浮按钮点击逻辑
        fabUpload.setOnClickListener {
            // 理论上只有开启状态才能点击，这里可以写跳转上传页面的逻辑
            Toast.makeText(this, "雅鉴 VIP 身份确认，请开启您的分享", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 读取用户信息，根据引荐人数动态调整置换权限
     */
    private fun checkVipStatusAndLoadData() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ExchangeActivity)
            val currentUser = db.userDao().getCurrentUser()

            if (currentUser == null) {
                Toast.makeText(this@ExchangeActivity, "请先登录名帖", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            val targetCount = 10 // 设定的 VIP 门槛
            val currentCount = currentUser.referralCount

            if (currentCount >= targetCount) {
                // --- 情况 A：已经是 VIP ---
                tvVipHint.text = "雅鉴 VIP：已开启置换分享权限"
                tvVipHint.setTextColor(Color.parseColor("#4CAF50")) // 雅致的绿色

                fabUpload.isEnabled = true
                fabUpload.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#8B4513")) // 主题色
                fabUpload.imageTintList = ColorStateList.valueOf(Color.WHITE)
            } else {
                // --- 情况 B：尚未达标 ---
                val diff = targetCount - currentCount
                tvVipHint.text = "再引荐 $diff 位好友即可升级 VIP，加油！"
                tvVipHint.setTextColor(Color.parseColor("#8B4513"))

                fabUpload.isEnabled = false // 禁用按钮
                fabUpload.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BDBDBD")) // 灰色
                fabUpload.imageTintList = ColorStateList.valueOf(Color.parseColor("#EEEEEE"))

                // 点击灰色按钮时的提示
                fabUpload.setOnClickListener {
                    Toast.makeText(this@ExchangeActivity, "功德未满，暂时无法开启置换功能", Toast.LENGTH_SHORT).show()
                }
            }

            // 模拟加载置换区数据（实际应从服务器获取）
            loadExchangeData()
        }
    }

    private fun loadExchangeData() {
        // 这里后续接入置换区的 API
        // 目前可以先留空，或者加载一些示例数据
    }

    private fun showUploadDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_upload_exchange, null)

        // 注意拼写：findViewById (末尾是字母 d)
        val etTitle = dialogView.findViewById<android.widget.EditText>(R.id.etUploadTitle)
        val etStory = dialogView.findViewById<android.widget.EditText>(R.id.etUploadStory)
        val etWant = dialogView.findViewById<android.widget.EditText>(R.id.etUploadWant)
        val etContact = dialogView.findViewById<android.widget.EditText>(R.id.etUploadContact)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("— 刊登雅鉴 —")
            .setView(dialogView)
            // 关键修复：Lambda 参数必须写 _, _ ->
            .setPositiveButton("发布") { _, _ ->
                val title = etTitle.text.toString().trim()
                if (title.isNotEmpty()) {
                    saveExchangeItem(title, etStory.text.toString(), etWant.text.toString(), etContact.text.toString())
                } else {
                    android.widget.Toast.makeText(this, "请写下雅物名号", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("罢笔", null)
            .show()
    }

    private fun saveExchangeItem(title: String, story: String, want: String, contact: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ExchangeActivity)
            val currentUser = db.userDao().getCurrentUser()

            val newItem = ExchangeGift(
                ownerName = currentUser?.account ?: "匿名雅士",
                title = title,
                story = story,
                want = want,
                contact = contact,
                imageUrl = "" // 图片功能下一步完善
            )

            db.exchangeDao().insertExchangeGift(newItem)
            Toast.makeText(this@ExchangeActivity, "宝贝已入市集", Toast.LENGTH_SHORT).show()
            // 此处后续增加刷新列表的逻辑
        }
    }

    private var selectedImageUri: String = "" // 用于记录选中的图片路径

    // 声明相册启动器
    private val pickImageLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it.toString()
            // 这里需要一个全局变量引用弹窗里的 ImageView 才能实时预览
            // 建议在 showUploadDialog 里设置预览图
        }
    }
}