package com.example.strivo.feature.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.AccentTitle
import com.example.strivo.core.ui.components.Card
import com.example.strivo.core.ui.components.Chip
import com.example.strivo.core.ui.components.SoftButton
import com.example.strivo.core.ui.components.StatTile
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.analytics.BodyReport
import com.example.strivo.domain.analytics.TrendPoint
import kotlin.math.abs

// --- Weight ---

@Composable
internal fun WeightSection(body: BodyReport, onUpdate: () -> Unit) {
    Column {
        AccentTitle("Weight")
        Spacer(Modifier.height(12.dp))
        val progress = body.weight
        val trend = body.trend
        if (progress == null || trend.isEmpty()) {
            MissingData("Add your weight and it will be tracked here every time you update it.")
            Spacer(Modifier.height(12.dp))
            AccentButton(text = "Add weight", onClick = onUpdate, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
            return
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Trend weight", style = AppType.Caption, color = AppColors.TextSecondary)
                    Text("${fixed1(trend.last().trendKg)} kg", style = AppType.MetricLarge, color = AppColors.TextPrimary)
                }
                body.weeklyRateKg?.let { rate ->
                    Column(
                        modifier = Modifier
                            .clip(AppShapes.Chip)
                            .background(AppColors.SurfaceHigh)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text("${signed(rate)} kg", style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary)
                        Text("per week", style = AppType.Caption, color = AppColors.TextSecondary)
                    }
                }
            }
            Text(
                "Last weigh-in ${fixed1(progress.latest.kg)} kg · ${shortDate(progress.latest.date)}",
                style = AppType.Caption,
                color = AppColors.TextSecondary,
            )
            if (progress.hasTrend) {
                Spacer(Modifier.height(16.dp))
                WeightLineChart(trend)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Change", "${signed(trend.last().trendKg - trend.first().trendKg)} kg", Modifier.weight(1f), background = AppColors.SurfaceHigh)
                    StatTile("Highest", "${fixed1(progress.highKg)} kg", Modifier.weight(1f), background = AppColors.SurfaceHigh)
                    StatTile("Lowest", "${fixed1(progress.lowKg)} kg", Modifier.weight(1f), background = AppColors.SurfaceHigh)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "The trend line smooths out day-to-day swings from water and food, so it shows where your weight is really heading. " +
                        "The rate needs weigh-ins at least a week apart.",
                    style = AppType.Caption,
                    color = AppColors.TextTertiary,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "This is your first weigh-in. Update your weight each week and the trend will appear here.",
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(14.dp))
            SoftButton(text = "Update weight", onClick = onUpdate, modifier = Modifier.fillMaxWidth(), height = 46.dp, icon = Icons.Rounded.Edit)
        }
    }
}

internal fun signed(value: Double): String = (if (value > 0.05) "+" else if (value < -0.05) "−" else "") + fixed1(abs(value))

/** Weigh-ins as dots with the trend weight as a line through them; tap to read a day. */
@Composable
private fun WeightLineChart(points: List<TrendPoint>) {
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val first = points.first().date
    val spanDays = maxOf(1L, points.last().date.toEpochDay() - first.toEpochDay()).toFloat()
    val lowest = points.minOf { minOf(it.kg, it.trendKg) }
    val highest = points.maxOf { maxOf(it.kg, it.trendKg) }
    // Pad the scale so the line does not touch the top and bottom, and a flat history still draws mid-height.
    val pad = maxOf(0.5, (highest - lowest) * 0.15)
    val scaleMin = lowest - pad
    val scaleMax = highest + pad
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = AppColors.TextTertiary, fontSize = 10.sp)
    val summary = points.joinToString(" ") { "${shortDate(it.date)}: ${fixed1(it.kg)} kg, trend ${fixed1(it.trendKg)} kg." }
    val point = points[selected]

    Text(shortDate(point.date), style = AppType.Caption, color = AppColors.TextSecondary)
    Text(
        "${fixed1(point.kg)} kg · trend ${fixed1(point.trendKg)} kg",
        style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"),
        color = AppColors.TextPrimary,
    )
    Spacer(Modifier.height(8.dp))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .semantics { contentDescription = "Weight over time. $summary" }
            .pointerInput(points) {
                detectTapGestures { tap ->
                    val inset = 8.dp.toPx()
                    val width = size.width - 2 * inset
                    selected = points.indices.minByOrNull { index ->
                        abs(inset + (points[index].date.toEpochDay() - first.toEpochDay()) / spanDays * width - tap.x)
                    } ?: selected
                }
            },
    ) {
        val inset = 8.dp.toPx()
        val width = size.width - 2 * inset
        val labelRoom = 14.dp.toPx()
        val chartHeight = size.height - labelRoom
        fun x(p: TrendPoint) = inset + (p.date.toEpochDay() - first.toEpochDay()) / spanDays * width
        fun y(kg: Double) = labelRoom + chartHeight * (1 - ((kg - scaleMin) / (scaleMax - scaleMin)).toFloat())

        listOf(highest, lowest).distinct().forEach { kg ->
            drawLine(AppColors.SurfaceHigh, Offset(0f, y(kg)), Offset(size.width, y(kg)), strokeWidth = 1.dp.toPx())
        }
        drawText(measurer, "${fixed1(highest)} kg", Offset(0f, 0f), style = labelStyle)

        // The trend as a line, the weigh-ins as quiet dots around it.
        for (i in 0 until points.lastIndex) {
            drawLine(
                AppColors.Accent,
                Offset(x(points[i]), y(points[i].trendKg)),
                Offset(x(points[i + 1]), y(points[i + 1].trendKg)),
                strokeWidth = 2.5.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
        points.forEachIndexed { index, p ->
            val isSelected = index == selected
            drawCircle(
                if (isSelected) AppColors.TextPrimary else AppColors.TextTertiary,
                radius = (if (isSelected) 4.5 else 3.0).dp.toPx(),
                center = Offset(x(p), y(p.kg)),
            )
        }
        val sel = points[selected]
        drawCircle(AppColors.Surface, radius = 7.dp.toPx(), center = Offset(x(sel), y(sel.trendKg)))
        drawCircle(AppColors.Accent, radius = 5.dp.toPx(), center = Offset(x(sel), y(sel.trendKg)))
    }
}
