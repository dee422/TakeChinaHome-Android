package com.dee.android.pbl.takechinahome

import com.google.gson.annotations.SerializedName
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
    ): ApiResponse<Any?> // 修正：统一使用泛型版

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
    ): ApiResponse<Any?>

    // 6. 提交/审核交换申请
    @FormUrlEncoded
    @POST("exchange/apply_review.php")
    suspend fun applyExchangeReview(
        @Field("id") id: Int,
        @Field("owner_email") owner_email: String,
        @Field("item_name") item_name: String,
        @Field("description") description: String,
        @Field("image_data") image_data: String?,
        @Field("contact_code") contact_code: String,
        @Field("exchange_wish") exchange_wish: Int
    ): ApiResponse<Any?>

    // 7. 确认订单/同步云端
    @FormUrlEncoded
    @POST("orders/confirm.php")
    suspend fun uploadOrderConfirm(
        @Field("user_email") userEmail: String,
        @Field("contact_name") contactName: String,
        @Field("order_details_json") json: String
    ): ApiResponse<Any?>

    // 8. 获取市场交换列表
    @GET("exchange/get_market.php")
    suspend fun getMarketGifts(): List<ExchangeGift>

    // 9. 下架置换品
    @FormUrlEncoded
    @POST("take_down_item.php")
    suspend fun requestTakeDown(
        @Field("id") id: Int,
        @Field("owner_email") ownerEmail: String
    ): ApiResponse<Any?>

    // 10. 删除置换品
    @FormUrlEncoded
    @POST("delete_exchange_item.php")
    suspend fun deleteExchangeItem(
        @Field("id") id: Int,
        @Field("owner_email") ownerEmail: String
    ): ApiResponse<Any?>

    // 11. 重新上架
    @FormUrlEncoded
    @POST("relist_item.php")
    suspend fun relistItem(
        @Field("id") id: Int,
        @Field("owner_email") ownerEmail: String
    ): ApiResponse<Any?>

    // --- 旧版历史记录 (保留以防旧页面崩溃) ---
    @GET("get_order_history.php")
    suspend fun getOrderHistory(
        @Query("email") email: String
    ): List<OrderHistory>

    // ✨ 新版意向卷宗系统 (新航道)
    @GET("get_customer_intent_orders.php")
    suspend fun getRealtimeOrders(
        @Query("email") email: String
    ): ApiResponse<List<Order>> // 这里现在不会报错了

    @FormUrlEncoded
    @POST("delete_order_customer.php")
    suspend fun deleteOrder(@Field("id") id: Int): ApiResponse<Any?>

    // ✨ 客户端同步意向确认接口
    @FormUrlEncoded
    @POST("update_order_intent.php")
    suspend fun confirmOrderIntent(
        @Field("order_id") orderId: Int,
        @Field("target_gift_name") giftName: String,
        @Field("target_qty") qty: Int,
        @Field("delivery_date") date: String,
        @Field("contact_method") contact: String,
        @Field("manager_name") managerName: String,
        @Field("intent_confirm_status") status: Int = 1
    ): ApiResponse<Any?>

    @GET("get_managers.php")
    suspend fun getManagers(): ApiResponse<List<Manager>>
}

// --- 数据类定义 ---

// 1. 统一的响应结构
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null // 增加 data 字段支持
)

data class LoginResponse(
    val status: String,
    val message: String,
    val account: String?,
    val invite_code: String?
)

// 2. ✨ 核心 Order 模型 (必须与管理端字段一致)
data class Order(
    val id: Int,
    @SerializedName("is_intent") val isIntent: Int,
    val status: String,
    @SerializedName("contact_name") val contactName: String,
    @SerializedName("user_email") val userEmail: String,
    val details: List<OrderDetailItem>,
    @SerializedName("ai_suggestion") val aiSuggestion: String?,
    @SerializedName("target_gift_name") val targetGiftName: String?,
    @SerializedName("target_qty") val targetQty: Int,
    @SerializedName("delivery_date") val deliveryDate: String?,
    @SerializedName("contact_method") val contactMethod: String?,
    @SerializedName("intent_confirm_status") val intentConfirmStatus: Int,
    @SerializedName("manager_name") val managerName: String?
)

data class OrderDetailItem(
    val name: String,
    val qty: Int,
    val spec: String?,
    val note: String?
)

data class Manager(
    val username: String,
    // ✨ 使用 SerializedName 适配 PHP 可能返回的 'name' 或 'nickname'
    @SerializedName("nickname") val nickname: String
)