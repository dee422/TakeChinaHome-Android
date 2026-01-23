package com.dee.android.pbl.takechinahome

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog

class GiftAdapter(
    private val giftList: List<Gift>,
    private val onNameLongClick: (RecyclerView.ViewHolder) -> Unit,
    private val onItemClick: (Gift) -> Unit // 处理详情点击
) : RecyclerView.Adapter<GiftAdapter.GiftViewHolder>() {

    class GiftViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.giftNameText)
        val dateText: TextView = view.findViewById(R.id.giftDeadlineText)
        val specText: TextView = view.findViewById(R.id.giftSpecText)
        val descText: TextView = view.findViewById(R.id.giftDescText)
        val carouselRecycler: RecyclerView = view.findViewById(R.id.imageCarouselRecyclerView)
        val wishButton: View = view.findViewById(R.id.btnWish)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GiftViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gift, parent, false)
        return GiftViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: GiftViewHolder, position: Int) {
        val gift = giftList[position]

        holder.nameText.text = gift.name
        holder.dateText.text = gift.deadline
        holder.specText.text = gift.spec
        holder.descText.text = gift.desc

        // 点击整个条目查看详情
        holder.itemView.setOnClickListener {
            onItemClick(gift)
        }

        // 长按名称触发侧滑
        holder.nameText.setOnLongClickListener {
            onNameLongClick(holder)
            true
        }

        holder.wishButton.setOnClickListener {
            showWishFormDialog(holder.itemView.context, gift)
        }

        holder.carouselRecycler.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = ImageAdapter(gift.images)
            setHasFixedSize(true)
            setOnTouchListener { v, _ ->
                v.parent.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }

    private fun showWishFormDialog(context: Context, gift: Gift) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_wish_form, null)
        dialog.setContentView(view)

        val etContact = view.findViewById<EditText>(R.id.etContact)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmitWish)
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)

        tvTitle.text = "登记意向：${gift.name}"

        btnSubmit.setOnClickListener {
            val contact = etContact.text.toString()
            if (contact.isBlank()) {
                Toast.makeText(context, "请留下联系方式", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "记录成功，稍后联系您", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun getItemCount() = giftList.size
}