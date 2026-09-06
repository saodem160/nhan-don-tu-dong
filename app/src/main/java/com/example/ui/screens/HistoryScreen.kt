package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.OrderHistoryEntity
import com.example.ui.components.ActionStatusBadge
import com.example.ui.components.CategoryBadge
import com.example.ui.theme.AccentMint
import com.example.ui.theme.DriverOrange
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.TextMuted
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val historyList by viewModel.orderHistory.collectAsState()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsState()
    val isMasterScanning by viewModel.isMasterScanning.collectAsState()

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var selectedItemForDetail by remember { mutableStateOf<OrderHistoryEntity?>(null) }
    var selectedFilterChip by remember { mutableStateOf("ALL") }

    val filteredList = remember(historyList, selectedFilterChip) {
        when (selectedFilterChip) {
            "MATCHED" -> historyList.filter { it.isMatch }
            "REJECTED" -> historyList.filter { !it.isMatch }
            "FAST" -> historyList.filter { it.orderType == "FAST" }
            "VIP" -> historyList.filter { it.orderType == "VIP" }
            "FOOD" -> historyList.filter { it.orderType == "FOOD" }
            "BIKE" -> historyList.filter { it.orderType == "BIKE" }
            else -> historyList
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Status & Service Alert Banner
        if (!isAccessibilityEnabled) {
            OutlinedCard(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF2D1B1B)),
                border = androidx.compose.foundation.BorderStroke(1.dp, DriverOrange),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = DriverOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dịch vụ tiếp cận chưa bật",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Bật để ứng dụng quét màn hình tự động và đọc đơn.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DriverOrange),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Bật ngay", color = Color(0xFF0F1115), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        } else {
            // Service active banner with master toggle switch
            OutlinedCard(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isMasterScanning) AccentMint.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isMasterScanning) StatusGreen else Color(0xFF6B7280))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isMasterScanning) "Tự động quét màn hình: ĐANG BẬT" else "Tự động quét: ĐANG TẮT",
                                fontWeight = FontWeight.Bold,
                                color = if (isMasterScanning) AccentMint else TextMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (isMasterScanning) "Đang tự động đọc đơn khi mở app tài xế" else "Bật công tắc để hệ thống quét tự động",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }
                    Switch(
                        checked = isMasterScanning,
                        onCheckedChange = { viewModel.setMasterScanning(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF0F1115),
                            checkedTrackColor = AccentMint,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }

        // Header Bar with count and action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LỊCH SỬ QUÉT & LỌC ĐƠN",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = AccentMint,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Tổng: ${historyList.size} đơn • Đạt tiêu chí: ${historyList.count { it.isMatch }}",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Manual scan trigger button
                Button(
                    onClick = { viewModel.triggerScreenScan() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("btn_force_scan")
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = Color(0xFF0F1115),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Quét Ngay",
                        color = Color(0xFF0F1115),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (historyList.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { showClearConfirmDialog = true },
                        modifier = Modifier.testTag("clear_history_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = stringResource(R.string.clear_history),
                            tint = ErrorRed
                        )
                    }
                }
            }
        }

        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val chips = listOf(
                "ALL" to "Tất cả (${historyList.size})",
                "MATCHED" to "Đã nhận/Đạt (${historyList.count { it.isMatch }})",
                "REJECTED" to "Bỏ qua (${historyList.count { !it.isMatch }})",
                "FAST" to "FAST",
                "VIP" to "VIP",
                "FOOD" to "FOOD",
                "BIKE" to "BIKE"
            )
            for ((key, label) in chips) {
                FilterChip(
                    selected = selectedFilterChip == key,
                    onClick = { selectedFilterChip = key },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentMint.copy(alpha = 0.2f),
                        selectedLabelColor = AccentMint,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = TextMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (selectedFilterChip == key) AccentMint else MaterialTheme.colorScheme.outline,
                        enabled = true,
                        selected = selectedFilterChip == key
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        if (filteredList.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(34.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = AccentMint
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (historyList.isEmpty()) "Chưa có đơn hàng nào được quét" else "Không có đơn phù hợp bộ lọc",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (historyList.isEmpty()) {
                            "Khi bạn mở ứng dụng tài xế (màn hình tìm kiếm đơn hàng), hệ thống sẽ tự động quét các thẻ đơn, bóc tách khoảng cách lấy/giao, tiền cước, loại đơn (VIP, FAST...) và lưu tại đây kèm thông báo."
                        } else {
                            "Hãy chọn bộ lọc 'Tất cả' để xem toàn bộ lịch sử quét."
                        },
                        fontSize = 13.sp,
                        color = TextMuted,
                        lineHeight = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.triggerScreenScan() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentMint),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = Color(0xFF0F1115),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Quét màn hình ngay", color = Color(0xFF0F1115), fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("history_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredList, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        onClick = { selectedItemForDetail = item }
                    )
                }
            }
        }
    }

    // Detail Dialog
    selectedItemForDetail?.let { item ->
        OrderDetailDialog(
            item = item,
            onDismiss = { selectedItemForDetail = null }
        )
    }

    // Clear History Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(text = stringResource(R.string.clear_history), fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Text(text = stringResource(R.string.confirm_clear_history), color = TextMuted)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearConfirmDialog = false
                    },
                    modifier = Modifier.testTag("confirm_clear_dialog_btn")
                ) {
                    Text(text = stringResource(R.string.dialog_confirm), color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmDialog = false }
                ) {
                    Text(text = stringResource(R.string.dialog_cancel), color = TextMuted)
                }
            }
        )
    }
}

