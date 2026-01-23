package com.dee.android.pbl.takechinahome

import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class HomeActivity : AppCompatActivity() {

    private lateinit var adapter: GiftAdapter
    private val myGifts = mutableListOf<Gift>()
    private var mediaPlayer: MediaPlayer? = null // 1. 定义播放器变量

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // --- A. 启动音乐播放 ---
        startBGM()

        // --- B. 初始化数据 ---
        if (myGifts.isEmpty()) {
            myGifts.add(Gift("正月\n十五", "青釉莲花尊", "上元佳节，平安喜乐"))
            myGifts.add(Gift("二月\n初二", "影青斗笠杯", "龙抬头，万物生机"))
            myGifts.add(Gift("三月\n初三", "织金牡丹锦", "春和景明，繁花似锦"))
        }

        // --- C. 设置 RecyclerView ---
        val recyclerView = findViewById<RecyclerView>(R.id.giftRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = GiftAdapter(myGifts)
        recyclerView.adapter = adapter

        // --- D. 设置下拉刷新 ---
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

        // 1. 创建滑动处理回调
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false // 我们不做拖动排序，只做侧滑
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val deletedGift = myGifts[position]

                myGifts.removeAt(position)
                adapter.notifyItemRemoved(position)

                // 使用 android.R.id.content 这是一个通用的方法，指向当前 Activity 的根视图
                val snackbar = com.google.android.material.snackbar.Snackbar.make(
                    findViewById(android.R.id.content),
                    "已移出：${deletedGift.name}",
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                )

                // 自定义颜色让它更显眼（可选）
                snackbar.setActionTextColor(Color.YELLOW)

                snackbar.setAction("撤销") {
                    myGifts.add(position, deletedGift)
                    adapter.notifyItemInserted(position)
                    recyclerView.scrollToPosition(position)
                }
                snackbar.show()
            }
        }

// 5. 将处理工具绑定到 RecyclerView
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    // --- 核心：播放逻辑 ---
    private fun startBGM() {
        try {
            // 注意：这里 R.raw.bg_music 必须与你 res/raw 里的文件名一致
            mediaPlayer = MediaPlayer.create(this, R.raw.bg_music)

            if (mediaPlayer == null) {
                Log.e("TakeChinaHome", "音乐加载失败：请检查 res/raw 目录下是否有 bg_music 文件")
                return
            }

            mediaPlayer?.apply {
                isLooping = true       // 开启循环
                setVolume(0.7f, 0.7f)  // 设置音量 (0.0 到 1.0)
                start()                // 开始播放
            }
            Log.d("TakeChinaHome", "音乐播放器已启动")
        } catch (e: Exception) {
            Log.e("TakeChinaHome", "播放异常: ${e.message}")
        }
    }

    // --- 生命周期管理：防止 App 关了音乐还在响 ---
    override fun onResume() {
        super.onResume()
        mediaPlayer?.start() // 切回 App 恢复播放
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause() // 切到后台暂停播放
    }

    override fun onDestroy() {
        super.onDestroy()
        // 彻底销毁，释放内存资源
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}