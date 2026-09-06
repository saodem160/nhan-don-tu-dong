package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "filter_configs")
data class FilterConfigEntity(
    @PrimaryKey
    val categoryKey: String, // FAST, VIP, FOOD, BIKE
    val isEnabled: Boolean,
    val maxPickupDistanceKm: Float,
    val maxDeliveryDistanceKm: Float,
    val minShippingFeeVnd: Int
)
