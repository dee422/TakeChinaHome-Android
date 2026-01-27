package com.dee.android.pbl.takechinahome

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 置换区藏品实体类
 * 用于存储 VIP 用户发布的置换信息
 */
// ExchangeGift.kt 示例
@Entity(tableName = "exchange_gifts")
data class ExchangeGift(
    @PrimaryKey
    var id: String = "", // 必须是 var，必须有默认值
    var ownerName: String = "",
    var title: String = "",
    var story: String = "",
    var want: String = "",
    var contact: String = "",
    var imageUrl: String = "",

    // --- 新增：审核状态 ---
    // 0: 本地草稿(仅自己可见), 1: 审核中, 2: 已公示(管理员确认), 3: 已下架
    var status: Int = 0
)