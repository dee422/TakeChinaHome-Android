package com.dee.android.pbl.takechinahome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class IntentListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 从 Intent 中获取 HomeActivity 传过来的 email
        val userEmail = intent.getStringExtra("USER_EMAIL") ?: ""

        setContent {
            // 因为在同一个 package 下，这里直接调用，不报红
            CustomerIntentListScreen(
                userEmail = userEmail,
                onBack = { finish() }
            )
        }
    }
}