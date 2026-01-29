package com.dee.android.pbl.takechinahome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ExchangeAdapter(
    private var items: List<ExchangeGift>, // 改为 var 方便重新赋值
    private val onItemClick: (ExchangeGift) -> Unit
) : RecyclerView.Adapter<ExchangeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivItem: ImageView = view.findViewById(R.id.ivExchangeItem)
        val tvTitle: TextView = view.findViewById(R.id.tvExchangeTitle)
        val tvOwner: TextView = view.findViewById(R.id.tvExchangeOwner)
        val tvStatus: TextView = view.findViewById(R.id.tvExchangeStatus)
    }

    // --- 添加这个方法，解决 ExchangeActivity 中的报错 ---
    fun updateData(newList: List<ExchangeGift>) {
        this.items = newList
        notifyDataSetChanged() // 提醒 RecyclerView 数据变了，赶紧刷新界面
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_exchange, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title

        // 安全处理邮箱分割，防止数据为空时崩溃
        val ownerDisplayName = if (item.ownerEmail.contains("@")) {
            item.ownerEmail.split("@")[0]
        } else {
            item.ownerEmail
        }
        holder.tvOwner.text = "藏主: $ownerDisplayName"

        // 状态显示逻辑
        holder.tvStatus.text = when(item.status) {
            1 -> "待审核"
            2 -> "已上架"
            3 -> "已下架"
            else -> "画卷中" // 对应状态 0
        }

        Glide.with(holder.itemView.context)
            .load(item.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(holder.ivItem)

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}