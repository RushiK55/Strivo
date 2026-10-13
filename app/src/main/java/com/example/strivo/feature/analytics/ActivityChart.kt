package com.example.strivo.feature.analytics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.BarChart
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
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
import com.example.strivo.core.ui.components.AccentTitle
import com.example.strivo.core.ui.components.Card
import com.example.strivo.core.ui.components.Chip
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.analytics.AnalyticsReport
import java.time.LocalDate
import java.util.Locale
import java.time.format.TextStyle as DateTextStyle

// --- Daily activity chart ---

@Composable
internal fun ActivityChart(report: AnalyticsReport) {
    var metric by remember { mutableStateOf(ChartMetric.Volume) }
    var selected by remember(report) {
        mutableIntStateOf(report.days.indexOfLast { !it.isRestDay }.takeIf { it >= 0 } ?: report.days.lastIndex)
    }
    val days = report.days

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentTitle("Daily activity", modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChartMetric.entries.forEach { option -> Chip(option.label, selected = option == metric) { metric = option } }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            // The readout for the tapped bar; the chart itself stays free of numbers.
            val day = days.getOrNull(selected)
            Text(text = day?.let { dayHeadline(it) }.orEmpty(), style = AppType.Caption, color = AppColors.TextSecondary)
            Text(text = day?.let { dayReadout(it, metric) }.orEmpty(), style = AppType.BodyStrong, color = AppColors.TextPrimary, maxLines = 1)
            Spacer(Modifier.height(16.dp))
            BarChart(
                values = days.map { metric.value(it) },
                descriptions = days.map { dayHeadline(it) },
                format = metric::format,
                selected = selected,
                onSelect = { selected = it },
            )
            Spacer(Modifier.height(8.dp))
            AxisLabels(rangeAxisLabels(days.map { it.date }), selected)
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
                val color = if (index == selected) AppColors.Accent else lerp(AppColors.Surface, AppColors.Accent, 0.45f)
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
internal fun rangeAxisLabels(dates: List<LocalDate>): List<String> {
    val every = if (dates.size <= 31) 5 else 15
    return dates.mapIndexed { index, date ->
        when {
            dates.size <= 7 -> date.dayOfWeek.getDisplayName(DateTextStyle.NARROW, Locale.getDefault())
            (dates.lastIndex - index) % every == 0 -> date.dayOfMonth.toString()
            else -> ""
        }
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
