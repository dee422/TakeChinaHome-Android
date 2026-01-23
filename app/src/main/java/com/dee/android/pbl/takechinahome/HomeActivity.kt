package com.dee.android.pbl.takechinahome

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private var tvEmptyHint: TextView? = null

    private val deletePaint = Paint().apply {
        color = "#B22222".toColorInt()
        isAntiAlias = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        startBGM()
        tvEmptyHint = findViewById(R.id.tvEmptyHint)

        val recyclerView = findViewById<RecyclerView>(R.id.giftRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        setupTouchHelper(recyclerView)

        // 绑定三个参数：列表、长按逻辑、点击逻辑
        adapter = GiftAdapter(myGifts, { viewHolder ->
            itemTouchHelper.startSwipe(viewHolder)
        }, { gift ->
            showGiftDetailDialog(gift)
        })
        recyclerView.adapter = adapter

        // 1. 优先加载本地缓存
        loadCachedGifts()

        // 2. 只有第一次安装且本地为空时才自动同步
        val prefs = getSharedPreferences("DataCache", MODE_PRIVATE)
        if (prefs.getBoolean("is_first_run", true) && myGifts.isEmpty()) {
            loadGiftsFromServer(isInitial = true)
        }

        updateEmptyView()

        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setColorSchemeColors("#8B4513".toColorInt())
        swipeRefreshLayout.setOnRefreshListener {
            refreshGifts(swipeRefreshLayout)
        }
    }

    private fun showGiftDetailDialog(gift: Gift) {
        MaterialAlertDialogBuilder(this)
            .setTitle(gift.name)
            .setMessage("【规格】${gift.spec}\n\n${gift.desc}")
            .setPositiveButton("合上画卷", null)
            .show()
    }

    private fun updateEmptyView() {
        tvEmptyHint?.visibility = if (myGifts.isEmpty()) View.VISIBLE else View.GONE
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
                    .setPositiveButton("确定") { _, _ -> performDelete(position, giftToDelete) }
                    .setNegativeButton("取消") { dialog, _ ->
                        adapter.notifyItemChanged(position)
                        dialog.dismiss()
                    }
                    .setCancelable(false).show()
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

    private fun performDelete(position: Int, deletedGift: Gift) {
        myGifts.removeAt(position)
        cacheGiftsLocally()
        adapter.notifyItemRemoved(position)
        updateEmptyView()

        Snackbar.make(findViewById(android.R.id.content), "已移出：${deletedGift.name}", Snackbar.LENGTH_LONG)
            .setAction("撤销") {
                myGifts.add(position, deletedGift)
                cacheGiftsLocally()
                adapter.notifyItemInserted(position)
                updateEmptyView()
            }.show()
    }

    private fun loadGiftsFromServer(isInitial: Boolean = false) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getGifts()
                myGifts.clear()
                myGifts.addAll(response)
                @SuppressLint("NotifyDataSetChanged")
                adapter.notifyDataSetChanged()
                cacheGiftsLocally()
                updateEmptyView()

                if (isInitial) {
                    getSharedPreferences("DataCache", MODE_PRIVATE).edit { putBoolean("is_first_run", false) }
                }
            } catch (e: Exception) { Log.e("TakeChinaHome", "API异常: ${e.message}") }
        }
    }

    private fun refreshGifts(swipe: SwipeRefreshLayout) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getGifts()
                if (response.isNotEmpty()) {
                    myGifts.clear()
                    myGifts.addAll(response)
                    @SuppressLint("NotifyDataSetChanged")
                    adapter.notifyDataSetChanged()
                    cacheGiftsLocally()
                    updateEmptyView()
                    Toast.makeText(this@HomeActivity, "画卷已重新同步", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@HomeActivity, "同步失败，请检查网络", Toast.LENGTH_SHORT).show()
            } finally { swipe.isRefreshing = false }
        }
    }

    private fun startBGM() {
        if (mediaPlayer != null) return
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.bg_music)
            mediaPlayer?.apply { isLooping = true; setVolume(0.6f, 0.6f); start() }
        } catch (e: Exception) { Log.e("TakeChinaHome", "BGM加载异常", e) }
    }

    private fun cacheGiftsLocally() {
        val json = gson.toJson(myGifts)
        getSharedPreferences("DataCache", Context.MODE_PRIVATE).edit { putString("cached_gifts", json) }
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
            } catch (e: Exception) { Log.e("TakeChinaHome", "加载缓存失败") }
        }
    }

    override fun onResume() { super.onResume(); mediaPlayer?.start() }
    override fun onPause() { super.onPause(); mediaPlayer?.pause() }
    override fun onDestroy() { super.onDestroy(); mediaPlayer?.release(); mediaPlayer = null }
}