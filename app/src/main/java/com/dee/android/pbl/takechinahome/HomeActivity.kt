package com.dee.android.pbl.takechinahome

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class HomeActivity : AppCompatActivity() {

    private lateinit var adapter: GiftAdapter
    private val myGifts = mutableListOf<Gift>()
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        startBGM()

        if (myGifts.isEmpty()) {
            myGifts.add(Gift("正月\n十五", "青釉莲花尊", "上元佳节，平安喜乐"))
            myGifts.add(Gift("二月\n初二", "影青斗笠杯", "龙抬头，万物生机"))
            myGifts.add(Gift("三月\n初三", "织金牡丹锦", "春和景明，繁花似锦"))
        }

        val recyclerView = findViewById<RecyclerView>(R.id.giftRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = GiftAdapter(myGifts)
        recyclerView.adapter = adapter

        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#8B4513"))

        swipeRefreshLayout.setOnRefreshListener {
            Handler(Looper.getMainLooper()).postDelayed({
                myGifts.add(0, Gift("清明", "影青刻花碗", "春风化雨，追思先贤"))
                adapter.notifyDataSetChanged()
                swipeRefreshLayout.isRefreshing = false
                Toast.makeText(this, "云端画卷已更新", Toast.LENGTH_SHORT).show()
            }, 2000)
        }

        // --- 修改后的滑动回调 ---
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val deletedGift = myGifts[position]

                myGifts.removeAt(position)
                adapter.notifyItemRemoved(position)

                val snackbar = com.google.android.material.snackbar.Snackbar.make(
                    findViewById(android.R.id.content),
                    "已移出：${deletedGift.name}",
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                )
                snackbar.setActionTextColor(Color.YELLOW)
                snackbar.setAction("撤销") {
                    myGifts.add(position, deletedGift)
                    adapter.notifyItemInserted(position)
                    recyclerView.scrollToPosition(position)
                }
                snackbar.show()
            }

            // 这里就是你要找的绘制方法！手动重写它
            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val paint = Paint()

                if (dX < 0) { // 向左滑动时绘制
                    // 1. 绘制朱砂红背景
                    paint.color = Color.parseColor("#B22222")
                    val background = RectF(
                        itemView.right.toFloat() + dX,
                        itemView.top.toFloat(),
                        itemView.right.toFloat(),
                        itemView.bottom.toFloat()
                    )
                    c.drawRect(background, paint)

                    // 2. 绘制你 AI 制作的“弃”字印章
                    val icon = ContextCompat.getDrawable(this@HomeActivity, R.drawable.ic_discard)
                    icon?.let {
                        // 动态计算印章大小和位置（居中）
                        val iconSize = (itemView.height * 0.5).toInt()
                        val margin = (itemView.height - iconSize) / 2
                        val top = itemView.top + margin
                        val bottom = itemView.bottom - margin
                        val right = itemView.right - margin
                        val left = right - iconSize

                        it.setBounds(left, top, right, bottom)
                        // 即使 SVG 里面没设颜色，这里也可以强制渲染为白色
                        it.setTint(Color.WHITE)
                        it.draw(c)
                    }
                }

                // 记得调用父类方法，否则滑动动画会失效
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun startBGM() {
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.bg_music)
            mediaPlayer?.apply {
                isLooping = true
                setVolume(0.7f, 0.7f)
                start()
            }
        } catch (e: Exception) {
            Log.e("TakeChinaHome", "播放异常: ${e.message}")
        }
    }

    override fun onResume() { super.onResume() ; mediaPlayer?.start() }
    override fun onPause() { super.onPause() ; mediaPlayer?.pause() }
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}