package com.dee.android.pbl.takechinahome

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.MotionEvent
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // 1. 绑定视图
        etAccount = findViewById(R.id.etRegAccount)
        etPassword = findViewById(R.id.etRegPassword)
        etEmail = findViewById(R.id.etRegEmail)
        etInviteCode = findViewById(R.id.etRegInviteCode)

        val btnSubmit = findViewById<MaterialButton>(R.id.btnRegisterSubmit)
        val tvApply = findViewById<TextView>(R.id.tvApplyInviteCode)
        val tvGoLogin = findViewById<TextView>(R.id.tvGoLogin) // 请在XML增加此ID

        // 2. 初始化密码显示/隐藏切换
        setupPasswordVisibilityToggle()

        // 3. 提交登记逻辑
        btnSubmit.setOnClickListener {
            val account = etAccount.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val inputInviteCode = etInviteCode.text.toString().trim()

            if (account.isEmpty() || password.isEmpty() || email.isEmpty() || inputInviteCode.isEmpty()) {
                Toast.makeText(this, "请补齐登记信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            performServerRegister(account, email, password, inputInviteCode)
        }

        // 4. 跳转登录页面逻辑 (解决注销后无法进入的问题)
        tvGoLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // 5. 邮件申请逻辑
        tvApply.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:edward_dee@ichessgeek.com")
                putExtra(Intent.EXTRA_SUBJECT, "申请岁时礼序引荐码")
                putExtra(Intent.EXTRA_TEXT, "申请雅号：${etAccount.text}\n申请缘由：")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "未找到邮件客户端", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 实现密码显隐切换（点击输入框右侧图标）
     */
    private fun setupPasswordVisibilityToggle() {
        etPassword.typeface = Typeface.DEFAULT // 防止切换时字体变样
        etPassword.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                // 判断点击位置是否在右侧图标区域 (24dp左右)
                if (event.rawX >= (etPassword.right - etPassword.compoundDrawables[2].bounds.width())) {
                    isPasswordVisible = !isPasswordVisible
                    if (isPasswordVisible) {
                        etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                        etPassword.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_close_clear_cancel, 0)
                    } else {
                        etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                        etPassword.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_view, 0)
                    }
                    etPassword.setSelection(etPassword.text.length)
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun performServerRegister(nickname: String, email: String, pass: String, fromCode: String) {
        val client = OkHttpClient()
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
                runOnUiThread { Toast.makeText(this@RegisterActivity, "云端信使受阻", Toast.LENGTH_LONG).show() }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val responseData = resp.body?.string()
                    runOnUiThread {
                        try {
                            if (resp.isSuccessful && responseData != null) {
                                val json = JSONObject(responseData)
                                if (json.optString("status") == "success") {
                                    val myNewCode = json.optString("invite_code")
                                    saveToLocalAndJump(nickname, email, myNewCode)
                                } else {
                                    Toast.makeText(this@RegisterActivity, "登记失败：${json.optString("message")}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@RegisterActivity, "数据解析异常", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    private fun saveToLocalAndJump(nickname: String, email: String, inviteCode: String) {
        // 强制在主线程协程中处理跳转，内部切换 IO 处理数据库
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val db = AppDatabase.getDatabase(this@RegisterActivity)

                val newUser = User(
                    account = nickname,
                    password = "",
                    email = email,
                    invitationCode = inviteCode,
                    isCurrentUser = true,
                    referralCount = 0
                )

                // 数据库写操作切到 IO 线程
                withContext(Dispatchers.IO) {
                    db.userDao().clearUsers()
                    db.userDao().insertUser(newUser)
                }

                Toast.makeText(this@RegisterActivity, "名帖登记成功，画卷开启", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@RegisterActivity, HomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                Toast.makeText(this@RegisterActivity, "本地存根失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

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
                if (etInviteCode.text.isEmpty() && text.matches(Regex("^[A-Z0-9]{6,8}$"))) {
                    etInviteCode.setText(text)
                    Toast.makeText(this, "已自动识别引荐码", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}