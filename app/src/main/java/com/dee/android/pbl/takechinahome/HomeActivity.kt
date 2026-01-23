package com.dee.android.pbl.takechinahome

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.core.content.edit // 必须导入这个用于 KTX 优化
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var adapter: GiftAdapter
    private val myGifts = mutableListOf<Gift>()
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var itemTouchHelper: ItemTouchHelper
    private val gson = Gson()

    // 【性能优化】：将 Paint 提取为成员变量，避免在 onChildDraw 中重复创建
    private val deletePaint = Paint().apply {
        color = "#B22222".toColorInt()
        isAntiAlias = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        startBGM()

        val recyclerView = findViewById<RecyclerView>(R.id.giftRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        setupTouchHelper(recyclerView)

        adapter = GiftAdapter(myGifts) { viewHolder ->
            itemTouchHelper.startSwipe(viewHolder)
        }
        recyclerView.adapter = adapter

        // 优先从本地缓存加载，实现“退出不重置”
        loadCachedGifts()

        // 只有本地没数据时才去云端加载初始数据
        if (myGifts.isEmpty()) {
            loadGiftsFromServer()
        }

        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setColorSchemeColors("#8B4513".toColorInt())
        swipeRefreshLayout.setOnRefreshListener {
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
        cacheGiftsLocally() // 数据变动立即持久化
        adapter.notifyItemRemoved(position)

        lifecycleScope.launch {
            try {
                RetrofitClient.instance.deleteGift(deletedGift.id)
            } catch (_: Exception) {
                Log.e("TakeChinaHome", "云端同步失败")
            }
        }

        Snackbar.make(findViewById(android.R.id.content), "已移出：${deletedGift.name}", Snackbar.LENGTH_LONG)
            .setAction("撤销") {
                myGifts.add(position, deletedGift)
                cacheGiftsLocally() // 撤销后也要同步持久化
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
                cacheGiftsLocally() // 云端数据拉取成功后缓存一份
            } catch (e: Exception) {
                Log.e("TakeChinaHome", "API异常: ${e.message}")
                Toast.makeText(this@HomeActivity, "无法连接到画卷服务器", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshGifts(swipe: SwipeRefreshLayout, rv: RecyclerView) {
        val newItem = Gift(
            id = System.currentTimeMillis().toInt(),
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
        cacheGiftsLocally() // 刷新增加数据后持久化
        rv.scrollToPosition(0)
        swipe.isRefreshing = false
        Toast.makeText(this, "云端画卷已更新", Toast.LENGTH_SHORT).show()
    }

    private fun startBGM() {
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

    // 保存联系方式逻辑（推荐使用 KTX 扩展函数 edit { ... }）
    private fun saveContactInfo(contact: String) {
        getSharedPreferences("UserPrefs", MODE_PRIVATE).edit {
            putString("saved_contact", contact)
        }
    }

    private fun getSavedContact(): String {
        return getSharedPreferences("UserPrefs", MODE_PRIVATE).getString("saved_contact", "") ?: ""
    }

    private fun cacheGiftsLocally() {
        val json = gson.toJson(myGifts)
        getSharedPreferences("DataCache", Context.MODE_PRIVATE).edit {
            putString("cached_gifts", json)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadCachedGifts() {
        val json = getSharedPreferences("DataCache", Context.MODE_PRIVATE).getString("cached_gifts", null)
        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<MutableList<Gift>>() {}.type
                val cachedList: MutableList<Gift> = gson.fromJson(json, type)
                myGifts.clear()
                myGifts.addAll(cachedList)
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Log.e("TakeChinaHome", "加载缓存失败: ${e.message}")
            }
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