@Composable
fun HistoryItemCard(
    item: OrderHistoryEntity,
    onClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss - dd/MM", Locale.getDefault()) }
    val formattedTime = remember(item.timestamp) { timeFormat.format(Date(item.timestamp)) }

    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.isMatch) AccentMint.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("history_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Category Badge + Action Status Badge + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(category = item.orderType)
                    Spacer(modifier = Modifier.width(6.dp))
                    ActionStatusBadge(actionTaken = item.actionTaken)
                }
                Text(
                    text = formattedTime,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fees Row: Shipping Fee (SEPARATED STRICTLY FROM COD)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shipping Fee
                Column {
                    Text(
                        text = "TIỀN CƯỚC (SHIP)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextMuted
                    )
                    Text(
                        text = if (item.shippingFeeVnd > 0) "%,d đ".format(item.shippingFeeVnd) else "Không rõ",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StatusGreen
                    )
                }

                // COD Amount (strictly separated)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "TIỀN ỨNG / COD",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextMuted
                    )
                    Text(
                        text = if (item.codAmountVnd > 0) "%,d đ".format(item.codAmountVnd) else "0 đ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.codAmountVnd > 0) Color(0xFFF87171) else TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Distance Information: Delivery and Pickup
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Pickup distance
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NearMe,
                        contentDescription = null,
                        tint = AccentMint,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Lấy (Trong ngoặc):",
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                        Text(
                            text = formatDistance(item.pickupDistanceKm),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Delivery distance
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = StatusGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Giao (Ngoài ngoặc):",
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                        Text(
                            text = formatDistance(item.deliveryDistanceKm),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Delivery Address line
            if (item.deliveryAddress.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = DriverOrange,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.deliveryAddress,
                        fontSize = 11.sp,
                        color = Color(0xFFE2E8F0),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Reason / Match note
            if (item.matchReason.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = if (item.isMatch) StatusGreen else Color(0xFFF87171),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.matchReason,
                        fontSize = 11.sp,
                        color = if (item.isMatch) StatusGreen else Color(0xFFF87171),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Chạm để xem chi tiết & nội dung quét thực tế",
                fontSize = 10.sp,
                color = AccentMint.copy(alpha = 0.7f)
            )
        }
    }
}

private fun formatDistance(km: Float): String {
    return if (km < 0.1f && km > 0f) {
        "${(km * 1000).toInt()}m"
    } else {
        "%.1f km".format(km)
    }
}

@Composable
fun OrderDetailDialog(
    item: OrderHistoryEntity,
    onDismiss: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.getDefault()) }
    val formattedTime = remember(item.timestamp) { timeFormat.format(Date(item.timestamp)) }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Chi Tiết Đơn Đã Quét", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                CategoryBadge(category = item.orderType)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status line
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hành động: ", fontSize = 12.sp, color = TextMuted)
                    ActionStatusBadge(actionTaken = item.actionTaken)
                }

                Text("Thời gian: $formattedTime", fontSize = 12.sp, color = TextMuted)

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Financials
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cước nhận (Ship):", fontSize = 12.sp, color = TextMuted)
                    Text(
                        if (item.shippingFeeVnd > 0) "%,d đ".format(item.shippingFeeVnd) else "Không rõ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusGreen
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tiền ứng / COD:", fontSize = 12.sp, color = TextMuted)
                    Text(
                        if (item.codAmountVnd > 0) "%,d đ".format(item.codAmountVnd) else "0 đ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.codAmountVnd > 0) Color(0xFFF87171) else Color.White
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Quãng đường giao (Ngoài ngoặc):", fontSize = 12.sp, color = TextMuted)
                    Text(formatDistance(item.deliveryDistanceKm), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Quãng đường lấy (Trong ngoặc):", fontSize = 12.sp, color = TextMuted)
                    Text(formatDistance(item.pickupDistanceKm), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                if (item.deliveryAddress.isNotEmpty()) {
                    Text("Địa chỉ: ${item.deliveryAddress}", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Text("Lý do đánh giá bộ lọc:", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Text(
                    item.matchReason.ifEmpty { "Không có ghi chú" },
                    fontSize = 12.sp,
                    color = if (item.isMatch) StatusGreen else Color(0xFFF87171),
                    fontWeight = FontWeight.Medium
                )

                if (item.rawText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Nội dung màn hình đã quét:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = item.rawText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = AccentMint, fontWeight = FontWeight.Bold)
            }
        }
    )
}
