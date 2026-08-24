package com.tarkeshstack.speakeasy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SpeakEasyTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp),
)
