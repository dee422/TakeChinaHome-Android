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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

class GiftAdapter(
    private val giftList: List<Gift>,
    private val onDeleteLongClick: (Gift, Int) -> Unit,
    private val onCustomClick: (Gift) -> Unit
) : RecyclerView.Adapter<GiftAdapter.GiftViewHolder>() {

    class GiftViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageArea: View? = view.findViewById(R.id.imageArea)
        val nameText: TextView = view.findViewById(R.id.giftNameText)
        val dateText: TextView = view.findViewById(R.id.giftDeadlineText)
        val specText: TextView = view.findViewById(R.id.giftSpecText)
        val descText: TextView = view.findViewById(R.id.giftDescText)
        val carouselRecycler: RecyclerView = view.findViewById(R.id.imageCarouselRecyclerView)
        val btnWish: MaterialButton = view.findViewById(R.id.btnWish)

        // 新增：藏友分享标签的引用
        val layoutShareTag: LinearLayout = view.findViewById(R.id.layoutShareTag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GiftViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gift, parent, false)
        return GiftViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: GiftViewHolder, position: Int) {
        val gift = giftList[position]

        // --- 新增：藏友分享标签逻辑 ---
        if (gift.isFriendShare) {
            holder.layoutShareTag.visibility = View.VISIBLE
            // 强制清除着色器，恢复图片原貌
            val imageView = holder.itemView.findViewById<ImageView>(R.id.ivShareIcon)
            imageView.imageTintList = null
        } else {
            holder.layoutShareTag.visibility = View.GONE
        }

        // 1. 数据绑定
        holder.nameText.text = gift.name
        holder.specText.text = "规格：${gift.spec}"
        holder.descText.text = gift.desc
        holder.dateText.text = "截止: ${gift.deadline}"

        // 2. 配置横向图片轮播
        holder.carouselRecycler.layoutManager =
            LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)

        if (gift.images.isNullOrEmpty()) {
            holder.carouselRecycler.visibility = View.GONE
        } else {
            holder.carouselRecycler.visibility = View.VISIBLE
            holder.carouselRecycler.adapter = ImageAdapter(gift.images)
        }

        // 3. 按钮状态
        if (gift.isSaved) {
            holder.btnWish.text = "已入画卷"
            holder.btnWish.setIconResource(R.drawable.ic_save)
        } else {
            holder.btnWish.text = "确入画卷"
            holder.btnWish.setIconResource(R.drawable.ic_heart)
        }

        holder.btnWish.setOnClickListener {
            onCustomClick(gift)
        }

        // 4. 长按删除
        holder.itemView.setOnLongClickListener {
            onDeleteLongClick(gift, position)
            true
        }
    }

    override fun getItemCount() = giftList.size

    // 该方法用于弹出底部表单（名帖登记）
    fun showWishFormDialog(context: Context) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_wish_form, null)
        dialog.setContentView(view)

        val etContact = view.findViewById<EditText>(R.id.etContact)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmitWish)

        // 回显逻辑：明天我们再优化具体的 Key，现在先保持空
        btnSubmit.setOnClickListener {
            val contact = etContact.text.toString()
            if (contact.isBlank()) {
                Toast.makeText(context, "请落笔联系方式", Toast.LENGTH_SHORT).show()
            } else {
                // 保存逻辑目前在 HomeActivity 处理
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}