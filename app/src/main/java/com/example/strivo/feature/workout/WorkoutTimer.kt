package com.example.strivo.feature.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.core.ui.components.AppCard
import com.example.strivo.core.ui.components.color
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.model.Exercise

// --- Timer header ---

/**
 * The workout clock with its two controls, and a slim bar for how many sets are done. START begins the first set,
 * PAUSE freezes every clock, RESUME picks up where it stopped.
 */
@Composable
internal fun WorkoutTimerHeader(
    viewModel: WorkoutViewModel,
    isRunning: Boolean,
    isPaused: Boolean,
    doneSets: Int,
    totalSets: Int,
    doneExercises: Int,
    totalExercises: Int,
    modifier: Modifier = Modifier,
) {
    val workoutTime by viewModel.workoutTime.collectAsStateWithLifecycle()
    val started = workoutTime != ZeroTime
    val progress by animateFloatAsState(
        if (totalSets == 0) 0f else doneSets.toFloat() / totalSets,
        tween(400),
        label = "workoutProgress",
    )

    AppCard(modifier = modifier, padding = PaddingValues(start = 18.dp, top = 16.dp, end = 14.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (dot, label) = when {
                        isRunning -> AppColors.Success to "In progress"
                        started || isPaused -> AppColors.Warning to "Paused"
                        else -> AppColors.TextTertiary to "Not started"
                    }
                    Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = AppType.Caption, color = AppColors.TextSecondary)
                }
                Text(text = workoutTime, style = AppType.MetricLarge, color = AppColors.TextPrimary)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AppColors.SurfaceHigh)
                    .clickable(enabled = started, onClick = viewModel::resetWorkout),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.RestartAlt,
                    contentDescription = "Reset timer",
                    tint = if (started) AppColors.TextPrimary else AppColors.TextTertiary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            val buttonColor by animateColorAsState(if (isRunning) AppColors.WarningAccent else AppColors.Accent, tween(250), label = "startColor")
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(buttonColor)
                    .clickable(onClick = viewModel::startStopWorkout)
                    .padding(start = 14.dp, end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        isRunning -> "Pause"
                        started -> "Resume"
                        else -> "Start"
                    },
                    color = Color.Black,
                    style = AppType.BodyStrong,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AppColors.SurfaceHigh)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AppColors.Accent)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Text("$doneSets of $totalSets sets", style = AppType.Caption, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
            Text("$doneExercises of $totalExercises exercises", style = AppType.Caption, color = AppColors.TextSecondary)
        }
    }
}

/** A rest clock as a slim card: what is resting, the time, and (between sets) a way to skip it. */
@Composable
private fun RestCard(title: String, time: String, onSkip: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.Control)
            .background(AppColors.Warning.copy(alpha = 0.12f))
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = if (onSkip == null) 16.dp else 4.dp)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = AppColors.Warning, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, style = AppType.BodyStrong, color = AppColors.Warning, modifier = Modifier.weight(1f), maxLines = 1)
        Text(time, style = AppType.Headline.copy(fontFeatureSettings = "tnum"), color = AppColors.Warning)
        if (onSkip != null) {
            TextButton(onClick = onSkip) { Text("Skip", style = AppType.BodyStrong, color = AppColors.TextPrimary) }
        }
    }
}

@Composable
internal fun GlobalRestIndicator(viewModel: WorkoutViewModel) {
    val restTime by viewModel.restTime.collectAsStateWithLifecycle()
    RestCard(
        title = "Rest before next set",
        time = restTime,
        onSkip = viewModel::stopRest,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
internal fun PostExerciseRest(index: Int, exercise: Exercise, viewModel: WorkoutViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isLive = state.showRestTimer && state.isExerciseRest && state.activeRestExerciseIndex == index

    if (isLive) {
        val restTime by viewModel.restTime.collectAsStateWithLifecycle()
        RestCard(title = "Rest before next exercise", time = restTime, onSkip = null, modifier = Modifier.padding(bottom = 12.dp))
    } else if (exercise.isCheck && exercise.restTime.isNotEmpty() && exercise.restTime != ZeroTimeMinSec) {
        // A quiet note between the cards for the rest that was taken.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.History, contentDescription = null, tint = AppColors.TextTertiary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Rest between exercises · ${exercise.restTime}", style = AppType.Caption, color = AppColors.TextTertiary)
        }
    }
}
