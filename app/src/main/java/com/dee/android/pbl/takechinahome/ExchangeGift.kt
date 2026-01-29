package com.dee.android.pbl.takechinahome

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "swap_items")
data class ExchangeGift(
    @PrimaryKey val id: String,
    @SerializedName("item_name") @ColumnInfo(name = "item_name") val title: String,
    @SerializedName("description") @ColumnInfo(name = "description") val story: String,
    @SerializedName("image_url") @ColumnInfo(name = "image_url") val imageUrl: String,
    @SerializedName("owner_email") @ColumnInfo(name = "owner_email") val ownerEmail: String,
    @SerializedName("status") @ColumnInfo(name = "status") var status: Int = 0 // 改为 var 方便更新状态
)