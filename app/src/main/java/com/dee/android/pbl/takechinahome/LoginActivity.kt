package com.dee.android.pbl.takechinahome

import android.content.Intent
import android.graphics.Typeface
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

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPass: EditText
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etLoginEmail)
        etPass = findViewById(R.id.etLoginPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLoginSubmit)
        val tvGoReg = findViewById<TextView>(R.id.tvGoRegister)

        // 密码显隐逻辑
        setupPasswordToggle()

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPass.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "请补齐鸿雁及密押", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            performLogin(email, pass)
        }

        tvGoReg.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }

    private fun setupPasswordToggle() {
        etPass.typeface = Typeface.DEFAULT
        etPass.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (event.rawX >= (etPass.right - etPass.compoundDrawables[2].bounds.width())) {
                    isPasswordVisible = !isPasswordVisible
                    etPass.transformationMethod = if (isPasswordVisible)
                        HideReturnsTransformationMethod.getInstance()
                    else PasswordTransformationMethod.getInstance()

                    val icon = if (isPasswordVisible) android.R.drawable.ic_menu_close_clear_cancel
                    else android.R.drawable.ic_menu_view
                    etPass.setCompoundDrawablesWithIntrinsicBounds(0, 0, icon, 0)
                    etPass.setSelection(etPass.text.length)
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun performLogin(email: String, pass: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                // 调用 Retrofit 接口
                val response = RetrofitClient.instance.login(email, pass)

                if (response.status == "success") {
                    // 登录成功，保存数据到本地数据库
                    saveUserToLocal(response.account ?: "雅士", email, response.invite_code ?: "")
                } else {
                    Toast.makeText(this@LoginActivity, "登入失败：${response.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "云端信使受阻: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun saveUserToLocal(name: String, email: String, code: String) {
        val db = AppDatabase.getDatabase(this)
        val newUser = User(
            account = name,
            password = "", // 密码不存本地，确保安全
            email = email,
            invitationCode = code,
            isCurrentUser = true
        )

        withContext(Dispatchers.IO) {
            db.userDao().clearUsers()
            db.userDao().insertUser(newUser)
        }

        Toast.makeText(this, "欢迎归来，$name", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}