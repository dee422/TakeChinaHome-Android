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
    private val onNameLongClick: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<GiftAdapter.GiftViewHolder>() {

    class GiftViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.giftNameText)
        val dateText: TextView = view.findViewById(R.id.giftDeadlineText)
        val specText: TextView = view.findViewById(R.id.giftSpecText)
        val descText: TextView = view.findViewById(R.id.giftDescText)
        val carouselRecycler: RecyclerView = view.findViewById(R.id.imageCarouselRecyclerView)
        // 修复第一个错误：添加按钮引用
        // 注意：Button 的包名应该是 android.widget.Button 或 com.google.android.material.button.MaterialButton
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

        holder.nameText.setOnLongClickListener {
            onNameLongClick(holder)
            true
        }

        // 处理“投缘”按钮点击
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

    // 修复第二个错误：实现意向表单弹窗
    private fun showWishFormDialog(context: Context, gift: Gift) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_wish_form, null)
        dialog.setContentView(view)

        // 1. 确保 ID 与 dialog_wish_form.xml 一致
        val etContact = view.findViewById<EditText>(R.id.etContact)
        val etRequirement = view.findViewById<EditText>(R.id.etRequirement)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmitWish) // 确保 XML 里有这个 ID
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle) // 对应 XML 中的标题

        tvTitle.text = "登记意向：${gift.name}"

        btnSubmit.setOnClickListener {
            val contact = etContact.text.toString()
            if (contact.isBlank()) {
                Toast.makeText(context, "请留下联系方式", Toast.LENGTH_SHORT).show()
            } else {
                // TODO: 未来调用 Retrofit 发送给管理 APP
                Toast.makeText(context, "记录成功，稍后联系您", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun getItemCount() = giftList.size
}