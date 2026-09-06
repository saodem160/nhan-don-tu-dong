package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.OrderCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [FilterConfigEntity::class, OrderHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "order_filter_database"
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed default values for the 4 categories
                        scope.launch(Dispatchers.IO) {
                            val dao = getDatabase(context, scope).orderDao()
                            val defaults = OrderCategory.entries.map { cat ->
                                FilterConfigEntity(
                                    categoryKey = cat.key,
                                    isEnabled = true,
                                    maxPickupDistanceKm = cat.defaultMaxPickupKm,
                                    maxDeliveryDistanceKm = cat.defaultMaxDeliveryKm,
                                    minShippingFeeVnd = cat.defaultMinFeeVnd
                                )
                            }
                            dao.insertFilterConfigs(defaults)
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
