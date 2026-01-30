package com.dee.android.pbl.takechinahome

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // 建议使用 Glide，如果不使用请根据你的框架调整
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

class GiftAdapter(
    private val giftList: List<Gift>,
    private val onDeleteLongClick: (Gift, Int) -> Unit,
    private val onCustomClick: (Gift) -> Unit
) : RecyclerView.Adapter<GiftAdapter.GiftViewHolder>() {

    class GiftViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.giftNameText)
        val dateText: TextView = view.findViewById(R.id.giftDeadlineText)
        val specText: TextView = view.findViewById(R.id.giftSpecText)
        val descText: TextView = view.findViewById(R.id.giftDescText)
        val carouselRecycler: RecyclerView = view.findViewById(R.id.imageCarouselRecyclerView)
        val btnWish: MaterialButton = view.findViewById(R.id.btnWish)
        val layoutShareTag: LinearLayout = view.findViewById(R.id.layoutShareTag)

        // 用于显示市集单张图片的 ImageView（如果你的布局里有的话，如果没有可以复用 Carousel 的一部分逻辑）
        // 假设我们在 item_gift 布局里有一个专门显示大图的 ImageView 叫 ivMarketImage
        // 或者我们兼容逻辑：给市集数据也套用 ImageAdapter
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GiftViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gift, parent, false)
        return GiftViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: GiftViewHolder, position: Int) {
        val gift = giftList[position]

        // --- 1. 藏友分享标签逻辑 ---
        if (gift.isFriendShare) {
            holder.layoutShareTag.visibility = View.VISIBLE
            holder.dateText.visibility = View.GONE // 市集物品通常没有截止日期，隐藏它更美观
            val imageView = holder.itemView.findViewById<ImageView>(R.id.ivShareIcon)
            imageView?.imageTintList = null
        } else {
            holder.layoutShareTag.visibility = View.GONE
            holder.dateText.visibility = View.VISIBLE
        }

        // --- 2. 基础数据绑定 ---
        holder.nameText.text = gift.name
        holder.specText.text = if (gift.spec.isNotBlank()) "规格：${gift.spec}" else ""
        holder.descText.text = gift.desc
        holder.dateText.text = "截止: ${gift.deadline}"

        // --- 3. 核心修复：兼容市集单图与官方多图轮播 ---
        holder.carouselRecycler.layoutManager =
            LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)

        val finalImages = if (gift.isFriendShare && gift.imageUrl.isNotBlank()) {
            // 如果是市集数据，把单张 imageUrl 包装成 List 给 ImageAdapter 使用
            listOf(gift.imageUrl)
        } else {
            gift.images
        }

        if (finalImages.isNullOrEmpty()) {
            holder.carouselRecycler.visibility = View.GONE
        } else {
            holder.carouselRecycler.visibility = View.VISIBLE
            // 这里复用你已有的 ImageAdapter，它内部应该使用了 Glide 加载地址
            holder.carouselRecycler.adapter = ImageAdapter(finalImages)
        }

        // --- 4. 按钮与交互逻辑 ---
        if (gift.isSaved) {
            holder.btnWish.text = "已入画卷"
            holder.btnWish.setIconResource(R.drawable.ic_save)
        } else {
            holder.btnWish.text = if (gift.isFriendShare) "我也想要" else "确入画卷"
            holder.btnWish.setIconResource(R.drawable.ic_heart)
        }

        holder.btnWish.setOnClickListener { onCustomClick(gift) }

        holder.itemView.setOnLongClickListener {
            onDeleteLongClick(gift, position)
            true
        }
    }

    override fun getItemCount() = giftList.size

    fun showWishFormDialog(context: Context) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_wish_form, null)
        dialog.setContentView(view)

        val etContact = view.findViewById<EditText>(R.id.etContact)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmitWish)

        btnSubmit.setOnClickListener {
            val contact = etContact.text.toString()
            if (contact.isBlank()) {
                Toast.makeText(context, "请落笔联系方式", Toast.LENGTH_SHORT).show()
            } else {
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}