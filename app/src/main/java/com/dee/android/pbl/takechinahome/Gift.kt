package com.dee.android.pbl.takechinahome

import java.io.Serializable

data class Gift(
    val id: String,
    val name: String,
    val label: String,
    val deadline: String,
    val spec: String,
    val desc: String,
    val images: List<String>,

    // 【新增定制字段】这些数据仅保存在手机本地内存中，用于生成清单图
    var customText: String = "",       // 刻花/底款内容
    var customQuantity: String = "1",  // 订购数量
    var customDeliveryDate: String = "",// 期望交货期
    var customNotes: String = "",       // 其他备注
    var isSaved: Boolean = false // 新增：标记是否已确认定制
) : Serializable