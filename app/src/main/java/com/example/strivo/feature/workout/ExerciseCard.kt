package com.example.strivo.feature.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.color
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.coach.Advice
import com.example.strivo.domain.coach.AdviceKind
import com.example.strivo.domain.coach.exerciseKey
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.ExerciseSet
import com.example.strivo.domain.model.SetType
import com.example.strivo.domain.model.blockingSetBefore
import com.example.strivo.domain.model.countsForRecords
import com.example.strivo.domain.model.dropSetsSuffix
import com.example.strivo.domain.model.setLabels
import kotlin.math.abs

// --- Exercise card ---

internal fun Double.kg(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

/** "3 × 10 · 40 kg" for the plan of an exercise, or null when it has no plan. */
private fun planLine(exercise: Exercise): String? {
    val sets = exercise.sets.toIntOrNull() ?: 0
    val reps = exercise.reps.toIntOrNull() ?: 0
    if (sets <= 0 || reps <= 0) return null
    val weight = exercise.weight.toDoubleOrNull() ?: 0.0
    return "$sets × $reps${exercise.dropSetsSuffix()} · " + if (weight > 0) "${weight.kg()} kg" else "bodyweight"
}

internal val SetColumnWidth = 40.dp
internal val ActionColumnWidth = 52.dp

@Composable
internal fun ExerciseItem(
    exerciseIndex: Int,
    exercise: Exercise,
    viewModel: WorkoutViewModel,
    onPickWeight: (Int, ExerciseSet) -> Unit,
    onPickReps: (Int, ExerciseSet) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val expanded = exerciseIndex in state.expandedExercises
    val completedSets = exercise.setsList.count { it.isCompleted }
    val hasActiveSet = state.activeSetExerciseIndex == exerciseIndex && state.activeSetIndex != null

    Column(
        modifier = Modifier
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .clip(AppShapes.Card)
            .background(AppColors.Surface)
            .animateContentSize(tween(250)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.toggleExpanded(exerciseIndex) }
                .padding(start = 14.dp, top = 14.dp, end = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        when {
                            exercise.isCheck -> AppColors.Accent
                            hasActiveSet -> AppColors.WarningAccent.copy(alpha = 0.18f)
                            else -> AppColors.SurfaceHigh
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (exercise.isCheck) {
                    Icon(Icons.Rounded.Check, contentDescription = "Done", tint = Color.Black, modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        "${exerciseIndex + 1}",
                        style = AppType.BodyStrong,
                        color = if (hasActiveSet) AppColors.WarningAccent else AppColors.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    style = AppType.Headline,
                    color = if (exercise.isCheck) AppColors.TextSecondary else AppColors.TextPrimary,
                    maxLines = 1,
                )
                Text(
                    text = listOfNotNull("$completedSets/${exercise.setsList.size} sets", planLine(exercise)).joinToString(" · "),
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = AppColors.TextTertiary,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(250)) + fadeOut(tween(150)),
        ) {
            Column(Modifier.padding(bottom = 12.dp)) {
                // The coach's weight for today, while the sets it applies to are still to do.
                val advice by viewModel.advice.collectAsStateWithLifecycle()
                val tip = advice[exerciseKey(exercise.name)]?.takeIf { tip ->
                    val kg = tip.suggestedKg ?: return@takeIf false
                    !exercise.isCheck && exercise.setsList.any { !it.isCompleted && !it.isStarted && it.countsForRecords && abs(it.weight - kg) > 0.01 }
                }
                if (tip != null) {
                    CoachTip(
                        advice = tip,
                        onUse = { viewModel.applyAdvice(exerciseIndex) },
                        onDismiss = { viewModel.dismissAdvice(exercise.name) },
                    )
                }
                // Column labels for the set table.
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TableLabel("Set", Modifier.width(SetColumnWidth))
                    TableLabel("kg", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    TableLabel("Reps", Modifier.weight(1f))
                    Spacer(Modifier.width(ActionColumnWidth))
                }
                val labels = exercise.setsList.setLabels()
                exercise.setsList.forEachIndexed { setIndex, set ->
                    SetRow(
                        exerciseIndex = exerciseIndex,
                        setIndex = setIndex,
                        set = set,
                        label = labels[setIndex],
                        locked = !set.isCompleted && !set.isStarted && exercise.setsList.blockingSetBefore(setIndex) != null,
                        viewModel = viewModel,
                        onPickWeight = { onPickWeight(setIndex, set) },
                        onPickReps = { onPickReps(setIndex, set) },
                        onPickType = { viewModel.setType(exerciseIndex, setIndex, it) },
                    )
                }

                // Add a set, or drop the last one that has not been started yet.
                val removable = exercise.setsList.indexOfLast { !it.isCompleted && !it.isStarted }
                Row(
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { viewModel.addSet(exerciseIndex) }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add set", style = AppType.BodyStrong, color = AppColors.Accent)
                    }
                    TextButton(onClick = { viewModel.addSet(exerciseIndex, SetType.Drop) }) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = null, tint = AppColors.SetDrop, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Drop set", style = AppType.BodyStrong, color = AppColors.SetDrop)
                    }
                    Spacer(Modifier.weight(1f))
                    if (removable >= 0) {
                        TextButton(onClick = { viewModel.deleteSet(exerciseIndex, removable) }) {
                            Icon(Icons.Rounded.Remove, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Remove", style = AppType.BodyStrong, color = AppColors.TextSecondary)
                        }
                    }
                }
                // Only offered once every set is done; until then the sets themselves are the next step.
                if (!exercise.isCheck && exercise.setsList.isNotEmpty() && exercise.setsList.all { it.isCompleted }) {
                    AccentButton(
                        text = "Finish exercise",
                        onClick = { viewModel.requestFinishExercise(exerciseIndex) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        height = 46.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TableLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppType.Overline, color = AppColors.TextTertiary, textAlign = TextAlign.Center, modifier = modifier)
}

/** The coach's suggestion for an exercise in the workout, with a button that puts its weight on the sets still to do. */
@Composable
private fun CoachTip(advice: Advice, onUse: () -> Unit, onDismiss: () -> Unit) {
    val kg = advice.suggestedKg ?: return
    val heavier = advice.kind == AdviceKind.IncreaseWeight
    val tint = if (heavier) AppColors.Accent else AppColors.SetWarmUp
    Row(
        modifier = Modifier
            .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (heavier) "Try ${kg.kg()} kg today" else "Lighter week: ${kg.kg()} kg",
                style = AppType.BodyStrong,
                color = tint,
            )
            // The heading already names the weight, so the reason is enough here.
            Text(advice.detail.substringBefore(" Try "), style = AppType.Caption, color = AppColors.TextSecondary)
        }
        TextButton(onClick = onUse) { Text("Use", style = AppType.BodyStrong, color = tint) }
        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Dismiss suggestion", tint = AppColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}
