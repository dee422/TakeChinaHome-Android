package com.dee.android.pbl.takechinahome

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    // === 模块一：原有礼品画卷功能 (保持路径一致) ===

    /** 1. 从云端拉取最新的礼品画卷 */
    @GET("api/v1/gifts.json")
    suspend fun getGifts(): List<Gift>

    /** 2. 告诉服务器：用户想移出这个 ID 的礼品 */
    @DELETE("api/v1/gifts/{id}")
    suspend fun deleteGift(@Path("id") id: String): Response<Unit> // 将 Int 改为 String


    // === 模块二：用户系统 (新增) ===

    /** 3. 用户注册：提交账户、邮箱、邀请码等 */
    @POST("api/v1/user/register")
    suspend fun register(@Body user: User): ApiResponse


    // === 模块三：藏品置换区 (新增) ===

    /** 4. 提交自己的藏品到置换区 (待审核状态) */
    @POST("api/v1/exchange/submit")
    suspend fun submitExchange(@Body gift: Gift): ApiResponse

    /** 5. 拉取全网已审核通过的置换藏品 */
    @GET("api/v1/exchange/market.json")
    suspend fun getMarketGifts(): List<Gift>
}

/**
 * 通用响应类：用于处理注册、提交等操作的回调
 */
data class ApiResponse(
    val success: Boolean,
    val message: String
)