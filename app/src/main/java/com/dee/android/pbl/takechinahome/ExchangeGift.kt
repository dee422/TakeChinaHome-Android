package com.dee.android.pbl.takechinahome

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 置换区藏品实体类
 * 用于存储 VIP 用户发布的置换信息
 */
@Entity(tableName = "exchange_gifts")
data class ExchangeGift(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ownerName: String,     // 发布者的雅号 (对应 User 里的 account)
    val title: String,         // 藏品名称
    val story: String,         // 藏品背后的故事/描述
    val want: String,          // 置换意向 (想换取什么)
    val contact: String,       // 联系方式 (暗号/微信/手机)
    val imageUrl: String,      // 藏品的图片路径 (本地 URI 或缩略图路径)
    val time: Long = System.currentTimeMillis() // 发布时间戳
)