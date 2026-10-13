package com.example.strivo.feature.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.Chip
import com.example.strivo.core.ui.components.ScreenTopBar
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.core.util.formatWeight
import com.example.strivo.domain.model.LibraryExercise
import com.example.strivo.domain.model.Plan
import kotlinx.coroutines.launch

/**
 * The exercise library: search it, filter by muscle group, read how an exercise is done and add it to a plan.
 * Opened from a plan, adding goes straight to that plan; opened on its own, the user picks the plan.
 */
@Composable
fun LibraryScreen(viewModel: LibraryViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<LibraryExercise?>(null) }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = { ScreenTopBar(title = "Exercise library", onBack = onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(state.query, viewModel::setQuery)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                item { Chip("All", selected = state.group == null, onClick = { viewModel.setGroup(null) }) }
                items(state.groups) { group -> Chip(group, selected = state.group == group, onClick = { viewModel.setGroup(group) }) }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Accent) }
                state.shown.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.exercises.isEmpty()) "The library is empty for now. Check back soon." else "Nothing matches \"${state.query.trim()}\".",
                        style = AppType.Body,
                        color = AppColors.TextSecondary,
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            "${state.shown.size} exercises",
                            style = AppType.Caption,
                            color = AppColors.TextTertiary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                        )
                    }
                    items(state.shown, key = { it.id }) { exercise -> ExerciseRow(exercise) { open = exercise } }
                }
            }
        }
    }

    open?.let { exercise ->
        ExerciseSheet(
            exercise = exercise,
            plans = state.plans,
            targetPlan = state.targetPlan,
            onAdd = viewModel::addTo,
            onDismiss = { open = null },
        )
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        placeholder = { Text("Search by name, muscle or equipment") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = AppColors.Accent) },
        shape = AppShapes.Control,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppColors.TextPrimary,
            unfocusedTextColor = AppColors.TextPrimary,
            focusedContainerColor = AppColors.Surface,
            unfocusedContainerColor = AppColors.Surface,
            focusedBorderColor = AppColors.Accent,
            unfocusedBorderColor = AppColors.Surface,
            focusedPlaceholderColor = AppColors.Placeholder,
            unfocusedPlaceholderColor = AppColors.Placeholder,
            cursorColor = AppColors.Accent,
        ),
    )
}

@Composable
private fun ExerciseRow(exercise: LibraryExercise, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Card)
            .background(AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(exercise.name, style = AppType.Headline, color = AppColors.TextPrimary, maxLines = 1)
            Text(
                listOf(exercise.muscleGroup, exercise.equipment, exercise.level).filter { it.isNotEmpty() }.joinToString(" · "),
                style = AppType.Caption,
                color = AppColors.TextSecondary,
                maxLines = 1,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = AppColors.TextTertiary)
    }
}

/** "4 sets × 8 reps · 40 kg" for the suggestion of an exercise. */
private fun suggestion(exercise: LibraryExercise): String =
    "${exercise.defaultSets} sets × ${exercise.defaultReps} reps" + if (exercise.defaultWeightKg > 0) " · ${formatWeight(exercise.defaultWeightKg)} kg" else " · bodyweight"

private fun planLabel(plan: Plan): String = if (plan.isPrebuilt) "${plan.planName} (library)" else "${plan.planName} · ${plan.planDay}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseSheet(
    exercise: LibraryExercise,
    plans: List<Plan>,
    targetPlan: Plan?,
    onAdd: suspend (LibraryExercise, Plan) -> Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var choosing by remember { mutableStateOf(false) }

    fun add(plan: Plan) {
        scope.launch {
            if (onAdd(exercise, plan)) {
                Toast.makeText(context, "${exercise.name} added to ${plan.planName}", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface,
        shape = AppShapes.Sheet,
    ) {
        LazyColumn(
            modifier = Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            item {
                Text(exercise.name, style = AppType.Title, color = AppColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(exercise.muscleGroup, exercise.equipment, exercise.level).filter { it.isNotEmpty() }.forEach { Tag(it) }
                }
                Spacer(Modifier.height(16.dp))
                Text(suggestion(exercise), style = AppType.BodyStrong, color = AppColors.Accent)
                if (exercise.instructions.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("How to do it", style = AppType.BodyStrong, color = AppColors.TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(exercise.instructions, style = AppType.Body, color = AppColors.TextPrimary)
                }
                if (exercise.tips.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.Control)
                            .background(AppColors.SurfaceHigh)
                            .padding(14.dp),
                    ) {
                        Text("Tip", style = AppType.Caption, color = AppColors.TextSecondary)
                        Text(exercise.tips, style = AppType.Body, color = AppColors.TextPrimary)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            if (targetPlan != null) {
                item {
                    AccentButton(
                        text = "Add to ${targetPlan.planName}",
                        onClick = { add(targetPlan) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Add,
                    )
                }
            } else if (!choosing) {
                item {
                    AccentButton(
                        text = "Add to a plan",
                        onClick = { choosing = true },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Add,
                    )
                }
            } else {
                item { Text("Choose a plan", style = AppType.BodyStrong, color = AppColors.TextSecondary, modifier = Modifier.padding(bottom = 8.dp)) }
                if (plans.isEmpty()) {
                    item { Text("You have no plans yet. Create one from Home, then add exercises to it.", style = AppType.Body, color = AppColors.TextSecondary) }
                }
                items(plans, key = { it.planId ?: 0L }) { plan ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(AppShapes.Control)
                            .background(AppColors.SurfaceHigh)
                            .clickable { add(plan) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(planLabel(plan), style = AppType.BodyStrong, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.Add, contentDescription = "Add", tint = AppColors.Accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text,
        style = AppType.Caption,
        color = AppColors.TextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.SurfaceHigh)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
