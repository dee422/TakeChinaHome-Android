package com.dee.android.pbl.takechinahome

import androidx.room.*

@Dao
interface ExchangeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(gift: ExchangeGift)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(gifts: List<ExchangeGift>)

    @Query("SELECT * FROM swap_items ORDER BY id DESC")
    suspend fun getAllExchangeGifts(): List<ExchangeGift>

    @Query("SELECT * FROM swap_items WHERE id = :giftId LIMIT 1")
    suspend fun getGiftById(giftId: Int): ExchangeGift?

    // --- 核心修复：增加标准删除方法，支持直接传入对象 ---
    @Delete
    suspend fun delete(gift: ExchangeGift)

    // 保留你原本按 ID 删除的方法（如果其他地方有用到）
    @Query("DELETE FROM swap_items WHERE id = :giftId")
    suspend fun deleteExchangeGift(giftId: Int)

    @Update
    suspend fun update(gift: ExchangeGift)

    @Query("DELETE FROM swap_items")
    suspend fun deleteAll()
}