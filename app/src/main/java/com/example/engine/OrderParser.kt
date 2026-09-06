package com.example.engine

import com.example.data.model.OrderCategory
import com.example.data.model.ParsedOrder
import java.util.Locale

object OrderParser {

    // Regex for: deliveryDistance (pickupDistance)
    // E.g. "1m (0.7km)", "2.1km (0.5km)", "1.8km (0.8km)", "500m (1.2km)"
    private val PAREN_DELIVERY_FIRST_REGEX = Regex(
        """(\d+(?:[.,]\d+)?)\s*(km|m)?\s*\(\s*(\d+(?:[.,]\d+)?)\s*(km|m)?\s*\)""",
        RegexOption.IGNORE_CASE
    )

    // Regex for: (pickupDistance) deliveryDistance
    // E.g. "(0.5km) 2.1km", "(0.7km) 1m"
    private val PAREN_PICKUP_FIRST_REGEX = Regex(
        """\(\s*(\d+(?:[.,]\d+)?)\s*(km|m)?\s*\)\s*(\d+(?:[.,]\d+)?)\s*(km|m)?""",
        RegexOption.IGNORE_CASE
    )

    // Regex for labeled distances e.g. "Lấy 0.5km", "Giao 2.1km"
    private val LABELED_PICKUP_REGEX = Regex(
        """(?:Lấy|Điểm lấy|Nhận hàng|Cách|Khoảng cách lấy)[\s:]*(\d+(?:[.,]\d+)?)\s*(km|m)?""",
        RegexOption.IGNORE_CASE
    )
    private val LABELED_DELIVERY_REGEX = Regex(
        """(?:Giao|Điểm giao|Giao hàng|Đến|Khoảng cách giao)[\s:]*(\d+(?:[.,]\d+)?)\s*(km|m)?""",
        RegexOption.IGNORE_CASE
    )

    // Regex for fallback single distances
    private val SINGLE_DISTANCE_REGEX = Regex(
        """(\d+(?:[.,]\d+)?)\s*(km|m)\b""",
        RegexOption.IGNORE_CASE
    )

    // Regex for COD / Tiền ứng / Thu hộ e.g. "COD 230K", "COD: 150K", "Tiền ứng: 200k"
    private val COD_PREFIX_REGEX = Regex(
        """\b(?:COD|Tiền\s*ứng|Thu\s*hộ|Ứng)[\s:]*(\d+(?:[.,]\d+)?)\s*(k|K|đ|VND|d)?\b""",
        RegexOption.IGNORE_CASE
    )

    // Regex for Shipping Fee / Cước phí / Phí ship / Thu nhập / Giá cước
    private val SHIPPING_FEE_PREFIX_REGEX = Regex(
        """(?:Tổng\s*phí\s*ship|Phí\s*DV|Phí\s*ship|Cước\s*phí|Cước|Tiền\s*cước|Thu\s*nhập|Giá\s*cước|Giá|Tiền\s*ship|Tổng\s*cước|Thanh\s*toán)[\s:]*(\d+(?:[.,]\d+)?)\s*(k|K|đ|VND|d)?\b""",
        RegexOption.IGNORE_CASE
    )

    // Regex for standalone fee like "10K", "11K", "22k" (ignoring kg/km)
    private val STANDALONE_K_REGEX = Regex(
        """\b(\d+)\s*[kK](?![a-zA-Z])\b"""
    )

    // Regex for currency VND numbers e.g. "15.000đ", "25,000 VND", "10.000"
    private val CURRENCY_AMOUNT_REGEX = Regex(
        """\b(\d{1,3}(?:[.,]\d{3})+)\s*(?:đ|VND|d)?\b""",
        RegexOption.IGNORE_CASE
    )

