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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class RegisterActivity : AppCompatActivity() {

    private lateinit var etInviteCode: EditText
    private lateinit var etAccount: EditText
    private lateinit var etPassword: EditText
    private lateinit var etEmail: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // 绑定视图
        etAccount = findViewById(R.id.etRegAccount)
        etPassword = findViewById(R.id.etRegPassword)
        etEmail = findViewById(R.id.etRegEmail)
        etInviteCode = findViewById(R.id.etRegInviteCode)

        val btnSubmit = findViewById<MaterialButton>(R.id.btnRegisterSubmit)
        val tvApply = findViewById<TextView>(R.id.tvApplyInviteCode)

        btnSubmit.setOnClickListener {
            val account = etAccount.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val inputInviteCode = etInviteCode.text.toString().trim()

            // 1. 基础校验
            if (account.isEmpty() || password.isEmpty() || email.isEmpty() || inputInviteCode.isEmpty()) {
                Toast.makeText(this, "请补齐登记信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. 发送到服务器登记
            performServerRegister(account, email, password, inputInviteCode)
        }

        // 邮件申请引荐码逻辑
        tvApply.setOnClickListener {
            val currentAccount = etAccount.text.toString()
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:edward_dee@ichessgeek.com")
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
     * 核心逻辑：使用 OkHttp 发送到服务器 PHP 接口
     */
    private fun performServerRegister(nickname: String, email: String, pass: String, fromCode: String) {
        val client = OkHttpClient()

        // 构造请求参数，必须与 PHP 中的 $_POST 键名一致
        val formBody = FormBody.Builder()
            .add("nickname", nickname)
            .add("email", email)
            .add("password", pass)
            .add("from_invite_code", fromCode)
            .build()

        val request = Request.Builder()
            .url("https://www.ichessgeek.com/api/v1/register.php")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@RegisterActivity, "网络连接失败", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                // 使用 .use 确保 ResponseBody 会被自动关闭，防止内存泄漏
                response.use { resp ->
                    // 注意：这里调用的是 .body() 方法，而不是直接访问 .body 属性
                    val responseData = resp.body()?.string()

                    runOnUiThread {
                        try {
                            if (resp.isSuccessful && responseData != null) {
                                val json = JSONObject(responseData)
                                val status = json.optString("status")
                                val message = json.optString("message")

                                if (status == "success") {
                                    val myNewInviteCode = json.optString("invite_code")
                                    saveToLocalAndJump(nickname, email, myNewInviteCode)
                                } else {
                                    Toast.makeText(this@RegisterActivity, "登记失败：$message", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(this@RegisterActivity, "服务器返回错误: ${resp.code()}", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@RegisterActivity, "数据解析异常", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    /**
     * 成功后的处理：可以存入 SharedPreferences 或 Room
     */
    private fun saveToLocalAndJump(nickname: String, email: String, inviteCode: String) {
        // 使用 lifecycleScope 异步保存到本地 Room 数据库
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@RegisterActivity)

                // 1. 构造要存入本地的对象（确保字段与你的 User 类完全一致）
                val newUser = User(
                    account = nickname, // 或者用 nickname
                    password = "",      // 服务器已加密，本地可以存空或混淆值
                    email = email,
                    invitationCode = inviteCode,
                    isCurrentUser = true,
                    referralCount = 0
                )

                // 2. 清除旧缓存并存入新用户
                db.userDao().clearUsers()
                db.userDao().insertUser(newUser)

                // 3. 执行跳转
                runOnUiThread {
                    Toast.makeText(this@RegisterActivity, "名帖登记成功，画卷开启", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@RegisterActivity, HomeActivity::class.java)
                    // 清空 Activity 栈，防止返回键又回到注册页
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@RegisterActivity, "本地存根失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * 自动识别剪贴板
     */
    override fun onResume() {
        super.onResume()
        checkClipboardForReferralCode()
    }

    private fun checkClipboardForReferralCode() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip()) {
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text?.toString()?.trim() ?: ""
                if (etInviteCode.text.isEmpty() && isFormattedInviteCode(text)) {
                    etInviteCode.setText(text)
                    Toast.makeText(this, "已从您的袖中（剪贴板）自动识别引荐码", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun isFormattedInviteCode(text: String): Boolean {
        // 匹配 8 位（因为 PHP 生成的是 8 位 md5 截取）大写字母或数字
        val regex = Regex("^[A-Z0-9]{8}$")
        return regex.matches(text)
    }
}