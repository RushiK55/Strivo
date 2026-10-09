package com.example.strivo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.data.analytics.AnalyticsRange
import com.example.strivo.data.analytics.AnalyticsReport
import com.example.strivo.data.analytics.DayStat
import com.example.strivo.data.model.WorkoutSession
import com.example.strivo.ui.components.AccentTitle
import com.example.strivo.ui.components.IconBadge
import com.example.strivo.ui.components.ScreenTopBar
import com.example.strivo.ui.theme.AppColors
import com.example.strivo.viewmodel.AnalyticsViewModel
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

/** What the daily chart measures. */
private enum class ChartMetric(val label: String) {
    Volume("Volume"),
    Time("Time"),
    Sets("Sets");

    fun value(day: DayStat): Double = when (this) {
        Volume -> day.volumeKg
        Time -> day.seconds / 60.0
        Sets -> day.sets.toDouble()
    }

    fun format(value: Double): String = when (this) {
        Volume -> formatKg(value)
        Time -> formatMinutes(value * 60)
        Sets -> "${value.toInt()} sets"
    }
}

private enum class AnalyticsTab(val label: String) {
    Activity("Activity"),
    Progress("Progress"),
    Body("Body"),
    Calories("Calories"),
}

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    onOpenSession: (WorkoutSession) -> Unit,
    onOpenFood: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(AnalyticsTab.Activity) }
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = { ScreenTopBar(title = "Analytics", onBack = null) },
    ) { padding ->
        val report = state.report
        val body = state.body
        val nutrition = state.nutrition
        if (state.isLoading || report == null || body == null || nutrition == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Accent)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnalyticsTab.entries.forEach { option -> Chip(option.label, selected = option == tab) { tab = option } }
                }
            }
            when (tab) {
                AnalyticsTab.Activity -> {
                    item { RangeSelector(state.range, viewModel::setRange) }
                    if (report.isEmpty) {
                        item { EmptyAnalytics(state.range) }
                    } else {
                        item { Overview(report) }
                        item { StatGrid(report) }
                        item { ActivityChart(report) }
                        item { TopExercises(report) }
                        item { PlansTrained(report) }
                        item { Timeline(report, onOpenSession) }
                    }
                }

                AnalyticsTab.Progress -> progressTab(body)
                AnalyticsTab.Body -> bodyTab(body)
                AnalyticsTab.Calories -> {
                    item { RangeSelector(state.range, viewModel::setRange) }
                    caloriesTab(report, body, nutrition, onOpenFood, viewModel::addActivity, viewModel::deleteActivity)
                }
            }
        }
    }
}

// --- Range ---

@Composable
private fun RangeSelector(selected: AnalyticsRange, onSelect: (AnalyticsRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnalyticsRange.entries.forEach { range ->
            Chip(range.label, selected = range == selected) { onSelect(range) }
        }
    }
}

