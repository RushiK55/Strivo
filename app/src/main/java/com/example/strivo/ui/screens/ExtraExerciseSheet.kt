package com.example.strivo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.data.model.Exercise
import com.example.strivo.ui.components.AccentButton
import com.example.strivo.ui.components.IconBadge
import com.example.strivo.ui.theme.AppColors

/**
 * Picks extra exercises for a day from the exercises the user already has (their plans, the prebuilt ones, earlier
 * extras). Tapping a row adds it to the day at once and ticks it; "New exercise" is for something not in the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraExerciseSheet(
    day: String,
    exercises: List<Exercise>,
    addedNames: Set<String>,
    onAdd: (Exercise) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = exercises.filter { it.name.contains(query.trim(), ignoreCase = true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
        ) {
            Text("Add extra exercise", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("Pick from your exercises to add to $day.", color = AppColors.TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search exercises") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = AppColors.Accent) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppColors.TextPrimary,
                    unfocusedTextColor = AppColors.TextPrimary,
                    focusedContainerColor = AppColors.Field,
                    unfocusedContainerColor = AppColors.Field,
                    focusedBorderColor = AppColors.Accent,
                    unfocusedBorderColor = AppColors.Field,
                    focusedPlaceholderColor = AppColors.Placeholder,
                    unfocusedPlaceholderColor = AppColors.Placeholder,
                    cursorColor = AppColors.Accent,
                ),
            )
            Spacer(Modifier.height(12.dp))

            if (shown.isEmpty()) {
                Text(
                    text = if (exercises.isEmpty()) "You have no exercises yet. Add a new one below." else "No exercise matches \"${query.trim()}\".",
                    color = AppColors.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.id ?: it.name.hashCode().toLong() }) { exercise ->
                        val added = exercise.name.trim().lowercase() in addedNames
                        ExerciseRow(exercise, added, onClick = { if (!added) onAdd(exercise) })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            AccentButton(
                text = "NEW EXERCISE",
                onClick = onNew,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Add,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("DONE", color = AppColors.TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            }
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, added: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Field)
            .clickable(enabled = !added, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(exercise.name, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            val weight = exercise.weight.toDoubleOrNull()
            Text(
                "${exercise.sets} sets · ${exercise.reps} reps" + if (weight != null && weight > 0) " · ${plainKg(weight)} kg" else "",
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
        if (added) {
            Icon(Icons.Rounded.Check, contentDescription = "Added", tint = AppColors.Accent, modifier = Modifier.size(24.dp))
        } else {
            IconBadge(
                icon = Icons.Rounded.Add,
                tint = Color.Black,
                background = AppColors.Accent,
                padding = 6.dp,
                iconSize = 18.dp,
            )
        }
    }
}

private fun plainKg(kg: Double): String = if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString()
