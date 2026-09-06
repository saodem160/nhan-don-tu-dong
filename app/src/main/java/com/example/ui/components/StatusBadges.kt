package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentMint
import com.example.ui.theme.StatusGreen

@Composable
fun CategoryBadge(category: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (category.uppercase()) {
        "FAST" -> Color(0x3338D39F) to AccentMint
        "VIP" -> Color(0x33C084FC) to Color(0xFFC084FC)
        "FOOD" -> Color(0x33F59E0B) to Color(0xFFFBBF24)
        "BIKE" -> Color(0x3338BDF8) to Color(0xFF38BDF8)
        else -> Color(0x3394A3B8) to Color(0xFFE2E2E6)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = category.uppercase(),
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun ActionStatusBadge(actionTaken: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when {
        actionTaken.contains("nhận", ignoreCase = true) -> Color(0x2E4ADE80) to Color(0xFF4ADE80)
        actionTaken.contains("Thử nghiệm", ignoreCase = true) -> Color(0x2E38D39F) to AccentMint
        actionTaken.contains("Không đạt", ignoreCase = true) -> Color(0x2EEF4444) to Color(0xFFF87171)
        else -> Color(0x2294A3B8) to Color(0xFF94A3B8)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = actionTaken,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

