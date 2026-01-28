package com.dee.android.pbl.takechinahome

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "order_history")
data class OrderHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val submitTime: String,
    val userEmail: String,      // 账号标识
    val accountOwner: String,   // 账号主（当时的雅号）
    val contactName: String,    // 联络人（当时登记的名帖）
    val detailsJson: String
)