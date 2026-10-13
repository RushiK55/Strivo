package com.example.strivo.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.components.AccentTitle
import com.example.strivo.core.ui.components.Card
import com.example.strivo.core.ui.components.Chip
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.core.util.formatKg
import com.example.strivo.domain.analytics.AnalyticsRange
import com.example.strivo.domain.analytics.AnalyticsReport
import com.example.strivo.domain.analytics.DayStat
import com.example.strivo.domain.model.WorkoutSession
import java.util.Locale
import java.time.format.TextStyle as DateTextStyle

// --- Overview ---

@Composable
internal fun Overview(report: AnalyticsReport) {
    Card {
        Text("Total volume", style = AppType.Caption, color = AppColors.TextSecondary)
        Text(formatKg(report.volumeKg), style = AppType.MetricLarge, color = AppColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        val change = report.volumeChangePercent
        if (change != null) {
            val up = change >= 0
            val color = if (up) AppColors.Success else AppColors.Danger
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(AppShapes.Chip)
                        .background(color.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (up) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown,
                        contentDescription = if (up) "Up" else "Down",
                        tint = color,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${"%.0f".format(Locale.getDefault(), kotlin.math.abs(change))}%", style = AppType.Caption.copy(fontWeight = FontWeight.SemiBold), color = color)
                }
                Spacer(Modifier.width(8.dp))
                Text("vs the previous ${report.range.label}", style = AppType.Caption, color = AppColors.TextSecondary)
            }
        } else {
            Text("Trained ${report.activeDays} of ${report.range.days} days", style = AppType.Caption, color = AppColors.TextSecondary)
        }
    }
}

/** The headline numbers as one card: two columns, thin dividers between the rows. */
@Composable
internal fun StatGrid(report: AnalyticsReport) {
    val tiles = listOf(
        "Workouts" to report.workouts.toString(),
        "Active days" to "${report.activeDays} / ${report.range.days}",
        "Current streak" to if (report.streak == 1) "1 day" else "${report.streak} days",
        "Time trained" to formatMinutes(report.totalSeconds.toDouble()),
        "Total sets" to report.sets.toString(),
        "Total reps" to report.reps.toString(),
        "Avg workout" to (report.avgSessionSeconds?.let { formatMinutes(it.toDouble()) } ?: "–"),
        "Avg rest" to (report.avgRestSeconds?.let { formatSeconds(it) } ?: "–"),
    )
    StatTable(tiles)
}

/** Label/value pairs laid out two per row inside a card. */
@Composable
internal fun StatTable(stats: List<Pair<String, String>>) {
    Card(padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
        stats.chunked(2).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) HorizontalDivider(thickness = 1.dp, color = AppColors.Divider)
            Row(Modifier.padding(vertical = 12.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = AppType.Caption, color = AppColors.TextSecondary, maxLines = 1)
                        Text(value, style = AppType.Headline.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary, maxLines = 1)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// --- Top exercises / plans ---

@Composable
internal fun TopExercises(report: AnalyticsReport, onOpen: (String) -> Unit) {
    if (report.topExercises.isEmpty()) return
    Column {
        AccentTitle("Top exercises")
        Spacer(Modifier.height(12.dp))
        Card {
            val best = report.topExercises.maxOf { if (it.volumeKg > 0) it.volumeKg else it.reps.toDouble() }
            report.topExercises.forEachIndexed { index, exercise ->
                if (index > 0) Spacer(Modifier.height(16.dp))
                val size = if (exercise.volumeKg > 0) exercise.volumeKg else exercise.reps.toDouble()
                RatioRow(
                    onClick = { onOpen(exercise.name) },
                    title = exercise.name,
                    value = if (exercise.volumeKg > 0) formatKg(exercise.volumeKg) else "${exercise.reps} reps",
                    subtitle = buildString {
                        append("${exercise.sets} sets · ${exercise.reps} reps")
                        if (exercise.bestWeightKg > 0) append(" · best ${formatKg(exercise.bestWeightKg)}")
                    },
                    fraction = (size / best).toFloat(),
                )
            }
        }
    }
}

@Composable
internal fun PlansTrained(report: AnalyticsReport) {
    if (report.plans.isEmpty()) return
    Column {
        AccentTitle("Plans trained")
        Spacer(Modifier.height(12.dp))
        Card {
            val most = report.plans.maxOf { it.workouts }
            report.plans.forEachIndexed { index, plan ->
                if (index > 0) Spacer(Modifier.height(16.dp))
                RatioRow(
                    title = plan.name,
                    value = if (plan.workouts == 1) "1 time" else "${plan.workouts} times",
                    subtitle = null,
                    fraction = plan.workouts.toFloat() / most,
                )
            }
        }
    }
}

/** A label, a value and a thin bar showing how it compares with the largest row. */
@Composable
internal fun RatioRow(title: String, value: String, subtitle: String?, fraction: Float, onClick: (() -> Unit)? = null) {
    Column(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = AppType.BodyStrong, color = AppColors.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1)
            Text(value, style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary)
        }
        if (subtitle != null) {
            Text(subtitle, style = AppType.Caption, color = AppColors.TextSecondary, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AppColors.SurfaceHigh),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AppColors.Accent),
            )
        }
    }
}

// --- What was done, day by day ---

@Composable
internal fun Timeline(report: AnalyticsReport, onOpenSession: (WorkoutSession) -> Unit) {
    // A week shows every day, rest days included; a month only the days that were trained.
    val rows = report.days.asReversed().let { days -> if (report.range == AnalyticsRange.Week) days else days.filter { !it.isRestDay } }
    Column {
        AccentTitle("What you did")
        Spacer(Modifier.height(12.dp))
        Card(padding = PaddingValues(vertical = 4.dp)) {
            rows.take(14).forEachIndexed { index, day ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 72.dp), thickness = 1.dp, color = AppColors.Divider)
                TimelineRow(day, onClick = day.sessions.firstOrNull()?.let { session -> { onOpenSession(session) } })
            }
        }
    }
}

@Composable
private fun TimelineRow(day: DayStat, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.getDefault()),
                style = AppType.Caption,
                color = AppColors.TextSecondary,
            )
            Text(
                day.date.dayOfMonth.toString(),
                style = AppType.Headline.copy(fontFeatureSettings = "tnum"),
                color = if (day.isRestDay) AppColors.TextTertiary else AppColors.TextPrimary,
            )
        }
        Spacer(Modifier.width(16.dp))
        if (day.isRestDay) {
            Text("Rest day", style = AppType.Body, color = AppColors.TextTertiary)
        } else {
            Column(Modifier.weight(1f)) {
                Text(day.planNames.joinToString(" + "), style = AppType.BodyStrong, color = AppColors.TextPrimary, maxLines = 1)
                Text(
                    "${day.exercises} exercises · ${formatMinutes(day.seconds.toDouble())} · ${formatKg(day.volumeKg)}",
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