    fun parse(rawText: String, orderIdHint: String? = null): ParsedOrder {
        val cleanText = rawText.replace("\n", " ").trim()

        // 1. Parse Distance: Delivery (outside parens) and Pickup (inside parens)
        var deliveryKm = 0f
        var pickupKm = 0f

        val deliveryFirstMatch = PAREN_DELIVERY_FIRST_REGEX.find(cleanText)
        val pickupFirstMatch = if (deliveryFirstMatch == null) PAREN_PICKUP_FIRST_REGEX.find(cleanText) else null

        if (deliveryFirstMatch != null) {
            val dVal = deliveryFirstMatch.groupValues[1].replace(',', '.').toFloatOrNull() ?: 0f
            val dUnit = deliveryFirstMatch.groupValues[2].lowercase(Locale.ROOT)
            val pVal = deliveryFirstMatch.groupValues[3].replace(',', '.').toFloatOrNull() ?: 0f
            val pUnit = deliveryFirstMatch.groupValues[4].lowercase(Locale.ROOT)

            deliveryKm = if (dUnit == "m") dVal / 1000f else dVal
            pickupKm = if (pUnit == "m") pVal / 1000f else pVal
        } else if (pickupFirstMatch != null) {
            val pVal = pickupFirstMatch.groupValues[1].replace(',', '.').toFloatOrNull() ?: 0f
            val pUnit = pickupFirstMatch.groupValues[2].lowercase(Locale.ROOT)
            val dVal = pickupFirstMatch.groupValues[3].replace(',', '.').toFloatOrNull() ?: 0f
            val dUnit = pickupFirstMatch.groupValues[4].lowercase(Locale.ROOT)

            pickupKm = if (pUnit == "m") pVal / 1000f else pVal
            deliveryKm = if (dUnit == "m") dVal / 1000f else dVal
        } else {
            // Check for explicit labels
            val pickupMatch = LABELED_PICKUP_REGEX.find(cleanText)
            val deliveryMatch = LABELED_DELIVERY_REGEX.find(cleanText)

            if (pickupMatch != null || deliveryMatch != null) {
                if (pickupMatch != null) {
                    var p = pickupMatch.groupValues[1].replace(',', '.').toFloatOrNull() ?: 0f
                    if (pickupMatch.groupValues[2].equals("m", ignoreCase = true)) p /= 1000f
                    pickupKm = p
                }
                if (deliveryMatch != null) {
                    var d = deliveryMatch.groupValues[1].replace(',', '.').toFloatOrNull() ?: 0f
                    if (deliveryMatch.groupValues[2].equals("m", ignoreCase = true)) d /= 1000f
                    deliveryKm = d
                }
            } else {
                // Fallback: search for standalone distance occurrences
                val allDistances = SINGLE_DISTANCE_REGEX.findAll(cleanText).toList()
                if (allDistances.size >= 2) {
                    var d = allDistances[0].groupValues[1].replace(',', '.').toFloatOrNull() ?: 0f
                    if (allDistances[0].groupValues[2].equals("m", ignoreCase = true)) d /= 1000f
                    deliveryKm = d

                    var p = allDistances[1].groupValues[1].replace(',', '.').toFloatOrNull() ?: 0f
                    if (allDistances[1].groupValues[2].equals("m", ignoreCase = true)) p /= 1000f
                    pickupKm = p
                } else if (allDistances.size == 1) {
                    var d = allDistances[0].groupValues[1].replace(',', '.').toFloatOrNull() ?: 0f
                    if (allDistances[0].groupValues[2].equals("m", ignoreCase = true)) d /= 1000f
                    deliveryKm = d
                }
            }
        }

        // 2. Parse COD strictly and separate from Shipping Fee
        var codAmount = 0
        val codMatches = COD_PREFIX_REGEX.findAll(cleanText)
        var codStartIndex = -1
        var codEndIndex = -1
        for (m in codMatches) {
            val numStr = m.groupValues[1].replace(".", "").replace(",", "")
            val unit = m.groupValues[2].uppercase(Locale.ROOT)
            val baseVal = numStr.toIntOrNull() ?: 0
            val fullVal = if (unit == "K" || (baseVal in 1..999 && !m.groupValues[1].contains("."))) baseVal * 1000 else baseVal
            codAmount = fullVal
            codStartIndex = m.range.first
            codEndIndex = m.range.last
            break
        }

        // 3. Parse Shipping Fee (ignoring any match inside the COD portion)
        var shippingFee = 0
        val feeWithPrefix = SHIPPING_FEE_PREFIX_REGEX.find(cleanText)
        if (feeWithPrefix != null) {
            val numStr = feeWithPrefix.groupValues[1].replace(".", "").replace(",", "")
            val unit = feeWithPrefix.groupValues[2].uppercase(Locale.ROOT)
            val baseVal = numStr.toIntOrNull() ?: 0
            shippingFee = if (unit == "K" || (baseVal in 1..999 && !feeWithPrefix.groupValues[1].contains("."))) baseVal * 1000 else baseVal
        }

        if (shippingFee == 0) {
            // Check standalone K (e.g. "10K", "11K", "25K")
            val kMatches = STANDALONE_K_REGEX.findAll(cleanText)
            for (kMatch in kMatches) {
                // Ensure this is not part of the COD range
                if (codStartIndex != -1 && kMatch.range.first >= codStartIndex && kMatch.range.last <= codEndIndex + 4) {
                    continue
                }
                val kVal = kMatch.groupValues[1].toIntOrNull() ?: 0
                if (kVal in 5..500) { // realistic shipping fee in thousands (5k to 500k)
                    shippingFee = kVal * 1000
                    break
                }
            }

            // If still not found, check currency amounts e.g. "15.000đ"
            if (shippingFee == 0) {
                val currMatches = CURRENCY_AMOUNT_REGEX.findAll(cleanText)
                for (currMatch in currMatches) {
                    if (codStartIndex != -1 && currMatch.range.first >= codStartIndex && currMatch.range.last <= codEndIndex + 4) {
                        continue
                    }
                    val rawNum = currMatch.groupValues[1].replace(".", "").replace(",", "")
                    val valInt = rawNum.toIntOrNull() ?: 0
                    if (valInt in 5_000..1_000_000) {
                        shippingFee = valInt
                        break
                    }
                }
            }
        }

        // 4. Identify Order Category (FAST, VIP, FOOD, BIKE)
        var detectedCategory: OrderCategory? = null
        var rawCategory = ""
        val upper = cleanText.uppercase(Locale.ROOT)

        // Check exact word boundaries or prominent tokens
        for (cat in OrderCategory.entries) {
            val pattern = Regex("""\b${cat.key}\b""", RegexOption.IGNORE_CASE)
            if (pattern.containsMatchIn(upper)) {
                detectedCategory = cat
                rawCategory = cat.key
                break
            }
        }

        // Fallback check if category is contained as substring
        if (detectedCategory == null) {
            if (upper.contains("FOOD") || upper.contains("THỨC ĂN") || upper.contains("ĐỒ ĂN") || upper.contains("MÓN ĂN")) {
                detectedCategory = OrderCategory.FOOD
                rawCategory = "FOOD"
            } else if (upper.contains("VIP") || upper.contains("TIẾT KIỆM") || upper.contains("SIÊU RẺ")) {
                detectedCategory = OrderCategory.VIP
                rawCategory = "VIP"
            } else if (upper.contains("FAST") || upper.contains("SIÊU TỐC") || upper.contains("SIEU TOC") || upper.contains("HỎA TỐC") || upper.contains("EXPRESS") || upper.contains("GIAO HÀNG")) {
                detectedCategory = OrderCategory.FAST
                rawCategory = "FAST"
            } else if (upper.contains("BIKE") || upper.contains("XE MÁY") || upper.contains("CHỞ KHÁCH") || upper.contains("DI CHUYỂN")) {
                detectedCategory = OrderCategory.BIKE
                rawCategory = "BIKE"
            } else {
                // Default to FAST if distance or fee is present
                if (deliveryKm > 0 || shippingFee > 0) {
                    detectedCategory = OrderCategory.FAST
                    rawCategory = "FAST"
                }
            }
        }

        val extractedAddr = extractOrderAddress(cleanText)
        val extractedNote = extractOrderNote(cleanText)
        val finalAddr = if (extractedNote.isNotEmpty()) "$extractedAddr (nhắn: $extractedNote)" else extractedAddr

        val finalIdentifier = orderIdHint ?: generateOrderIdentifier(
            cleanText,
            detectedCategory?.key,
            deliveryKm,
            pickupKm,
            shippingFee,
            extractedAddr
        )

        return ParsedOrder(
            identifier = finalIdentifier,
            orderType = detectedCategory,
            rawCategoryText = rawCategory.ifEmpty { "KHÁC" },
            deliveryDistanceKm = deliveryKm,
            pickupDistanceKm = pickupKm,
            shippingFeeVnd = shippingFee,
            codAmountVnd = codAmount,
            pickupAddress = "",
            deliveryAddress = finalAddr,
            rawText = cleanText
        )
    }

