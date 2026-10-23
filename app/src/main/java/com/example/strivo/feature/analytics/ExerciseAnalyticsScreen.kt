package com.example.strivo.feature.analytics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.core.ui.components.AccentTitle
import com.example.strivo.core.ui.components.Card
import com.example.strivo.core.ui.components.ScreenTopBar
import com.example.strivo.core.ui.components.SegmentedControl
import com.example.strivo.core.ui.components.StatTile
import com.example.strivo.core.ui.components.color
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.core.util.formatKg
import com.example.strivo.domain.analytics.ExerciseHistory
import com.example.strivo.domain.analytics.ExerciseSession
import com.example.strivo.domain.analytics.RecordType
import com.example.strivo.domain.coach.Advice
import com.example.strivo.feature.coach.AdviceCard
import java.util.Locale
import kotlin.math.abs

// --- One exercise ---

private enum class ExerciseMetric(val label: String) {
    TopSet("Top set"),
    OneRepMax("Est. 1RM"),
    Volume("Volume"),
    Reps("Reps"),
}

private fun ExerciseMetric.value(session: ExerciseSession): Double = when (this) {
    ExerciseMetric.TopSet -> session.topWeightKg
    ExerciseMetric.OneRepMax -> session.bestEstimatedMaxKg ?: 0.0
    ExerciseMetric.Volume -> session.volumeKg
    ExerciseMetric.Reps -> session.mostReps.toDouble()
}

private fun ExerciseMetric.format(value: Double): String = when (this) {
    ExerciseMetric.Reps -> "${value.toInt()} reps"
    ExerciseMetric.Volume -> formatKg(value)
    else -> kg(value)
}

/** Everything about one exercise: how it has progressed, its records, best weights by reps and every workout. */
@Composable
internal fun ExerciseAnalyticsScreen(
    history: ExerciseHistory,
    advice: Advice?,
    acceptLabel: (Advice) -> String?,
    onAccept: (Advice) -> Unit,
    onDecline: (Advice) -> Unit,
    onBack: () -> Unit,
) {
    val metrics = if (history.isWeighted) {
        listOf(ExerciseMetric.TopSet, ExerciseMetric.OneRepMax, ExerciseMetric.Volume)
    } else {
        listOf(ExerciseMetric.Reps)
    }
    var metric by remember(history.name) { mutableStateOf(metrics.first()) }

    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        ScreenTopBar(title = history.name, onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { ProgressSummary(history) }
            // What to do next time, right under where it stands now.
            if (advice != null) item { AdviceCard(listOf(advice), acceptLabel = acceptLabel, onAccept = onAccept, onDecline = onDecline) }
            if (history.sessions.size >= 2) {
                item {
                    Column {
                        AccentTitle("Progress")
                        Spacer(Modifier.height(12.dp))
                        Card {
                            if (metrics.size > 1) {
                                SegmentedControl(
                                    options = metrics.map { it.label },
                                    selectedIndex = metrics.indexOf(metric),
                                    onSelect = { metric = metrics[it] },
                                    trackColor = AppColors.SurfaceHigh,
                    selectedColor = AppColors.SetBorder,
                                )
                                Spacer(Modifier.height(14.dp))
                            }
                            SessionLineChart(history.sessions, metric)
                        }
                    }
                }
            }
            item { RecordsSection(history) }
            if (history.isWeighted && history.repMaxes.isNotEmpty()) item { RepMaxSection(history) }
            item {
                Column {
                    AccentTitle("Totals")
                    Spacer(Modifier.height(12.dp))
                    val sets = history.sessions.sumOf { it.workSetCount }
                    StatTable(
                        listOfNotNull(
                            "Workouts" to history.sessions.size.toString(),
                            "Sets" to sets.toString(),
                            "Reps" to history.sessions.sumOf { it.reps }.toString(),
                            if (history.isWeighted) "Volume" to formatKg(history.sessions.sumOf { it.volumeKg }) else null,
                            "Avg rest" to (history.avgRestSeconds?.let { formatSeconds(it) } ?: "–"),
                            "Avg set time" to (history.avgSetSeconds?.let { formatSeconds(it) } ?: "–"),
                        ),
                    )
                }
            }
            item { SessionsSection(history) }
        }
    }
}

