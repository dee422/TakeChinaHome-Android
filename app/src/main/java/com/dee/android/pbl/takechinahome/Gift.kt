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
    var id: Int = 0,             // 修正：JSON 里的 id 是数字，所以用 Int

    @SerializedName("name")
    var name: String = "",

    @SerializedName("deadline")
    var deadline: String = "",

    @SerializedName("spec")
    var spec: String = "",

    @SerializedName("desc")
    var desc: String = "",

    @Ignore
    @SerializedName("images")
    var images: List<String> = mutableListOf(),

    // 以下是本地操作字段，JSON 中没有，所以给默认值
    var label: String = "",
    var customText: String = "",
    var customQuantity: String = "1",
    var customDeliveryDate: String = "",
    var customNotes: String = "",
    var isSaved: Boolean = false
) : Serializable {
    // 必须保留一个给 Room 使用的构造函数（包含所有非 @Ignore 字段）
    constructor() : this(0, "", "", "", "", mutableListOf())
}