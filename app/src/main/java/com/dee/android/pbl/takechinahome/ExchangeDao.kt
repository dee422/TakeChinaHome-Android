package com.dee.android.pbl.takechinahome

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ExchangeDao {
    @Insert
    suspend fun insertExchangeGift(gift: ExchangeGift)

    @Query("SELECT * FROM exchange_gifts ORDER BY time DESC")
    suspend fun getAllExchangeGifts(): List<ExchangeGift>

    @Query("DELETE FROM exchange_gifts WHERE id = :giftId")
    suspend fun deleteExchangeGift(giftId: Int)
}