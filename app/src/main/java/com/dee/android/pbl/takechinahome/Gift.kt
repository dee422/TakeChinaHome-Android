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

    // @Ignore tells Room not to save the list (which requires a TypeConverter)
    // But Gson will still save/load this from your JSON cache.
    @Ignore
    var images: List<String> = mutableListOf(),

    var customText: String = "",
    var customQuantity: String = "1",
    var customDeliveryDate: String = "",
    var customNotes: String = "",
    var isSaved: Boolean = false
) : Serializable {
    // Empty constructor required by some versions of Room/Serialization
    constructor() : this("", "", "", "", "", "", mutableListOf(), "", "1", "", "", false)
}