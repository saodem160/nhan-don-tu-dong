package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.AppDatabase
import com.example.data.local.FilterConfigEntity
import com.example.data.local.OrderHistoryEntity
import com.example.data.model.OrderCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OrderRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val scope: CoroutineScope
) {
    private val orderDao = database.orderDao()
    private val prefs: SharedPreferences = context.getSharedPreferences("order_filter_prefs", Context.MODE_PRIVATE)

    // Master Scanning Toggle
    private val _isMasterScanningEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_MASTER_SCAN, false)
    )
    val isMasterScanningEnabled: StateFlow<Boolean> = _isMasterScanningEnabled.asStateFlow()

    // Test Mode Toggle ("Chỉ Lọc Không Nhận")
    private val _isTestModeEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_TEST_MODE, true)
    )
    val isTestModeEnabled: StateFlow<Boolean> = _isTestModeEnabled.asStateFlow()

    // Floating Widget Toggle
    private val _isFloatingWidgetEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_FLOATING_WIDGET, false)
    )
    val isFloatingWidgetEnabled: StateFlow<Boolean> = _isFloatingWidgetEnabled.asStateFlow()

    // Live Cooldown Pause Timestamp (e.g. 15-20 min after live grab)
    private val _cooldownUntil = MutableStateFlow(
        prefs.getLong(KEY_COOLDOWN_UNTIL, 0L)
    )
    val cooldownUntil: StateFlow<Long> = _cooldownUntil.asStateFlow()

    // Filter Configs Flow from Room
    val filterConfigsFlow: Flow<List<FilterConfigEntity>> = orderDao.getAllFilterConfigsFlow()

    // Order History Flow from Room
    val orderHistoryFlow: Flow<List<OrderHistoryEntity>> = orderDao.getAllHistoryFlow()

    // In-memory Ignore List / Set for recently processed orders
    private val ignoreSet = LinkedHashSet<String>()
    private val MAX_IGNORE_CACHE = 200

    fun isIgnored(identifier: String): Boolean {
        synchronized(ignoreSet) {
            return ignoreSet.contains(identifier)
        }
    }

    fun markIgnored(identifier: String) {
        synchronized(ignoreSet) {
            if (ignoreSet.size >= MAX_IGNORE_CACHE) {
                val iterator = ignoreSet.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            ignoreSet.add(identifier)
        }
    }

    fun clearIgnoreList() {
        synchronized(ignoreSet) {
            ignoreSet.clear()
        }
    }

    fun setMasterScanning(enabled: Boolean) {
        _isMasterScanningEnabled.value = enabled
        prefs.edit().putBoolean(KEY_MASTER_SCAN, enabled).apply()
    }

    fun setTestMode(enabled: Boolean) {
        _isTestModeEnabled.value = enabled
        prefs.edit().putBoolean(KEY_TEST_MODE, enabled).apply()
    }

    fun setFloatingWidget(enabled: Boolean) {
        _isFloatingWidgetEnabled.value = enabled
        prefs.edit().putBoolean(KEY_FLOATING_WIDGET, enabled).apply()
    }

    fun setCooldown(cooldownUntilMs: Long) {
        _cooldownUntil.value = cooldownUntilMs
        prefs.edit().putLong(KEY_COOLDOWN_UNTIL, cooldownUntilMs).apply()
    }

    suspend fun saveFilterConfigs(configs: List<FilterConfigEntity>) = withContext(Dispatchers.IO) {
        orderDao.insertFilterConfigs(configs)
    }

    suspend fun getAllFilterConfigsSync(): List<FilterConfigEntity> = withContext(Dispatchers.IO) {
        val list = orderDao.getAllFilterConfigs()
        if (list.isEmpty()) {
            val defaults = OrderCategory.entries.map { cat ->
                FilterConfigEntity(
                    categoryKey = cat.key,
                    isEnabled = true,
                    maxPickupDistanceKm = cat.defaultMaxPickupKm,
                    maxDeliveryDistanceKm = cat.defaultMaxDeliveryKm,
                    minShippingFeeVnd = cat.defaultMinFeeVnd
                )
            }
            orderDao.insertFilterConfigs(defaults)
            defaults
        } else {
            list
        }
    }

    suspend fun logOrderHistory(history: OrderHistoryEntity) = withContext(Dispatchers.IO) {
        orderDao.insertHistory(history)
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        orderDao.clearAllHistory()
    }

    companion object {
        private const val KEY_MASTER_SCAN = "master_scan_enabled"
        private const val KEY_TEST_MODE = "test_mode_enabled"
        private const val KEY_FLOATING_WIDGET = "floating_widget_enabled"
        private const val KEY_COOLDOWN_UNTIL = "cooldown_until_ts"

        @Volatile
        private var INSTANCE: OrderRepository? = null

        fun getInstance(context: Context, database: AppDatabase, scope: CoroutineScope): OrderRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = OrderRepository(context.applicationContext, database, scope)
                INSTANCE = instance
                instance
            }
        }
    }
}
