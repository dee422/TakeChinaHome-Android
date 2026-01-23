package com.dee.android.pbl.takechinahome

import retrofit2.http.GET
import retrofit2.http.DELETE
import retrofit2.http.Path

interface ApiService {
    // 1. 从云端拉取最新的礼品画卷
    @GET("api/v1/gifts.json")
    suspend fun getGifts(): List<Gift>

    // 2. 告诉服务器：用户想移出这个 ID 的礼品
    // 这样下次 loadGiftsFromServer 时，这个礼品就真的消失了
    @DELETE("api/v1/gifts/{id}")
    suspend fun deleteGift(@Path("id") id: Int): retrofit2.Response<Unit>
}