package com.example.strivo.feature.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.components.Chip
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.core.util.formatWeight
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.LibraryExercise
import com.example.strivo.domain.plan.ExercisePicker

/**
 * The exercise library, made for adding to one plan. A sheet over the plan: a tap on + puts the exercise straight into the plan (with its
 * suggested sets, reps and weight) and the sheet stays open, so several can be added in a row and the plan fills up behind it.
 * The muscle groups the plan already trains come first ("Suggested"), what the plan has is ticked and moves to the end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    planName: String,
    planExercises: List<Exercise>,
    library: List<LibraryExercise>,
    onAdd: (LibraryExercise) -> Unit,
    /** The user wants an exercise that is not in the library: open the form to write one. */
    onCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    val groups = remember(library) { library.map { it.muscleGroup }.distinct() }
    val suggested = remember(planExercises, library) { ExercisePicker.groupsOf(planExercises, library) }
    var chip by remember { mutableStateOf(if (suggested.isNotEmpty()) ExercisePicker.SUGGESTED else ExercisePicker.ALL) }
    var query by remember { mutableStateOf("") }
    val shown = remember(library, planExercises, query, chip) { ExercisePicker.list(library, planExercises, query, chip) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface,
        shape = AppShapes.Sheet,
    ) {
        Column(Modifier.fillMaxHeight(0.92f).navigationBarsPadding()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add to $planName", style = AppType.Title, color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(AppShapes.Chip)
                            .background(AppColors.AccentSoft)
                            .clickable(onClick = onCustom)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add custom", style = AppType.BodyStrong, color = AppColors.Accent, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    if (suggested.isEmpty()) "Tap + to add an exercise from the library. It stays open, so you can add several. Not here? Add custom."
                    else "Tap + to add. Suggested are the muscles this plan already trains (${suggested.joinToString(", ")}).",
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                singleLine = true,
                placeholder = { Text("Search the whole library") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = AppColors.Accent) },
                shape = AppShapes.Control,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppColors.TextPrimary,
                    unfocusedTextColor = AppColors.TextPrimary,
                    focusedContainerColor = AppColors.SurfaceHigh,
                    unfocusedContainerColor = AppColors.SurfaceHigh,
                    focusedBorderColor = AppColors.Accent,
                    unfocusedBorderColor = AppColors.SurfaceHigh,
                    focusedPlaceholderColor = AppColors.Placeholder,
                    unfocusedPlaceholderColor = AppColors.Placeholder,
                    cursorColor = AppColors.Accent,
                ),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                if (suggested.isNotEmpty()) item { Chip(ExercisePicker.SUGGESTED, selected = query.isBlank() && chip == ExercisePicker.SUGGESTED, onClick = { chip = ExercisePicker.SUGGESTED; query = "" }) }
                item { Chip(ExercisePicker.ALL, selected = query.isBlank() && chip == ExercisePicker.ALL, onClick = { chip = ExercisePicker.ALL; query = "" }) }
                items(groups) { group -> Chip(group, selected = query.isBlank() && chip == group, onClick = { chip = group; query = "" }) }
            }

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (library.isEmpty()) "The exercise library is empty for now." else "Nothing matches.",
                        style = AppType.Body,
                        color = AppColors.TextSecondary,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                    items(shown, key = { it.id }) { exercise ->
                        PickerRow(exercise, added = ExercisePicker.isIn(planExercises, exercise), onAdd = { onAdd(exercise) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(exercise: LibraryExercise, added: Boolean, onAdd: () -> Unit) {
    val suggestion = "${exercise.defaultSets} × ${exercise.defaultReps}" +
        if (exercise.defaultWeightKg > 0) " · ${formatWeight(exercise.defaultWeightKg)} kg" else " · bodyweight"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Control)
            .clickable(enabled = !added, onClick = onAdd)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                exercise.name,
                style = AppType.BodyStrong,
                color = if (added) AppColors.TextTertiary else AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(exercise.muscleGroup, exercise.equipment, suggestion).filter { it.isNotEmpty() }.joinToString(" · "),
                style = AppType.Caption,
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (added) AppColors.SurfaceHigh else AppColors.AccentSoft)
                .semantics { contentDescription = if (added) "${exercise.name} is in the plan" else "Add ${exercise.name}" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (added) Icons.Rounded.Check else Icons.Rounded.Add,
                contentDescription = null,
                tint = if (added) AppColors.TextTertiary else AppColors.Accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
