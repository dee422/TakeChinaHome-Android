package com.dee.android.pbl.takechinahome

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "gifts")
data class Gift(
    @PrimaryKey
    var id: String = "",
    var name: String = "",
    var label: String = "",
    var deadline: String = "",
    var spec: String = "",
    var desc: String = "",

    @Ignore
    var images: List<String> = mutableListOf(),

    var customText: String = "",
    var customQuantity: String = "1",
    var customDeliveryDate: String = "",
    var customNotes: String = "",
    var isSaved: Boolean = false
) : Serializable
// 删除了多余的 constructor() 块