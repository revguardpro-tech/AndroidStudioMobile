package com.androidstudiomobile.ui.theme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val DarkColors = darkColorScheme(
    primary = Color(0xFF4CAF50), onPrimary = Color.Black,
    primaryContainer = Color(0xFF1B5E20), onPrimaryContainer = Color.White,
    secondary = Color(0xFF03A9F4), onSecondary = Color.Black,
    background = Color(0xFF1E1E1E), onBackground = Color(0xFFD4D4D4),
    surface = Color(0xFF252526), onSurface = Color(0xFFD4D4D4),
    surfaceVariant = Color(0xFF2D2D30), onSurfaceVariant = Color(0xFFBCBCBC),
    error = Color(0xFFCF6679), onError = Color.Black,
    outline = Color(0xFF454545), outlineVariant = Color(0xFF3C3C3C)
)
@Composable fun AndroidStudioMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = AppTypography, content = content)
}