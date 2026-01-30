package com.dee.android.pbl.takechinahome

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.github.chrisbanes.photoview.PhotoView

class ImageAdapter(private val imageUrls: List<String>) :
    RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.itemImageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        // 这里的关键是确保 item_image 布局被正确加载
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val url = imageUrls[position]

        // 如果 URL 为空，Glide 默认会显示 placeholder
        Glide.with(holder.itemView.context)
            .load(url)
            .placeholder(R.drawable.ic_launcher_background)
            .error(R.drawable.ic_launcher_background) // 出错时也显示占位图，避免空白
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.imageView)

        holder.itemView.setOnClickListener {
            showFullScreenImage(holder.itemView.context, url)
        }
    }

    private fun showFullScreenImage(context: Context, url: String) {
        // 创建全屏对话框
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        // 关键：将原来的 ImageView 换成 PhotoView
        val photoView = PhotoView(context)
        photoView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // 设置缩放模式为完整显示
        photoView.scaleType = ImageView.ScaleType.FIT_CENTER
        dialog.setContentView(photoView)

        // 加载图片
        Glide.with(context)
            .load(url)
            .placeholder(R.drawable.ic_launcher_background)
            .into(photoView)

        // 点击图片关闭预览
        photoView.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    override fun getItemCount(): Int = imageUrls.size
}