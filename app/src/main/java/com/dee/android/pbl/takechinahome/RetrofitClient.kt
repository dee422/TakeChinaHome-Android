package com.dee.android.pbl.takechinahome

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// RetrofitClient.kt
object RetrofitClient {
    private const val BASE_URL = "https://ichessgeek.com/api/v1/"

    val instance: ApiService by lazy { // 确保这里是 ApiService
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}