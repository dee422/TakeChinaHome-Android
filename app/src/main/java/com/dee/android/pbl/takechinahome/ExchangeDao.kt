package com.dee.android.pbl.takechinahome

import androidx.room.*

@Dao
interface ExchangeDao {

    // 1. 插入或更新：使用 REPLACE 策略。
    // 这样当你从云端刷新数据时，相同 ID 的项会自动更新，不会产生重复。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(gift: ExchangeGift)

    // 批量插入：用于从服务器获取列表后一次性同步到本地
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(gifts: List<ExchangeGift>)

    // 2. 获取全部：按 ID 倒序排列，让新发布的物什排在最前面
    @Query("SELECT * FROM swap_items ORDER BY id DESC")
    suspend fun getAllExchangeGifts(): List<ExchangeGift>

    // 3. 根据 ID 查询：这是解决“详情页数据丢失”的终极方案
    // 如果 Intent 传对象失败，详情页可以用 ID 直接从这里拿数据
    @Query("SELECT * FROM swap_items WHERE id = :giftId LIMIT 1")
    suspend fun getGiftById(giftId: Int): ExchangeGift?

    // 4. 删除逻辑：ID 应该是 Int 类型，与实体类保持一致
    @Query("DELETE FROM swap_items WHERE id = :giftId")
    suspend fun deleteExchangeGift(giftId: Int)

    // 5. 更新逻辑：用于本地修改状态（如下架）
    @Update
    suspend fun update(gift: ExchangeGift)

    // 6. 清空表：谨慎使用，通常仅在切换账号时调用
    @Query("DELETE FROM swap_items")
    suspend fun deleteAll()
}