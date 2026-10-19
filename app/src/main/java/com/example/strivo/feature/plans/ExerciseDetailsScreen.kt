package com.example.strivo.feature.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.CollapsingListScreen
import com.example.strivo.core.ui.components.SectionLabel
import com.example.strivo.core.ui.components.color
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.Plan
import com.example.strivo.domain.model.setLabels

private fun kgText(kg: Double): String = if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString()

@Composable
fun ExerciseDetailsScreen(
    exercise: Exercise,
    plan: Plan,
    onBack: () -> Unit,
    onStartWorkout: () -> Unit,
    /** False when the plan is only being looked at (the day is done, or has passed): no button to start it. */
    canStart: Boolean = true,
) {
    CollapsingListScreen(
        title = exercise.name,
        subtitle = if (plan.isPrebuilt) plan.planName else "${plan.planName} · ${plan.planDay}",
        expandedHeight = 132.dp,
        onBack = onBack,
        header = {},
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // The plan for this exercise in one row.
                val weight = exercise.weight.toDoubleOrNull() ?: 0.0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.Card)
                        .background(AppColors.Surface)
                        .padding(vertical = 16.dp),
                ) {
                    PlanStat("Sets", exercise.sets, Modifier.weight(1f))
                    StatDividerLine()
                    PlanStat("Reps", exercise.reps, Modifier.weight(1f))
                    StatDividerLine()
                    PlanStat("Weight", if (weight > 0) "${kgText(weight)} kg" else "Bodyweight", Modifier.weight(1f))
                    if (exercise.dropSets > 0) {
                        StatDividerLine()
                        PlanStat("Drop sets", exercise.dropSets.toString(), Modifier.weight(1f))
                    }
                }

                if (exercise.notes.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    SectionLabel("Notes")
                    Text(
                        text = exercise.notes,
                        style = AppType.Body,
                        color = AppColors.TextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.Card)
                            .background(AppColors.Surface)
                            .padding(16.dp),
                    )
                }

                if (exercise.setsList.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    SectionLabel(if (exercise.isCheck) "Today's sets" else "Target sets")
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.Card)
                            .background(AppColors.Surface)
                            .padding(vertical = 4.dp)
                    ) {
                        val labels = exercise.setsList.setLabels()
                        exercise.setsList.forEachIndexed { index, set ->
                            if (index > 0) HorizontalDivider(Modifier.padding(start = 60.dp), thickness = 1.dp, color = AppColors.Divider)
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(if (set.isCompleted) AppColors.Accent else AppColors.SurfaceHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (set.isCompleted) {
                                        Icon(Icons.Rounded.Check, contentDescription = "Done", tint = androidx.compose.ui.graphics.Color.Black, modifier = Modifier.size(16.dp))
                                    } else {
                                        Text(labels[index], style = AppType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = set.type.color())
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    if (set.weight > 0) "${kgText(set.weight)} kg × ${set.reps}" else "${set.reps} reps",
                                    style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"),
                                    color = AppColors.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                val duration = set.setDuration.takeIf { it.isNotEmpty() && it != "00:00:00" && it != "00:00" }
                                if (duration != null) Text(duration, style = AppType.Caption, color = AppColors.TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        if (canStart) item {
            AccentButton(
                text = if (exercise.isCheck) "Restart workout" else "Start workout",
                onClick = onStartWorkout,
                modifier = Modifier
                    .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 100.dp)
                    .fillMaxWidth(),
                height = 54.dp,
                icon = if (exercise.isCheck) Icons.Rounded.Refresh else Icons.Rounded.PlayArrow,
            )
        }
    }
}

@Composable
private fun PlanStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = AppType.Title.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary, maxLines = 1)
        Text(label, style = AppType.Caption, color = AppColors.TextSecondary)
    }
}

@Composable
private fun StatDividerLine() {
    Box(
        Modifier
            .padding(vertical = 6.dp)
            .width(1.dp)
            .height(36.dp)
            .background(AppColors.Divider)
    )
}
