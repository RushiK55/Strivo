package com.example.strivo.feature.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.core.ui.components.AccentTitle
import com.example.strivo.core.ui.components.Card
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.core.util.formatKg
import com.example.strivo.domain.analytics.WeekVolume
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/* Body, progress and calories pages of the analytics screen. Each states what it needs when data is missing. */

internal fun fixed1(value: Double) = "%.1f".format(Locale.getDefault(), value)
internal fun kcal(value: Double) = "%,.0f kcal".format(Locale.getDefault(), value)
internal fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@Composable
internal fun MissingData(message: String) {
    Card {
        Text(message, color = AppColors.TextSecondary, fontSize = 14.sp)
    }
}

// --- Weekly volume (shown on the overview) ---

@Composable
internal fun WeeklyVolumeSection(weeks: List<WeekVolume>) {
    Column {
        AccentTitle("Weekly volume")
        Spacer(Modifier.height(12.dp))
        if (weeks.all { it.volumeKg == 0.0 && it.workouts == 0 }) {
            MissingData("Finish workouts and your volume for each week shows up here.")
            return
        }
        var selected by remember(weeks) { mutableIntStateOf(weeks.indexOfLast { it.workouts > 0 }.takeIf { it >= 0 } ?: weeks.lastIndex) }
        Card {
            val week = weeks[selected]
            Text("Week of ${shortDate(week.weekStart)}", style = AppType.Caption, color = AppColors.TextSecondary)
            Text(
                "${formatKg(week.volumeKg)} · ${if (week.workouts == 1) "1 workout" else "${week.workouts} workouts"}",
                style = AppType.BodyStrong,
                color = AppColors.TextPrimary,
            )
            Spacer(Modifier.height(16.dp))
            BarChart(
                values = weeks.map { it.volumeKg },
                descriptions = weeks.map { "Week of ${shortDate(it.weekStart)}" },
                format = ::formatKg,
                selected = selected,
                onSelect = { selected = it },
            )
            Spacer(Modifier.height(8.dp))
            AxisLabels(weeks.mapIndexed { i, w -> if (i % 2 == weeks.lastIndex % 2) w.weekStart.dayOfMonth.toString() else "" }, selected)
            Spacer(Modifier.height(4.dp))
            Text("The last ${weeks.size} weeks; each number is the Monday the week starts on.", style = AppType.Caption, color = AppColors.TextTertiary)
        }
    }
}
