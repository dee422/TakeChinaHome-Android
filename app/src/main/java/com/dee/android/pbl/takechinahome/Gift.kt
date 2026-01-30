package com.dee.android.pbl.takechinahome

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Entity(tableName = "gifts")
data class Gift(
    @PrimaryKey
    @SerializedName("id")
    var id: Int = 0,

    @SerializedName("name")
    var name: String = "",

    @SerializedName("deadline")
    var deadline: String = "",

    @SerializedName("spec")
    var spec: String = "",

    @SerializedName("desc")
    var desc: String = "",

    // 官方数据：图片列表
    @Ignore
    @SerializedName("images")
    var images: List<String> = mutableListOf(),

    // --- 新增：兼容市集数据的单张图片字段 ---
    var imageUrl: String = "",

    // --- 新增：置换意向 (1: 置换, 2: 售卖) ---
    var exchangeWish: Int = 1,

    // 本地操作字段
    var label: String = "",
    var customText: String = "",
    var customQuantity: String = "1",
    var customDeliveryDate: String = "",
    var customNotes: String = "",
    var isSaved: Boolean = false,
    var isFriendShare: Boolean = false
) : Serializable