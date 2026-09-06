package com.example.data.model

data class ParsedOrder(
    val identifier: String,
    val orderType: OrderCategory?,
    val rawCategoryText: String,
    val deliveryDistanceKm: Float, // OUTSIDE parentheses (e.g. 2.1km)
    val pickupDistanceKm: Float,   // INSIDE parentheses (e.g. 0.5km)
    val shippingFeeVnd: Int,       // Shipping fee, e.g. 11K -> 11000
    val codAmountVnd: Int,         // Cash On Delivery / Tiền ứng / Thu hộ (strictly distinct from shipping fee)
    val pickupAddress: String = "",
    val deliveryAddress: String = "",
    val rawText: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
