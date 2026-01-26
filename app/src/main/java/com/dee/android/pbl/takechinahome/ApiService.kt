package com.dee.android.pbl.takechinahome

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    // 1. 获取岁时礼清单
    @GET("gifts.json")
    suspend fun getGifts(): List<Gift>

    // 2. 删除特定礼品
    @DELETE("gifts/{id}")
    suspend fun deleteGift(@Path("id") id: Int): Response<Unit>

    // 3. 名帖登记 (注册)
    // 改为 FormUrlEncoded 保持与 PHP $_POST 接收逻辑一致
    @FormUrlEncoded
    @POST("register.php")
    suspend fun register(
        @Field("nickname") nickname: String,
        @Field("email") email: String,
        @Field("password") pass: String,
        @Field("from_invite_code") fromCode: String
    ): ApiResponse

    // 4. 归雁寻踪 (登录)
    @FormUrlEncoded
    @POST("login.php")
    suspend fun login(
        @Field("email") email: String,
        @Field("password") pass: String
    ): LoginResponse

    // 5. 修订密信 (修改密码)
    @FormUrlEncoded
    @POST("update_password.php")
    suspend fun updatePassword(
        @Field("email") email: String,
        @Field("old_password") oldPass: String,
        @Field("new_password") newPass: String
    ): ApiResponse

    // 6. 置换市场相关
    @POST("exchange/submit")
    suspend fun submitExchange(@Body gift: Gift): ApiResponse

    @GET("exchange/market.json")
    suspend fun getMarketGifts(): List<Gift>
}

/**
 * 通用响应类：用于处理注册、修改密码等简单操作的回调
 */
data class ApiResponse(
    val success: Boolean,
    val message: String
)

/**
 * 登录专属响应类：包含用户雅号和引荐码
 */
data class LoginResponse(
    val status: String,    // 与 PHP 返回的 "success" 或 "error" 对应
    val message: String,
    val account: String?,  // 雅号
    val invite_code: String? // 用户自己的引荐码
)