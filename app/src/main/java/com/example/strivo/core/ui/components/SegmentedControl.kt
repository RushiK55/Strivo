package com.example.strivo.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType

/** iOS-style segmented control: one track, the chosen option lifted. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = AppColors.Surface,
    selectedColor: Color = AppColors.SurfaceHigh,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.Control)
            .background(trackColor)
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) selectedColor else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = AppType.Caption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) AppColors.TextPrimary else AppColors.TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
