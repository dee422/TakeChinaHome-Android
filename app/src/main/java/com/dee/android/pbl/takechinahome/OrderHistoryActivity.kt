package com.dee.android.pbl.takechinahome

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OrderHistoryActivity : AppCompatActivity() {

    private lateinit var adapter: OrderHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 创建一个简单的列表视图作为根布局
        val recyclerView = RecyclerView(this).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -1)
            setPadding(40, 40, 40, 40)
            layoutManager = LinearLayoutManager(this@OrderHistoryActivity)
        }
        setContentView(recyclerView)
        supportActionBar?.title = "往期清单记录"

        adapter = OrderHistoryAdapter(emptyList()) { order ->
            // 点击某条历史记录，查看详情
            showOrderDetail(order)
        }
        recyclerView.adapter = adapter

        loadHistoryData()
    }

    private fun loadHistoryData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@OrderHistoryActivity)
            val user = db.userDao().getCurrentUser()
            if (user != null) {
                val history = db.orderHistoryDao().getOrdersByUser(user.email)
                withContext(Dispatchers.Main) {
                    if (history.isEmpty()) {
                        Toast.makeText(this@OrderHistoryActivity, "尚无确入记录", Toast.LENGTH_SHORT).show()
                    }
                    adapter.updateData(history)
                }
            }
        }
    }

    private fun showOrderDetail(order: OrderHistory) {
        // 1. 将 JSON 转回礼品列表
        val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
        val rawList: List<Map<String, Any>> = com.google.gson.Gson().fromJson(order.detailsJson, type)

        // 2. 拼接成易读的文本
        val detailBuilder = StringBuilder()
        rawList.forEachIndexed { index, map ->
            detailBuilder.append("${index + 1}. ${map["name"]}\n")
            detailBuilder.append("   规格：${map["spec"]}\n")
            detailBuilder.append("   数量：${map["qty"]}\n")
            if (!map["note"].toString().isNullOrBlank()) {
                detailBuilder.append("   备注：${map["note"]}\n")
            }
            detailBuilder.append("\n")
        }

        // 3. 弹窗显示
        MaterialAlertDialogBuilder(this)
            .setTitle("确入详情 - ${order.contactName}")
            .setMessage(detailBuilder.toString())
            .setPositiveButton("阅毕", null)
            .show()
    }
}