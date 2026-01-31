package com.dee.android.pbl.takechinahome

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Entity(tableName = "swap_items")
data class ExchangeGift(
    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    var id: Int = 0,

    @SerializedName("owner_email")
    var ownerEmail: String = "",

    @SerializedName("item_name")
    var itemName: String = "",

    @SerializedName("description")
    var description: String = "",

    @SerializedName("image_url")
    var imageUrl: String = "",

    @SerializedName("status")
    var status: Int = 1, // 1: 待审核, 2: 已上架, 3: 已下架

    @SerializedName("contact_code")
    var contactCode: String = "",

    @SerializedName("exchange_wish")
    var exchangeWish: Int = 1,

    @SerializedName("create_time")
    var createTime: String? = null
) : Serializable