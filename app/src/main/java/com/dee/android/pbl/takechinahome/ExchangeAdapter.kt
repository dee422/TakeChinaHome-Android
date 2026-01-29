package com.dee.android.pbl.takechinahome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dee.android.pbl.takechinahome.R

class ExchangeAdapter(
    private val items: List<ExchangeGift>,
    private val onItemClick: (ExchangeGift) -> Unit
) : RecyclerView.Adapter<ExchangeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivItem: ImageView = view.findViewById(R.id.ivExchangeItem)
        val tvTitle: TextView = view.findViewById(R.id.tvExchangeTitle)
        val tvOwner: TextView = view.findViewById(R.id.tvExchangeOwner)
        val tvStatus: TextView = view.findViewById(R.id.tvExchangeStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_exchange, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        holder.tvOwner.text = "藏主: ${item.ownerEmail.split("@")[0]}" // 简化显示邮箱前缀

        // 状态显示逻辑
        holder.tvStatus.text = when(item.status) {
            1 -> "待审核"
            2 -> "已上架"
            3 -> "已下架"
            else -> "画卷中"
        }

        Glide.with(holder.itemView.context)
            .load(item.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(holder.ivItem)

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}