package com.dee.android.pbl.takechinahome

import android.annotation.SuppressLint
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt

class HomeActivity : AppCompatActivity() {

    private lateinit var adapter: GiftAdapter
    private val myGifts = mutableListOf<Gift>()
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var itemTouchHelper: ItemTouchHelper

    // 【性能优化】：将 Paint 提取为成员变量，避免在 onChildDraw 中重复创建
    private val deletePaint = Paint().apply {
        color = "#B22222".toColorInt()
        isAntiAlias = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // 1. 初始化 BGM
        startBGM()

        // 2. 初始化 RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.giftRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 3. 初始化 ItemTouchHelper 逻辑
        setupTouchHelper(recyclerView)

        // 4. 初始化 Adapter，并设置长按触发滑动
        adapter = GiftAdapter(myGifts) { viewHolder ->
            itemTouchHelper.startSwipe(viewHolder)
        }
        recyclerView.adapter = adapter

        // 5. 加载数据
        loadGiftsFromServer()

        // 6. 下拉刷新逻辑
        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setColorSchemeColors("#8B4513".toColorInt())
        swipeRefreshLayout.setOnRefreshListener {
            // 模拟网络延迟
            Handler(Looper.getMainLooper()).postDelayed({
                refreshGifts(swipeRefreshLayout, recyclerView)
            }, 1500)
        }
    }

    private fun setupTouchHelper(recyclerView: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            override fun isItemViewSwipeEnabled(): Boolean = false

            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val giftToDelete = myGifts[position]

                AlertDialog.Builder(this@HomeActivity)
                    .setTitle("移出画卷")
                    .setMessage("确定要将「${giftToDelete.name}」移出您的岁时礼序吗？")
                    .setPositiveButton("确定") { _, _ ->
                        performDelete(position, giftToDelete, recyclerView)
                    }
                    .setNegativeButton("取消") { dialog, _ ->
                        adapter.notifyItemChanged(position)
                        dialog.dismiss()
                    }
                    .setCancelable(false)
                    .show()
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                val itemView = vh.itemView
                if (dX < 0) {
                    // 使用预先创建的 deletePaint 绘制背景
                    c.drawRect(RectF(itemView.right.toFloat() + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat()), deletePaint)

                    ContextCompat.getDrawable(this@HomeActivity, R.drawable.ic_discard)?.let { icon ->
                        val iconSize = (itemView.height * 0.25).toInt()
                        val margin = (itemView.height - iconSize) / 2
                        icon.setBounds(itemView.right - margin - iconSize, itemView.top + margin, itemView.right - margin, itemView.bottom - margin)
                        icon.setTint(Color.WHITE)
                        icon.draw(c)
                    }
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun performDelete(position: Int, deletedGift: Gift, recyclerView: RecyclerView) {
        myGifts.removeAt(position)
        adapter.notifyItemRemoved(position)

        lifecycleScope.launch {
            try {
                // 假设删除接口，若没有可先注释
                RetrofitClient.instance.deleteGift(deletedGift.id)
            } catch (_: Exception) {
                Log.e("TakeChinaHome", "云端同步失败")
            }
        }

        Snackbar.make(findViewById(android.R.id.content), "已移出：${deletedGift.name}", Snackbar.LENGTH_LONG)
            .setAction("撤销") {
                myGifts.add(position, deletedGift)
                adapter.notifyItemInserted(position)
                recyclerView.scrollToPosition(position)
            }.show()
    }

    private fun loadGiftsFromServer() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getGifts()
                myGifts.clear()
                myGifts.addAll(response)
                @SuppressLint("NotifyDataSetChanged")
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Log.e("TakeChinaHome", "API异常: ${e.message}")
                Toast.makeText(this@HomeActivity, "无法连接到画卷服务器", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshGifts(swipe: SwipeRefreshLayout, rv: RecyclerView) {
        val newItem = Gift(
            id = System.currentTimeMillis().toInt(), // 随机ID
            deadline = "2026-05-05",
            name = "官窑八角杯",
            spec = "高5cm / 青瓷",
            desc = "雨过天晴云破处，者般颜色做将来",
            images = listOf(
                "https://ichessgeek.com/takechinahome/gift100_1.jpg",
                "https://ichessgeek.com/takechinahome/gift100_2.jpg",
                "https://ichessgeek.com/takechinahome/gift100_3.jpg")
        )
        myGifts.add(0, newItem)
        adapter.notifyItemInserted(0)
        rv.scrollToPosition(0)
        swipe.isRefreshing = false
        Toast.makeText(this, "云端画卷已更新", Toast.LENGTH_SHORT).show()
    }

    private fun startBGM() {
        // 防止重复播放导致卡顿
        if (mediaPlayer != null) return

        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.bg_music)
            mediaPlayer?.apply {
                isLooping = true
                setVolume(0.6f, 0.6f)
                start()
            }
        } catch (e: Exception) {
            Log.e("TakeChinaHome", "BGM加载异常", e)
        }
    }

    override fun onResume() {
        super.onResume()
        if (mediaPlayer?.isPlaying == false) mediaPlayer?.start()
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}