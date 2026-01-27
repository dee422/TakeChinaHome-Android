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
        val tvTime: TextView = view.findViewById(android.R.id.text1)
        val tvContact: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // 使用系统内置的双行列表布局，省去写 XML 的麻烦，也可以自己定义
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.tvTime.text = "确入时间：${item.submitTime}"
        holder.tvContact.text = "当时雅号：${item.contactName}"
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = list.size

    fun updateData(newList: List<OrderHistory>) {
        this.list = newList
        notifyDataSetChanged()
    }
}