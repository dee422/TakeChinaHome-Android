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

class HomeActivity : AppCompatActivity() {

    private lateinit var adapter: GiftAdapter
    private val myGifts = mutableListOf<Gift>()
    private var mediaPlayer: MediaPlayer? = null
    // 需要把 itemTouchHelper 提出来作为成员变量
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        startBGM()

        val recyclerView = findViewById<RecyclerView>(R.id.giftRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 初始化 ItemTouchHelper 逻辑
        setupTouchHelper(recyclerView)

        // 初始化 Adapter，传入长按回调
        adapter = GiftAdapter(myGifts) { viewHolder ->
            // 只有这里被触发时，才手动开启滑动
            itemTouchHelper.startSwipe(viewHolder)
        }
        recyclerView.adapter = adapter

        loadGiftsFromServer()

        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#8B4513"))
        swipeRefreshLayout.setOnRefreshListener {
            Handler(Looper.getMainLooper()).postDelayed({
                val newItem = Gift(
                    id = 101, // 模拟新ID
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
                recyclerView.scrollToPosition(0)
                swipeRefreshLayout.isRefreshing = false
                Toast.makeText(this, "云端画卷已更新", Toast.LENGTH_SHORT).show()
            }, 2000)
        }
    }

    private fun setupTouchHelper(recyclerView: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            // 【核心修复一】：禁用默认的滑动判定
            override fun isItemViewSwipeEnabled(): Boolean = false

            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val giftToDelete = myGifts[position]

                // --- 规则二：确认弹窗 ---
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
                    val paint = Paint().apply { color = Color.parseColor("#B22222") }
                    c.drawRect(RectF(itemView.right.toFloat() + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat()), paint)

                    ContextCompat.getDrawable(this@HomeActivity, R.drawable.ic_discard)?.let { icon ->
                        val iconSize = (itemView.height * 0.2).toInt() // 侧滑图标小一点更精致
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
                val response = RetrofitClient.instance.deleteGift(deletedGift.id)
                if (response.isSuccessful) Log.d("TakeChinaHome", "云端同步成功")
            } catch (e: Exception) {
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
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Log.e("TakeChinaHome", "API异常: ${e.message}")
            }
        }
    }

    private fun startBGM() {
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.bg_music)
            mediaPlayer?.apply { isLooping = true; setVolume(0.7f, 0.7f); start() }
        } catch (e: Exception) { Log.e("TakeChinaHome", "BGM异常") }
    }

    override fun onResume() { super.onResume(); mediaPlayer?.start() }
    override fun onPause() { super.onPause(); mediaPlayer?.pause() }
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}