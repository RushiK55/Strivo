package com.example.strivo.feature.workout

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.DecimalWheelPicker
import com.example.strivo.core.ui.components.SoftButton
import com.example.strivo.core.ui.components.WheelPicker
import com.example.strivo.core.ui.components.color
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.SetType
import com.example.strivo.domain.model.isWork
import com.example.strivo.domain.model.setLabels

// --- Dialogs & sheets ---

@Composable
internal fun WeightPickerDialog(initial: Double, onDismiss: () -> Unit, onSelected: (Double) -> Unit) {
    var weight by remember { mutableStateOf(initial) }
    PickerDialog(title = "Weight", onDismiss = onDismiss, onSet = { onSelected(weight) }) {
        DecimalWheelPicker(label = "kg", minValue = 0, maxValue = 500, value = weight, onValueChange = { weight = it })
    }
}

@Composable
internal fun RepsPickerDialog(initial: Int, onDismiss: () -> Unit, onSelected: (Int) -> Unit) {
    var reps by remember { mutableStateOf(initial) }
    PickerDialog(title = "Reps", onDismiss = onDismiss, onSet = { onSelected(reps) }) {
        Box(Modifier.height(180.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            WheelPicker(minValue = 1, maxValue = 100, initialValue = initial, onChanged = { reps = it })
        }
    }
}

@Composable
private fun PickerDialog(title: String, onDismiss: () -> Unit, onSet: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        shape = RoundedCornerShape(22.dp),
        title = { Text(title, style = AppType.Title, color = AppColors.TextPrimary) },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = AppType.BodyStrong, color = AppColors.TextSecondary) } },
        confirmButton = { TextButton(onClick = onSet) { Text("Set", style = AppType.BodyStrong, color = AppColors.Accent) } },
    )
}

/** The finished sets of an exercise, as "40 × 10" lines, before it is saved. */
@Composable
internal fun SummarySheetContent(
    exercise: Exercise,
    viewModel: WorkoutViewModel,
    onAddSet: () -> Unit,
    onConfirm: () -> Unit,
) {
    val workoutTime by viewModel.workoutTime.collectAsStateWithLifecycle()
    val done = exercise.setsList.filter { it.isCompleted }
    val labels = done.setLabels()
    val workSets = done.filter { it.isWork }
    val volume = workSets.sumOf { it.weight * it.reps }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        Text("Finish ${exercise.name}?", style = AppType.Title, color = AppColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            listOfNotNull(
                if (workSets.size == 1) "1 set" else "${workSets.size} sets",
                if (volume > 0) "${volume.kg()} kg volume" else null,
                "workout $workoutTime",
            ).joinToString(" · "),
            style = AppType.Caption,
            color = AppColors.TextSecondary,
        )
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(AppShapes.Control)
                .background(AppColors.SurfaceHigh)
                .padding(vertical = 6.dp)
        ) {
            done.forEachIndexed { i, set ->
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (set.type == SetType.Normal) "Set ${labels[i]}" else set.type.label,
                        style = AppType.Body,
                        color = set.type.color(),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (set.weight > 0) "${set.weight.kg()} kg × ${set.reps}" else "${set.reps} reps",
                        style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"),
                        color = AppColors.TextPrimary,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SoftButton(text = "Add set", onClick = onAddSet, modifier = Modifier.weight(1f), icon = Icons.Rounded.Add)
            AccentButton(text = "Save exercise", onClick = onConfirm, modifier = Modifier.weight(1.4f))
        }
    }
}

@Composable
internal fun CompleteSheetContent(planName: String, viewModel: WorkoutViewModel, exerciseCount: Int, onFinish: () -> Unit) {
    val duration by viewModel.workoutTime.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val doneSets = state.exercises.flatMap { ex -> ex.setsList.filter { it.isCompleted && it.isWork } }
    val volume = doneSets.sumOf { it.weight * it.reps }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(AppColors.AccentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Workout complete", style = AppType.Title, color = AppColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Nice work on $planName.",
            textAlign = TextAlign.Center,
            style = AppType.Body,
            color = AppColors.TextSecondary,
        )
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(AppShapes.Control)
                .background(AppColors.SurfaceHigh)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatItem("Duration", duration, Modifier.weight(1f))
            StatItem("Exercises", exerciseCount.toString(), Modifier.weight(1f))
            StatItem("Sets", doneSets.size.toString(), Modifier.weight(1f))
            if (volume > 0) StatItem("Volume", "${(volume / 1000).let { if (it >= 1) "%.1f t".format(it) else "${volume.toInt()} kg" }}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        AccentButton(text = "Done", onClick = onFinish, modifier = Modifier.fillMaxWidth(), height = 54.dp)
    }
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = AppType.Headline.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary, maxLines = 1)
        Text(label, style = AppType.Caption, color = AppColors.TextSecondary)
    }
}
