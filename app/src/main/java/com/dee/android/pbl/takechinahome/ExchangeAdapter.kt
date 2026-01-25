package com.dee.android.pbl.takechinahome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

// 修复点：增加 onItemClick 回调参数
class ExchangeAdapter(
    private val items: List<ExchangeGift>,
    private val onItemClick: (ExchangeGift) -> Unit
) : RecyclerView.Adapter<ExchangeAdapter.ViewHolder>() {

    init {
        // 告知适配器每个项都有唯一 ID
        setHasStableIds(true)
    }

    // 关键修复：使用实体类中的 UUID 的哈希值作为稳定 ID
    override fun getItemId(position: Int): Long {
        return items[position].id.hashCode().toLong()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCover: ImageView = view.findViewById(R.id.ivExchangeCover)
        val tvTitle: TextView = view.findViewById(R.id.tvExchangeTitle)
        val tvOwner: TextView = view.findViewById(R.id.tvExchangeOwner)
        val tvWant: TextView = view.findViewById(R.id.tvExchangeWant)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exchange, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // 1. 立即重置文字，防止复用导致的错乱
        holder.tvTitle.text = item.title
        holder.tvOwner.text = "持有人：${item.ownerName}"
        holder.tvWant.text = "意向：${item.want}"

        // 2. 关键修复：在加载新图前清除之前的图片，并显式处理空路径
        Glide.with(holder.itemView.context).clear(holder.ivCover)

        Glide.with(holder.itemView.context)
            .load(item.imageUrl)
            .placeholder(R.drawable.logo_main)
            .diskCacheStrategy(DiskCacheStrategy.ALL) // 缓存全尺寸图片，加快刷新时的读取速度
            .into(holder.ivCover)

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}