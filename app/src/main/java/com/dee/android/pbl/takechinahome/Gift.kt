package com.dee.android.pbl.takechinahome

/**
 * 礼品数据类
 * 字段名必须与 JSON 中的 Key 完全一致，否则 Gson 会解析失败
 */
data class Gift(
    val id: Int,
    val deadline: String,   // 下单截止日期
    val name: String,       // 品名
    val spec: String,       // 规格
    val desc: String,       // 描述
    val images: List<String> // 照片 URL 列表（对应 JSON 中的数组）
)