/** The headline: the latest top set, how much it has gone up since the first workout, and how fast. */
@Composable
private fun ProgressSummary(history: ExerciseHistory) {
    Card {
        val top = history.latest.topSet
        Text("Latest top set · ${history.lastDone.format(ShortDay)}", style = AppType.Caption, color = AppColors.TextSecondary)
        Text(top?.let(::setText) ?: "–", style = AppType.MetricLarge, color = AppColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        if (history.sessions.size < 2) {
            Text(
                "Do it in another workout and you will see how it is progressing.",
                style = AppType.Caption,
                color = AppColors.TextSecondary,
            )
            return@Card
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (history.isWeighted) {
                StatTile(
                    "Since first",
                    signedKg(history.changeKg) + (history.changePercent?.let { " · ${"%+.0f".format(Locale.getDefault(), it)}%" } ?: ""),
                    Modifier.weight(1f),
                    background = AppColors.SurfaceHigh,
                )
                StatTile(
                    "Per week",
                    history.kgPerWeek?.let { signedKg(it) } ?: "–",
                    Modifier.weight(1f),
                    background = AppColors.SurfaceHigh,
                )
            } else {
                StatTile("Since first", "${if (history.changeReps > 0) "+" else ""}${history.changeReps} reps", Modifier.weight(1f), background = AppColors.SurfaceHigh)
                StatTile("Workouts", history.sessions.size.toString(), Modifier.weight(1f), background = AppColors.SurfaceHigh)
            }
        }
        if (history.isWeighted && history.kgPerWeek == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "The weekly rate shows once you have three workouts at least two weeks apart.",
                style = AppType.Caption,
                color = AppColors.TextTertiary,
            )
        }
    }
}

/** One point per workout, spaced by date; tap a point to read that workout. */
@Composable
private fun SessionLineChart(sessions: List<ExerciseSession>, metric: ExerciseMetric) {
    val values = sessions.map { metric.value(it) }
    var selected by remember(sessions, metric) { mutableIntStateOf(values.lastIndex) }
    val first = sessions.first().date
    val span = maxOf(1L, sessions.last().date.toEpochDay() - first.toEpochDay()).toFloat()
    val low = values.min()
    val high = values.max()
    val pad = maxOf(if (metric == ExerciseMetric.Reps) 1.0 else 2.5, (high - low) * 0.15)
    val scaleMin = (low - pad).coerceAtLeast(0.0)
    val scaleMax = high + pad
    val progress = remember { Animatable(0f) }
    LaunchedEffect(metric, sessions) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(500))
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = AppColors.TextTertiary, fontSize = 10.sp)
    val session = sessions[selected]

    Text(session.date.format(LongDay), style = AppType.Caption, color = AppColors.TextSecondary)
    Text(
        "${metric.format(values[selected])} · ${session.sets.filter { it.reps > 0 }.joinToString("  ") { setText(it) }}",
        style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"),
        color = AppColors.TextPrimary,
        maxLines = 1,
    )
    Spacer(Modifier.height(12.dp))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .semantics {
                contentDescription = "${metric.label} per workout. " +
                    sessions.indices.joinToString(" ") { "${sessions[it].date.format(ShortDay)}: ${metric.format(values[it])}." }
            }
            .pointerInput(sessions, metric) {
                detectTapGestures { tap ->
                    val inset = 10.dp.toPx()
                    val width = size.width - 2 * inset
                    selected = sessions.indices.minByOrNull { i ->
                        abs(inset + (sessions[i].date.toEpochDay() - first.toEpochDay()) / span * width - tap.x)
                    } ?: selected
                }
            },
    ) {
        val inset = 10.dp.toPx()
        val width = size.width - 2 * inset
        val top = 16.dp.toPx()
        val bottom = size.height - 16.dp.toPx()
        fun x(i: Int) = inset + (sessions[i].date.toEpochDay() - first.toEpochDay()) / span * width
        fun y(v: Double) = bottom - ((v - scaleMin) / (scaleMax - scaleMin)).toFloat() * (bottom - top) * progress.value

        listOf(high, low).distinct().forEach { v ->
            drawLine(AppColors.SurfaceHigh, Offset(0f, y(v)), Offset(size.width, y(v)), strokeWidth = 1.dp.toPx())
        }
        drawText(measurer, metric.format(high), Offset(0f, 0f), style = labelStyle)
        drawText(measurer, sessions.first().date.format(ShortDay), Offset(0f, bottom + 2.dp.toPx()), style = labelStyle)
        val endLabel = measurer.measure(sessions.last().date.format(ShortDay), labelStyle)
        drawText(endLabel, topLeft = Offset(size.width - endLabel.size.width, bottom + 2.dp.toPx()))

        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        drawPath(path, AppColors.Accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        values.forEachIndexed { i, v ->
            val isSelected = i == selected
            drawCircle(AppColors.Surface, radius = (if (isSelected) 7 else 5).dp.toPx(), center = Offset(x(i), y(v)))
            drawCircle(if (isSelected) AppColors.TextPrimary else AppColors.Accent, radius = (if (isSelected) 5.0 else 3.5).dp.toPx(), center = Offset(x(i), y(v)))
        }
    }
}

