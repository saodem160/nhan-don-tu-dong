package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.OrderFilterApp
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class FloatingWidgetService : Service() {

    private val tag = "FloatingWidget"
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var isExpanded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(tag, "Cannot draw overlays, permission not granted")
            stopSelf()
            return
        }

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            setupFloatingView()
            observeAppState()
        } catch (e: Exception) {
            Log.e(tag, "Error during onCreate in FloatingWidgetService", e)
        }
    }

    private fun startForegroundServiceNotification() {
        try {
            val channelId = "floating_widget_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Bong bóng Lọc Đơn Hàng",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Thông báo dịch vụ hiển thị nút nổi lọc đơn hàng"
                    setShowBadge(false)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }

            val pendingIntent = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification: Notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Bong bóng nổi đang hoạt động")
                .setContentText("Hỗ trợ lọc và nhận đơn tài xế")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    1001,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Throwable) {
            Log.e(tag, "Error starting foreground service notification", e)
        }
    }

    @SuppressLint("ClickableViewAccessibility", "UseSwitchCompatOrMaterialCode")
    private fun setupFloatingView() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        // Root container for floating widget
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Compact bubble view
        val bubbleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 16, 24, 16)
            background = createBubbleBackground(Color.parseColor("#0F1115"), Color.parseColor("#38D39F"))
        }

        val statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 24).apply {
                rightMargin = 16
            }
            background = createCircleBackground(Color.parseColor("#38D39F"))
        }

        val bubbleText = TextView(this).apply {
            text = "LỌC ĐƠN: THỬ"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        bubbleLayout.addView(statusDot)
        bubbleLayout.addView(bubbleText)

        // Expanded Control Panel
        val panelLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(24, 20, 24, 20)
            val panelBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f
                setColor(Color.parseColor("#1E2025"))
                setStroke(2, Color.parseColor("#333742"))
            }
            background = panelBg
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
            layoutParams = lp
        }

        val panelTitle = TextView(this).apply {
            text = "Điều Khiển Nhanh"
            setTextColor(Color.parseColor("#38D39F"))
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 12)
        }
        panelLayout.addView(panelTitle)

        // Master Scan Switch
        val masterSwitch = Switch(this).apply {
            text = "Quét tự động: "
            setTextColor(Color.parseColor("#E2E2E6"))
            isChecked = OrderFilterApp.instance.repository.isMasterScanningEnabled.value
            setOnCheckedChangeListener { _, isChecked ->
                OrderFilterApp.instance.repository.setMasterScanning(isChecked)
            }
        }
        panelLayout.addView(masterSwitch)

        // Test Mode Switch
        val testModeSwitch = Switch(this).apply {
            text = "Chế độ thử nghiệm: "
            setTextColor(Color.parseColor("#E2E2E6"))
            isChecked = OrderFilterApp.instance.repository.isTestModeEnabled.value
            setOnCheckedChangeListener { _, isChecked ->
                OrderFilterApp.instance.repository.setTestMode(isChecked)
            }
        }
        panelLayout.addView(testModeSwitch)

        // Cooldown Status Label
        val cooldownLabel = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#F59E0B"))
            textSize = 12f
            visibility = View.GONE
            setPadding(0, 8, 0, 8)
        }
        panelLayout.addView(cooldownLabel)

        // Open App Button
        val openAppBtn = Button(this).apply {
            text = "Mở Ứng Dụng"
            setTextColor(Color.parseColor("#0F1115"))
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = createBubbleBackground(Color.parseColor("#38D39F"), Color.parseColor("#38D39F"))
            setOnClickListener {
                val intent = Intent(this@FloatingWidgetService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
            }
        }
        panelLayout.addView(openAppBtn)

        rootLayout.addView(bubbleLayout)
        rootLayout.addView(panelLayout)

        // Drag & Click listener on bubble
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        bubbleLayout.setOnTouchListener { _, event ->
            try {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            if (rootLayout.isAttachedToWindow) {
                                windowManager?.updateViewLayout(rootLayout, params)
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // Clicked -> toggle expanded panel
                            isExpanded = !isExpanded
                            panelLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
                        }
                        true
                    }
                    else -> false
                }
            } catch (e: Exception) {
                Log.e(tag, "Error handling touch on floating bubble", e)
                false
            }
        }

        try {
            floatingView = rootLayout
            windowManager?.addView(rootLayout, params)
        } catch (e: Exception) {
            Log.e(tag, "Failed to add floating view to WindowManager", e)
            floatingView = null
            stopSelf()
        }
    }

    private fun observeAppState() {
        val repo = OrderFilterApp.instance.repository

        serviceScope.launch {
            repo.isMasterScanningEnabled.collectLatest {
                updateBubbleUi()
            }
        }

        serviceScope.launch {
            repo.isTestModeEnabled.collectLatest {
                updateBubbleUi()
            }
        }

        serviceScope.launch {
            repo.cooldownUntil.collectLatest {
                updateBubbleUi()
            }
        }
    }

    private fun updateBubbleUi() {
        try {
            val root = floatingView as? LinearLayout ?: return
            if (!root.isAttachedToWindow) return
            val bubbleLayout = root.getChildAt(0) as? LinearLayout ?: return
            val statusDot = bubbleLayout.getChildAt(0) ?: return
            val bubbleText = bubbleLayout.getChildAt(1) as? TextView ?: return

            val repo = OrderFilterApp.instance.repository
            val isScanning = repo.isMasterScanningEnabled.value
            val isTest = repo.isTestModeEnabled.value
            val cooldown = repo.cooldownUntil.value
            val now = System.currentTimeMillis()

            if (!isScanning) {
                statusDot.background = createCircleBackground(Color.parseColor("#94A3B8"))
                bubbleText.text = "LỌC ĐƠN: ĐÃ TẮT"
            } else if (now < cooldown) {
                val remainMin = ((cooldown - now) / 60000) + 1
                statusDot.background = createCircleBackground(Color.parseColor("#F59E0B"))
                bubbleText.text = "TẠM DỪNG (${remainMin}p)"
            } else if (isTest) {
                statusDot.background = createCircleBackground(Color.parseColor("#38BDF8"))
                bubbleText.text = "LỌC ĐƠN: THỬ"
            } else {
                statusDot.background = createCircleBackground(Color.parseColor("#10B981"))
                bubbleText.text = "LỌC ĐƠN: LIVE"
            }
        } catch (e: Exception) {
            Log.e(tag, "Error updating bubble UI", e)
        }
    }

    private fun createBubbleBackground(bgColor: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 32f
            setColor(bgColor)
            setStroke(3, strokeColor)
        }
    }

    private fun createCircleBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            floatingView?.let { view ->
                if (view.isAttachedToWindow) {
                    windowManager?.removeViewImmediate(view)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error removing floating view on destroy", e)
        } finally {
            floatingView = null
        }
    }

    companion object {
        fun start(context: Context) {
            try {
                val intent = Intent(context, FloatingWidgetService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("FloatingWidget", "Error starting FloatingWidgetService", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, FloatingWidgetService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e("FloatingWidget", "Error stopping FloatingWidgetService", e)
            }
        }
    }
}
