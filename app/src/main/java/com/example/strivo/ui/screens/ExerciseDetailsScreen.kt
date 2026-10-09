package com.example.strivo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.data.model.Exercise
import com.example.strivo.data.model.Plan
import com.example.strivo.ui.components.AccentButton
import com.example.strivo.ui.components.CollapsingListScreen
import com.example.strivo.ui.components.IconBadge
import com.example.strivo.ui.components.SectionLabel
import com.example.strivo.ui.theme.AppColors

@Composable
fun ExerciseDetailsScreen(
    exercise: Exercise,
    plan: Plan,
    onBack: () -> Unit,
    onStartWorkout: () -> Unit,
) {
    CollapsingListScreen(
        title = exercise.name,
        expandedHeight = 250.dp,
        onBack = onBack,
        header = {
            Box(Modifier.fillMaxSize().padding(bottom = 44.dp), contentAlignment = Alignment.Center) {
                IconBadge(
                    icon = Icons.Rounded.FitnessCenter,
                    background = AppColors.Accent.copy(alpha = 0.1f),
                    border = BorderStroke(2.dp, AppColors.Accent.copy(alpha = 0.2f)),
                    padding = 30.dp,
                    iconSize = 80.dp,
                )
            }
        },
    ) {
        item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                SectionLabel("Quick Summary")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Sets", exercise.sets, Icons.Outlined.Layers)
                    StatCard("Reps", exercise.reps, Icons.Rounded.Repeat)
                    StatCard("Weight", "${exercise.weight} kg", Icons.Rounded.FitnessCenter)
                }
                Spacer(Modifier.height(40.dp))

                if (exercise.notes.isNotEmpty()) {
                    SectionLabel("Exercise Notes")
                    Text(
                        text = exercise.notes,
                        color = AppColors.TextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 25.6.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(25.dp))
                            .background(AppColors.Surface)
                            .border(BorderStroke(1.dp, AppColors.Field), RoundedCornerShape(25.dp))
                            .padding(20.dp),
                    )
                    Spacer(Modifier.height(40.dp))
                }

                SectionLabel("Current Plan")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(25.dp))
                        .background(AppColors.Surface)
                        .border(BorderStroke(1.dp, AppColors.Field), RoundedCornerShape(25.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(icon = Icons.AutoMirrored.Outlined.Assignment)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(plan.planName, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, fontSize = 18.sp)
                        Text(plan.planDay, color = AppColors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(40.dp))

                if (exercise.setsList.isNotEmpty()) {
                    SectionLabel(if (exercise.isCheck) "Performance History" else "Target Sets")
                }
            }
        }

        itemsIndexed(exercise.setsList) { index, set ->
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(25.dp))
                    .background(AppColors.Surface)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (set.isCompleted) AppColors.Accent else AppColors.Field),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        fontSize = 14.sp,
                        color = if (set.isCompleted) Color.Black else AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${set.weight} kg × ${set.reps} reps",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = AppColors.TextPrimary,
                    )
                    if (exercise.isCheck && set.setDuration.isNotEmpty() && set.setDuration != "00:00:00") {
                        Text(
                            "Duration: ${set.setDuration}",
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                if (set.isCompleted) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = AppColors.Accent,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        item {
            AccentButton(
                text = if (exercise.isCheck) "RESTART WORKOUT" else "START WORKOUT",
                onClick = onStartWorkout,
                modifier = Modifier
                    .padding(start = 24.dp, top = 40.dp, end = 24.dp, bottom = 100.dp)
                    .fillMaxWidth(),
                height = 60.dp,
                shape = RoundedCornerShape(20.dp),
                letterSpacing = 1.2.sp,
                icon = if (exercise.isCheck) Icons.Rounded.Refresh else Icons.Rounded.PlayArrow,
            )
        }
    }
}

@Composable
private fun RowScope.StatCard(label: String, value: String, icon: ImageVector) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(25.dp))
            .background(AppColors.Surface)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(12.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = AppColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = AppColors.TextSecondary, fontWeight = FontWeight.Bold)
    }
}
