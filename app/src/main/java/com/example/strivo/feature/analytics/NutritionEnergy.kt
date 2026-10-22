package com.example.strivo.feature.analytics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.core.ui.components.AccentTitle
import com.example.strivo.core.ui.components.Card
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.analytics.AnalyticsRange
import com.example.strivo.domain.analytics.EnergyDay
import com.example.strivo.domain.analytics.EnergySummary
import kotlin.math.abs

// --- Energy balance: what came in against what went out ---

/**
 * The tab's headline. The average gap between eaten and burned and what it means on the scale, the two sides as bars
 * (burned split into base and activity), and, over more than a day, each day's meal against its burn.
 */
@Composable
internal fun EnergySection(energy: EnergySummary?, range: AnalyticsRange, actualKgPerWeek: Double?) {
    Column {
        AccentTitle("Energy balance")
        Spacer(Modifier.height(12.dp))
        if (energy == null || energy.days.none { it.burnedKcal != null }) {
            MissingData("Add your gender, age, height and weight in Profile, then log what you eat, and eaten against burned shows up here.")
            return@Column
        }
        Card {
            val balance = energy.avgBalance
            val perWeek = energy.kgPerWeek
            val eaten = energy.avgEaten
            val burned = energy.avgBurned
            // One logged day is not an average: it may be today, half eaten. Say so rather than show a huge deficit.
            val tooEarly = range != AnalyticsRange.Today && energy.countedDays < 2
            if (balance == null || perWeek == null || eaten == null || burned == null) {
                Text("Nothing logged ${range.phrase}", style = AppType.Headline, color = AppColors.TextPrimary)
                Text("Log what you eat and the gap to what you burn shows up here.", style = AppType.Caption, color = AppColors.TextSecondary)
            } else {
                if (tooEarly) {
                    Text("Too early to say", style = AppType.Headline, color = AppColors.TextPrimary)
                    Text(
                        "Log food on a few days and your average deficit or surplus shows up here. Logged so far:",
                        style = AppType.Caption,
                        color = AppColors.TextSecondary,
                    )
                } else Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                range == AnalyticsRange.Today -> if (balance <= 0) "Deficit so far" else "Surplus so far"
                                balance <= 0 -> "Average deficit"
                                else -> "Average surplus"
                            },
                            style = AppType.Caption,
                            color = AppColors.TextSecondary,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("%,.0f".format(java.util.Locale.getDefault(), abs(balance)), style = AppType.MetricLarge, color = AppColors.TextPrimary)
                            Text(" kcal a day", style = AppType.Body, color = AppColors.TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                    Text(
                        "${signed(perWeek)} kg a week",
                        style = AppType.BodyStrong,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(AppColors.SurfaceHigh).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(if (tooEarly) 12.dp else 16.dp))
                val scale = maxOf(eaten, burned)
                AverageBar("Eaten", eaten, scale, listOf(eaten to AppColors.Accent), detail = null)
                Spacer(Modifier.height(12.dp))
                AverageBar(
                    "Burned", burned, scale,
                    listOf((energy.avgBaseKcal ?: burned) to AppColors.TextTertiary, (energy.avgActiveKcal ?: 0.0) to AppColors.Success),
                    detail = "Base ${kcal(energy.avgBaseKcal ?: burned)} + active ${kcal(energy.avgActiveKcal ?: 0.0)}",
                )
            }
            if (energy.days.size > 1) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(thickness = 1.dp, color = AppColors.Divider)
                Spacer(Modifier.height(16.dp))
                DayByDay(energy.days)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                buildString {
                    if (energy.countedDays > 0) append("From ${energy.countedDays} of ${energy.days.size} days with food logged in ${range.phrase}. ")
                    if (actualKgPerWeek != null) append("Your weight actually moved ${signed(actualKgPerWeek)} kg a week over the last four weeks.")
                }.trim(),
                style = AppType.Caption,
                color = AppColors.TextTertiary,
            )
        }
    }
}

/** One side of the balance as a bar on a shared scale, so eaten and burned can be compared by eye. */
@Composable
private fun AverageBar(label: String, value: Double, scale: Double, segments: List<Pair<Double, Color>>, detail: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppType.Body, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        Text(kcal(value), style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary)
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(AppColors.SurfaceHigh)) {
        segments.filter { it.first > 0 }.forEach { (kcal, color) ->
            Box(Modifier.weight((kcal / scale).toFloat()).fillMaxHeight().background(color))
        }
        val rest = 1.0 - segments.sumOf { it.first.coerceAtLeast(0.0) } / scale
        if (rest > 0.001) Spacer(Modifier.weight(rest.toFloat()))
    }
    if (detail != null) {
        Spacer(Modifier.height(4.dp))
        Text(detail, style = AppType.Caption, color = AppColors.TextSecondary)
    }
}

