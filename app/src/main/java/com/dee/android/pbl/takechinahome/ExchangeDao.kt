package com.dee.android.pbl.takechinahome

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ExchangeDao {
    @Insert
    suspend fun insertExchangeGift(gift: ExchangeGift)

    @Query("SELECT * FROM exchange_gifts")
    suspend fun getAllExchangeGifts(): List<ExchangeGift>

    // 修复：giftId 必须是 String，因为 ExchangeGift 的 id 是 String
    @Query("DELETE FROM exchange_gifts WHERE id = :giftId")
    suspend fun deleteExchangeGift(giftId: String)
}