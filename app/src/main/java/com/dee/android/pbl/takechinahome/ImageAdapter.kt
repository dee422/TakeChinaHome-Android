package com.dee.android.pbl.takechinahome

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
// 注意：这里已经修复为正确的导入路径，不再包含 .github
import com.bumptech.glide.Glide

class ImageAdapter(private val imageUrls: List<String>) :
    RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    // 内部类：持有 item_image.xml 中的 ImageView 引用
    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.itemImageView)
    }

    // 创建照片墙中每一张图片的“格位”
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    // 核心绑定逻辑：加载图片并处理点击事件
    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val url = imageUrls[position]

        // 使用 Glide 加载图片，centerCrop 确保多图排列整齐
        Glide.with(holder.itemView.context)
            .load(url)
            .centerCrop()
            .into(holder.imageView)

        // 点击监听：触发全屏预览对话框
        holder.itemView.setOnClickListener {
            showFullScreenImage(holder.itemView.context, url)
        }
    }

    // 点击后的全屏预览逻辑
    private fun showFullScreenImage(context: Context, url: String) {
        // 创建全屏对话框
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = ImageView(context)
        dialog.setContentView(imageView)

        // 加载原始大图
        Glide.with(context)
            .load(url)
            .into(imageView)

        // 再次点击大图即可退出预览
        imageView.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // 返回当前礼品的图片总数
    override fun getItemCount(): Int = imageUrls.size
}