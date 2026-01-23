package com.dee.android.pbl.takechinahome

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

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

        // --- 核心修复：智能判断滑动方向 ---
        holder.carouselRecycler.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = ImageAdapter(gift.images)
            setHasFixedSize(true)

            // 使用 GestureDetector 来区分横向和纵向手势
            val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                    // 如果横向位移大于纵向位移，判定为“划照片”，禁止父布局拦截
                    if (abs(distanceX) > abs(distanceY)) {
                        parent.requestDisallowInterceptTouchEvent(true)
                    } else {
                        // 否则判定为“上下滑列表”，允许父布局拦截并滚动
                        parent.requestDisallowInterceptTouchEvent(false)
                    }
                    return false
                }
            })

            setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                // 当手指抬起时，恢复拦截权限
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    parent.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }

    override fun getItemCount() = giftList.size
}