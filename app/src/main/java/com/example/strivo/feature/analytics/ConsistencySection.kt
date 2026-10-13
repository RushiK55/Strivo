package com.example.strivo.feature.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.core.ui.components.AccentTitle
import com.example.strivo.core.ui.components.Card
import com.example.strivo.core.ui.components.StatTile
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.analytics.CalendarWeeks
import com.example.strivo.domain.analytics.ConsistencyReport
import com.example.strivo.domain.analytics.TimeOfDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import java.time.format.TextStyle as DateTextStyle

// --- Consistency ---

/** Against the user's own schedule: how many plan days were trained, and a calendar of the last weeks. */
@Composable
internal fun ConsistencySection(consistency: ConsistencyReport) {
    Column {
        AccentTitle("Consistency")
        Spacer(Modifier.height(12.dp))
        Card {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val adherence = consistency.adherence
                StatTile(
                    "Plan days done",
                    adherence?.let { "${it.done} / ${it.scheduled}" } ?: "–",
                    Modifier.weight(1f),
                    background = AppColors.SurfaceHigh,
                )
                StatTile(
                    "Workouts a week",
                    "%.1f".format(Locale.getDefault(), consistency.workoutsPerWeek),
                    Modifier.weight(1f),
                    background = AppColors.SurfaceHigh,
                )
            }
            consistency.adherence?.let { adherence ->
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(AppColors.SurfaceHigh)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((adherence.percent / 100).toFloat().coerceIn(0.02f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(AppColors.Accent)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${"%.0f".format(Locale.getDefault(), adherence.percent)}% of the days your plans are scheduled, since your first workout.",
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                )
            } ?: run {
                Spacer(Modifier.height(8.dp))
                Text("Add plans to days of the week and this shows how many of those days you train.", style = AppType.Caption, color = AppColors.TextSecondary)
            }
            Spacer(Modifier.height(18.dp))
            Text("Last $CalendarWeeks weeks", style = AppType.Caption, color = AppColors.TextSecondary)
            Spacer(Modifier.height(8.dp))
            TrainingCalendar(consistency)
        }
    }
}

/** One column per week, one row per weekday (Monday on top); trained days are filled. */
@Composable
private fun TrainingCalendar(consistency: ConsistencyReport) {
    val today = LocalDate.now()
    val dayLetters = DayOfWeek.entries.map { it.getDisplayName(DateTextStyle.NARROW, Locale.getDefault()) }
    Row {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 6.dp)) {
            dayLetters.forEachIndexed { index, letter ->
                Box(Modifier.height(18.dp), contentAlignment = Alignment.Center) {
                    Text(if (index % 2 == 0) letter else "", style = AppType.Caption.copy(fontSize = 10.sp), color = AppColors.TextTertiary)
                }
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(CalendarWeeks) { week ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(7) { day ->
                        val date = consistency.calendarStart.plusDays(week * 7L + day)
                        val color = when {
                            date > today -> Color.Transparent
                            date in consistency.trainedDays -> AppColors.Accent
                            else -> AppColors.SurfaceHigh
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                                .then(if (date == today) Modifier.border(BorderStroke(1.dp, AppColors.TextPrimary), RoundedCornerShape(4.dp)) else Modifier)
                        )
                    }
                }
            }
        }
    }
}

/** Which weekdays and times of day the user trains in the range. */
@Composable
internal fun TrainingPattern(consistency: ConsistencyReport) {
    Column {
        AccentTitle("When you train")
        Spacer(Modifier.height(12.dp))
        Card {
            val most = consistency.byWeekday.maxOrNull()?.takeIf { it > 0 } ?: 1
            Row(Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.Bottom) {
                consistency.byWeekday.forEachIndexed { index, count ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (count > 0) "$count" else "", style = AppType.Caption, color = AppColors.TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .width(22.dp)
                                .height((60f * count / most).coerceAtLeast(3f).dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (count == most && count > 0) AppColors.Accent else if (count > 0) lerp(AppColors.Surface, AppColors.Accent, 0.45f) else AppColors.SurfaceHigh)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            AxisLabels(DayOfWeek.entries.map { it.getDisplayName(DateTextStyle.SHORT, Locale.getDefault()) }, selected = -1)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeOfDay.entries.forEach { slot ->
                    StatTile(slot.label, consistency.byTimeOfDay[slot]?.toString() ?: "0", Modifier.weight(1f), background = AppColors.SurfaceHigh)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Workouts by the time they started: morning before 12, afternoon until 5, evening after.", style = AppType.Caption, color = AppColors.TextTertiary)
        }
    }
}
