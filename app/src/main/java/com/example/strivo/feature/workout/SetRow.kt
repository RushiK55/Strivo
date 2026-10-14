package com.example.strivo.feature.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.core.ui.components.color
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.model.ExerciseSet
import com.example.strivo.domain.model.SetType

// --- Set row ---

/**
 * One row of the set table: number, weight and reps (tap either to change it), and the round action button that
 * starts the set, finishes it, or shows it is done. Under it, a small line with the set's time and rest.
 */
@Composable
internal fun SetRow(
    exerciseIndex: Int,
    setIndex: Int,
    label: String,
    locked: Boolean,
    set: ExerciseSet,
    viewModel: WorkoutViewModel,
    onPickWeight: () -> Unit,
    onPickReps: () -> Unit,
    onPickType: (SetType) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isActive = state.activeSetExerciseIndex == exerciseIndex && state.activeSetIndex == setIndex
    val isResting = state.showRestTimer &&
        !state.isExerciseRest &&
        state.activeRestExerciseIndex == exerciseIndex &&
        state.activeRestSetIndex == setIndex
    val hasDuration = set.setDuration.isNotEmpty() && set.setDuration != ZeroTimeMinSec
    val hasRest = set.restTime.isNotEmpty() && set.restTime != ZeroTimeMinSec

    val rowColor by animateColorAsState(
        when {
            isActive -> AppColors.WarningAccent.copy(alpha = 0.10f)
            set.isCompleted -> AppColors.AccentSoft
            else -> Color.Transparent
        },
        tween(250),
        label = "setRowColor",
    )

    Column(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowColor)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SetTypeBadge(
                label = label,
                type = set.type,
                numberColor = when {
                    set.isCompleted -> AppColors.Accent
                    isActive -> AppColors.WarningAccent
                    else -> AppColors.TextSecondary
                },
                onPick = onPickType,
            )
            MetricField(
                value = when {
                    set.weight > 0.0 -> set.weight.kg()
                    set.reps > 0 -> "BW"
                    else -> "–"
                },
                filled = !set.isCompleted,
                onClick = onPickWeight,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            MetricField(
                value = if (set.reps == 0) "–" else set.reps.toString(),
                filled = !set.isCompleted,
                onClick = onPickReps,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.width(ActionColumnWidth), contentAlignment = Alignment.CenterEnd) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                set.isCompleted -> AppColors.Accent
                                isActive -> AppColors.WarningAccent
                                locked -> AppColors.SurfaceHigh.copy(alpha = 0.45f)
                                else -> AppColors.SurfaceHigh
                            }
                        )
                        .clickable { viewModel.onSetButton(exerciseIndex, setIndex) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when {
                            set.isCompleted -> Icons.Rounded.Check
                            isActive -> Icons.Rounded.Stop
                            else -> Icons.Rounded.PlayArrow
                        },
                        contentDescription = when {
                            set.isCompleted -> "Set done"
                            isActive -> "Finish set"
                            locked -> "Locked, finish the earlier sets first"
                            else -> "Start set"
                        },
                        tint = when {
                            set.isCompleted || isActive -> Color.Black
                            locked -> AppColors.TextTertiary
                            else -> AppColors.TextPrimary
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // The set's own clock, then the rest that followed it.
        if (isActive || hasDuration || isResting || (set.isCompleted && hasRest)) {
            Row(
                modifier = Modifier.padding(start = SetColumnWidth, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isActive) {
                    val setTime by viewModel.setTime.collectAsStateWithLifecycle()
                    Icon(Icons.Rounded.Timer, contentDescription = null, tint = AppColors.WarningAccent, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Set time $setTime", style = AppType.Caption, color = AppColors.WarningAccent)
                } else {
                    if (hasDuration) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = AppColors.TextTertiary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(set.setDuration, style = AppType.Caption, color = AppColors.TextSecondary)
                        Spacer(Modifier.width(12.dp))
                    }
                    if (isResting) {
                        val restTime by viewModel.restTime.collectAsStateWithLifecycle()
                        Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = AppColors.Rest, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Resting $restTime", style = AppType.Caption, color = AppColors.Rest)
                    } else if (set.isCompleted && hasRest) {
                        Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = AppColors.TextTertiary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Rest ${set.restTime}", style = AppType.Caption, color = AppColors.TextSecondary)
                    }
                }
            }
        }
    }
}

/** A tappable value in the set table. Sets still to do show a field; done sets show just the number. */
@Composable
private fun MetricField(value: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) AppColors.SurfaceHigh else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value,
            style = AppType.BodyStrong.copy(fontSize = 16.sp, fontFeatureSettings = "tnum"),
            color = if (value == "–") AppColors.TextTertiary else AppColors.TextPrimary,
            maxLines = 1,
        )
    }
}

/**
 * The first cell of a set row: its number, or W / D / F for a warm-up, drop set or set to failure. Tap it to pick the type.
 */
@Composable
private fun SetTypeBadge(label: String, type: SetType, numberColor: Color, onPick: (SetType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.width(SetColumnWidth), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (type == SetType.Normal) Color.Transparent else type.color().copy(alpha = 0.16f))
                .clickable { open = true }
                .semantics { contentDescription = "Set type: ${type.label}. Tap to change." },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = AppType.BodyStrong,
                color = if (type == SetType.Normal) numberColor else type.color(),
                textAlign = TextAlign.Center,
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(AppColors.SurfaceHigh),
        ) {
            SetType.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(
                                option.label,
                                style = AppType.BodyStrong,
                                color = if (option == type) AppColors.Accent else option.color(AppColors.TextPrimary),
                            )
                            Text(option.hint, style = AppType.Caption, color = AppColors.TextSecondary, modifier = Modifier.widthIn(max = 240.dp))
                        }
                    },
                    onClick = {
                        open = false
                        onPick(option)
                    },
                )
            }
        }
    }
}
