package com.dee.android.pbl.takechinahome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GiftAdapter(private val giftList: List<Gift>) :
    RecyclerView.Adapter<GiftAdapter.GiftViewHolder>() {

    // 内部类：就像是画卷上的一个“格位预览”
    class GiftViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateText: TextView = view.findViewById(R.id.giftDate)
        val nameText: TextView = view.findViewById(R.id.giftName)
        val descText: TextView = view.findViewById(R.id.giftDesc)
    }

    // 第一步：准备画布（加载 item_gift.xml）
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GiftViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gift, parent, false)
        return GiftViewHolder(view)
    }

    // 第二步：提笔临摹（把数据填入控件）
    override fun onBindViewHolder(holder: GiftViewHolder, position: Int) {
        val gift = giftList[position]
        holder.dateText.text = gift.date
        holder.nameText.text = gift.name
        holder.descText.text = gift.desc
    }

    // 总共有多少条礼品
    override fun getItemCount() = giftList.size
}