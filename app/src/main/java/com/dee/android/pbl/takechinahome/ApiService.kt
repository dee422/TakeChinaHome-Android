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

    // 6. 提交置换品申请审核 (使用 FormUrlEncoded 方便 PHP 处理)
    @FormUrlEncoded
    @POST("exchange/apply_review.php")
    suspend fun applyExchangeReview(
        @Field("id") id: String,
        @Field("owner") owner: String,
        @Field("title") title: String,
        @Field("story") story: String,
        @Field("want") want: String,
        @Field("contact") contact: String,
        @Field("image_data") imageData: String? = null // 可传 Base64 或图片链接
    ): ApiResponse

    // 7. 确认订单上传 (将生成的订单元数据发至后台)
    @FormUrlEncoded
    @POST("orders/confirm.php") // 确保路径与服务器一致
    suspend fun uploadOrderConfirm(
        @Field("user_email") user_email: String,
        @Field("contact_name") contact_name: String,
        @Field("order_details_json") order_details_json: String
    ): ApiResponse

    // 8. 申请下架
    @FormUrlEncoded
    @POST("exchange/take_down.php")
    suspend fun requestTakeDown(@Field("id") itemId: String): ApiResponse
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