package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "order_history")
data class OrderHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderIdentifier: String,
    val orderType: String,
    val shippingFeeVnd: Int,
    val codAmountVnd: Int,
    val deliveryDistanceKm: Float,
    val pickupDistanceKm: Float,
    val pickupAddress: String,
    val deliveryAddress: String,
    val actionTaken: String, // "Đã nhận đơn", "Đóng (Thử nghiệm)", "Đóng (Không đạt tiêu chí)"
    val isMatch: Boolean,
    val matchReason: String,
    val rawText: String,
    val timestamp: Long = System.currentTimeMillis()
)
