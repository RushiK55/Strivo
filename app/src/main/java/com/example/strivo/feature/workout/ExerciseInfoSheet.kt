package com.example.strivo.feature.workout

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.components.SoftButton
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.LibraryExercise
import com.example.strivo.domain.model.dropSetsSuffix

/**
 * Everything the app knows about the exercise being done, one tap from the workout: the plan for it (sets, reps, weight), the notes
 * written in the plan (by the user or the trainer), how to do it, the tip, the muscles it works, and a video when there is one. The
 * words about the exercise come from the exercise library (found by name); with none, the plan and its notes are still shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseInfoSheet(exercise: Exercise, details: LibraryExercise?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val weight = exercise.weight.toDoubleOrNull() ?: 0.0
    val plan = "${exercise.sets} × ${exercise.reps}${exercise.dropSetsSuffix()} · " + if (weight > 0) "${weight.kg()} kg" else "bodyweight"
    val nothingMore = exercise.notes.isBlank() && details == null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface,
        shape = AppShapes.Sheet,
    ) {
        LazyColumn(
            modifier = Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            item {
                Text(exercise.name, style = AppType.Title, color = AppColors.TextPrimary)
                val tags = listOfNotNull(details?.muscleGroup, details?.equipment, details?.level).filter { it.isNotBlank() }
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tags.forEach { Tag(it) } }
                }
                Spacer(Modifier.height(14.dp))
                Text("YOUR PLAN", style = AppType.Overline, color = AppColors.Accent)
                Text(plan, style = AppType.Headline, color = AppColors.TextPrimary)
            }
            if (exercise.notes.isNotBlank()) {
                item { Section("Notes", exercise.notes, highlight = true) }
            }
            details?.takeIf { it.instructions.isNotBlank() }?.let { item { Section("How to do it", it.instructions) } }
            details?.takeIf { it.tips.isNotBlank() && it.tips != exercise.notes }?.let { item { Section("Tip", it.tips, highlight = true) } }
            details?.let { library ->
                val main = library.primaryMuscles.map(::muscleName)
                val helpers = library.secondaryMuscles.map(::muscleName)
                if (main.isNotEmpty() || helpers.isNotEmpty()) {
                    item {
                        Section(
                            "Muscles worked",
                            listOfNotNull(
                                main.takeIf { it.isNotEmpty() }?.let { "Main: ${it.joinToString(", ")}" },
                                helpers.takeIf { it.isNotEmpty() }?.let { "Helping: ${it.joinToString(", ")}" },
                            ).joinToString("\n"),
                        )
                    }
                }
            }
            details?.videoUrl?.takeIf { it.startsWith("https://") }?.let { url ->
                item {
                    Spacer(Modifier.height(16.dp))
                    SoftButton(
                        text = "Watch how it is done",
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.PlayCircle,
                        contentColor = AppColors.Accent,
                    )
                }
            }
            if (nothingMore) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "There is nothing more written about this exercise yet. Add notes to it in its plan and they show here.",
                        style = AppType.Caption,
                        color = AppColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, text: String, highlight: Boolean = false) {
    Spacer(Modifier.height(18.dp))
    Text(title.uppercase(), style = AppType.Overline, color = AppColors.Accent)
    Spacer(Modifier.height(4.dp))
    if (highlight) {
        Text(
            text,
            style = AppType.Body,
            color = AppColors.TextPrimary,
            modifier = Modifier.fillMaxWidth().clip(AppShapes.Control).background(AppColors.SurfaceHigh).padding(14.dp),
        )
    } else {
        Text(text, style = AppType.Body, color = AppColors.TextPrimary)
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text,
        style = AppType.Caption,
        color = AppColors.TextSecondary,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(AppColors.SurfaceHigh).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** "delt-front" is shown as "Delt front": the catalogue's own names are for the 3D body screen. */
private fun muscleName(id: String): String = id.replace('-', ' ').replaceFirstChar { it.uppercase() }
