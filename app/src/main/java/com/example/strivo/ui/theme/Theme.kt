package com.example.strivo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AppColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF1A1A1A)
    val Accent = Color(0xFFFFB800)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xFF8E8E8E)
    val CardGrey = Color(0xFF262626)

    /** Input fields, chips and dividers that sit on top of [Surface]. */
    val Field = Color(0xFF2C2C2E)
    val Placeholder = Color(0xFF5E5E5E)
    val SetBorder = Color(0xFF3A3A3C)

    val Danger = Color(0xFFFF5252) // redAccent
    val Success = Color(0xFF69F0AE) // greenAccent
    val Warning = Color(0xFFFF9800) // orange
    val WarningAccent = Color(0xFFFFAB40) // orangeAccent
}

private val StrivoColorScheme = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = Color.Black,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.Field,
    onSurfaceVariant = AppColors.TextSecondary,
    surfaceContainer = AppColors.Surface,
    surfaceContainerHigh = AppColors.Surface,
    surfaceContainerHighest = AppColors.Field,
    error = AppColors.Danger,
)

@Composable
fun StrivoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StrivoColorScheme, content = content)
}
