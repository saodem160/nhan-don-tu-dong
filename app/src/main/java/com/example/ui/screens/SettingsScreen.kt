package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.OrderCategory
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentMintDark
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.TextDarkOnMint
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.WarningYellow
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val draftConfigs by viewModel.draftConfigs.collectAsState()
    val isMasterScanning by viewModel.isMasterScanning.collectAsState()
    val isTestMode by viewModel.isTestMode.collectAsState()
    val isFloatingWidget by viewModel.isFloatingWidget.collectAsState()
    val cooldownUntil by viewModel.cooldownUntil.collectAsState()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsState()
    val hasOverlayPermission by viewModel.hasOverlayPermission.collectAsState()

    val currentDraft = draftConfigs[selectedCategory]

    // Real-time ticker for cooldown display
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(cooldownUntil) {
        while (cooldownUntil > System.currentTimeMillis()) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
        currentTime = System.currentTimeMillis()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Accessibility Service Warning Banner (Immersive Amber/Red glass style)
        if (!isAccessibilityEnabled) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x22EF4444)),
                border = BorderStroke(1.dp, Color(0x66EF4444)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.accessibility_permission_required),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.accessibility_permission_desc),
                            fontSize = 11.sp,
                            color = Color(0xFFFCA5A5)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("grant_accessibility_btn")
                    ) {
                        Text(text = "Bật", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Overlay Permission Warning Banner
        if (!hasOverlayPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x22F59E0B)),
                border = BorderStroke(1.dp, Color(0x66F59E0B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = WarningYellow,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.overlay_permission_required),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFFFBBF24)
                        )
                        Text(
                            text = "Cần quyền này để hiển thị bong bóng điều khiển nổi trên màn hình",
                            fontSize = 11.sp,
                            color = Color(0xFFFDE68A)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WarningYellow),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("grant_overlay_btn")
                    ) {
                        Text(text = "Cấp quyền", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }

        // 3. Cooldown Notice Banner (if in 15-20 min pause after live grab)
        val isCooldownActive = cooldownUntil > currentTime
        if (isCooldownActive) {
            val remainSeconds = (cooldownUntil - currentTime) / 1000
            val min = remainSeconds / 60
            val sec = remainSeconds % 60
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x1F38D39F)),
                border = BorderStroke(1.dp, Color(0x6638D39F)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LockClock,
                        contentDescription = null,
                        tint = AccentMint,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.cooldown_active),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Tự động mở lại sau: %02d:%02d".format(min, sec),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentMint
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.clearCooldown() },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AccentMint),
                        modifier = Modifier.testTag("cancel_cooldown_btn")
                    ) {
                        Text(text = "Tiếp tục ngay", fontSize = 11.sp, color = AccentMint, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Master Controls Card
        OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "BẢNG ĐIỀU KHIỂN CHÍNH",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = AccentMint,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Master Toggle: Quét đơn nền
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.master_scan_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.master_scan_subtitle),
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = isMasterScanning,
                        onCheckedChange = { viewModel.setMasterScanning(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentMint,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            uncheckedThumbColor = TextMuted
                        ),
                        modifier = Modifier.testTag("master_scan_toggle")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline)

                // Test Mode: Styled like the amber card in Immersive UI
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x1AF59E0B))
                        .border(1.dp, Color(0x4DF59E0B), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.test_mode_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = WarningYellow
                            )
                            Text(
                                text = stringResource(R.string.test_mode_subtitle),
                                fontSize = 11.sp,
                                color = Color(0xFFFDE68A)
                            )
                        }
                        Switch(
                            checked = isTestMode,
                            onCheckedChange = { viewModel.setTestMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = WarningYellow,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                uncheckedThumbColor = TextMuted
                            ),
                            modifier = Modifier.testTag("test_mode_toggle")
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline)

                // Floating Widget Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.floating_widget_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.floating_widget_subtitle),
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = isFloatingWidget,
                        onCheckedChange = { viewModel.setFloatingWidget(it, context) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentMint,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            uncheckedThumbColor = TextMuted
                        ),
                        modifier = Modifier.testTag("floating_widget_toggle")
                    )
                }
            }
        }

        // 5. Service-Specific Filters with Category TabRow (FAST | VIP | FOOD | BIKE)
        OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "LỌC ĐỘC LẬP THEO LOẠI ĐƠN",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = AccentMint,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Chọn từng thẻ để cấu hình riêng cho từng dịch vụ chạy đồng thời:",
                    fontSize = 12.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(14.dp))

                // TabRow segmented bar matching Immersive UI: bg-[#1E2025] p-1 rounded-2xl border border-slate-800
                val categories = OrderCategory.entries
                val selectedIndex = categories.indexOf(selectedCategory).coerceAtLeast(0)

                TabRow(
                    selectedTabIndex = selectedIndex,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                            color = AccentMint,
                            height = 3.dp
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .testTag("category_tab_row")
                ) {
                    categories.forEachIndexed { index, category ->
                        val isSelected = index == selectedIndex
                        val isCatEnabled = draftConfigs[category]?.isEnabled == true

                        Tab(
                            selected = isSelected,
                            onClick = { viewModel.selectCategory(category) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val icon = when (category) {
                                        OrderCategory.FAST -> Icons.Default.Bolt
                                        OrderCategory.VIP -> Icons.Default.Star
                                        OrderCategory.FOOD -> Icons.Default.Fastfood
                                        OrderCategory.BIKE -> Icons.Default.DirectionsBike
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = if (isSelected) AccentMint else TextMuted
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = category.displayName,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                        color = if (isSelected) AccentMint else TextMuted,
                                        fontSize = 12.sp
                                    )
                                    if (isCatEnabled) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(StatusGreen)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.testTag("tab_${category.key.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Show settings ONLY for currently selected order type
                if (currentDraft != null) {
                    // Category Enable Switch
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (currentDraft.isEnabled) AccentMint.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.enable_order_type),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (currentDraft.isEnabled) AccentMint else Color.White
                                )
                                Text(
                                    text = "Cho phép quét và nhận đơn ${selectedCategory.displayName}",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                            Switch(
                                checked = currentDraft.isEnabled,
                                onCheckedChange = {
                                    viewModel.updateDraftConfig(selectedCategory, isEnabled = it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentMint,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                                    uncheckedThumbColor = TextMuted
                                ),
                                modifier = Modifier.testTag("enable_category_${selectedCategory.key.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Khoảng cách lấy tối đa (Limit for distance to shop)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.max_pickup_distance),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "%.1f".format(currentDraft.maxPickupDistanceKm),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = AccentMint
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "KM",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                        Text(
                            text = "Trích xuất từ giá trị trong dấu ngoặc đơn (e.g. (0.5km))",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Slider(
                            value = currentDraft.maxPickupDistanceKm,
                            onValueChange = {
                                viewModel.updateDraftConfig(selectedCategory, maxPickup = it)
                            },
                            valueRange = 0.5f..8.0f,
                            steps = 14, // 0.5 to 8.0 in 0.5 step
                            colors = SliderDefaults.colors(
                                thumbColor = AccentMint,
                                activeTrackColor = AccentMint,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.testTag("max_pickup_slider")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Khoảng cách giao tối đa (Limit for distance from shop to customer)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.max_delivery_distance),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "%.1f".format(currentDraft.maxDeliveryDistanceKm),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = AccentMint
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "KM",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                        Text(
                            text = "Trích xuất từ khoảng cách ngoài/trước dấu ngoặc (e.g. 2.1km)",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Slider(
                            value = currentDraft.maxDeliveryDistanceKm,
                            onValueChange = {
                                viewModel.updateDraftConfig(selectedCategory, maxDelivery = it)
                            },
                            valueRange = 1.0f..25.0f,
                            steps = 23, // 1 to 25 in 1km steps
                            colors = SliderDefaults.colors(
                                thumbColor = AccentMint,
                                activeTrackColor = AccentMint,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.testTag("max_delivery_slider")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Phí ship tối thiểu (Minimum shipping fee, default 10,000 VND)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.min_shipping_fee),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "%,d đ".format(currentDraft.minShippingFeeVnd),
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = StatusGreen
                            )
                        }
                        Text(
                            text = "Chỉ nhận đơn có cước phí bằng hoặc cao hơn mức này (tách biệt với COD)",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Slider(
                            value = currentDraft.minShippingFeeVnd.toFloat(),
                            onValueChange = {
                                viewModel.updateDraftConfig(selectedCategory, minFee = it.toInt())
                            },
                            valueRange = 10_000f..100_000f,
                            steps = 17, // 10k to 100k in 5k steps
                            colors = SliderDefaults.colors(
                                thumbColor = StatusGreen,
                                activeTrackColor = StatusGreen,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.testTag("min_fee_slider")
                        )

                        // Quick Select Chips for Common Fees
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val feePresets = listOf(10_000, 15_000, 20_000, 30_000, 50_000)
                            feePresets.forEach { presetFee ->
                                val isSelected = currentDraft.minShippingFeeVnd == presetFee
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.updateDraftConfig(selectedCategory, minFee = presetFee)
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccentMint,
                                        selectedLabelColor = TextDarkOnMint,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = TextMuted
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = if (isSelected) AccentMint else MaterialTheme.colorScheme.outline
                                    ),
                                    label = {
                                        Text(
                                            text = "${presetFee / 1000}K",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                                        )
                                    },
                                    modifier = Modifier.testTag("fee_chip_${presetFee / 1000}k")
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Master "Lưu cấu hình" Button - Immersive UI High-Impact Action Button
        Button(
            onClick = { viewModel.saveAllConfigurations() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("save_settings_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentMint,
                contentColor = TextDarkOnMint
            )
        ) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = TextDarkOnMint
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.save_settings).uppercase(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = TextDarkOnMint
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