@Composable
internal fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Text(
        text = label,
        color = if (selected) Color.Black else AppColors.TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) AppColors.Accent else AppColors.Surface)
            .border(BorderStroke(1.dp, if (selected) AppColors.Accent else AppColors.Field), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun EmptyAnalytics(range: AnalyticsRange) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(
            icon = Icons.Rounded.BarChart,
            tint = AppColors.Accent.copy(alpha = 0.5f),
            background = AppColors.Surface,
            padding = 30.dp,
            iconSize = 60.dp,
        )
        Spacer(Modifier.height(24.dp))
        Text("No workouts in the last ${range.label}", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Finish a workout and your analytics will show up here.",
            color = AppColors.TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

// --- Overview ---

@Composable
private fun Overview(report: AnalyticsReport) {
    Card {
        Text("TOTAL VOLUME", color = AppColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        Text(formatKg(report.volumeKg), color = AppColors.TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        val change = report.volumeChangePercent
        if (change != null) {
            val up = change >= 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (up) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown,
                    contentDescription = if (up) "Up" else "Down",
                    tint = if (up) AppColors.Success else AppColors.Danger,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${"%.0f".format(Locale.getDefault(), kotlin.math.abs(change))}% ${if (up) "more" else "less"} than the previous ${report.range.label}",
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
        } else {
            Text(
                "Trained ${report.activeDays} of ${report.range.days} days",
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun StatGrid(report: AnalyticsReport) {
    val tiles = listOf(
        "Workouts" to report.workouts.toString(),
        "Active days" to "${report.activeDays} / ${report.range.days}",
        "Current streak" to if (report.streak == 1) "1 day" else "${report.streak} days",
        "Time trained" to formatMinutes(report.totalSeconds.toDouble()),
        "Total sets" to report.sets.toString(),
        "Total reps" to report.reps.toString(),
        "Avg workout" to (report.avgSessionSeconds?.let { formatMinutes(it.toDouble()) } ?: "--"),
        "Avg rest between sets" to (report.avgRestSeconds?.let { formatSeconds(it) } ?: "--"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Surface)
            .padding(16.dp),
    ) {
        Text(value, color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(label, color = AppColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// --- Daily activity chart ---

@Composable
private fun ActivityChart(report: AnalyticsReport) {
    var metric by remember { mutableStateOf(ChartMetric.Volume) }
    var selected by remember(report) {
        mutableIntStateOf(report.days.indexOfLast { !it.isRestDay }.takeIf { it >= 0 } ?: report.days.lastIndex)
    }
    val days = report.days

    Column {
        AccentTitle("DAILY ACTIVITY")
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChartMetric.entries.forEach { option -> Chip(option.label, selected = option == metric) { metric = option } }
        }
        Spacer(Modifier.height(16.dp))
        Card {
            // The readout for the tapped bar; the chart itself stays free of numbers.
            val day = days.getOrNull(selected)
            Text(
                text = day?.let { dayHeadline(it) }.orEmpty(),
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = day?.let { dayReadout(it, metric) }.orEmpty(),
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            BarChart(
                values = days.map { metric.value(it) },
                descriptions = days.map { dayHeadline(it) },
                format = metric::format,
                selected = selected,
                onSelect = { selected = it },
            )
            Spacer(Modifier.height(8.dp))
            AxisLabels(dayAxisLabels(days), selected)
        }
    }
}

/**
 * Vertical bars, one per value, with a quiet grid. Tapping a bar calls [onSelect]; the caller shows that
 * bar's details in text, so the chart itself carries no numbers except the peak.
 */
@Composable
internal fun BarChart(
    values: List<Double>,
    descriptions: List<String>,
    format: (Double) -> String,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(600))
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = AppColors.TextSecondary, fontSize = 10.sp)
    val summary = values.indices.joinToString(" ") { "${descriptions.getOrElse(it) { "" }}: ${format(values[it])}." }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = summary }
            .pointerInput(values.size) {
                detectTapGestures { tap ->
                    val slot = size.width / values.size
                    onSelect((tap.x / slot).toInt().coerceIn(0, values.lastIndex))
                }
            },
    ) {
        val topPad = 18.dp.toPx()
        val baseline = size.height
        val chartHeight = baseline - topPad
        val slot = size.width / values.size
        val barWidth = minOf(slot * 0.6f, 28.dp.toPx())
        val radius = 4.dp.toPx()

        // Quiet grid: baseline, half and peak.
        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val y = baseline - chartHeight * fraction
            drawLine(AppColors.Field, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        drawText(measurer, format(max), Offset(0f, 0f), style = labelStyle)

        values.forEachIndexed { index, value ->
            val x = index * slot + (slot - barWidth) / 2
            if (value <= 0) {
                // A rest day keeps a small stub so the day is still visible.
                drawRoundRect(AppColors.Field, Offset(x, baseline - 3.dp.toPx()), Size(barWidth, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
            } else {
                val height = (value / max).toFloat() * chartHeight * progress.value
                val color = AppColors.Accent.copy(alpha = if (index == selected) 1f else 0.5f)
                val top = baseline - height
                // Rounded top, square bottom so the bar sits flat on the baseline.
                drawRoundRect(color, Offset(x, top), Size(barWidth, height), CornerRadius(radius))
                val flat = minOf(radius, height)
                drawRect(color, Offset(x, baseline - flat), Size(barWidth, flat))
            }
        }
    }
}

/** Labels under the bars: weekday initials for a week, every fifth date number for a month. */
private fun dayAxisLabels(days: List<DayStat>): List<String> = days.mapIndexed { index, day ->
    when {
        days.size <= 7 -> day.date.dayOfWeek.getDisplayName(DateTextStyle.NARROW, Locale.getDefault())
        index % 5 == 0 || index == days.lastIndex -> day.date.dayOfMonth.toString()
        else -> ""
    }
}

@Composable
internal fun AxisLabels(labels: List<String>, selected: Int) {
    Row(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                fontSize = 11.sp,
                fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Medium,
                color = if (index == selected) AppColors.TextPrimary else AppColors.TextSecondary,
            )
        }
    }
}

// --- Top exercises / plans ---

@Composable
private fun TopExercises(report: AnalyticsReport) {
    if (report.topExercises.isEmpty()) return
    Column {
        AccentTitle("TOP EXERCISES")
        Spacer(Modifier.height(16.dp))
        Card {
            val best = report.topExercises.maxOf { if (it.volumeKg > 0) it.volumeKg else it.reps.toDouble() }
            report.topExercises.forEachIndexed { index, exercise ->
                if (index > 0) Spacer(Modifier.height(16.dp))
                val size = if (exercise.volumeKg > 0) exercise.volumeKg else exercise.reps.toDouble()
                RatioRow(
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
private fun PlansTrained(report: AnalyticsReport) {
    if (report.plans.isEmpty()) return
    Column {
        AccentTitle("PLANS TRAINED")
        Spacer(Modifier.height(16.dp))
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
internal fun RatioRow(title: String, value: String, subtitle: String?, fraction: Float) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(value, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        if (subtitle != null) {
            Text(subtitle, color = AppColors.TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AppColors.Field),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AppColors.Accent),
            )
        }
    }
}

// --- What was done, day by day ---

@Composable
private fun Timeline(report: AnalyticsReport, onOpenSession: (WorkoutSession) -> Unit) {
    // A week shows every day, rest days included; a month only the days that were trained.
    val rows = report.days.asReversed().let { days -> if (report.range == AnalyticsRange.Week) days else days.filter { !it.isRestDay } }
    Column {
        AccentTitle("WHAT YOU DID")
        Spacer(Modifier.height(16.dp))
        Card {
            rows.forEachIndexed { index, day ->
                if (index > 0) Spacer(Modifier.height(16.dp))
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
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.getDefault()).uppercase(),
                color = AppColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(day.date.dayOfMonth.toString(), color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(12.dp))
        if (day.isRestDay) {
            Text("Rest day", color = AppColors.TextSecondary, fontSize = 14.sp)
        } else {
            Column(Modifier.weight(1f)) {
                Text(day.planNames.joinToString(" + "), color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "${day.exercises} exercises · ${formatMinutes(day.seconds.toDouble())} · ${formatKg(day.volumeKg)}",
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

// --- Pieces ---

@Composable
internal fun Card(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Surface)
            .border(BorderStroke(1.dp, AppColors.Field), shape)
            .padding(20.dp),
    ) {
        content()
    }
}

// --- Formatting ---

internal fun formatKg(kg: Double): String = "%,.0f kg".format(Locale.getDefault(), kg)

/** 52 min, 1h 05m. */
internal fun formatMinutes(seconds: Double): String {
    val minutes = (seconds / 60).toInt()
    return if (minutes < 60) "$minutes min" else "%dh %02dm".format(minutes / 60, minutes % 60)
}

/** 1:35 for 95 seconds. */
internal fun formatSeconds(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

internal fun dayHeadline(day: DayStat): String {
    val name = day.date.dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.getDefault())
    val label = if (day.date == LocalDate.now()) "Today" else name
    return "$label, ${day.date.dayOfMonth} ${day.date.month.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())}"
}

private fun dayReadout(day: DayStat, metric: ChartMetric): String {
    if (day.isRestDay) return "Rest day"
    return "${day.planNames.joinToString(" + ")} · ${metric.format(metric.value(day))}"
}
