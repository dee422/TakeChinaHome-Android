package com.dee.android.pbl.takechinahome

import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExchangeDetailActivity : AppCompatActivity() {

    private lateinit var btnAction: Button
    private lateinit var currentGift: ExchangeGift

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exchange_detail)

        // 1. 获取传递的对象 (支持序列化)
        val gift = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getSerializableExtra("EXTRA_GIFT", ExchangeGift::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("EXTRA_GIFT") as? ExchangeGift
        }

        if (gift == null) {
            Toast.makeText(this, "无法读取物什数据", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        currentGift = gift

        val ivDetail: ImageView = findViewById(R.id.ivDetailImage)
        val tvTitle: TextView = findViewById(R.id.tvDetailTitle)
        val tvOwner: TextView = findViewById(R.id.tvDetailOwner)
        val tvStory: TextView = findViewById(R.id.tvDetailStory)
        val tvWish: TextView? = findViewById(R.id.tvDetailWish)
        val tvContact: TextView? = findViewById(R.id.tvDetailContact)
        btnAction = findViewById(R.id.btnTakeDown)

        // 2. 填充字段
        tvTitle.text = currentGift.itemName
        tvStory.text = currentGift.description
        val displayOwner = if (currentGift.ownerEmail.contains("@")) currentGift.ownerEmail.split("@")[0] else currentGift.ownerEmail
        tvOwner.text = "藏主：$displayOwner"
        tvWish?.text = "置换意向：${if (currentGift.exchangeWish == 2) "售卖" else "置换"}"
        tvContact?.text = "联系暗号：${currentGift.contactCode}"

        // 3. 图片加载逻辑 (支持本地路径与网络路径)
        val rawUrl = currentGift.imageUrl ?: ""
        val loadTarget: Any = when {
            rawUrl.startsWith("http") -> rawUrl
            rawUrl.startsWith("/") -> File(rawUrl)
            rawUrl.isNotEmpty() -> "https://www.ichessgeek.com/takechinahome/uploads/$rawUrl"
            else -> android.R.drawable.ic_menu_gallery
        }

        Glide.with(this)
            .load(loadTarget)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_dialog_alert)
            .into(ivDetail)

        ivDetail.setOnClickListener {
            showFullScreenZoomableImage(this, loadTarget)
        }

        // 4. 权限检查与按钮初始化
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ExchangeDetailActivity)
            val currentUser = withContext(Dispatchers.IO) { db.userDao().getCurrentUser() }

            if (currentUser?.email == currentGift.ownerEmail) {
                btnAction.visibility = View.VISIBLE
                refreshButtonState()
            } else {
                btnAction.visibility = View.GONE
            }
        }
    }

    /**
     * 核心功能：根据 status 刷新按钮 UI
     * status: 1=审核中, 2=已上架, 3=已下架
     */
    private fun refreshButtonState() {
        when (currentGift.status) {
            1 -> {
                btnAction.text = "审核中"
                btnAction.isEnabled = false
                btnAction.setBackgroundColor(Color.LTGRAY)
            }
            2 -> {
                btnAction.text = "撤回下架"
                btnAction.isEnabled = true
                btnAction.setBackgroundColor(Color.parseColor("#E91E63"))
                btnAction.setOnClickListener { performTakeDown() }
            }
            3 -> {
                btnAction.isEnabled = true
                btnAction.text = "重新申请上架"
                btnAction.setBackgroundColor(Color.parseColor("#4CAF50"))
                btnAction.setOnClickListener { performRelist() }
            }
        }
    }

    private fun performTakeDown() {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("正在撤回物什...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.instance.requestTakeDown(currentGift.id, currentGift.ownerEmail)
                }
                progressDialog.dismiss()

                if (response.success) {
                    currentGift.status = 3 // 标记为已下架
                    updateLocalAndUI("物什已撤回")
                } else {
                    showError(response.message)
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(this@ExchangeDetailActivity, "网络异常", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performRelist() {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("正在提交申请...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.instance.relistItem(currentGift.id, currentGift.ownerEmail)
                }
                progressDialog.dismiss()

                if (response.success) {
                    // 【关键修正】重新申请后，状态应为 1 (待审核)
                    currentGift.status = 1
                    updateLocalAndUI("申请已提交，请静候雅鉴")
                } else {
                    showError(response.message)
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(this@ExchangeDetailActivity, "网络异常", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 提取公共的本地更新逻辑
    private suspend fun updateLocalAndUI(msg: String) {
        withContext(Dispatchers.IO) {
            AppDatabase.getDatabase(this@ExchangeDetailActivity).exchangeDao().update(currentGift)
        }
        Toast.makeText(this@ExchangeDetailActivity, msg, Toast.LENGTH_SHORT).show()
        refreshButtonState()
        setResult(RESULT_OK)
    }

    private fun showError(msg: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("操作失败")
            .setMessage(msg)
            .setPositiveButton("好", null)
            .show()
    }

    private fun showFullScreenZoomableImage(context: Context, loadTarget: Any) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val photoView = PhotoView(context)
        photoView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        photoView.scaleType = ImageView.ScaleType.FIT_CENTER
        dialog.setContentView(photoView)
        Glide.with(context).load(loadTarget).into(photoView)
        photoView.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}