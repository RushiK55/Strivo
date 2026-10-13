package com.example.strivo.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType

/** One surface, rounded like every other card, with [padding] inside. */
@Composable
fun Card(padding: PaddingValues = PaddingValues(18.dp), content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Card)
            .background(AppColors.Surface)
            .padding(padding),
    ) {
        content()
    }
}

/** A small pill: the selected one is tinted with the accent, the rest are quiet. */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = AppType.Caption.copy(fontWeight = FontWeight.SemiBold),
        color = if (selected) AppColors.Accent else AppColors.TextSecondary,
        maxLines = 1,
        modifier = Modifier
            .clip(AppShapes.Chip)
            .background(if (selected) AppColors.AccentSoft else AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** A label over a value, on a small rounded tile. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, background: Color = AppColors.Surface) {
    Column(
        modifier = modifier
            .clip(AppShapes.Control)
            .background(background)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = AppType.Caption, color = AppColors.TextSecondary, maxLines = 1)
        Text(value, style = AppType.Headline.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary, maxLines = 1)
    }
}
