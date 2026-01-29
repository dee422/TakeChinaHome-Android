package com.dee.android.pbl.takechinahome

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ExchangeDao {

    // 1. 修正方法名为 insert，以匹配 AddItemActivity 中的调用
    // 加上 OnConflictStrategy.REPLACE，如果 ID 重复则覆盖，防止崩溃
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(gift: ExchangeGift)

    // 2. 修正表名为 swap_items，与你的 ExchangeGift 实体类保持一致
    @Query("SELECT * FROM swap_items")
    suspend fun getAllExchangeGifts(): List<ExchangeGift>

    // 3. 修正表名为 swap_items
    @Query("DELETE FROM swap_items WHERE id = :giftId")
    suspend fun deleteExchangeGift(giftId: String)

    @Update
    suspend fun update(gift: ExchangeGift)
}