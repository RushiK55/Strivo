package com.example.strivo.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colour tokens. A true-black base with two lifted surfaces, quiet greys for secondary text, and one accent
 * used sparingly: primary actions, the current selection and the numbers that matter.
 */
object AppColors {
    val Background = Color(0xFF000000)

    /** Cards and sheets. */
    val Surface = Color(0xFF151517)

    /** Things sitting on a card: rows, chips, stepper buttons. */
    val SurfaceHigh = Color(0xFF1F1F23)

    val Accent = Color(0xFFFFB800)

    /** A tint of the accent for selected backgrounds and icon tiles. */
    val AccentSoft = Color(0x24FFB800)

    val TextPrimary = Color(0xFFF4F4F5)
    val TextSecondary = Color(0xFF9A9AA1)
    val TextTertiary = Color(0xFF65656C)
    val CardGrey = Color(0xFF262626)

    /** Input fields, chips and dividers that sit on top of [Surface]. */
    val Field = Color(0xFF232327)
    val Divider = Color(0xFF242428)
    val Placeholder = Color(0xFF5E5E66)
    val SetBorder = Color(0xFF34343A)

    val Danger = Color(0xFFFF5A5F)
    /** The rest between sets and exercises on the workout screen: red, so it reads at a glance as a different phase from the set (orange). */
    val Rest = Color(0xFFFF5A5F)
    val Success = Color(0xFF4ADE80)
    val Warning = Color(0xFFFF9F0A)
    val WarningAccent = Color(0xFFFFAB40)

    // Set types, kept apart from the amber used for the set in progress.
    val SetWarmUp = Color(0xFF5AC8FA)
    val SetDrop = Color(0xFFB388FF)
}

/** Type scale: one family, a few clear steps, sentence case. Numbers use tabular figures so timers do not jitter. */
object AppType {
    val LargeTitle = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
    val Title = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
    val Headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
    val BodyStrong = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
    val Caption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val Overline = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
    val Metric = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
    val MetricLarge = TextStyle(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", letterSpacing = (-1).sp)
}

/** Corner radii: cards are softly rounded, controls a little less. */
object AppShapes {
    val Card = RoundedCornerShape(18.dp)
    val Control = RoundedCornerShape(14.dp)
    val Chip = RoundedCornerShape(10.dp)
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
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
    surfaceContainerLow = AppColors.Surface,
    surfaceContainerHigh = AppColors.Surface,
    surfaceContainerHighest = AppColors.SurfaceHigh,
    outline = AppColors.SetBorder,
    outlineVariant = AppColors.Divider,
    error = AppColors.Danger,
)

private val StrivoTypography = Typography(
    headlineLarge = AppType.LargeTitle,
    titleLarge = AppType.Title,
    titleMedium = AppType.Headline,
    bodyLarge = AppType.Body,
    bodyMedium = AppType.Body,
    bodySmall = AppType.Caption,
    labelLarge = AppType.BodyStrong,
    labelMedium = AppType.Overline,
)

@Composable
fun StrivoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StrivoColorScheme, typography = StrivoTypography, content = content)
}
