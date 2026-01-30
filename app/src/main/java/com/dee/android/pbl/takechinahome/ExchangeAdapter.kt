package com.dee.android.pbl.takechinahome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class ExchangeAdapter(
    private var items: List<ExchangeGift>,
    private val onItemClick: (ExchangeGift) -> Unit
) : RecyclerView.Adapter<ExchangeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivItem: ImageView = view.findViewById(R.id.ivExchangeItem)
        val tvTitle: TextView = view.findViewById(R.id.tvExchangeTitle)
        val tvOwner: TextView = view.findViewById(R.id.tvExchangeOwner)
        val tvStatus: TextView = view.findViewById(R.id.tvExchangeStatus)
        // 对应 XML 中的置换意向文本框
        val tvWant: TextView? = view.findViewById(R.id.tvExchangeWant)
    }

    fun updateData(newList: List<ExchangeGift>) {
        this.items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_exchange, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // 1. 【核心修正】基础文字信息绑定：使用 itemName 替换 title
        holder.tvTitle.text = item.itemName

        val displayOwner = if (item.ownerEmail.contains("@")) item.ownerEmail.split("@")[0] else item.ownerEmail
        holder.tvOwner.text = "藏主: $displayOwner"

        // 2. 状态映射 (规格第8项)
        holder.tvStatus.text = when(item.status) {
            1 -> "待审核"
            2 -> "已上架"
            3 -> "已下架"
            4 -> "处理中"
            else -> "未知"
        }

        // 3. 置换意向映射 (规格第5项)
        // 逻辑：1对应置换，2对应售卖
        holder.tvWant?.text = if (item.exchangeWish == 2) "意向: 售卖" else "意向: 置换"

        // 4. 健壮的图片加载逻辑
        val rawUrl = item.imageUrl ?: ""
        val loadTarget: Any = when {
            // 情况A：已经是完整的网络URL
            rawUrl.startsWith("http") -> rawUrl

            // 情况B：是手机本地路径（针对刚拍摄/选取的图片）
            rawUrl.startsWith("/") -> File(rawUrl)

            // 情况C：只是一个文件名（来自云端同步，拼接到标准 uploads 路径）
            rawUrl.isNotEmpty() -> "https://www.ichessgeek.com/takechinahome/uploads/$rawUrl"

            // 情况D：路径为空
            else -> android.R.drawable.ic_menu_gallery
        }

        Glide.with(holder.itemView.context)
            .load(loadTarget)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_dialog_alert)
            .into(holder.ivItem)

        // 5. 点击事件：跳转详情
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}