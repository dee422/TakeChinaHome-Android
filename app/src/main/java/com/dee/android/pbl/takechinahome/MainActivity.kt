package com.dee.android.pbl.takechinahome

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 视觉：启动 Logo 渐变动画
        val logoImage = findViewById<ImageView>(R.id.logoImage)
        val statusText = findViewById<TextView>(R.id.statusText)
        val fadeInAnim = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        logoImage.startAnimation(fadeInAnim)

        // 2. 核心逻辑：延迟 1.5 秒后根据登录状态分流
        Handler(Looper.getMainLooper()).postDelayed({
            checkUserStatusAndJump()
        }, 1500)
    }

    private fun checkUserStatusAndJump() {
        lifecycleScope.launch {
            // 从 Room 数据库中寻找当前登录的用户
            val db = AppDatabase.getDatabase(this@MainActivity)
            val currentUser = withContext(Dispatchers.IO) {
                db.userDao().getCurrentUser()
            }

            if (currentUser != null) {
                // 情况 A：本地有名帖，直接进入画卷
                val intent = Intent(this@MainActivity, HomeActivity::class.java)
                startActivity(intent)
            } else {
                // 情况 B：本地无名帖（初次安装或已注销），前往登录页面
                val intent = Intent(this@MainActivity, LoginActivity::class.java)
                startActivity(intent)
            }

            // 动画过渡并关闭当前启动页
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }
}