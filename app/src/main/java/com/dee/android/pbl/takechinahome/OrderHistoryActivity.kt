package com.dee.android.pbl.takechinahome

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 往期卷轴页面：展示已确入归卷的历史清单
 */
class OrderHistoryActivity : AppCompatActivity() {

    private lateinit var adapter: OrderHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 动态创建列表视图（宣纸底色视觉）
        val recyclerView = RecyclerView(this).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#F4EFE2")) // 延续宣纸色
            setPadding(32, 32, 32, 32)
            layoutManager = LinearLayoutManager(this@OrderHistoryActivity)
        }
        setContentView(recyclerView)
        supportActionBar?.title = "往期卷宗 · 溯源"

        // 2. 初始化适配器，并定义点击“卷宗”条目时的反馈
        adapter = OrderHistoryAdapter(emptyList()) { order ->
            showOrderDetail(order)
        }
        recyclerView.adapter = adapter

        // 3. 加载本地数据库中的历史数据
        loadHistoryData()
    }

    /**
     * 从本地 Room 数据库获取当前用户的历史记录
     */
    private fun loadHistoryData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@OrderHistoryActivity)
            // 获取当前登录用户，以便过滤属于该账户的卷宗
            val user = db.userDao().getCurrentUser()
            if (user != null) {
                // 根据邮箱查询历史
                val history = db.orderHistoryDao().getOrdersByUser(user.email)
                withContext(Dispatchers.Main) {
                    if (history.isEmpty()) {
                        Toast.makeText(this@OrderHistoryActivity, "卷宗空空，尚无确入记录", Toast.LENGTH_SHORT).show()
                    }
                    adapter.updateData(history)
                }
            }
        }
    }

    /**
     * 核心逻辑：解析 JSON 数据并展示详细的礼品清单
     */
    private fun showOrderDetail(order: OrderHistory) {
        try {
            // 1. 将存储在数据库中的 JSON 字符串反序列化为 Map 列表
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rawList: List<Map<String, Any>> = Gson().fromJson(order.detailsJson, type)

            // 2. 拼接详情文本
            val detailBuilder = StringBuilder()

            // 顶部信息：溯源三要素
            detailBuilder.append("【 归卷存证 】\n")
            detailBuilder.append("确入时间：${order.submitTime}\n")
            detailBuilder.append("账户归属：${order.userEmail}\n")
            detailBuilder.append("当时雅号：${order.accountOwner}\n") // 下单瞬间的名字
            detailBuilder.append("联络官名帖：${order.contactName}\n")
            detailBuilder.append("————————————————\n\n")

            // 礼品明细
            rawList.forEachIndexed { index, map ->
                detailBuilder.append("第 ${index + 1} 选：${map["name"]}\n")
                detailBuilder.append("   规格：${map["spec"]}\n")
                detailBuilder.append("   数量：${map["qty"]}\n")

                val note = map["note"]?.toString()
                if (!note.isNullOrBlank() && note != "无") {
                    detailBuilder.append("   特别叮嘱：$note\n")
                }
                detailBuilder.append("\n")
            }

            // 3. 弹出 Material 风格的详情对话框
            MaterialAlertDialogBuilder(this)
                .setTitle("— 卷宗明细 · ${order.contactName} —")
                .setMessage(detailBuilder.toString())
                .setPositiveButton("阅毕归卷", null)
                .show()

        } catch (e: Exception) {
            Toast.makeText(this, "解析明细失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}