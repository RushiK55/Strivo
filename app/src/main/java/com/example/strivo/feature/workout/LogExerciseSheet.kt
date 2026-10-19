package com.example.strivo.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.SoftButton
import com.example.strivo.core.ui.components.color
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.ExerciseSet
import com.example.strivo.domain.model.SetType
import com.example.strivo.domain.model.setLabels

/** One set being logged after the fact. [seconds] is how long it took; 0 means the time was not recorded. */
private data class LogSet(val weight: Double, val reps: Int, val seconds: Int, val type: SetType)

private enum class LogField { Weight, Reps, Time }

/** "01:05" for 65 seconds: the same text a timed set is saved with. */
internal fun setTimeText(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

/** The seconds in a saved "MM:SS" set time; 0 when it is empty or cannot be read. */
internal fun setTimeSeconds(text: String): Int {
    val parts = text.split(":")
    if (parts.size != 2) return 0
    val minutes = parts[0].trim().toIntOrNull() ?: return 0
    val seconds = parts[1].trim().toIntOrNull() ?: return 0
    return minutes * 60 + seconds
}

/**
 * For an exercise that was done without the timer: the sets, reps, weight and (if known) time of each set are filled in
 * afterwards. They start from the plan; saving marks them all done, so the exercise is done and goes into history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogExerciseSheet(exercise: Exercise, onDismiss: () -> Unit, onSave: (List<ExerciseSet>) -> Unit) {
    val alreadyDone = remember(exercise.id) { exercise.setsList.filter { it.isCompleted } }
    val rows = remember(exercise.id) {
        val toDo = exercise.setsList.filter { !it.isCompleted }.map { LogSet(it.weight, it.reps.takeIf { r -> r > 0 } ?: 10, 0, it.type) }
        val last = exercise.setsList.lastOrNull()
        mutableStateListOf<LogSet>().apply {
            addAll(toDo.ifEmpty { listOf(LogSet(last?.weight ?: 0.0, last?.reps?.takeIf { it > 0 } ?: 10, 0, SetType.Normal)) })
        }
    }
    var editing by remember { mutableStateOf<Pair<Int, LogField>?>(null) }
    val labels = (alreadyDone + rows.map { ExerciseSet(it.weight, it.reps, type = it.type) }).setLabels().drop(alreadyDone.size)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text("Log ${exercise.name}", style = AppType.Title, color = AppColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Did it without the timer? Fill in what you did. The time of a set is optional.",
                style = AppType.Caption,
                color = AppColors.TextSecondary,
            )
            Spacer(Modifier.height(16.dp))

            Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                LogLabel("Set", Modifier.width(SetColumnWidth))
                LogLabel("kg", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                LogLabel("Reps", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                LogLabel("Time", Modifier.weight(1f))
                Spacer(Modifier.width(36.dp))
            }
            rows.forEachIndexed { index, row ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        labels[index],
                        style = AppType.BodyStrong,
                        color = if (row.type == SetType.Normal) AppColors.TextSecondary else row.type.color(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(SetColumnWidth),
                    )
                    MetricField(
                        value = if (row.weight > 0.0) row.weight.kg() else "BW",
                        filled = true,
                        onClick = { editing = index to LogField.Weight },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    MetricField(value = row.reps.toString(), filled = true, onClick = { editing = index to LogField.Reps }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    MetricField(
                        value = if (row.seconds > 0) setTimeText(row.seconds) else "–",
                        filled = true,
                        onClick = { editing = index to LogField.Time },
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = rows.size > 1) { rows.removeAt(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (rows.size > 1) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove set", tint = AppColors.TextTertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftButton(
                    text = "Add set",
                    onClick = { rows.add(rows.last().copy(seconds = 0)) },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Add,
                )
                AccentButton(
                    text = "Save as done",
                    onClick = {
                        onSave(
                            rows.map {
                                ExerciseSet(
                                    weight = it.weight,
                                    reps = it.reps,
                                    isCompleted = true,
                                    setDuration = if (it.seconds > 0) setTimeText(it.seconds) else "",
                                    type = it.type,
                                )
                            }
                        )
                    },
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }

    editing?.let { (index, field) ->
        val row = rows.getOrNull(index)
        if (row == null) {
            editing = null
        } else when (field) {
            LogField.Weight -> WeightPickerDialog(
                initial = row.weight,
                onDismiss = { editing = null },
                onSelected = {
                    rows[index] = row.copy(weight = it)
                    editing = null
                },
            )

            LogField.Reps -> RepsPickerDialog(
                initial = row.reps,
                onDismiss = { editing = null },
                onSelected = {
                    rows[index] = row.copy(reps = it)
                    editing = null
                },
            )

            LogField.Time -> TimePickerDialog(
                initialSeconds = row.seconds,
                onDismiss = { editing = null },
                onSelected = {
                    rows[index] = row.copy(seconds = it)
                    editing = null
                },
            )
        }
    }
}

@Composable
private fun LogLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppType.Overline, color = AppColors.TextTertiary, textAlign = TextAlign.Center, modifier = modifier)
}
