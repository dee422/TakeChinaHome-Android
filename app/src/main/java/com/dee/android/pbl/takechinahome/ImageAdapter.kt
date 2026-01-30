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

class ImageAdapter(private val imageUrls: List<String>) :
    RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.itemImageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val url = imageUrls[position]

        // 优化 1：添加 crossFade 渐变动画，加载更平滑
        // 优化 2：添加 placeholder 占位图，防止加载过程一片空白
        Glide.with(holder.itemView.context)
            .load(url)
            .placeholder(R.drawable.ic_launcher_background) // 建议换成你的古风占位图
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.imageView)

        holder.itemView.setOnClickListener {
            showFullScreenImage(holder.itemView.context, url)
        }
    }

    private fun showFullScreenImage(context: Context, url: String) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = ImageView(context)

        // 优化 3：设置全屏大图的缩放类型为 fitCenter，保证长图能完整显示而不被剪裁
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        dialog.setContentView(imageView)

        Glide.with(context)
            .load(url)
            .into(imageView)

        imageView.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun getItemCount(): Int = imageUrls.size
}