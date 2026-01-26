package com.dee.android.pbl.takechinahome

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // 1. 建议带上 www（如果你服务器配置了的话），并包含 api/v1/，结尾必须有斜杠
    private const val BASE_URL = "https://www.ichessgeek.com/api/v1/"

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}