@Composable
private fun RecordsSection(history: ExerciseHistory) {
    Column {
        AccentTitle("Personal records")
        Spacer(Modifier.height(12.dp))
        Card(padding = PaddingValues(vertical = 4.dp)) {
            history.records.forEachIndexed { index, record ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 1.dp, color = AppColors.Divider)
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(record.type.label, style = AppType.Body, color = AppColors.TextPrimary)
                        Text(record.date.format(ShortDay), style = AppType.Caption, color = AppColors.TextTertiary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            when (record.type) {
                                RecordType.MostReps -> "${record.valueKg.toInt()} reps"
                                RecordType.HeaviestWeight -> kg(record.valueKg)
                                else -> formatKg(record.valueKg)
                            },
                            style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"),
                            color = AppColors.TextPrimary,
                        )
                        record.set?.let { set ->
                            if (record.type != RecordType.MostReps || set.weight > 0) {
                                Text(setText(set), style = AppType.Caption, color = AppColors.TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The heaviest weight for each number of reps, like a rep-max table. */
@Composable
private fun RepMaxSection(history: ExerciseHistory) {
    Column {
        AccentTitle("Best weight by reps")
        Spacer(Modifier.height(12.dp))
        Card(padding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)) {
            Row(Modifier.padding(bottom = 6.dp)) {
                Text("Reps", style = AppType.Overline, color = AppColors.TextTertiary, modifier = Modifier.width(56.dp))
                Text("Weight", style = AppType.Overline, color = AppColors.TextTertiary, modifier = Modifier.weight(1f))
                Text("Est. 1RM", style = AppType.Overline, color = AppColors.TextTertiary, modifier = Modifier.width(84.dp), textAlign = TextAlign.End)
            }
            history.repMaxes.forEach { max ->
                HorizontalDivider(thickness = 1.dp, color = AppColors.Divider)
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${max.reps}", style = AppType.BodyStrong, color = AppColors.TextSecondary, modifier = Modifier.width(56.dp))
                    Column(Modifier.weight(1f)) {
                        Text(kg(max.weightKg), style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary)
                        Text(max.date.format(ShortDay), style = AppType.Caption, color = AppColors.TextTertiary)
                    }
                    Text(
                        com.example.strivo.domain.analytics.estimatedOneRepMax(max.weightKg, max.reps)?.let { kg(Math.round(it * 2) / 2.0) } ?: "–",
                        style = AppType.Caption,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.width(84.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

/** Every workout with this exercise, newest first. */
@Composable
private fun SessionsSection(history: ExerciseHistory) {
    Column {
        AccentTitle("Workouts")
        Spacer(Modifier.height(12.dp))
        Card(padding = PaddingValues(vertical = 4.dp)) {
            history.sessions.asReversed().forEachIndexed { index, session ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 1.dp, color = AppColors.Divider)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(session.date.format(LongDay), style = AppType.BodyStrong, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
                        if (session.volumeKg > 0) Text(formatKg(session.volumeKg), style = AppType.Caption, color = AppColors.TextSecondary)
                    }
                    Text(session.planName, style = AppType.Caption, color = AppColors.TextTertiary)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        session.sets.filter { it.reps > 0 }.take(6).forEach { set ->
                            Box(
                                Modifier
                                    .clip(AppShapes.Chip)
                                    .background(if (set == session.topSet) AppColors.AccentSoft else AppColors.SurfaceHigh)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    setText(set),
                                    style = AppType.Caption.copy(fontFeatureSettings = "tnum"),
                                    color = if (set == session.topSet) AppColors.Accent else set.type.color(AppColors.TextPrimary),
                                )
                            }
                        }
                        val more = session.sets.count { it.reps > 0 } - 6
                        if (more > 0) Text("+$more", style = AppType.Caption, color = AppColors.TextSecondary, modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
            }
        }
    }
}
