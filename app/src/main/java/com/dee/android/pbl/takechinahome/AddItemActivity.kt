package com.dee.android.pbl.takechinahome

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dee.android.pbl.takechinahome.databinding.ActivityAddItemBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddItemActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddItemBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddItemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPublish.setOnClickListener {
            val title = binding.etTitle.text.toString().trim()
            val description = binding.etDescription.text.toString().trim()

            if (title.isNotEmpty() && description.isNotEmpty()) {
                // 实际开发建议从本地数据库获取当前登录用户的 Email
                val currentUserEmail = "user@example.com"

                // 1. 修正：使用对齐后的字段名 itemName 和 description
                val newGift = ExchangeGift(
                    id = 0,
                    ownerEmail = currentUserEmail,
                    itemName = title,       // 修正：title -> itemName
                    description = description, // 修正：story -> description
                    imageUrl = "",
                    status = 1,             // 初始状态：待审核
                    contactCode = "未填写",   // 对应规格第8项
                    exchangeWish = 1        // 对应规格第9项，默认为 1(置换)
                )
                saveAndSync(newGift)
            } else {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveAndSync(gift: ExchangeGift) {
        lifecycleScope.launch {
            try {
                // A. 保存到本地数据库
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(this@AddItemActivity)
                    db.exchangeDao().insert(gift)
                }

                // B. 同步到云端：必须严格匹配 ApiService 中的 @Field 参数名
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.instance.applyExchangeReview(
                        id = gift.id,
                        owner_email = gift.ownerEmail,      // 对齐 ApiService 命名
                        item_name = gift.itemName,          // 对齐 ApiService 命名
                        description = gift.description,     // 对齐 ApiService 命名
                        contact_code = gift.contactCode,    // 对齐 ApiService 命名
                        exchange_wish = gift.exchangeWish,  // 对齐 ApiService 类型 (Int)
                        image_data = null                   // 对齐 ApiService 命名
                    )
                }

                if (response.success) {
                    Toast.makeText(this@AddItemActivity, "发布成功，等待审核", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@AddItemActivity, "提交失败: ${response.message}", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                android.util.Log.e("AddItem", "Sync Error: ${e.message}")
                // 即使网络失败，本地已经保存，符合离线可用原则
                Toast.makeText(this@AddItemActivity, "云端同步异常，本地已存入", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}