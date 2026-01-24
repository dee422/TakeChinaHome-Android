package com.dee.android.pbl.takechinahome

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val account: String,
    val email: String,
    val password: String, // 实际开发建议存加密后的哈希值
    val invitationCode: String,
    val isCurrentUser: Boolean = false // 标记是否为当前手机登录的用户
)