package com.example.data.model

enum class OrderCategory(
    val key: String,
    val displayName: String,
    val defaultMaxPickupKm: Float,
    val defaultMaxDeliveryKm: Float,
    val defaultMinFeeVnd: Int
) {
    FAST("FAST", "FAST", 2.0f, 5.0f, 10_000),
    VIP("VIP", "VIP", 2.5f, 6.0f, 10_000),
    FOOD("FOOD", "FOOD", 1.5f, 4.0f, 10_000),
    BIKE("BIKE", "BIKE", 2.0f, 5.0f, 10_000);

    companion object {
        fun fromString(value: String): OrderCategory? {
            val upper = value.uppercase().trim()
            return entries.firstOrNull { it.key == upper || upper.contains(it.key) }
        }
    }
}
