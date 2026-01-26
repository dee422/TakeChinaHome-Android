package com.dee.android.pbl.takechinahome

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    // 去掉开头的 api/v1/，也不要加 /
    @GET("gifts.json")
    suspend fun getGifts(): List<Gift>

    @DELETE("gifts/{id}")
    suspend fun deleteGift(@Path("id") id: String): Response<Unit>

    // 注意：你服务器上的注册文件路径是 register.php 还是 user/register？
    // 如果是之前我们写的那个 PHP 文件，应该是：
    @POST("register.php")
    suspend fun register(@Body user: User): ApiResponse

    @POST("exchange/submit")
    suspend fun submitExchange(@Body gift: Gift): ApiResponse

    @GET("exchange/market.json")
    suspend fun getMarketGifts(): List<Gift>
}

/**
 * 通用响应类：用于处理注册、提交等操作的回调
 */
data class ApiResponse(
    val success: Boolean,
    val message: String
)