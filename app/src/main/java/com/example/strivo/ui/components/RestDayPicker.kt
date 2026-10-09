package com.example.strivo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
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
import com.example.strivo.ui.theme.AppColors
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Seven day chips to pick the days off the gym: tap to switch a day on or off. Any number of days, including none. */
@Composable
fun RestDayPicker(selected: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp, androidx.compose.ui.Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DayOfWeek.entries.forEach { day ->
            val isSelected = day in selected
            Text(
                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                color = if (isSelected) Color.Black else AppColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (isSelected) AppColors.Accent else AppColors.Surface)
                    .clickable { onChange(if (isSelected) selected - day else selected + day) }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            )
        }
    }
}

/** "Sun", "Wed, Sun" or "None" - the days as a short line of text. */
fun restDaysSummary(days: Set<DayOfWeek>): String =
    if (days.isEmpty()) "None" else days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
