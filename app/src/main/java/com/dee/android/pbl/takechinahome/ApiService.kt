package com.dee.android.pbl.takechinahome

import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // 1. 获取岁时礼清单
    @GET("gifts.json")
    suspend fun getGifts(): List<Gift>

    // 2. 删除特定礼品
    @FormUrlEncoded
    @POST("delete_gift.php")
    suspend fun deleteGift(@Field("id") id: Int): Response<Unit>

    // 3. 用户注册
    @FormUrlEncoded
    @POST("register.php")
    suspend fun register(
        @Field("nickname") nickname: String,
        @Field("email") email: String,
        @Field("password") pass: String,
        @Field("from_invite_code") fromCode: String
    ): ApiResponse

    // 4. 用户登录
    @FormUrlEncoded
    @POST("login.php")
    suspend fun login(
        @Field("email") email: String,
        @Field("password") pass: String
    ): LoginResponse

    // 5. 修改密码
    @FormUrlEncoded
    @POST("update_password.php")
    suspend fun updatePassword(
        @Field("email") email: String,
        @Field("old_password") oldPass: String,
        @Field("new_password") newPass: String
    ): ApiResponse

    /**
     * 6. 提交/审核交换申请 (核心修正点)
     * 参数名统一采用下划线格式，确保与 ExchangeActivity 中的调用及 PHP 接收完全一致
     */
    @FormUrlEncoded
    @POST("exchange/apply_review.php")
    suspend fun applyExchangeReview(
        @Field("id") id: Int,
        @Field("owner_email") owner_email: String,
        @Field("item_name") item_name: String,
        @Field("description") description: String,
        @Field("image_data") image_data: String?,
        @Field("contact_code") contact_code: String,
        @Field("exchange_wish") exchange_wish: Int // 修正为 Int (1:置换, 2:售卖)
    ): ApiResponse

    // 7. 确认订单/同步云端
    @FormUrlEncoded
    @POST("orders/confirm.php")
    suspend fun uploadOrderConfirm(
        @Field("user_email") userEmail: String,
        @Field("contact_name") contactName: String,
        @Field("order_details_json") json: String
    ): ApiResponse

    // 8. 获取市场交换列表
    @GET("exchange/get_market.php")
    suspend fun getMarketGifts(): List<ExchangeGift>

    // 9. 下架礼品
    @FormUrlEncoded
    @POST("take_down_item.php")
    suspend fun requestTakeDown(
        @Field("item_id") id: Int,
        @Field("owner_email") owner_email: String
    ): ApiResponse
}

// --- 数据类 ---

data class ApiResponse(
    val success: Boolean,
    val message: String
)

data class LoginResponse(
    val status: String,
    val message: String,
    val account: String?,
    val invite_code: String?
)