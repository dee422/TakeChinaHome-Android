package com.dee.android.pbl.takechinahome

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "order_history")
data class OrderHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val submitTime: String,      // 提交时间，如 "2026-01-27 15:30"
    val contactName: String,     // 当时填写的雅号/收件人
    val detailsJson: String,     // 整个订单礼品列表转换成的 JSON 字符串
    val userEmail: String        // 所属用户
)