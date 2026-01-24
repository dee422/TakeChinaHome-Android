package com.dee.android.pbl.takechinahome

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etAccount = findViewById<EditText>(R.id.etRegAccount)
        val etPassword = findViewById<EditText>(R.id.etRegPassword)
        val etEmail = findViewById<EditText>(R.id.etRegEmail)
        val etInviteCode = findViewById<EditText>(R.id.etRegInviteCode)
        val btnSubmit = findViewById<MaterialButton>(R.id.btnRegisterSubmit)
        val tvApply = findViewById<TextView>(R.id.tvApplyInviteCode)

        btnSubmit.setOnClickListener {
            val account = etAccount.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val inviteCode = etInviteCode.text.toString().trim()

            if (account.isEmpty() || password.isEmpty() || email.isEmpty() || inviteCode.isEmpty()) {
                Toast.makeText(this, "请补齐登记信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 构造用户对象
            val user = User(
                account = account,
                password = password,
                email = email,
                invitationCode = inviteCode,
                isCurrentUser = true
            )

            performRegister(user)
        }

        // 邮件申请引荐码逻辑
        tvApply.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:admin@ichessgeek.com")
                putExtra(Intent.EXTRA_SUBJECT, "申请岁时礼序引荐码")
                val currentAccount = findViewById<EditText>(R.id.etRegAccount).text.toString()
                putExtra(Intent.EXTRA_TEXT, "申请雅号：${currentAccount}\n申请缘由：")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "未找到邮件客户端", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performRegister(user: User) {
        lifecycleScope.launch {
            // --- 开发者模式开关 ---
            val isOfflineTest = true // 后端接口上线后，请将此处改为 false

            if (isOfflineTest) {
                saveUserLocally(user)
                return@launch
            }

            try {
                // 1. 尝试同步云端
                val response = RetrofitClient.instance.register(user)
                if (response.success) {
                    saveUserLocally(user)
                } else {
                    Toast.makeText(this@RegisterActivity, "登记失败：${response.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RegisterActivity, "网络波动，无法连接云端", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 将用户信息写入 Room 并进入主页
     */
    private suspend fun saveUserLocally(user: User) {
        try {
            val db = AppDatabase.getDatabase(this@RegisterActivity)
            db.userDao().clearUsers() // 清理旧数据，保证唯一性
            db.userDao().insertUser(user)

            Toast.makeText(this@RegisterActivity, "名帖登记成功，画卷开启", Toast.LENGTH_SHORT).show()

            // 进入主界面
            val intent = Intent(this@RegisterActivity, HomeActivity::class.java)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this@RegisterActivity, "本地存根失败，请检查数据库配置", Toast.LENGTH_LONG).show()
        }
    }
}