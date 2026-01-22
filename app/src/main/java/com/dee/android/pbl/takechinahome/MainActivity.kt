package com.dee.android.pbl.takechinahome

import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import android.content.Intent

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 确保您已经按上一步教的创建了 activity_main.xml 布局文件
        setContentView(R.layout.activity_main)

        // 1. 找到图片控件
        val logoImage = findViewById<ImageView>(R.id.logoImage)

        // 2. 加载并启动动画
        val fadeInAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_in)
        logoImage.startAnimation(fadeInAnim)

        // 3. 原有的登录逻辑保持不变
        testLogin()
    }

    private fun testLogin() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://www.ichessgeek.com/api/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        // 注意这里是 ::class.java
        val service = retrofit.create(ApiService::class.java)

        service.login("admin", "china123").enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                // 找到界面上的文字控件
                val statusTextView = findViewById<TextView>(R.id.statusText)

                if (response.isSuccessful) {
                    val loginRes = response.body()
                    if (loginRes?.status == "success") {
                        statusTextView.text = "鉴权成功，即将进入..."

                        // 使用 Handler 延迟 1.5 秒跳转，给用户看一眼漂亮 Logo 的时间
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            // 这里暂时用打印模拟跳转，下一课我们教 Intent 跳转
                            Log.d("TakeChinaHome", "跳转到礼品日历页面")
                            // 明确指明是 MainActivity 的上下文
                            val intent = Intent(this@MainActivity, HomeActivity::class.java)
                            startActivity(intent)

                            // 动画过渡
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            finish()
                        }, 1500)
                    } else {
                        statusTextView.text = "审核未通过: ${loginRes?.message}"
                    }
                } else {
                    statusTextView.text = "服务器响应错误: ${response.code()}"
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                val statusTextView = findViewById<TextView>(R.id.statusText)
                statusTextView.text = "连接失败，请检查网络"
                Log.e("TakeChinaHome", "Error: ${t.message}")
            }
        })
    }

// --- 以下类定义请放在类定义大括号外 ---

    data class LoginResponse(
        val status: String,
        val data: LoginData? = null,
        val message: String? = null
    )

    data class LoginData(
        val token: String,
        val role: String,
        val welcome_msg: String
    )

    interface ApiService {
        @FormUrlEncoded
        @POST("login.php")
        fun login(
            @Field("username") user: String,
            @Field("password") pass: String
        ): Call<LoginResponse>
    }
}