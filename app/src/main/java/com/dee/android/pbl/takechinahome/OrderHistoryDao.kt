package com.dee.android.pbl.takechinahome

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OrderHistoryDao {
    @Insert
    suspend fun insertOrder(order: OrderHistory)

    @Query("SELECT * FROM order_history WHERE userEmail = :email ORDER BY id DESC")
    suspend fun getOrdersByUser(email: String): List<OrderHistory>

    @Query("DELETE FROM order_history WHERE id = :orderId")
    suspend fun deleteOrder(orderId: Int)
}