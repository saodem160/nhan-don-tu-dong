package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    // Filter Configurations
    @Query("SELECT * FROM filter_configs")
    fun getAllFilterConfigsFlow(): Flow<List<FilterConfigEntity>>

    @Query("SELECT * FROM filter_configs")
    suspend fun getAllFilterConfigs(): List<FilterConfigEntity>

    @Query("SELECT * FROM filter_configs WHERE categoryKey = :categoryKey LIMIT 1")
    suspend fun getFilterConfig(categoryKey: String): FilterConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilterConfigs(configs: List<FilterConfigEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilterConfig(config: FilterConfigEntity)

    // Order History
    @Query("SELECT * FROM order_history ORDER BY timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<OrderHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: OrderHistoryEntity): Long

    @Query("DELETE FROM order_history")
    suspend fun clearAllHistory()
}
