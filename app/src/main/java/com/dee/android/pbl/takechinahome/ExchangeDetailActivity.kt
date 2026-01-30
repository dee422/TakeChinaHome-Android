package com.dee.android.pbl.takechinahome

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExchangeDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exchange_detail)

        // 1. 获取传递的对象
        val gift = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getSerializableExtra("EXTRA_GIFT", ExchangeGift::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("EXTRA_GIFT") as? ExchangeGift
        }

        if (gift == null) {
            Toast.makeText(this, "无法读取物什数据", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val ivDetail: ImageView = findViewById(R.id.ivDetailImage)
        val tvTitle: TextView = findViewById(R.id.tvDetailTitle)
        val tvOwner: TextView = findViewById(R.id.tvDetailOwner)
        val tvStory: TextView = findViewById(R.id.tvDetailStory)

        // 使用安全查找，防止布局中缺失 ID 导致报错
        val tvWish: TextView? = findViewById(R.id.tvDetailWish)
        val tvContact: TextView? = findViewById(R.id.tvDetailContact)
        val btnTakeDown: Button = findViewById(R.id.btnTakeDown)

        // 2. 【核心修正】填充标准化字段 (对齐 swap_items 表结构)
        tvTitle.text = gift.itemName
        tvStory.text = gift.description

        val displayOwner = if (gift.ownerEmail.contains("@")) gift.ownerEmail.split("@")[0] else gift.ownerEmail
        tvOwner.text = "藏主：$displayOwner"

        // 只有当布局中存在这些 TextView 时才赋值
        tvWish?.text = "置换意向：${if (gift.exchangeWish == 2) "售卖" else "置换"}"
        tvContact?.text = "联系暗号：${gift.contactCode}"

        // 3. 健壮的图片加载逻辑
        val rawUrl = gift.imageUrl ?: ""
        val loadTarget: Any = when {
            rawUrl.startsWith("http") -> rawUrl
            rawUrl.startsWith("/") -> File(rawUrl)
            rawUrl.isNotEmpty() -> "https://www.ichessgeek.com/takechinahome/uploads/$rawUrl"
            else -> android.R.drawable.ic_menu_gallery
        }

        Glide.with(this)
            .load(loadTarget)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_dialog_alert)
            .into(ivDetail)

        // 4. 下架逻辑
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ExchangeDetailActivity)
            val currentUser = withContext(Dispatchers.IO) { db.userDao().getCurrentUser() }

            // 逻辑检查：必须是本人且状态为“已上架”
            if (currentUser?.email == gift.ownerEmail && gift.status == 2) {
                btnTakeDown.visibility = View.VISIBLE
                btnTakeDown.setOnClickListener { performTakeDown(gift) }
            } else {
                btnTakeDown.visibility = View.GONE
            }
        }
    }

    private fun performTakeDown(item: ExchangeGift) {
        lifecycleScope.launch {
            try {
                // 调用接口同步云端状态
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.instance.requestTakeDown(item.id, item.ownerEmail)
                }
                if (response.success) {
                    withContext(Dispatchers.IO) {
                        item.status = 3 // 设置为已下架
                        AppDatabase.getDatabase(this@ExchangeDetailActivity).exchangeDao().update(item)
                    }
                    Toast.makeText(this@ExchangeDetailActivity, "物什已撤回", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@ExchangeDetailActivity, response.message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ExchangeDetailActivity, "网络连接失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
}