package com.dee.android.pbl.takechinahome

import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // 1. 获取岁时礼清单 (对应 gifts.json)
    @GET("gifts.json")
    suspend fun getGifts(): List<Gift>

    // 2. 删除特定礼品 (对应 delete_gift.php)
    @FormUrlEncoded
    @POST("delete_gift.php")
    suspend fun deleteGift(@Field("id") id: Int): Response<Unit>

    // 3. 用户注册 (对应 register.php)
    @FormUrlEncoded
    @POST("register.php")
    suspend fun register(
        @Field("nickname") nickname: String,
        @Field("email") email: String,
        @Field("password") pass: String,
        @Field("from_invite_code") fromCode: String
    ): ApiResponse

    // 4. 用户登录 (对应 login.php)
    @FormUrlEncoded
    @POST("login.php")
    suspend fun login(
        @Field("email") email: String,
        @Field("password") pass: String
    ): LoginResponse

    // 5. 修改密码 (对应 update_password.php)
    @FormUrlEncoded
    @POST("update_password.php")
    suspend fun updatePassword(
        @Field("email") email: String,
        @Field("old_password") oldPass: String,
        @Field("new_password") newPass: String
    ): ApiResponse

    // 6. 提交/审核交换申请 (对应 exchange/apply_review.php)
    @FormUrlEncoded
    @POST("exchange/apply_review.php")
    suspend fun applyExchangeReview(
        @Field("id") id: String,
        @Field("owner_email") ownerEmail: String,
        @Field("item_name") title: String,
        @Field("description") story: String,
        @Field("image_data") imageData: String? = null
    ): ApiResponse

    // 7. 确认订单/同步云端 (对应 orders/confirm.php)
    @FormUrlEncoded
    @POST("orders/confirm.php")
    suspend fun uploadOrderConfirm(
        @Field("user_email") userEmail: String,
        @Field("contact_name") contactName: String,
        @Field("order_details_json") json: String
    ): ApiResponse

    // 8. 获取市场交换列表 (对应 exchange/get_market.php)
    @GET("exchange/get_market.php")
    suspend fun getMarketGifts(): List<ExchangeGift>

    // 9. 下架礼品 (对应 take_down_item.php)
    @FormUrlEncoded
    @POST("take_down_item.php")
    suspend fun requestTakeDown(@Field("id") itemId: String): ApiResponse
}

// --- 在 ApiService 接口的花括号结束后添加 ---

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