    private fun generateOrderIdentifier(
        text: String,
        cat: String?,
        deliveryKm: Float,
        pickupKm: Float,
        fee: Int,
        address: String
    ): String {
        val cleanAddr = address.filter { it.isLetterOrDigit() }.take(15)
        val hash = (cleanAddr.ifEmpty { text }.hashCode() and 0x7FFFFFFF).toString(16).takeLast(6).uppercase(Locale.ROOT)
        val catPrefix = cat ?: "ORDER"
        return "$catPrefix-$hash-${deliveryKm}km-${pickupKm}km-${fee / 1000}K"
    }

    private fun extractOrderAddress(text: String): String {
        val prefix = listOf("Lấy tại:", "Điểm lấy:", "Từ:", "Giao tại:", "Điểm giao:", "Đến:")
        for (p in prefix) {
            val idx = text.indexOf(p, ignoreCase = true)
            if (idx != -1) {
                val after = text.substring(idx + p.length).trim()
                val cut = after.indexOfAny(charArrayOf('\n', '|', '•'))
                val res = if (cut != -1) after.substring(0, cut).trim() else after.take(80).trim()
                if (res.isNotEmpty()) return res
            }
        }

        // Look for address after timestamp (e.g. "VIP 09:11 <address>") or after "COD <amount>"
        val timeRegex = Regex("""\b\d{1,2}:\d{2}\b""")
        val timeMatch = timeRegex.find(text)
        if (timeMatch != null) {
            var afterTime = text.substring(timeMatch.range.last + 1).trim()
            val codMatch = Regex("""\bCOD\s*\d+\s*[kKđ]?\b""", RegexOption.IGNORE_CASE).find(afterTime)
            if (codMatch != null && codMatch.range.first <= 2) {
                afterTime = afterTime.substring(codMatch.range.last + 1).trim()
            }
            val noteIdx = afterTime.indexOf("nhắn:", ignoreCase = true)
            val addrText = if (noteIdx != -1) afterTime.substring(0, noteIdx).trim() else afterTime
            if (addrText.isNotEmpty() && addrText.length >= 5) {
                return addrText.take(90).trim()
            }
        }

        // Fallback: match house number + street name with comma
        val addressRegex = Regex(
            """\b\d+[/A-Za-z0-9.-]*\s+[^0-9,\n]+,\s*[^,\n]+(?:,\s*[^,\n]+)*""",
            RegexOption.IGNORE_CASE
        )
        val match = addressRegex.find(text)
        if (match != null) {
            val noteIdx = match.value.indexOf("nhắn:", ignoreCase = true)
            val clean = if (noteIdx != -1) match.value.substring(0, noteIdx).trim() else match.value.trim()
            return clean.take(90)
        }

        return ""
    }

    private fun extractOrderNote(text: String): String {
        val noteKeywords = listOf("nhắn:", "Ghi chú:", "Lưu ý:", "Note:")
        for (kw in noteKeywords) {
            val idx = text.indexOf(kw, ignoreCase = true)
            if (idx != -1) {
                val after = text.substring(idx + kw.length).trim()
                val cut = after.indexOfAny(charArrayOf('\n', '|', '•'))
                val res = if (cut != -1) after.substring(0, cut).trim() else after.take(60).trim()
                if (res.isNotEmpty()) return res
            }
        }
        return ""
    }
}
