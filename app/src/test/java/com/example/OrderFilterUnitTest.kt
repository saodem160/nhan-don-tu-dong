package com.example

import com.example.data.local.FilterConfigEntity
import com.example.data.model.OrderCategory
import com.example.engine.OrderFilteringEngine
import com.example.engine.OrderParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderFilterUnitTest {

    @Test
    fun testParseExampleOrderPromptFormat() {
        // User prompt example: "2.1km (0.5km) Dưới 10kg 11K FAST"
        val raw = "2.1km (0.5km) Dưới 10kg 11K FAST"
        val parsed = OrderParser.parse(raw)

        // 1. Delivery Distance: OUTSIDE/BEFORE parentheses
        assertEquals(2.1f, parsed.deliveryDistanceKm, 0.01f)

        // 2. Pickup Distance: INSIDE parentheses
        assertEquals(0.5f, parsed.pickupDistanceKm, 0.01f)

        // 3. Shipping Fee: 11K -> 11000
        assertEquals(11_000, parsed.shippingFeeVnd)

        // 4. COD: None in this order
        assertEquals(0, parsed.codAmountVnd)

        // 5. Category: FAST
        assertEquals(OrderCategory.FAST, parsed.orderType)
    }

    @Test
    fun testParseAllFourCardsFromUserScreenshot() {
        // Card 1 from screenshot:
        // 1m (0.7km) 1kg 10K VIP 09:11
        // 31 Nguyễn Tri Phương, Phường 5, Đông H...
        val card1Raw = "1m (0.7km) 1kg 10K VIP 09:11 31 Nguyễn Tri Phương, Phường 5, Đông Hà, Quảng Trị"
        val parsed1 = OrderParser.parse(card1Raw)
        assertEquals(0.001f, parsed1.deliveryDistanceKm, 0.0005f) // 1 meter = 0.001km
        assertEquals(0.7f, parsed1.pickupDistanceKm, 0.01f)
        assertEquals(10_000, parsed1.shippingFeeVnd)
        assertEquals(0, parsed1.codAmountVnd)
        assertEquals(OrderCategory.VIP, parsed1.orderType)
        assertTrue(parsed1.deliveryAddress.contains("Nguyễn Tri Phương"))

        // Card 2 from screenshot:
        // 1m (0.4km) 1kg 10K VIP 09:10
        // 09 Ngô Quyền, Phường 5, Đông Hà, Quảng...
        val card2Raw = "1m (0.4km) 1kg 10K VIP 09:10 09 Ngô Quyền, Phường 5, Đông Hà, Quảng Trị"
        val parsed2 = OrderParser.parse(card2Raw)
        assertEquals(0.001f, parsed2.deliveryDistanceKm, 0.0005f)
        assertEquals(0.4f, parsed2.pickupDistanceKm, 0.01f)
        assertEquals(10_000, parsed2.shippingFeeVnd)
        assertEquals(0, parsed2.codAmountVnd)
        assertEquals(OrderCategory.VIP, parsed2.orderType)
        assertTrue(parsed2.deliveryAddress.contains("Ngô Quyền"))

        // Card 3 from screenshot:
        // 2.1km (0.5km) Dưới 10kg 11K FAST 09:10
        // 303 Lê Duẩn, Phường 5, Nam Đông Hà, Qu... nhắn: yến chưng
        val card3Raw = "2.1km (0.5km) Dưới 10kg 11K FAST 09:10 303 Lê Duẩn, Phường 5, Nam Đông Hà, Quảng Trị nhắn: yến chưng"
        val parsed3 = OrderParser.parse(card3Raw)
        assertEquals(2.1f, parsed3.deliveryDistanceKm, 0.01f)
        assertEquals(0.5f, parsed3.pickupDistanceKm, 0.01f)
        assertEquals(11_000, parsed3.shippingFeeVnd)
        assertEquals(0, parsed3.codAmountVnd)
        assertEquals(OrderCategory.FAST, parsed3.orderType)
        assertTrue(parsed3.deliveryAddress.contains("Lê Duẩn"))
        assertTrue(parsed3.deliveryAddress.contains("yến chưng"))

        // Card 4 from screenshot:
        // 1.8km (0.8km) Dưới 10kg 10K FAST 09:08
        // COD 230K
        // 12/1 Bà Huyện Thanh Quan, Phường 5, Đô...
        val card4Raw = "1.8km (0.8km) Dưới 10kg 10K FAST 09:08 COD 230K 12/1 Bà Huyện Thanh Quan, Phường 5, Đông Hà, Quảng Trị"
        val parsed4 = OrderParser.parse(card4Raw)
        assertEquals(1.8f, parsed4.deliveryDistanceKm, 0.01f)
        assertEquals(0.8f, parsed4.pickupDistanceKm, 0.01f)
        assertEquals(10_000, parsed4.shippingFeeVnd) // Shipping fee is strictly 10K
        assertEquals(230_000, parsed4.codAmountVnd) // COD is strictly 230K
        assertEquals(OrderCategory.FAST, parsed4.orderType)
        assertTrue(parsed4.deliveryAddress.contains("Bà Huyện Thanh Quan"))
    }

    @Test
    fun testParseDistinguishesShippingFeeFromCod() {
        // Must STRICTLY distinguish shipping fee from COD
        val raw = "3.5km (1.2km) COD: 250K Cước phí: 22K BIKE Điểm lấy: Tiệm Bánh Điểm giao: Số 10 Lý Thường Kiệt"
        val parsed = OrderParser.parse(raw)

        assertEquals(3.5f, parsed.deliveryDistanceKm, 0.01f)
        assertEquals(1.2f, parsed.pickupDistanceKm, 0.01f)
        assertEquals(22_000, parsed.shippingFeeVnd)
        assertEquals(250_000, parsed.codAmountVnd)
        assertEquals(OrderCategory.BIKE, parsed.orderType)
    }

    @Test
    fun testFilteringEngineMatchingAndRejection() {
        val configs = mapOf(
            "FAST" to FilterConfigEntity(
                categoryKey = "FAST",
                isEnabled = true,
                maxPickupDistanceKm = 2.0f,
                maxDeliveryDistanceKm = 5.0f,
                minShippingFeeVnd = 10_000
            ),
            "FOOD" to FilterConfigEntity(
                categoryKey = "FOOD",
                isEnabled = false, // Disabled
                maxPickupDistanceKm = 1.5f,
                maxDeliveryDistanceKm = 4.0f,
                minShippingFeeVnd = 15_000
            )
        )

        // Case 1: Valid FAST order that satisfies all criteria
        val validOrder = OrderParser.parse("2.1km (0.5km) 11K FAST")
        val result1 = OrderFilteringEngine.evaluate(validOrder, configs)
        assertTrue(result1.isMatch)
        assertEquals(OrderCategory.FAST, result1.matchedCategory)

        // Case 2: FAST order with fee below minimum (e.g. 9K < 10,000)
        val lowFeeOrder = OrderParser.parse("2.1km (0.5km) 9K FAST")
        val result2 = OrderFilteringEngine.evaluate(lowFeeOrder, configs)
        assertFalse(result2.isMatch)
        assertTrue(result2.reason.contains("Cước phí quá thấp"))

        // Case 3: FAST order with pickup distance exceeding maximum (3.0km > 2.0km)
        val farPickupOrder = OrderParser.parse("2.1km (3.0km) 15K FAST")
        val result3 = OrderFilteringEngine.evaluate(farPickupOrder, configs)
        assertFalse(result3.isMatch)
        assertTrue(result3.reason.contains("Khoảng cách lấy quá xa"))

        // Case 4: FOOD order when category is disabled
        val disabledFoodOrder = OrderParser.parse("1.5km (0.5km) 20K FOOD")
        val result4 = OrderFilteringEngine.evaluate(disabledFoodOrder, configs)
        assertFalse(result4.isMatch)
        assertTrue(result4.reason.contains("Đã tắt nhận"))
    }

    @Test
    fun testParseReverseParensAndLabeledDistances() {
        // Format: (Pickup) Delivery
        val reverse = OrderParser.parse("(0.8km) 4.2km Siêu Tốc Cước: 35.000đ")
        assertEquals(4.2f, reverse.deliveryDistanceKm, 0.01f)
        assertEquals(0.8f, reverse.pickupDistanceKm, 0.01f)
        assertEquals(35_000, reverse.shippingFeeVnd)
        assertEquals(OrderCategory.FAST, reverse.orderType)

        // Labeled format
        val labeled = OrderParser.parse("Điểm lấy: 1.2km - Điểm giao: 5.5km - Thu nhập: 40k")
        assertEquals(5.5f, labeled.deliveryDistanceKm, 0.01f)
        assertEquals(1.2f, labeled.pickupDistanceKm, 0.01f)
        assertEquals(40_000, labeled.shippingFeeVnd)
    }
}
