package com.dee.android.pbl.takechinahome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 往期卷宗适配器
 * 展示内容包含：下单时间、登录邮箱(唯一标识)、账号主雅号(快照)、联络官名帖
 */
class OrderHistoryAdapter(
    private var list: List<OrderHistory>,
    private val onItemClick: (OrderHistory) -> Unit
) : RecyclerView.Adapter<OrderHistoryAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvHistoryTime)
        val tvEmail: TextView = view.findViewById(R.id.tvHistoryEmail)    // 新增：邮箱引用
        val tvOwner: TextView = view.findViewById(R.id.tvHistoryOwner)
        val tvContact: TextView = view.findViewById(R.id.tvHistoryContact)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // 确保您已经按照之前的建议更新了 layout/item_order_history.xml
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]

        // 1. 展示时间
        holder.tvTime.text = "确入时间：${item.submitTime}"

        // 2. 展示账号唯一标识（用于追溯）
        holder.tvEmail.text = "账户归属：${item.userEmail}"

        // 3. 展示账号主雅号（下单时刻的名字快照）
        holder.tvOwner.text = "账号主 (雅号)：${item.accountOwner}"

        // 4. 展示实际联络官信息
        holder.tvContact.text = "联络官 (名帖)：${item.contactName}"

        // 列表点击事件：通常用于查看该订单的详细清单明细
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = list.size

    /**
     * 当从本地数据库加载或刷新卷宗时调用
     */
    fun updateData(newList: List<OrderHistory>) {
        this.list = newList
        notifyDataSetChanged()
    }
}