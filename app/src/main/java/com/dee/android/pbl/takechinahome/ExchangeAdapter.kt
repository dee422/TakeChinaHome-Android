package com.dee.android.pbl.takechinahome

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class ExchangeAdapter(
    private var items: List<ExchangeGift>,
    private val currentUserEmail: String, // 用于权限判断
    private val onItemClick: (ExchangeGift) -> Unit,
    private val onItemLongClick: (ExchangeGift) -> Unit // 长按回调
) : RecyclerView.Adapter<ExchangeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivItem: ImageView = view.findViewById(R.id.ivExchangeItem)
        val tvTitle: TextView = view.findViewById(R.id.tvExchangeTitle)
        val tvOwner: TextView = view.findViewById(R.id.tvExchangeOwner)
        val tvStatus: TextView = view.findViewById(R.id.tvExchangeStatus)
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

        // 1. 文字信息填充
        holder.tvTitle.text = item.itemName ?: "未命名"
        val displayOwner = if (item.ownerEmail.contains("@")) item.ownerEmail.split("@")[0] else item.ownerEmail
        holder.tvOwner.text = "藏主: $displayOwner"

        // 2. 状态逻辑：1审核中，2已上架，3已下架，4处理中
        holder.tvStatus.text = when(item.status) {
            1 -> "待审核"
            2 -> "已上架"
            3 -> "已下架"
            4 -> "处理中"
            else -> "未知"
        }

        // 3. 意向逻辑：1置换，2售卖
        holder.tvWant?.text = if (item.exchangeWish == 2) "意向: 售卖" else "意向: 置换"

        // 4. 图片加载路径逻辑 (对齐你的数据库和本地存储逻辑)
        val rawUrl = item.imageUrl ?: ""
        val loadTarget: Any = when {
            rawUrl.startsWith("http") -> rawUrl
            rawUrl.startsWith("/") -> File(rawUrl)
            rawUrl.isNotEmpty() -> "https://www.ichessgeek.com/takechinahome/uploads/$rawUrl"
            else -> android.R.drawable.ic_menu_gallery
        }

        Glide.with(holder.itemView.context)
            .load(loadTarget)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_dialog_alert)
            .into(holder.ivItem)

        // 5. 点击监听：跳转详情
        holder.itemView.setOnClickListener { onItemClick(item) }

        // 6. 长按监听：只有自己的物什才能触发删除逻辑
        holder.itemView.setOnLongClickListener {
            if (item.ownerEmail == currentUserEmail) {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onItemLongClick(item)
                true
            } else {
                // 别人的东西，长按没有任何反应，或者给个提示
                Toast.makeText(holder.itemView.context, "非本人藏品无法操作", Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    override fun getItemCount() = items.size
}