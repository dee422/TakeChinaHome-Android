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
    // 更改为传递具体的数据对象和位置，方便 HomeActivity 处理
    private val onDeleteLongClick: (Gift, Int) -> Unit,
    private val onCustomClick: (Gift) -> Unit
) : RecyclerView.Adapter<GiftAdapter.GiftViewHolder>() {

    class GiftViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // 这里的 ID 需要与你的 item_gift.xml 保持一致
        val imageArea: View? = view.findViewById(R.id.imageArea)
        val nameText: TextView = view.findViewById(R.id.giftNameText)
        val dateText: TextView = view.findViewById(R.id.giftDeadlineText)
        val specText: TextView = view.findViewById(R.id.giftSpecText)
        val descText: TextView = view.findViewById(R.id.giftDescText)
        val carouselRecycler: RecyclerView = view.findViewById(R.id.imageCarouselRecyclerView)
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

        // 1. 数据绑定：将 Gift 对象的属性填入视图
        holder.nameText.text = gift.name
        holder.specText.text = "规格：${gift.spec}"
        holder.descText.text = gift.desc
        holder.dateText.text = "截止: ${gift.deadline}"

        // 2. 配置横向图片轮播
        // --- 原代码 (第 52-55 行) ---
        // holder.carouselRecycler.layoutManager =
        //     LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
        // holder.carouselRecycler.adapter = ImageAdapter(gift.images)


        // --- 修改后的代码 ---
        // 2. 配置横向图片轮播
        holder.carouselRecycler.layoutManager =
            LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)

        // 关键点：增加空判断逻辑。
        // 如果 images 为空（说明是被 Room 忽略了或者是数据库数据），
        // 我们需要确保不传递一个空列表给 ImageAdapter，或者给它一个占位图。
        if (gift.images.isNullOrEmpty()) {
            // 方案 A：如果是数据库读取出的空数据，我们可以尝试手动恢复（如果你有缓存路径）
            // 方案 B：或者先隐藏这个列表，避免显示空白白块
            holder.carouselRecycler.visibility = View.GONE
        } else {
            holder.carouselRecycler.visibility = View.VISIBLE
            holder.carouselRecycler.adapter = ImageAdapter(gift.images)
        }

        // 3. 按钮状态：根据 isSaved 状态显示不同的文字和图标
        if (gift.isSaved) {
            holder.btnWish.text = "已入画卷"
            holder.btnWish.setIconResource(R.drawable.ic_save)
        } else {
            holder.btnWish.text = "确入画卷"
            holder.btnWish.setIconResource(R.drawable.ic_heart)
        }

        // 确入画卷按钮点击事件
        holder.btnWish.setOnClickListener {
            onCustomClick(gift)
        }

        // 4. 核心改动：长按整个卡片触发删除对话框
        // 删除了之前容易冲突的侧滑拦截逻辑，现在滑动完全交给图片轮播
        holder.itemView.setOnLongClickListener {
            onDeleteLongClick(gift, position)
            true // 返回 true 表示我们消费了长按事件
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

        btnSubmit.setOnClickListener {
            val contact = etContact.text.toString()
            if (contact.isBlank()) {
                Toast.makeText(context, "请落笔联系方式", Toast.LENGTH_SHORT).show()
            } else {
                // 具体保存逻辑已移动到 HomeActivity 中处理 UserPrefs
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}