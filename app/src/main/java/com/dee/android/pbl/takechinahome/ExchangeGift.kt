package com.dee.android.pbl.takechinahome

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "swap_items")
data class ExchangeGift(
    @PrimaryKey(autoGenerate = true) // 设为自增，匹配数据库 int 类型
    val id: Int = 0,
    val title: String,
    val story: String,
    val imageUrl: String,
    val ownerEmail: String,
    var status: Int = 0
)