package com.dee.android.pbl.takechinahome

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Entity(tableName = "swap_items")
data class ExchangeGift(
    @PrimaryKey(autoGenerate = true)
    @SerializedName("id") var id: Int = 0,
    @SerializedName("owner_email") var ownerEmail: String = "",
    @SerializedName("item_name") var itemName: String = "",
    @SerializedName("description") var description: String = "",
    @SerializedName("image_url") var imageUrl: String = "", // 对应数据库 image_url
    @SerializedName("status") var status: Int = 1,
    @SerializedName("contact_code") var contactCode: String = "",
    @SerializedName("exchange_wish") var exchangeWish: Int = 1,
    @SerializedName("create_time") var createTime: String? = null // 后台自动生成，前台只读
) : Serializable