// --- Day by day ---

@Composable
private fun DayByDay(days: List<EnergyDay>) {
    var selected by remember(days) {
        mutableIntStateOf(days.indexOfLast { it.isLogged }.takeIf { it >= 0 } ?: days.lastIndex)
    }
    val day = days.getOrNull(selected)
    Text(day?.let { dayLabel(it.date) }.orEmpty(), style = AppType.Caption, color = AppColors.TextSecondary)
    Text(day?.let(::dayReadout).orEmpty(), style = AppType.BodyStrong, color = AppColors.TextPrimary, maxLines = 1)
    Spacer(Modifier.height(14.dp))
    EnergyDaysChart(days, selected) { selected = it }
    Spacer(Modifier.height(8.dp))
    AxisLabels(rangeAxisLabels(days.map { it.date }), selected)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem("Eaten") { Box(Modifier.size(8.dp).clip(CircleShape).background(AppColors.Accent)) }
        LegendItem("Burned") { Box(Modifier.width(12.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(AppColors.TextPrimary)) }
    }
}

@Composable
private fun LegendItem(label: String, mark: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        mark()
        Spacer(Modifier.width(6.dp))
        Text(label, style = AppType.Caption, color = AppColors.TextSecondary)
    }
}

private fun plain(value: Double): String = "%,.0f".format(java.util.Locale.getDefault(), value)

private fun dayReadout(day: EnergyDay): String {
    val burned = day.burnedKcal
    return when {
        !day.isLogged && burned == null -> "Nothing logged"
        !day.isLogged -> "Nothing logged · burned ${plain(burned!!)}"
        burned == null -> "Ate ${plain(day.eaten.toDouble())} kcal"
        else -> {
            val gap = day.eaten - burned
            "Ate ${plain(day.eaten.toDouble())} · burned ${plain(burned)} · ${plain(abs(gap))} ${if (gap <= 0) "under" else "over"}"
        }
    }
}

/** A bar of calories eaten for each day, with a tick across it at what the day burned. Tap a day to read it. */
@Composable
private fun EnergyDaysChart(days: List<EnergyDay>, selected: Int, onSelect: (Int) -> Unit) {
    val max = days.maxOf { maxOf(it.eaten.toDouble(), it.burnedKcal ?: 0.0) }.takeIf { it > 0 } ?: 1.0
    val progress = remember { Animatable(0f) }
    LaunchedEffect(days) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(600))
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = AppColors.TextSecondary, fontSize = 10.sp)
    val summary = days.joinToString(" ") { "${dayLabel(it.date)}: ${dayReadout(it)}." }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = summary }
            .pointerInput(days.size) {
                detectTapGestures { tap -> onSelect((tap.x / (size.width / days.size)).toInt().coerceIn(0, days.lastIndex)) }
            },
    ) {
        val topPad = 18.dp.toPx()
        val baseline = size.height
        val chartHeight = baseline - topPad
        val slot = size.width / days.size
        val barWidth = minOf(slot * 0.6f, 28.dp.toPx())
        val radius = 4.dp.toPx()

        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val y = baseline - chartHeight * fraction
            drawLine(AppColors.Field, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        drawText(measurer, kcal(max), Offset(0f, 0f), style = labelStyle)

        days.forEachIndexed { index, day ->
            val x = index * slot + (slot - barWidth) / 2
            if (day.eaten <= 0) {
                drawRoundRect(AppColors.Field, Offset(x, baseline - 3.dp.toPx()), Size(barWidth, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
            } else {
                val height = (day.eaten / max).toFloat() * chartHeight * progress.value
                val color = if (index == selected) AppColors.Accent else lerp(AppColors.Surface, AppColors.Accent, 0.45f)
                drawRoundRect(color, Offset(x, baseline - height), Size(barWidth, height), CornerRadius(radius))
                val flat = minOf(radius, height)
                drawRect(color, Offset(x, baseline - flat), Size(barWidth, flat))
            }
            day.burnedKcal?.let { burned ->
                val y = baseline - (burned / max).toFloat() * chartHeight * progress.value
                val reach = minOf(slot * 0.9f, barWidth + 8.dp.toPx())
                val tickColor = if (index == selected) AppColors.TextPrimary else AppColors.TextPrimary.copy(alpha = 0.6f)
                drawLine(tickColor, Offset(index * slot + (slot - reach) / 2, y), Offset(index * slot + (slot + reach) / 2, y), strokeWidth = 2.dp.toPx())
            }
        }
    }
}
