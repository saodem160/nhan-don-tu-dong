package com.example.engine

import com.example.data.local.FilterConfigEntity
import com.example.data.model.OrderCategory
import com.example.data.model.ParsedOrder

data class FilterEvaluationResult(
    val isMatch: Boolean,
    val matchedCategory: OrderCategory?,
    val reason: String
)

object OrderFilteringEngine {

    // Cooldown duration after a successful LIVE grab: 15 minutes
    const val LIVE_GRAB_COOLDOWN_MS = 15 * 60 * 1000L

    fun evaluate(
        order: ParsedOrder,
        configs: Map<String, FilterConfigEntity>
    ): FilterEvaluationResult {
        val category = order.orderType ?: OrderCategory.FAST
        val config = configs[category.key] ?: configs.values.firstOrNull { it.isEnabled }

        if (config == null) {
            return FilterEvaluationResult(
                isMatch = false,
                matchedCategory = category,
                reason = "Chưa có cấu hình cho loại đơn ${category.key}"
            )
        }

        if (!config.isEnabled) {
            return FilterEvaluationResult(
                isMatch = false,
                matchedCategory = category,
                reason = "Đã tắt nhận loại đơn ${category.key} trong cài đặt"
            )
        }

        // Check 1: Pickup Distance (khoảng cách lấy)
        if (config.maxPickupDistanceKm > 0 && order.pickupDistanceKm > config.maxPickupDistanceKm) {
            return FilterEvaluationResult(
                isMatch = false,
                matchedCategory = category,
                reason = "Khoảng cách lấy quá xa: %.1f km > tối đa %.1f km".format(
                    order.pickupDistanceKm, config.maxPickupDistanceKm
                )
            )
        }

        // Check 2: Delivery Distance (khoảng cách giao)
        if (config.maxDeliveryDistanceKm > 0 && order.deliveryDistanceKm > config.maxDeliveryDistanceKm) {
            return FilterEvaluationResult(
                isMatch = false,
                matchedCategory = category,
                reason = "Khoảng cách giao quá xa: %.1f km > tối đa %.1f km".format(
                    order.deliveryDistanceKm, config.maxDeliveryDistanceKm
                )
            )
        }

        // Check 3: Minimum Shipping Fee (phí ship tối thiểu)
        if (order.shippingFeeVnd < config.minShippingFeeVnd) {
            return FilterEvaluationResult(
                isMatch = false,
                matchedCategory = category,
                reason = "Cước phí quá thấp: %,d đ < tối thiểu %,d đ".format(
                    order.shippingFeeVnd, config.minShippingFeeVnd
                )
            )
        }

        return FilterEvaluationResult(
            isMatch = true,
            matchedCategory = category,
            reason = "Thỏa mãn tiêu chí ${category.key} (Lấy: %.1fkm, Giao: %.1fkm, Cước: %,d đ)".format(
                order.pickupDistanceKm,
                order.deliveryDistanceKm,
                order.shippingFeeVnd
            )
        )
    }
}
