package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.ParsedOrder
import com.example.engine.FilterEvaluationResult
import java.text.NumberFormat
import java.util.Locale

object NotificationHelper {

    private const val TAG = "NotificationHelper"
    const val CHANNEL_ID = "order_filter_alerts_channel"
    private const val CHANNEL_NAME = "Thông báo Lọc Đơn Hàng"
    private const val CHANNEL_DESC = "Thông báo tức thì khi phát hiện và lọc đơn hàng trên màn hình"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun postOrderAlert(
        context: Context,
        order: ParsedOrder,
        evaluation: FilterEvaluationResult,
        actionTaken: String
    ) {
        try {
            createNotificationChannel(context)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val isMatch = evaluation.isMatch
            val category = order.orderType?.key ?: order.rawCategoryText
            val feeFormatted = if (order.shippingFeeVnd > 0) {
                NumberFormat.getNumberInstance(Locale.GERMANY).format(order.shippingFeeVnd) + " đ"
            } else {
                "Không rõ"
            }

            val title = if (isMatch) {
                "⚡ ĐÃ LỌC ĐƠN THỎA MÃN [$category]"
            } else {
                "⛔ ĐÃ BỎ QUA ĐƠN [$category]"
            }

            val shortContent = "Cước: $feeFormatted | Giao: %.1f km (Lấy: %.1f km) - %s".format(
                order.deliveryDistanceKm,
                order.pickupDistanceKm,
                actionTaken
            )

            val bigText = buildString {
                append("• Trạng thái: ").append(actionTaken).append("\n")
                append("• Cước nhận (Ship): ").append(feeFormatted).append("\n")
                if (order.codAmountVnd > 0) {
                    val codFormatted = NumberFormat.getNumberInstance(Locale.GERMANY).format(order.codAmountVnd) + " đ"
                    append("• Tiền ứng/COD: ").append(codFormatted).append("\n")
                }
                append("• Quãng đường giao: ").append("%.1f km".format(order.deliveryDistanceKm)).append("\n")
                append("• Quãng đường lấy: ").append("%.1f km".format(order.pickupDistanceKm)).append("\n")
                append("• Đánh giá: ").append(evaluation.reason)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationId = (System.currentTimeMillis() % 100000).toInt()

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(shortContent)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            notificationManager.notify(notificationId, builder.build())

            // Also show an on-screen Toast so driver sees it immediately even over other apps
            Handler(Looper.getMainLooper()).post {
                val toastMessage = if (isMatch) {
                    "⚡ [LỌC ĐƠN $category] Cước $feeFormatted • Giao %.1fkm ($actionTaken)".format(order.deliveryDistanceKm)
                } else {
                    "⛔ [BỎ QUA $category] Cước $feeFormatted • Giao %.1fkm: ${evaluation.reason}".format(order.deliveryDistanceKm)
                }
                Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post order alert notification", e)
        }
    }
}
