package com.dee.android.pbl.takechinahome

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// 必须同时包含这三个实体，且 version 必须比你之前的版本号大 (比如改为 2)
@Database(entities = [User::class, Gift::class, ExchangeGift::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun exchangeDao(): ExchangeDao // 确保这里有新增的 Dao
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "take_china_home_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}