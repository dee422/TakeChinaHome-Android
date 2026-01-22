package com.dee.android.pbl.takechinahome

// 定义每一份礼品的“档案”
data class Gift(
    val date: String,    // 日期，如“正月十五”
    val name: String,    // 礼品名，如“青釉莲花尊”
    val desc: String     // 定制理由
)