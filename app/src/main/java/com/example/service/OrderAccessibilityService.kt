package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.OrderFilterApp
import com.example.data.local.FilterConfigEntity
import com.example.data.local.OrderHistoryEntity
import com.example.data.model.ParsedOrder
import com.example.engine.FilterEvaluationResult
import com.example.engine.OrderFilteringEngine
import com.example.engine.OrderParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class OrderAccessibilityService : AccessibilityService() {

    private val tag = "OrderAccessService"
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var wakeLock: PowerManager.WakeLock? = null
    private var isScanningEnabled = false
    private var isTestMode = true
    private var cooldownUntilTs = 0L
    private var currentConfigs = mapOf<String, FilterConfigEntity>()
    private var pollingJob: Job? = null

    private fun updatePollingScan(enabled: Boolean) {
        pollingJob?.cancel()
        if (enabled) {
            pollingJob = serviceScope.launch {
                while (isActive) {
                    delay(2000)
                    if (isScanningEnabled) {
                        val now = System.currentTimeMillis()
                        if (isTestMode || now >= cooldownUntilTs) {
                            try {
                                scanAllCandidateWindows()
                            } catch (e: Exception) {
                                Log.e(tag, "Polling scan error", e)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(tag, "OrderAccessibilityService connected")
        instance = this

        val repo = OrderFilterApp.instance.repository

        // Observe Master Scan
        serviceScope.launch {
            repo.isMasterScanningEnabled.collectLatest { enabled ->
                isScanningEnabled = enabled
                updateWakeLock(enabled)
                updatePollingScan(enabled)
            }
        }

        // Observe Test Mode
        serviceScope.launch {
            repo.isTestModeEnabled.collectLatest { testMode ->
                isTestMode = testMode
            }
        }

        // Observe Cooldown
        serviceScope.launch {
            repo.cooldownUntil.collectLatest { ts ->
                cooldownUntilTs = ts
            }
        }

        // Observe Filter Configs
        serviceScope.launch {
            repo.filterConfigsFlow.collectLatest { list ->
                currentConfigs = list.associateBy { it.categoryKey }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isScanningEnabled || event == null) return

        // Prevent acting during the cooldown period after a live grab
        val now = System.currentTimeMillis()
        if (!isTestMode && now < cooldownUntilTs) {
            return
        }

        val eventSource = event.source
        val root = rootInActiveWindow ?: eventSource ?: return

        try {
            scanWindowForOrders(root)
        } catch (e: Exception) {
            Log.e(tag, "Error processing accessibility event tree", e)
        }
    }

    override fun onInterrupt() {
        Log.d(tag, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        releaseWakeLock()
        instance = null
    }

    private fun updateWakeLock(acquire: Boolean) {
        try {
            if (acquire) {
                if (wakeLock == null) {
                    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                    wakeLock = pm?.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "OrderFilterApp:KeepScanActiveWakeLock"
                    )
                }
                if (wakeLock?.isHeld == false) {
                    wakeLock?.acquire(60 * 60 * 1000L) // 60 min timeout
                }
            } else {
                releaseWakeLock()
            }
        } catch (e: Exception) {
            Log.e(tag, "WakeLock error", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error releasing wake lock", e)
        }
    }

    private fun scanAllCandidateWindows(): Boolean {
        var foundAny = false
        val candidateRoots = mutableListOf<AccessibilityNodeInfo>()

        try {
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                for (w in windowList) {
                    if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        w.root?.let { candidateRoots.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            // Safe fallback
        }

        if (candidateRoots.isEmpty()) {
            rootInActiveWindow?.let { candidateRoots.add(it) }
        }

        for (root in candidateRoots) {
            try {
                if (scanWindowForOrders(root)) {
                    foundAny = true
                }
            } catch (e: Exception) {
                Log.e(tag, "Error scanning candidate root", e)
            }
        }
        return foundAny
    }

    private fun scanWindowForOrders(root: AccessibilityNodeInfo): Boolean {
        // Do not scan our own application's screen to avoid parsing the history view
        val rootPackage = root.packageName?.toString() ?: ""
        if (rootPackage == packageName) {
            return false
        }

        val repo = OrderFilterApp.instance.repository
        val hasAcceptButton = findAcceptButton(root)
        val hasCloseButton = findCloseButton(root)

        // Case A: If an order detail dialog/modal is already open (as shown in image)
        if (isDetailDialogOrScreen(root, hasAcceptButton, hasCloseButton)) {
            val fullScreenText = extractSubtreeText(root)
            if (fullScreenText.length >= 10) {
                val fullOrder = OrderParser.parse(fullScreenText)
                if (fullOrder.shippingFeeVnd > 0 || fullOrder.deliveryDistanceKm > 0f) {
                    if (!repo.isIgnored(fullOrder.identifier)) {
                        repo.markIgnored(fullOrder.identifier)
                        val evaluation = OrderFilteringEngine.evaluate(fullOrder, currentConfigs)
                        if (evaluation.isMatch) {
                            if (isTestMode) {
                                // TEST MODE: Strictly click "Đóng", NEVER click "Từ chối" or "Nhận đơn"
                                val targetClose = hasCloseButton ?: findCloseButton(root)
                                if (targetClose != null) {
                                    clickNodeOrCoordinates(targetClose)
                                }
                                val action = "Đạt tiêu chí (Thử nghiệm - Đã bấm Đóng)"
                                logHistory(fullOrder, evaluation, action)
                                com.example.util.NotificationHelper.postOrderAlert(this, fullOrder, evaluation, action)
                            } else {
                                // LIVE MODE: Click the green "Nhận đơn" button
                                val targetAccept = hasAcceptButton ?: findAcceptButton(root)
                                if (targetAccept != null) {
                                    clickNodeOrCoordinates(targetAccept)
                                }
                                val action = "Đã nhận đơn (Đã bấm nút Nhận đơn)"
                                logHistory(fullOrder, evaluation, action)
                                com.example.util.NotificationHelper.postOrderAlert(this, fullOrder, evaluation, action)

                                val cooldownTs = System.currentTimeMillis() + OrderFilteringEngine.LIVE_GRAB_COOLDOWN_MS
                                repo.setCooldown(cooldownTs)
                            }
                        } else {
                            // Non-matching order in detail modal: dismiss by clicking "Đóng"
                            if (hasCloseButton != null) {
                                clickNodeOrCoordinates(hasCloseButton)
                            }
                            val action = "Đóng (Không đạt tiêu chí)"
                            logHistory(fullOrder, evaluation, action)
                            com.example.util.NotificationHelper.postOrderAlert(this, fullOrder, evaluation, action)
                        }
                        return true
                    }
                }
            }
        }

        // Case B: Order List Screen - scan for order cards in the list
        val textNodes = mutableListOf<AccessibilityNodeInfo>()
        collectTextNodes(root, textNodes)

        val uniqueCardContainers = mutableListOf<AccessibilityNodeInfo>()
        val seenCardPositions = mutableSetOf<String>()

        for (node in textNodes) {
            val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim()
            if (text.length < 2) continue

            // Exclude tab items and bottom nav items so they are NEVER grouped into an order card
            if (isTabOrNavigationText(text)) continue

            // Recognize order card indicators (distance in paren, km, m, fee, category, COD)
            val hasCardIndicator = text.contains("km", ignoreCase = true) ||
                    text.contains("m)", ignoreCase = true) ||
                    text.contains("m (", ignoreCase = true) ||
                    text.contains("k", ignoreCase = true) ||
                    text.contains("VIP", ignoreCase = true) ||
                    text.contains("FAST", ignoreCase = true) ||
                    text.contains("FOOD", ignoreCase = true) ||
                    text.contains("BIKE", ignoreCase = true) ||
                    text.contains("COD", ignoreCase = true) ||
                    text.contains("Đông Hà", ignoreCase = true) ||
                    text.contains("Phường", ignoreCase = true)

            if (hasCardIndicator) {
                val card = findCardContainer(node)
                val rect = Rect()
                card.getBoundsInScreen(rect)
                val cardPosKey = "${rect.left}_${rect.top}_${rect.right}_${rect.bottom}"

                // Validate reasonable card size (avoid full screen or zero-size container)
                if (rect.height() in 50..850 && rect.width() > 150) {
                    if (!seenCardPositions.contains(cardPosKey)) {
                        seenCardPositions.add(cardPosKey)
                        uniqueCardContainers.add(card)
                    }
                }
            }
        }

        // Process every distinct card discovered on screen
        var scannedCount = 0
        for (card in uniqueCardContainers) {
            val cardText = extractSubtreeText(card)
            if (cardText.length < 6) continue

            val order = OrderParser.parse(cardText)

            // Must have at least a valid distance or fee to qualify as a real order
            if (order.shippingFeeVnd == 0 && order.deliveryDistanceKm == 0f) {
                continue
            }

            // Skip if already evaluated previously to avoid duplicate spamming
            if (repo.isIgnored(order.identifier)) {
                continue
            }
            repo.markIgnored(order.identifier)
            scannedCount++

            // Evaluate order against active filter configurations
            val evaluation = OrderFilteringEngine.evaluate(order, currentConfigs)

            if (evaluation.isMatch) {
                // MATCH FOUND: Follow exact requested workflow:
                // 1. Click specifically on this order card
                // 2. Wait for detail popup (Image 2)
                // 3. Click the green "Nhận đơn" button (or "Đóng" in test mode)
                processMatchedCardInList(card, order, evaluation)
                return true // Stop scanning this turn to handle the matched order
            } else {
                // Log non-matching order into History & post alert so driver is informed!
                val action = "Bỏ qua (Không đạt tiêu chí)"
                logHistory(order, evaluation, action)
                com.example.util.NotificationHelper.postOrderAlert(this, order, evaluation, action)
            }
        }

        return scannedCount > 0
    }

    private fun processMatchedCardInList(
        card: AccessibilityNodeInfo,
        order: ParsedOrder,
        evaluation: FilterEvaluationResult
    ) {
        val repo = OrderFilterApp.instance.repository

        serviceScope.launch {
            Log.d(tag, "Matched order found: ${order.identifier}. Clicking card to open details...")

            // 1. Click directly on the matched order card
            clickNodeOrCoordinates(card)

            // 2. Poll for the detail popup/dialog to appear (polling up to 2000ms, every 120ms)
            var detailRoot: AccessibilityNodeInfo? = null
            var acceptBtn: AccessibilityNodeInfo? = null
            var closeBtn: AccessibilityNodeInfo? = null

            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 2000) {
                delay(120)
                val candidateRoots = mutableListOf<AccessibilityNodeInfo>()
                try {
                    val wList = windows
                    if (!wList.isNullOrEmpty()) {
                        for (w in wList) {
                            if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                                w.root?.let { candidateRoots.add(it) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Safe fallback
                }
                if (candidateRoots.isEmpty()) {
                    rootInActiveWindow?.let { candidateRoots.add(it) }
                }

                for (r in candidateRoots) {
                    val acc = findAcceptButton(r)
                    val cls = findCloseButton(r)
                    if (acc != null || cls != null) {
                        detailRoot = r
                        acceptBtn = acc
                        closeBtn = cls
                        break
                    }
                }
                if (detailRoot != null) break
            }

            if (isTestMode) {
                // TEST MODE: Wait a brief moment for visual confirmation, then strictly click "Đóng"
                // NEVER click "Từ chối", NEVER click "Nhận đơn"
                delay(250)
                val targetClose = closeBtn ?: detailRoot?.let { findCloseButton(it) } ?: rootInActiveWindow?.let { findCloseButton(it) }
                if (targetClose != null) {
                    clickNodeOrCoordinates(targetClose)
                }
                val action = "Đạt tiêu chí (Thử nghiệm - Đã mở chi tiết & bấm Đóng)"
                logHistory(order, evaluation, action)
                com.example.util.NotificationHelper.postOrderAlert(this@OrderAccessibilityService, order, evaluation, action)
            } else {
                // LIVE MODE: Click the green "Nhận đơn" button
                val targetAccept = acceptBtn ?: detailRoot?.let { findAcceptButton(it) } ?: rootInActiveWindow?.let { findAcceptButton(it) }
                if (targetAccept != null) {
                    clickNodeOrCoordinates(targetAccept)
                    val action = "Đã nhận đơn (Bấm đơn -> Bấm nút Nhận đơn)"
                    logHistory(order, evaluation, action)
                    com.example.util.NotificationHelper.postOrderAlert(this@OrderAccessibilityService, order, evaluation, action)

                    // Pause auto-accept for cooldown after successful LIVE grab
                    val cooldownTs = System.currentTimeMillis() + OrderFilteringEngine.LIVE_GRAB_COOLDOWN_MS
                    repo.setCooldown(cooldownTs)
                } else {
                    val action = "Đạt tiêu chí (Đã nhấp mở đơn)"
                    logHistory(order, evaluation, action)
                    com.example.util.NotificationHelper.postOrderAlert(this@OrderAccessibilityService, order, evaluation, action)
                }
            }
        }
    }

    private fun logHistory(order: ParsedOrder, eval: FilterEvaluationResult, action: String) {
        serviceScope.launch {
            val entity = OrderHistoryEntity(
                orderIdentifier = order.identifier,
                orderType = order.orderType?.key ?: order.rawCategoryText,
                shippingFeeVnd = order.shippingFeeVnd,
                codAmountVnd = order.codAmountVnd,
                deliveryDistanceKm = order.deliveryDistanceKm,
                pickupDistanceKm = order.pickupDistanceKm,
                pickupAddress = order.pickupAddress,
                deliveryAddress = order.deliveryAddress,
                actionTaken = action,
                isMatch = eval.isMatch,
                matchReason = eval.reason,
                rawText = order.rawText,
                timestamp = System.currentTimeMillis()
            )
            OrderFilterApp.instance.repository.logOrderHistory(entity)
        }
    }

    private fun isTabOrNavigationText(text: String): Boolean {
        val t = text.trim().lowercase(Locale.ROOT)
        return t.contains("đã nhận") ||
                t.startsWith("chờ") ||
                t == "trả" ||
                t.contains("tìm kiếm") ||
                t == "đơn hàng" ||
                t == "bản đồ" ||
                t == "lịch sử" ||
                t == "thống kê" ||
                t == "tài khoản"
    }

    private fun isDetailDialogOrScreen(
        root: AccessibilityNodeInfo,
        acceptBtn: AccessibilityNodeInfo?,
        closeBtn: AccessibilityNodeInfo?
    ): Boolean {
        if (acceptBtn == null && closeBtn == null) return false
        val fullText = extractSubtreeText(root)

        // If screen contains the list tabs ("Đã nhận" and "Chờ" or "Tìm kiếm"), it's the list screen!
        if (fullText.contains("Đã nhận", ignoreCase = true) &&
            (fullText.contains("Chờ", ignoreCase = true) || fullText.contains("Tìm kiếm", ignoreCase = true))
        ) {
            return false
        }

        // Must have detail indicators
        return acceptBtn != null && (
            closeBtn != null ||
            fullText.contains("Từ chối", ignoreCase = true) ||
            fullText.contains("Phí DV", ignoreCase = true) ||
            fullText.contains("Thu hộ", ignoreCase = true) ||
            fullText.contains("Tổng phí ship", ignoreCase = true)
        )
    }

    private fun findAcceptButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectTextNodes(root, nodes)
        for (n in nodes) {
            val text = (n.text?.toString() ?: n.contentDescription?.toString() ?: "").trim()
            if (text.isEmpty()) continue

            // STRICT EXCLUSION: Never match tab "Đã nhận" or navigation items
            if (isTabOrNavigationText(text)) continue

            val isAccept = text.equals("Nhận đơn", ignoreCase = true) ||
                    text.endsWith("Nhận đơn", ignoreCase = true) ||
                    (text.contains("Nhận đơn", ignoreCase = true) && !text.contains("Đã nhận", ignoreCase = true)) ||
                    text.equals("Chấp nhận đơn", ignoreCase = true) ||
                    text.equals("TIẾP NHẬN", ignoreCase = true) ||
                    text.equals("Nhận cuốc", ignoreCase = true)

            if (isAccept) {
                return n
            }
        }
        return null
    }

    private fun findCloseButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectTextNodes(root, nodes)
        for (n in nodes) {
            val text = (n.text?.toString() ?: n.contentDescription?.toString() ?: "").trim()
            if (text.isEmpty()) continue

            // STRICT: Never click "Từ chối" as close
            if (text.contains("Từ chối", ignoreCase = true)) continue
            if (isTabOrNavigationText(text)) continue

            if (text.equals("Đóng", ignoreCase = true) ||
                text.equals("Hủy", ignoreCase = true) ||
                text.equals("Close", ignoreCase = true) ||
                text.equals("Bỏ qua", ignoreCase = true) ||
                text == "✕" || text.equals("X", ignoreCase = true)
            ) {
                return n
            }
        }
        return null
    }

    private fun tapAtCoordinates(x: Float, y: Float, onComplete: (() -> Unit)? = null) {
        if (x <= 0f || y <= 0f) return
        mainHandler.post {
            try {
                val path = Path().apply {
                    moveTo(x, y)
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                    .build()
                dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.d(tag, "Tap gesture completed at ($x, $y)")
                        onComplete?.invoke()
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.w(tag, "Tap gesture cancelled at ($x, $y)")
                        onComplete?.invoke()
                    }
                }, null)
            } catch (e: Exception) {
                Log.e(tag, "Failed to dispatch tap gesture at ($x, $y)", e)
            }
        }
    }

    private fun clickNodeOrCoordinates(node: AccessibilityNodeInfo) {
        clickNode(node)
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() > 0 && rect.height() > 0) {
            tapAtCoordinates(rect.exactCenterX(), rect.exactCenterY())
        }
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        while (target != null) {
            if (target.isClickable) {
                return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            target = target.parent
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun collectTextNodes(node: AccessibilityNodeInfo?, result: MutableList<AccessibilityNodeInfo>, depth: Int = 0) {
        if (node == null || depth > 25 || result.size > 350) return
        try {
            if (!node.text.isNullOrEmpty() || !node.contentDescription.isNullOrEmpty()) {
                result.add(node)
            }
            for (i in 0 until node.childCount) {
                collectTextNodes(node.getChild(i), result, depth + 1)
            }
        } catch (e: Exception) {
            // Node might be recycled
        }
    }

    private fun findCardContainer(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current: AccessibilityNodeInfo = node
        while (current.parent != null) {
            val p = current.parent ?: break
            val pRect = Rect()
            p.getBoundsInScreen(pRect)
            val cRect = Rect()
            current.getBoundsInScreen(cRect)

            if (p.isScrollable || pRect.height() > 900 || p.parent == null) {
                return current
            }
            if (cRect.height() in 60..700 && pRect.height() > cRect.height() + 200) {
                return current
            }
            current = p
        }
        return current
    }

    private fun extractSubtreeText(container: AccessibilityNodeInfo, depth: Int = 0): String {
        if (depth > 12) return ""
        val sb = StringBuilder()
        try {
            container.text?.let { sb.append(it).append(" ") }
            container.contentDescription?.let { sb.append(it).append(" ") }
            for (i in 0 until container.childCount.coerceAtMost(30)) {
                val child = container.getChild(i) ?: continue
                sb.append(extractSubtreeText(child, depth + 1)).append(" ")
            }
        } catch (e: Exception) {
            // Safe traversal
        }
        return sb.toString().trim()
    }

    private fun findNodeByTexts(root: AccessibilityNodeInfo, targetTexts: List<String>): AccessibilityNodeInfo? {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectTextNodes(root, nodes)
        for (n in nodes) {
            val text = (n.text?.toString() ?: n.contentDescription?.toString() ?: "").trim()
            for (target in targetTexts) {
                if (text.equals(target, ignoreCase = true) || text.contains(target, ignoreCase = true)) {
                    return n
                }
            }
        }
        return null
    }

    fun forceScanCurrentWindow(): Boolean {
        return scanAllCandidateWindows()
    }

    companion object {
        var instance: OrderAccessibilityService? = null
            private set

        fun isServiceRunning(): Boolean = instance != null
    }
}
