package com.dee.android.pbl.takechinahome

import android.content.ClipboardManager
import android.content.Context
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

    private lateinit var etInviteCode: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etAccount = findViewById<EditText>(R.id.etRegAccount)
        val etPassword = findViewById<EditText>(R.id.etRegPassword)
        val etEmail = findViewById<EditText>(R.id.etRegEmail)
        etInviteCode = findViewById(R.id.etRegInviteCode) // 这是他人给的引荐码
        val btnSubmit = findViewById<MaterialButton>(R.id.btnRegisterSubmit)
        val tvApply = findViewById<TextView>(R.id.tvApplyInviteCode)

        btnSubmit.setOnClickListener {
            val account = etAccount.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val inputInviteCode = etInviteCode.text.toString().trim()

            if (account.isEmpty() || password.isEmpty() || email.isEmpty() || inputInviteCode.isEmpty()) {
                Toast.makeText(this, "请补齐登记信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 系统为新用户生成属于他自己的唯一引荐码
            val myNewInvitationCode = generateUniqueInviteCode()

            // 构造用户对象
            val user = User(
                account = account,
                password = password,
                email = email,
                invitationCode = myNewInvitationCode, // 存入系统为他生成的码
                isCurrentUser = true,
                referralCount = 0 // 初始引荐人数为0
            )

            performRegister(user)
        }

        // 邮件申请引荐码逻辑
        tvApply.setOnClickListener {
            val currentAccount = etAccount.text.toString()
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:admin@ichessgeek.com")
                putExtra(Intent.EXTRA_SUBJECT, "申请岁时礼序引荐码")
                putExtra(Intent.EXTRA_TEXT, "申请雅号：${currentAccount}\n申请缘由：")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "未找到邮件客户端", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 方案 B 核心逻辑：自动识别剪贴板中的引荐码
     */
    override fun onResume() {
        super.onResume()
        checkClipboardForReferralCode()
    }

    private fun checkClipboardForReferralCode() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // 检查剪贴板是否有内容
        if (clipboard.hasPrimaryClip()) {
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text?.toString()?.trim() ?: ""

                // 逻辑判断：如果当前输入框为空，且剪贴板内容符合引荐码格式（6位大写字母/数字）
                if (etInviteCode.text.isEmpty() && isFormattedInviteCode(text)) {
                    etInviteCode.setText(text)
                    Toast.makeText(this, "已从您的袖中（剪贴板）自动识别引荐码", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 验证是否为合法的引荐码格式
     */
    private fun isFormattedInviteCode(text: String): Boolean {
        // 匹配 6 位大写字母或数字，且排除掉容易混淆的字符
        val regex = Regex("^[A-Z2-9]{6}$")
        return regex.matches(text)
    }

    /**
     * 生成随机 6 位邀请码（剔除了易混淆的 0, O, 1, I）
     */
    private fun generateUniqueInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6)
            .map { chars.random() }
            .joinToString("")
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

    private suspend fun saveUserLocally(user: User) {
        try {
            val db = AppDatabase.getDatabase(this@RegisterActivity)
            db.userDao().clearUsers()
            db.userDao().insertUser(user)

            Toast.makeText(this@RegisterActivity, "名帖登记成功，画卷开启", Toast.LENGTH_SHORT).show()

            val intent = Intent(this@RegisterActivity, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this@RegisterActivity, "本地存根失败", Toast.LENGTH_LONG).show()
        }
    }
}