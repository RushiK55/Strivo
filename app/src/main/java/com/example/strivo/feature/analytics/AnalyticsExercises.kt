package com.example.strivo.feature.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.components.AccentTitle
import com.example.strivo.core.ui.components.Card
import com.example.strivo.core.ui.components.IconBadge
import com.example.strivo.core.ui.components.color
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.analytics.ExerciseHistory
import com.example.strivo.domain.analytics.RecordEvent
import com.example.strivo.domain.analytics.RecordType
import com.example.strivo.domain.model.ExerciseSet
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/* The Exercises tab of Analytics (every exercise and how it is progressing) and the page for one exercise. */

internal val ShortDay get() = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
internal val LongDay get() = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())

internal fun kg(value: Double): String = (if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(Locale.getDefault(), value)) + " kg"
internal fun signedKg(value: Double): String = when {
    value > 0.04 -> "+" + kg(value)
    value < -0.04 -> "−" + kg(abs(value))
    else -> "±0 kg"
}
internal fun setText(set: ExerciseSet): String =
    (set.type.badge?.let { "$it " } ?: "") +
        if (set.weight > 0) "${kg(set.weight).removeSuffix(" kg")} × ${set.reps}" else "${set.reps} reps"

// --- The tab ---

internal fun LazyListScope.exercisesTab(
    histories: List<ExerciseHistory>,
    records: List<RecordEvent>,
    onOpen: (String) -> Unit,
) {
    if (histories.isEmpty()) {
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconBadge(icon = Icons.Outlined.EmojiEvents, padding = 16.dp, iconSize = 30.dp)
                    Spacer(Modifier.height(14.dp))
                    Text("No exercises yet", style = AppType.Headline, color = AppColors.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Finish a workout and each exercise gets its own progress page here.",
                        style = AppType.Caption,
                        color = AppColors.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        return
    }

    item {
        Column {
            AccentTitle("Recent records")
            Spacer(Modifier.height(12.dp))
            if (records.isEmpty()) {
                Card {
                    Text(
                        "No new records in the last 30 days. Beat your heaviest weight, or do more reps with it, and it shows up here.",
                        style = AppType.Caption,
                        color = AppColors.TextSecondary,
                    )
                }
            } else {
                Card(padding = PaddingValues(vertical = 4.dp)) {
                    records.take(6).forEachIndexed { index, record ->
                        if (index > 0) HorizontalDivider(Modifier.padding(start = 64.dp), thickness = 1.dp, color = AppColors.Divider)
                        RecordRow(record, onClick = { onOpen(record.exercise) })
                    }
                }
            }
        }
    }

    item {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentTitle("All exercises", modifier = Modifier.weight(1f))
                Text("${histories.size}", style = AppType.Caption, color = AppColors.TextSecondary)
            }
            Spacer(Modifier.height(12.dp))
            Card(padding = PaddingValues(vertical = 4.dp)) {
                histories.forEachIndexed { index, history ->
                    if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 1.dp, color = AppColors.Divider)
                    ExerciseProgressRow(history, onClick = { onOpen(history.name) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Change compares the heaviest set of your first and latest workout of each exercise.",
                style = AppType.Caption,
                color = AppColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun RecordRow(record: RecordEvent, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = Icons.Outlined.EmojiEvents, padding = 8.dp, iconSize = 20.dp, shape = RoundedCornerShape(11.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(record.exercise, style = AppType.BodyStrong, color = AppColors.TextPrimary, maxLines = 1)
            Text(
                when (record.type) {
                    RecordType.HeaviestWeight -> "Heaviest weight · ${record.set?.let(::setText) ?: kg(record.valueKg)} (was ${kg(record.previousKg)})"
                    else -> "Best estimated 1RM · ${kg(record.valueKg)} from ${record.set?.let(::setText).orEmpty()}"
                },
                style = AppType.Caption,
                color = AppColors.TextSecondary,
                maxLines = 1,
            )
        }
        Text(record.date.format(ShortDay), style = AppType.Caption, color = AppColors.TextTertiary)
    }
}

@Composable
private fun ExerciseProgressRow(history: ExerciseHistory, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(history.name, style = AppType.BodyStrong, color = AppColors.TextPrimary, maxLines = 1)
            Text(
                "${if (history.sessions.size == 1) "1 workout" else "${history.sessions.size} workouts"} · last ${history.lastDone.format(ShortDay)}",
                style = AppType.Caption,
                color = AppColors.TextSecondary,
                maxLines = 1,
            )
        }
        if (history.sessions.size >= 2) {
            Sparkline(
                values = history.sessions.map { if (history.isWeighted) it.topWeightKg else it.mostReps.toDouble() },
                modifier = Modifier.size(width = 56.dp, height = 26.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        val (text, delta) = when {
            history.sessions.size < 2 -> "New" to 0.0
            history.isWeighted -> signedKg(history.changeKg) to history.changeKg
            else -> "${if (history.changeReps > 0) "+" else ""}${history.changeReps} reps" to history.changeReps.toDouble()
        }
        Text(
            text,
            style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"),
            color = when {
                delta > 0 -> AppColors.Success
                delta < 0 -> AppColors.Danger
                else -> AppColors.TextSecondary
            },
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp),
        )
    }
}

/** A tiny line of the values, oldest to newest, with the last point marked. */
@Composable
private fun Sparkline(values: List<Double>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        fun y(v: Double) = if (max == min) size.height / 2 else (size.height * (1 - (v - min) / range)).toFloat()
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * stepX, y(v)) }
        drawPath(path, AppColors.Accent.copy(alpha = 0.8f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(AppColors.Accent, radius = 3.dp.toPx(), center = Offset((values.size - 1) * stepX, y(values.last())))
    }
}
