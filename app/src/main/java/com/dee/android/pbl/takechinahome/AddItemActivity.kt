package com.dee.android.pbl.takechinahome

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dee.android.pbl.takechinahome.AppDatabase
import com.dee.android.pbl.takechinahome.databinding.ActivityAddItemBinding // 假设你用了 ViewBinding
import com.dee.android.pbl.takechinahome.ExchangeGift
import com.dee.android.pbl.takechinahome.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class AddItemActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddItemBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddItemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPublish.setOnClickListener {
            val title = binding.etTitle.text.toString()
            val description = binding.etDescription.text.toString()

            if (title.isNotEmpty() && description.isNotEmpty()) {
                val newGift = ExchangeGift(
                    id = 0,
                    title = title,
                    story = description,
                    imageUrl = "", // 这里暂时留空，之后可以接上传图片逻辑
                    ownerEmail = "user@example.com" // 这里应获取当前登录用户的Email
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
                // A. 保存到本地 Room
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(this@AddItemActivity)
                    db.exchangeDao().insert(gift)
                }

                // B. 同步到云端 (改用协程方式调用)
                // 注意：这里调用的是 applyExchangeReview，对应 ApiService 中的定义
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.instance.applyExchangeReview(
                        id = gift.id,
                        ownerEmail = gift.ownerEmail,
                        title = gift.title,
                        story = gift.story,
                        imageData = null // 暂时没传图片
                    )
                }

                if (response.success) {
                    Toast.makeText(this@AddItemActivity, "发布成功，等待审核", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@AddItemActivity, "提交失败: ${response.message}", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                // 网络连接失败或解析失败
                android.util.Log.e("AddItem", "Sync Error: ${e.message}")
                Toast.makeText(this@AddItemActivity, "云端同步异常，本地已存入", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}