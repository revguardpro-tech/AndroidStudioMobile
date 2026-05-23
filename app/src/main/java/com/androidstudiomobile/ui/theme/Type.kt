package com.androidstudiomobile.ui.theme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
val AppTypography = Typography(
    bodySmall  = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default,   fontSize = 14.sp),
    bodyLarge  = TextStyle(fontFamily = FontFamily.Default,   fontSize = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
)