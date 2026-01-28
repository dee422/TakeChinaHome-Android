package com.dee.android.pbl.takechinahome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OrderHistoryAdapter(
    private var list: List<OrderHistory>,
    private val onItemClick: (OrderHistory) -> Unit
) : RecyclerView.Adapter<OrderHistoryAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvHistoryTime)
        val tvOwner: TextView = view.findViewById(R.id.tvHistoryOwner)
        val tvContact: TextView = view.findViewById(R.id.tvHistoryContact)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.tvTime.text = "确入时间：${item.submitTime}"
        // accountOwner 是账号主，contactName 是联络人
        holder.tvOwner.text = "账号主 (雅号)：${item.accountOwner}"
        holder.tvContact.text = "联络官 (名帖)：${item.contactName}"

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = list.size

    fun updateData(newList: List<OrderHistory>) {
        this.list = newList
        notifyDataSetChanged()
    }
}