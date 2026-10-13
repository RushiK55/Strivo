package com.example.strivo.feature.plans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.DecimalWheelPicker
import com.example.strivo.core.ui.components.IconBadge
import com.example.strivo.core.ui.components.ScreenTopBar
import com.example.strivo.core.ui.components.SectionLabel
import com.example.strivo.core.ui.components.WheelPicker
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.core.util.formatWeight
import com.example.strivo.domain.model.DropStepKg
import com.example.strivo.domain.model.EXTRA_PLAN_NAME
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.MaxPlannedDropSets
import com.example.strivo.domain.model.Plan
import com.example.strivo.domain.model.plannedSets
import kotlinx.coroutines.launch

@Composable
fun SaveExerciseScreen(
    plan: Plan?, // Optional for extra exercises
    exercise: Exercise?,
    isExtra: Boolean,
    exerciseViewModel: ExerciseViewModel,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(exercise?.name.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(exercise?.notes.orEmpty()) }
    var nameError by remember { mutableStateOf<String?>(null) }
    // Exercises added straight to a day (the "Extra exercises" plan) are usually one-offs, so they vanish by default.
    val isExtraPlan = isExtra || plan?.planName == EXTRA_PLAN_NAME
    var vanishEndOfDay by rememberSaveable { mutableStateOf(exercise?.vanishEndOfDay ?: isExtraPlan) }

    var sets by rememberSaveable { mutableIntStateOf(exercise?.sets?.toIntOrNull() ?: 3) }
    var reps by rememberSaveable { mutableIntStateOf(exercise?.reps?.toIntOrNull() ?: 10) }
    var weight by rememberSaveable { mutableDoubleStateOf(exercise?.weight?.toDoubleOrNull() ?: 40.0) }
    var dropSets by rememberSaveable { mutableIntStateOf(exercise?.dropSets ?: 0) }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            ScreenTopBar(
                title = when {
                    exercise != null -> "Edit exercise"
                    isExtraPlan -> "Extra exercise"
                    else -> "New exercise"
                },
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("Exercise")
            NameField(value = name, onValueChange = { name = it; nameError = null }, error = nameError)

            Spacer(Modifier.height(30.dp))
            SectionLabel("Target")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.Card)
                    .background(AppColors.Surface)
                    .padding(vertical = 24.dp, horizontal = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WheelPicker(label = "Sets", minValue = 1, maxValue = 20, initialValue = sets, onChanged = { sets = it }, width = 65.dp)
                Spacer(Modifier.width(15.dp))
                WheelPicker(label = "Reps", minValue = 1, maxValue = 100, initialValue = reps, onChanged = { reps = it }, width = 65.dp)
                Spacer(Modifier.width(15.dp))
                DecimalWheelPicker(
                    label = "Weight (kg)",
                    minValue = 0,
                    maxValue = 500,
                    value = weight,
                    onValueChange = { weight = it },
                    intWidth = 75.dp,
                    decimalWidth = 55.dp,
                )
            }
            Spacer(Modifier.height(12.dp))
            DropSetsRow(dropSets = dropSets, weight = weight, onChange = { dropSets = it })

            if (isExtraPlan) {
                Spacer(Modifier.height(30.dp))
                SectionLabel("Options")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.Card)
                        .background(AppColors.Surface)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Vanish at end of day", fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(2.dp))
                        Text("On: only for today. Off: repeats every week.", color = AppColors.TextSecondary, fontSize = 13.sp)
                    }
                    Switch(
                        checked = vanishEndOfDay,
                        onCheckedChange = { vanishEndOfDay = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppColors.Accent,
                            checkedTrackColor = AppColors.Accent.copy(alpha = 0.3f),
                            checkedBorderColor = AppColors.Accent.copy(alpha = 0.3f),
                        ),
                    )
                }
            }

            Spacer(Modifier.height(30.dp))
            SectionLabel("Notes")
            TextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.Card),
                minLines = 4,
                maxLines = 4,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                placeholder = { Text("Add specific training tips or cues...", fontSize = 15.sp) },
                colors = transparentFieldColors(),
            )

            Spacer(Modifier.height(60.dp))
            AccentButton(
                text = if (exercise == null) "FINISH & SAVE" else "UPDATE EXERCISE",
                onClick = {
                    if (name.isEmpty()) {
                        nameError = "Required"
                        return@AccentButton
                    }
                    scope.launch {
                        if (exercise == null) {
                            exerciseViewModel.addExercise(
                                Exercise(
                                    planId = plan?.planId ?: -1L,
                                    name = name,
                                    weight = weight.toString(),
                                    sets = sets.toString(),
                                    reps = reps.toString(),
                                    notes = notes,
                                    isExtra = isExtra,
                                    vanishEndOfDay = vanishEndOfDay,
                                    setsList = emptyList(),
                                    dropSets = dropSets,
                                )
                            )
                        } else {
                            exerciseViewModel.updateExercise(
                                exercise.copy(
                                    name = name,
                                    notes = notes,
                                    vanishEndOfDay = vanishEndOfDay,
                                    sets = sets.toString(),
                                    reps = reps.toString(),
                                    weight = weight.toString(),
                                    dropSets = dropSets,
                                    // Sets nobody has started yet follow the edited plan; logged progress is kept.
                                    setsList = if (exercise.setsList.any { it.isStarted || it.isCompleted }) exercise.setsList else emptyList(),
                                )
                            )
                        }
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                height = 60.dp,
                shape = AppShapes.Control,
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, error: String?) {
    Column {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.Control),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp),
            placeholder = { Text("e.g. Bench Press", fontSize = 16.sp, fontWeight = FontWeight.Normal) },
            leadingIcon = {
                IconBadge(
                    icon = Icons.Rounded.FitnessCenter,
                    padding = 8.dp,
                    iconSize = 20.dp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
            colors = transparentFieldColors(),
        )
        if (error != null) {
            Text(error, color = AppColors.Danger, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
        }
    }
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedTextColor = AppColors.TextPrimary,
    unfocusedTextColor = AppColors.TextPrimary,
    focusedContainerColor = AppColors.Surface,
    unfocusedContainerColor = AppColors.Surface,
    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    errorIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    focusedPlaceholderColor = AppColors.Placeholder,
    unfocusedPlaceholderColor = AppColors.Placeholder,
    cursorColor = AppColors.Accent,
)

/**
 * How many drop sets the plan adds after the working sets. The line under it spells out the weights the workout
 * will start with, so 5 kg steps are never a surprise.
 */
@Composable
private fun DropSetsRow(dropSets: Int, weight: Double, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Card)
            .background(AppColors.Surface)
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Drop sets", fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Spacer(Modifier.height(2.dp))
            val chain = Exercise(planId = -1, name = "", weight = weight.toString(), sets = "1", reps = "1", notes = "", dropSets = dropSets)
                .plannedSets()
                .joinToString(" → ") { formatWeight(it.weight) }
            Text(
                if (dropSets == 0) "None. Each one is ${formatWeight(DropStepKg)} kg lighter, straight after your last set." else "$chain kg, straight after your last set",
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
            )
        }
        StepButton(Icons.Rounded.Remove, "Fewer drop sets", enabled = dropSets > 0) { onChange(dropSets - 1) }
        Text(
            "$dropSets",
            style = AppType.Headline.copy(fontFeatureSettings = "tnum"),
            color = if (dropSets > 0) AppColors.SetDrop else AppColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp),
        )
        StepButton(Icons.Rounded.Add, "More drop sets", enabled = dropSets < MaxPlannedDropSets) { onChange(dropSets + 1) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(AppColors.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) AppColors.TextPrimary else AppColors.TextTertiary, modifier = Modifier.size(18.dp))
    }
}
