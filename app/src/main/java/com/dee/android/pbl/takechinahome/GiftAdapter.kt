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
import com.google.android.material.button.MaterialButton

class GiftAdapter(
    private val giftList: List<Gift>,
    private val onNameLongClick: (RecyclerView.ViewHolder) -> Unit,
    private val onCustomClick: (Gift) -> Unit // 处理“确入画卷”点击，弹出定制对话框
) : RecyclerView.Adapter<GiftAdapter.GiftViewHolder>() {

    class GiftViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.giftNameText)
        val dateText: TextView = view.findViewById(R.id.giftDeadlineText)
        val specText: TextView = view.findViewById(R.id.giftSpecText)
        val descText: TextView = view.findViewById(R.id.giftDescText)
        val carouselRecycler: RecyclerView = view.findViewById(R.id.imageCarouselRecyclerView)
        // 关键：这里类型必须是 MaterialButton 才能在后面设置 Icon 和 Text
        val btnWish: MaterialButton = view.findViewById(R.id.btnWish)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GiftViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gift, parent, false)
        return GiftViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: GiftViewHolder, position: Int) {
        val gift = giftList[position]

        // 1. 基础数据绑定 (根据 Gift.kt 的定义)
        holder.nameText.text = gift.name
        holder.specText.text = "规格：${gift.spec}"
        holder.descText.text = gift.desc // 修正：对应 Gift.kt 中的 desc 字段
        holder.dateText.text = "截止: ${gift.deadline}"

        // 2. 配置横向图片轮播
        holder.carouselRecycler.layoutManager =
            LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
        holder.carouselRecycler.adapter = ImageAdapter(gift.images)

        // 3. 按钮状态管理
        // 根据 isSaved 字段动态切换文案和图标
        holder.btnWish.text = if (gift.isSaved) "已入画卷" else "确入画卷"
        holder.btnWish.setIconResource(if (gift.isSaved) R.drawable.ic_save else R.drawable.ic_heart)

        // 4. 点击按钮弹出定制对话框 (dialog_gift_custom)
        holder.btnWish.setOnClickListener {
            onCustomClick(gift)
        }

        // 5. 长按名称触发 (用于删除或拖拽)
        holder.nameText.setOnLongClickListener {
            onNameLongClick(holder)
            true
        }

        // 6. 按照要求，彻底取消整个 Item 的点击事件
        holder.itemView.setOnClickListener(null)
    }

    override fun getItemCount() = giftList.size

    // 该方法现在主要由 HomeActivity 调用，用于全局联系人登记
    fun showWishFormDialog(context: Context) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_wish_form, null)
        dialog.setContentView(view)

        // 确保 dialog_wish_form.xml 包含这些 ID
        val etName = view.findViewById<EditText>(R.id.etName)
        val etContact = view.findViewById<EditText>(R.id.etContact)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmitWish)

        btnSubmit.setOnClickListener {
            val contact = etContact.text.toString()
            if (contact.isBlank()) {
                Toast.makeText(context, "请落笔联系方式", Toast.LENGTH_SHORT).show()
            } else {
                // 保存逻辑建议放在 Activity 中通过 SharedPreferences 实现
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}