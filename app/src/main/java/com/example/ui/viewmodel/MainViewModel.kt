package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.OrderFilterApp
import com.example.data.local.FilterConfigEntity
import com.example.data.local.OrderHistoryEntity
import com.example.data.model.OrderCategory
import com.example.service.FloatingWidgetService
import com.example.service.OrderAccessibilityService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryDraftConfig(
    val category: OrderCategory,
    val isEnabled: Boolean,
    val maxPickupDistanceKm: Float,
    val maxDeliveryDistanceKm: Float,
    val minShippingFeeVnd: Int
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as OrderFilterApp).repository

    // UI Tab selection (FAST | VIP | FOOD | BIKE)
    private val _selectedCategory = MutableStateFlow(OrderCategory.FAST)
    val selectedCategory: StateFlow<OrderCategory> = _selectedCategory.asStateFlow()

    // In-memory editable draft configurations for all 4 categories
    private val _draftConfigs = MutableStateFlow<Map<OrderCategory, CategoryDraftConfig>>(
        OrderCategory.entries.associateWith { cat ->
            CategoryDraftConfig(
                category = cat,
                isEnabled = true,
                maxPickupDistanceKm = cat.defaultMaxPickupKm,
                maxDeliveryDistanceKm = cat.defaultMaxDeliveryKm,
                minShippingFeeVnd = cat.defaultMinFeeVnd
            )
        }
    )
    val draftConfigs: StateFlow<Map<OrderCategory, CategoryDraftConfig>> = _draftConfigs.asStateFlow()

    // System status flags
    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _hasOverlayPermission = MutableStateFlow(false)
    val hasOverlayPermission: StateFlow<Boolean> = _hasOverlayPermission.asStateFlow()

    // Master toggles from Repository
    val isMasterScanning = repository.isMasterScanningEnabled
    val isTestMode = repository.isTestModeEnabled
    val isFloatingWidget = repository.isFloatingWidgetEnabled
    val cooldownUntil = repository.cooldownUntil

    // History flow from Room
    val orderHistory: StateFlow<List<OrderHistoryEntity>> = repository.orderHistoryFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Toast/Snackbar notifications
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.filterConfigsFlow.collect { entities ->
                if (entities.isNotEmpty()) {
                    val currentMap = _draftConfigs.value.toMutableMap()
                    for (entity in entities) {
                        val cat = OrderCategory.fromString(entity.categoryKey) ?: continue
                        currentMap[cat] = CategoryDraftConfig(
                            category = cat,
                            isEnabled = entity.isEnabled,
                            maxPickupDistanceKm = entity.maxPickupDistanceKm,
                            maxDeliveryDistanceKm = entity.maxDeliveryDistanceKm,
                            minShippingFeeVnd = entity.minShippingFeeVnd
                        )
                    }
                    _draftConfigs.value = currentMap
                }
            }
        }
        refreshSystemPermissions()
    }

    fun selectCategory(category: OrderCategory) {
        _selectedCategory.value = category
    }

    fun updateDraftConfig(
        category: OrderCategory,
        isEnabled: Boolean? = null,
        maxPickup: Float? = null,
        maxDelivery: Float? = null,
        minFee: Int? = null
    ) {
        val current = _draftConfigs.value[category] ?: return
        val updated = current.copy(
            isEnabled = isEnabled ?: current.isEnabled,
            maxPickupDistanceKm = maxPickup ?: current.maxPickupDistanceKm,
            maxDeliveryDistanceKm = maxDelivery ?: current.maxDeliveryDistanceKm,
            minShippingFeeVnd = minFee ?: current.minShippingFeeVnd
        )
        _draftConfigs.value = _draftConfigs.value + (category to updated)
    }

    fun saveAllConfigurations() {
        viewModelScope.launch {
            val entities = _draftConfigs.value.values.map { draft ->
                FilterConfigEntity(
                    categoryKey = draft.category.key,
                    isEnabled = draft.isEnabled,
                    maxPickupDistanceKm = draft.maxPickupDistanceKm,
                    maxDeliveryDistanceKm = draft.maxDeliveryDistanceKm,
                    minShippingFeeVnd = draft.minShippingFeeVnd
                )
            }
            repository.saveFilterConfigs(entities)
            _userMessage.emit("Đã lưu cấu hình thành công cho tất cả loại đơn!")
        }
    }

    fun setMasterScanning(enabled: Boolean) {
        repository.setMasterScanning(enabled)
        if (enabled && !_isAccessibilityEnabled.value) {
            viewModelScope.launch {
                _userMessage.emit("Đã bật quét tự động. Vui lòng bật Dịch vụ Hỗ trợ Tiếp cận để quét đơn trên màn hình!")
            }
        }
    }

    fun setTestMode(enabled: Boolean) {
        repository.setTestMode(enabled)
    }

    fun setFloatingWidget(enabled: Boolean, context: Context) {
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                viewModelScope.launch {
                    _userMessage.emit("Vui lòng cấp quyền hiển thị trên ứng dụng khác trước!")
                }
                return
            }
            repository.setFloatingWidget(true)
            FloatingWidgetService.start(context)
        } else {
            repository.setFloatingWidget(false)
            FloatingWidgetService.stop(context)
        }
    }

    fun clearCooldown() {
        repository.setCooldown(0L)
        viewModelScope.launch {
            _userMessage.emit("Đã kết thúc thời gian tạm dừng nhận đơn!")
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            _userMessage.emit("Đã xóa toàn bộ lịch sử đơn hàng.")
        }
    }

    fun refreshSystemPermissions() {
        val context = getApplication<Application>()
        _isAccessibilityEnabled.value = OrderAccessibilityService.isServiceRunning()
        _hasOverlayPermission.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun triggerScreenScan() {
        val service = OrderAccessibilityService.instance
        if (service != null) {
            val success = service.forceScanCurrentWindow()
            viewModelScope.launch {
                if (success) {
                    _userMessage.emit("Đã quét màn hình thành công!")
                } else {
                    _userMessage.emit("Không tìm thấy thông tin đơn hàng trên màn hình hiện tại.")
                }
            }
        } else {
            viewModelScope.launch {
                _userMessage.emit("Dịch vụ Hỗ trợ Tiếp cận chưa bật. Hãy bật dịch vụ trong Cài đặt trước!")
            }
        }
    }
}
