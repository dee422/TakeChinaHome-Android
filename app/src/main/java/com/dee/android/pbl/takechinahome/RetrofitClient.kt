package com.dee.android.pbl.takechinahome

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // 修正建议：确保这里的地址在浏览器能直接打开 gifts.json
    private const val BASE_URL = "https://ichessgeek.com/api/v1/"

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}