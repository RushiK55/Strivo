package com.example.strivo.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HistoryToggleOff
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.data.model.Exercise
import com.example.strivo.data.model.ExerciseSet
import com.example.strivo.data.model.Plan
import com.example.strivo.ui.components.DecimalWheelPicker
import com.example.strivo.ui.components.ScreenTopBar
import com.example.strivo.ui.components.StrivoAlertDialog
import com.example.strivo.ui.components.StrivoSnackbarHost
import com.example.strivo.ui.components.WheelPicker
import com.example.strivo.ui.components.showColored
import com.example.strivo.ui.theme.AppColors
import com.example.strivo.viewmodel.MessageKind
import com.example.strivo.viewmodel.WorkoutEvent
import com.example.strivo.viewmodel.WorkoutState
import com.example.strivo.viewmodel.WorkoutViewModel
import com.example.strivo.viewmodel.formatWeight

private const val ZeroTime = "00:00:00"
private const val ZeroTimeMinSec = "00:00"

private sealed interface Picker {
    data class Weight(val exerciseIndex: Int, val setIndex: Int, val initial: Double) : Picker
    data class Reps(val exerciseIndex: Int, val setIndex: Int, val initial: Int) : Picker
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    plan: Plan,
    viewModel: WorkoutViewModel,
    onBack: () -> Unit,
    onFinishWorkout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    var picker by remember { mutableStateOf<Picker?>(null) }
    var summaryExerciseIndex by remember { mutableStateOf<Int?>(null) }
    var showComplete by remember { mutableStateOf(false) }
    var workoutFinished by remember { mutableStateOf(false) }
    var focusOpen by remember { mutableStateOf(false) }
    var showFinishEarly by remember { mutableStateOf(false) }
    var showLeave by remember { mutableStateOf(false) }
    val hasProgress = state.exercises.any { ex -> ex.setsList.any { it.isCompleted } }

    // Leaving mid-workout asks first, so completed sets are not lost.
    val requestLeave = { if (hasProgress && !workoutFinished) showLeave = true else onBack() }
    BackHandler(onBack = requestLeave)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkoutEvent.Message -> snackbarHost.showColored(
                    event.text,
                    if (event.kind == MessageKind.Warning) AppColors.Warning else AppColors.Danger,
                )

                is WorkoutEvent.ShowSummary -> summaryExerciseIndex = event.exerciseIndex
                WorkoutEvent.FocusOnNextSet -> focusOpen = true

                WorkoutEvent.ShowComplete -> {
                    workoutFinished = true
                    showComplete = true
                }
            }
        }
    }

    // Focus mode: once a set is started the list is blurred and one big button drives the workout -
    // STOP while a set runs, then START for the next set - until the user closes it or the workout ends.
    val isSetActive = state.activeSetIndex != null
    LaunchedEffect(isSetActive) { if (isSetActive) focusOpen = true }
    LaunchedEffect(workoutFinished) { if (workoutFinished) focusOpen = false }

    val focus = focusTarget(state)
    var lastFocus by remember { mutableStateOf<FocusInfo?>(null) }
    if (focus != null) lastFocus = focus
    val focusVisible = focusOpen && focus != null
    val blurRadius by animateDpAsState(if (focusVisible) 18.dp else 0.dp, tween(350), label = "focusBlur")

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.blur(blurRadius),
        containerColor = AppColors.Background,
        topBar = { ScreenTopBar(title = plan.planName, onBack = requestLeave, fontSize = 20.sp) },
        snackbarHost = { StrivoSnackbarHost(snackbarHost) },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Accent)
            }

            state.exercises.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No exercises in this plan", color = AppColors.TextSecondary)
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 40.dp),
            ) {
                item {
                    WorkoutTimerHeader(
                        viewModel = viewModel,
                        isRunning = state.isWorkoutRunning,
                        modifier = Modifier.padding(top = 20.dp, bottom = 24.dp),
                    )
                }
                if (state.showRestTimer && !state.isExerciseRest) {
                    item {
                        GlobalRestIndicator(viewModel)
                    }
                }
                itemsIndexed(state.exercises, key = { _, e -> e.id ?: e.hashCode().toLong() }) { index, exercise ->
                    Column {
                        ExerciseItem(
                            exerciseIndex = index,
                            exercise = exercise,
                            viewModel = viewModel,
                            onPickWeight = { setIndex, set -> picker = Picker.Weight(index, setIndex, set.weight) },
                            onPickReps = { setIndex, set -> picker = Picker.Reps(index, setIndex, if (set.reps == 0) 10 else set.reps) },
                        )
                        Spacer(Modifier.height(12.dp))
                        PostExerciseRest(index, exercise, viewModel)
                        Spacer(Modifier.height(12.dp))
                    }
                }
                if (hasProgress && !workoutFinished) {
                    item {
                        Button(
                            onClick = { showFinishEarly = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Surface, contentColor = AppColors.Accent),
                            border = BorderStroke(1.dp, AppColors.Accent.copy(alpha = 0.4f)),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                        ) {
                            Text("FINISH WORKOUT", fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                        }
                    }
                }
            }
        }
    }

    FocusOverlay(visible = focusVisible, info = lastFocus, viewModel = viewModel, onClose = { focusOpen = false })
    }

    if (showFinishEarly) {
        val done = state.exercises.count { ex -> ex.setsList.any { it.isCompleted } }
        StrivoAlertDialog(
            title = "Finish workout?",
            onDismiss = { showFinishEarly = false },
            confirmText = "Finish",
            onConfirm = {
                showFinishEarly = false
                viewModel.finishEarly()
            },
        ) {
            Text(
                "You've worked on $done of ${state.exercises.size} exercises. Only the sets you completed are saved.",
                color = AppColors.TextSecondary,
            )
        }
    }

    if (showLeave) {
        StrivoAlertDialog(
            title = "Leave workout?",
            onDismiss = { showLeave = false },
            confirmText = "Save & Exit",
            dismissText = "Stay",
            onConfirm = {
                showLeave = false
                viewModel.saveAndExit()
                onBack()
            },
        ) {
            Text("Your completed sets will be saved to history.", color = AppColors.TextSecondary)
        }
    }

    // --- Pickers ---

    when (val current = picker) {
        is Picker.Weight -> WeightPickerDialog(
            initial = current.initial,
            onDismiss = { picker = null },
            onSelected = { weight ->
                viewModel.setWeight(current.exerciseIndex, current.setIndex, weight)
                picker = null
            },
        )

        is Picker.Reps -> RepsPickerDialog(
            initial = current.initial,
            onDismiss = { picker = null },
            onSelected = { reps ->
                viewModel.setReps(current.exerciseIndex, current.setIndex, reps)
                picker = null
            },
        )

        null -> Unit
    }

    // --- Finish-exercise summary ---

    summaryExerciseIndex?.let { index ->
        val exercise = state.exercises.getOrNull(index)
        if (exercise != null) {
            ModalBottomSheet(
                onDismissRequest = { summaryExerciseIndex = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = AppColors.Surface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                SummarySheetContent(
                    exercise = exercise,
                    viewModel = viewModel,
                    onAddSet = {
                        summaryExerciseIndex = null
                        viewModel.addSet(index)
                    },
                    onConfirm = {
                        summaryExerciseIndex = null
                        viewModel.confirmFinishExercise(index)
                    },
                )
            }
        }
    }

    // --- Workout complete ---

    if (showComplete) {
        ModalBottomSheet(
            onDismissRequest = { showComplete = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AppColors.Surface,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        ) {
            CompleteSheetContent(
                planName = plan.planName,
                viewModel = viewModel,
                exerciseCount = state.exercises.size,
                onFinish = {
                    showComplete = false
                    onFinishWorkout()
                },
            )
        }
    }
}

// --- Timer header ---

@Composable
private fun WorkoutTimerHeader(viewModel: WorkoutViewModel, isRunning: Boolean, modifier: Modifier = Modifier) {
    val workoutTime by viewModel.workoutTime.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(30.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Surface)
            .border(BorderStroke(1.dp, AppColors.Field), shape)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(AppColors.Accent.copy(alpha = 0.1f))
                    .padding(6.dp)
            ) {
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "TOTAL DURATION",
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = workoutTime,
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            color = AppColors.TextPrimary,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppColors.Field)
                    .clickable(onClick = viewModel::resetWorkout)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("RESET", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            }
            val buttonShape = RoundedCornerShape(20.dp)
            Row(
                modifier = Modifier
                    .weight(2f)
                    .then(
                        if (!isRunning) {
                            Modifier.shadow(
                                elevation = 8.dp,
                                shape = buttonShape,
                                ambientColor = AppColors.Accent.copy(alpha = 0.2f),
                                spotColor = AppColors.Accent.copy(alpha = 0.2f),
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clip(buttonShape)
                    .background(if (isRunning) AppColors.WarningAccent else AppColors.Accent)
                    .clickable(onClick = viewModel::startStopWorkout)
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        isRunning -> "PAUSE"
                        workoutTime != ZeroTime -> "RESUME"
                        else -> "START"
                    },
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    letterSpacing = 1.2.sp,
                )
            }
        }
    }
}

@Composable
private fun GlobalRestIndicator(viewModel: WorkoutViewModel) {
    val restTime by viewModel.restTime.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Warning.copy(alpha = 0.1f))
            .border(BorderStroke(1.dp, AppColors.Warning.copy(alpha = 0.3f)), shape)
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = AppColors.Warning, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text("RESTING BEFORE NEXT SET: $restTime", color = AppColors.Warning, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = viewModel::stopRest) { Text("SKIP", color = AppColors.Warning) }
    }
}

@Composable
private fun PostExerciseRest(index: Int, exercise: Exercise, viewModel: WorkoutViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isLive = state.showRestTimer && state.isExerciseRest && state.activeRestExerciseIndex == index

    if (isLive) {
        val restTime by viewModel.restTime.collectAsStateWithLifecycle()
        val shape = RoundedCornerShape(20.dp)
        Row(
            modifier = Modifier
                .padding(bottom = 12.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(AppColors.Accent.copy(alpha = 0.1f))
                .border(BorderStroke(1.dp, AppColors.Accent.copy(alpha = 0.2f)), shape)
                .padding(vertical = 12.dp, horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Timer, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("RESTING BEFORE NEXT EXERCISE", color = AppColors.Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(restTime, color = AppColors.Accent, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
    } else if (exercise.isCheck && exercise.restTime.isNotEmpty() && exercise.restTime != ZeroTimeMinSec) {
        Row(
            modifier = Modifier
                .padding(bottom = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(AppColors.Surface)
                .padding(vertical = 12.dp, horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.History, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("REST TAKEN", color = AppColors.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(exercise.restTime, color = AppColors.TextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
    }
}

// --- Exercise card ---

@Composable
private fun ExerciseItem(
    exerciseIndex: Int,
    exercise: Exercise,
    viewModel: WorkoutViewModel,
    onPickWeight: (Int, ExerciseSet) -> Unit,
    onPickReps: (Int, ExerciseSet) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val expanded = exerciseIndex in state.expandedExercises
    val completedSets = exercise.setsList.count { it.isCompleted }

    Column(
        modifier = Modifier
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(25.dp))
            .background(AppColors.Surface)
            .animateContentSize(tween(250)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.toggleExpanded(exerciseIndex) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (exercise.isCheck) AppColors.Accent.copy(alpha = 0.2f) else AppColors.Field)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = if (exercise.isCheck) Icons.Rounded.Check else Icons.Rounded.FitnessCenter,
                    contentDescription = null,
                    tint = if (exercise.isCheck) AppColors.Accent else AppColors.TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textDecoration = if (exercise.isCheck) TextDecoration.LineThrough else null,
                    color = if (exercise.isCheck) AppColors.TextSecondary else AppColors.TextPrimary,
                )
                Text(
                    text = "$completedSets / ${exercise.setsList.size} Sets Completed",
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = if (expanded) AppColors.Accent else AppColors.TextSecondary,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(250)) + fadeOut(tween(150)),
        ) {
            Column(Modifier.padding(16.dp)) {
                val plannedSets = exercise.sets.toIntOrNull() ?: 0
                val plannedReps = exercise.reps.toIntOrNull() ?: 0
                val plannedWeight = exercise.weight.toDoubleOrNull() ?: 0.0
                if (plannedSets > 0 && plannedReps > 0) {
                    Text(
                        text = "PLAN: $plannedSets × $plannedReps" + if (plannedWeight > 0) " @ $plannedWeight KG" else "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Accent,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = "Tap a weight or reps value to change it for today, or add / remove sets.",
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                    )
                }
                exercise.setsList.forEachIndexed { setIndex, set ->
                    SetRow(
                        exerciseIndex = exerciseIndex,
                        setIndex = setIndex,
                        set = set,
                        viewModel = viewModel,
                        onPickWeight = { onPickWeight(setIndex, set) },
                        onPickReps = { onPickReps(setIndex, set) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { viewModel.addSet(exerciseIndex) }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Set", color = AppColors.Accent)
                }
                Spacer(Modifier.height(16.dp))
                if (!exercise.isCheck && exercise.setsList.any { it.isCompleted }) {
                    Button(
                        onClick = { viewModel.requestFinishExercise(exerciseIndex) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = Color.Black),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                    ) {
                        Text("FINISH EXERCISE", fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    }
                }
            }
        }
    }
}

// --- Set row ---

@Composable
private fun SetRow(
    exerciseIndex: Int,
    setIndex: Int,
    set: ExerciseSet,
    viewModel: WorkoutViewModel,
    onPickWeight: () -> Unit,
    onPickReps: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isActive = state.activeSetExerciseIndex == exerciseIndex && state.activeSetIndex == setIndex
    val isResting = state.showRestTimer &&
        state.activeRestExerciseIndex == exerciseIndex &&
        state.activeRestSetIndex == setIndex
    val hasDuration = set.setDuration.isNotEmpty() && set.setDuration != ZeroTimeMinSec
    val shape = RoundedCornerShape(15.dp)

    Column {
        Column(
            modifier = Modifier
                .padding(bottom = 12.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(if (set.isCompleted) AppColors.Accent.copy(alpha = 0.05f) else AppColors.Field)
                .border(
                    BorderStroke(1.dp, if (set.isCompleted) AppColors.Accent.copy(alpha = 0.2f) else Color.Transparent),
                    shape,
                )
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (set.isCompleted) AppColors.Accent else AppColors.Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${setIndex + 1}",
                        fontSize = 10.sp,
                        color = if (set.isCompleted) Color.Black else AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                MetricField(
                    label = "KG",
                    value = if (set.weight == 0.0) (if (set.reps > 0) "BW" else "---") else set.weight.toString(),
                    onClick = onPickWeight,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                MetricField(
                    label = "REPS",
                    value = if (set.reps == 0) "---" else set.reps.toString(),
                    onClick = onPickReps,
                    modifier = Modifier.weight(1f),
                )
                if (!set.isCompleted) {
                    IconButton(onClick = { viewModel.deleteSet(exerciseIndex, setIndex) }) {
                        Icon(
                            Icons.Outlined.RemoveCircleOutline,
                            contentDescription = "Delete set",
                            tint = AppColors.Danger,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                IconButton(onClick = { viewModel.onSetButton(exerciseIndex, setIndex) }) {
                    Icon(
                        imageVector = when {
                            set.isCompleted -> Icons.Rounded.CheckCircle
                            isActive -> Icons.Filled.PauseCircleFilled
                            else -> Icons.Outlined.PlayCircleOutline
                        },
                        contentDescription = null,
                        tint = when {
                            set.isCompleted -> AppColors.Accent
                            isActive -> AppColors.WarningAccent
                            else -> AppColors.TextSecondary
                        },
                    )
                }
            }
            if (isActive || hasDuration) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Rounded.Timer else Icons.Rounded.HistoryToggleOff,
                        contentDescription = null,
                        tint = if (isActive) AppColors.WarningAccent else AppColors.TextSecondary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    if (isActive) {
                        val setTime by viewModel.setTime.collectAsStateWithLifecycle()
                        Text("Live: $setTime", fontSize = 10.sp, color = AppColors.WarningAccent, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Took: ${set.setDuration}", fontSize = 10.sp, color = AppColors.TextSecondary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (isResting) {
            RestTimeRow(time = null, viewModel = viewModel)
        } else if (set.isCompleted && set.restTime.isNotEmpty() && set.restTime != ZeroTimeMinSec) {
            RestTimeRow(time = set.restTime, viewModel = viewModel)
        }
    }
}

@Composable
private fun MetricField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .drawUnderline(AppColors.SetBorder)
            .padding(vertical = 8.dp),
    ) {
        Text(label, color = AppColors.TextSecondary, fontSize = 10.sp)
        Text(value, color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

private fun Modifier.drawUnderline(color: Color): Modifier = this.drawBehind {
    val y = size.height - 0.5.dp.toPx()
    drawLine(color, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
}

@Composable
private fun RestTimeRow(time: String?, viewModel: WorkoutViewModel) {
    val isLive = time == null
    val color = if (isLive) AppColors.WarningAccent else AppColors.TextSecondary
    Row(
        modifier = Modifier.padding(start = 40.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.HourglassEmpty, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isLive) "Resting before next set..." else "Rest: $time",
            fontSize = 12.sp,
            color = color,
            fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal,
        )
        if (isLive) {
            Spacer(Modifier.width(8.dp))
            val restTime by viewModel.restTime.collectAsStateWithLifecycle()
            Text(restTime, fontSize = 12.sp, color = AppColors.WarningAccent, fontWeight = FontWeight.Bold)
        }
    }
}

// --- Dialogs & sheets ---

@Composable
private fun WeightPickerDialog(initial: Double, onDismiss: () -> Unit, onSelected: (Double) -> Unit) {
    var weight by remember { mutableStateOf(initial) }
    PickerDialog(title = "Set Weight", onDismiss = onDismiss, onSet = { onSelected(weight) }) {
        DecimalWheelPicker(label = "KG", minValue = 0, maxValue = 500, value = weight, onValueChange = { weight = it })
    }
}

@Composable
private fun RepsPickerDialog(initial: Int, onDismiss: () -> Unit, onSelected: (Int) -> Unit) {
    var reps by remember { mutableStateOf(initial) }
    PickerDialog(title = "Set Reps", onDismiss = onDismiss, onSet = { onSelected(reps) }) {
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
        shape = RoundedCornerShape(25.dp),
        title = { Text(title, color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = AppColors.TextSecondary) } },
        confirmButton = { TextButton(onClick = onSet) { Text("SET", color = AppColors.Accent, fontWeight = FontWeight.Bold) } },
    )
}

@Composable
private fun SummarySheetContent(
    exercise: Exercise,
    viewModel: WorkoutViewModel,
    onAddSet: () -> Unit,
    onConfirm: () -> Unit,
) {
    val workoutTime by viewModel.workoutTime.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Complete ${exercise.name}?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text("Workout duration: $workoutTime", color = AppColors.TextSecondary)
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onAddSet,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, AppColors.Accent),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = AppColors.Accent)
                Spacer(Modifier.width(8.dp))
                Text("ADD SET", color = AppColors.Accent, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(2f),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = Color.Black),
                contentPadding = PaddingValues(vertical = 16.dp),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            ) {
                Text("CONFIRM & SAVE", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CompleteSheetContent(planName: String, viewModel: WorkoutViewModel, exerciseCount: Int, onFinish: () -> Unit) {
    val duration by viewModel.workoutTime.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(AppColors.Accent.copy(alpha = 0.1f))
                .padding(20.dp)
        ) {
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("WORKOUT COMPLETE!", fontSize = 24.sp, fontWeight = FontWeight.Black, color = AppColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "You've smashed your '$planName' session.",
            textAlign = TextAlign.Center,
            color = AppColors.TextSecondary,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("Duration", duration, Icons.Outlined.Timer)
            StatItem("Exercises", exerciseCount.toString(), Icons.Rounded.FitnessCenter)
        }
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = Color.Black),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        ) {
            Text("FINISH WORKOUT", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = AppColors.TextPrimary)
        Text(label, color = AppColors.TextSecondary, fontSize = 12.sp)
    }
}

// --- Focus mode ---

private data class FocusInfo(
    val exerciseIndex: Int,
    val setIndex: Int,
    val exerciseName: String,
    val setCount: Int,
    val weight: Double,
    val reps: Int,
    val isRunning: Boolean,
)

/** The set the focus screen is about: the running one, otherwise the next set still to do. */
private fun focusTarget(state: WorkoutState): FocusInfo? {
    val activeExercise = state.activeSetExerciseIndex
    val activeSet = state.activeSetIndex
    if (activeExercise != null && activeSet != null) {
        val exercise = state.exercises.getOrNull(activeExercise)
        val set = exercise?.setsList?.getOrNull(activeSet)
        if (exercise != null && set != null) {
            return FocusInfo(activeExercise, activeSet, exercise.name, exercise.setsList.size, set.weight, set.reps, isRunning = true)
        }
    }
    state.exercises.forEachIndexed { exerciseIndex, exercise ->
        if (exercise.isCheck) return@forEachIndexed
        val setIndex = exercise.setsList.indexOfFirst { !it.isCompleted }
        if (setIndex >= 0) {
            val set = exercise.setsList[setIndex]
            return FocusInfo(exerciseIndex, setIndex, exercise.name, exercise.setsList.size, set.weight, set.reps, isRunning = false)
        }
    }
    return null
}

/**
 * Full-screen focus view over the blurred workout list. One big round button: START for the next set
 * (accent), STOP while a set runs (orange). The list only shows again when the user closes this view.
 */
@Composable
private fun FocusOverlay(visible: Boolean, info: FocusInfo?, viewModel: WorkoutViewModel, onClose: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(300)), exit = fadeOut(tween(250))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                // Swallow touches so nothing behind the overlay can be tapped.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        )
    }
    AnimatedVisibility(
        visible = visible && info != null,
        modifier = Modifier.fillMaxSize(),
        enter = scaleIn(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            initialScale = 0.15f,
        ) + fadeIn(tween(200)),
        exit = scaleOut(tween(250), targetScale = 0.15f) + fadeOut(tween(200)),
    ) {
        if (info == null) return@AnimatedVisibility
        val setTime by viewModel.setTime.collectAsStateWithLifecycle()
        val restTime by viewModel.restTime.collectAsStateWithLifecycle()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val canStart = info.isRunning || info.reps > 0
        val isResting = state.showRestTimer && !info.isRunning
        val paused = state.isPaused
        val restingBetweenExercises = isResting && state.isExerciseRest
        var editing by remember { mutableStateOf<MetricEdit?>(null) }

        val buttonColor by animateColorAsState(
            when {
                paused -> AppColors.Accent
                info.isRunning -> AppColors.WarningAccent
                canStart -> AppColors.Accent
                else -> AppColors.Field
            },
            tween(300),
            label = "focusColor",
        )

        // The button pops (shrinks, then springs back) every time it changes from START to STOP or to the next set.
        val pop = remember { Animatable(1f) }
        LaunchedEffect(info.isRunning, info.exerciseIndex, info.setIndex) {
            pop.snapTo(0.8f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
        val pulse by rememberInfiniteTransition(label = "setPulse").animateFloat(
            initialValue = 1f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
            label = "setPulseScale",
        )

        // The same wheel pickers as in the exercise list, opened by tapping the weight or reps.
        when (editing) {
            MetricEdit.Weight -> WeightPickerDialog(
                initial = info.weight,
                onDismiss = { editing = null },
                onSelected = { viewModel.setWeight(info.exerciseIndex, info.setIndex, it); editing = null },
            )

            MetricEdit.Reps -> RepsPickerDialog(
                initial = if (info.reps == 0) 10 else info.reps,
                onDismiss = { editing = null },
                onSelected = { viewModel.setReps(info.exerciseIndex, info.setIndex, it); editing = null },
            )

            null -> Unit
        }

        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Show exercise list", tint = AppColors.TextPrimary)
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    info.exerciseName,
                    color = AppColors.TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (restingBetweenExercises) "UP NEXT · SET ${info.setIndex + 1} OF ${info.setCount}" else "SET ${info.setIndex + 1} OF ${info.setCount}",
                    color = AppColors.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(6.dp))
                Spacer(Modifier.height(20.dp))
                // The weight and reps of this set. The set can differ from the plan: tap a value to pick it on the wheel,
                // or nudge it with the round buttons. Every change is saved at once, and stopping the set saves it.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricStepper(
                        label = "KG",
                        onEdit = { editing = MetricEdit.Weight },
                        value = if (info.weight == 0.0) "BW" else formatWeight(info.weight),
                        onMinus = { viewModel.setWeight(info.exerciseIndex, info.setIndex, (info.weight - WeightStep).coerceAtLeast(0.0)) },
                        onPlus = { viewModel.setWeight(info.exerciseIndex, info.setIndex, info.weight + WeightStep) },
                        modifier = Modifier.weight(1f),
                    )
                    MetricStepper(
                        label = "REPS",
                        onEdit = { editing = MetricEdit.Reps },
                        value = if (info.reps > 0) info.reps.toString() else "--",
                        onMinus = { viewModel.setReps(info.exerciseIndex, info.setIndex, (info.reps - 1).coerceAtLeast(0)) },
                        onPlus = { viewModel.setReps(info.exerciseIndex, info.setIndex, info.reps + 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
                val saved = state.lastSavedSet
                if (!info.isRunning && saved != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "✓ Saved: $saved",
                        color = AppColors.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(24.dp))
                // What the clock below is counting: rest between sets or between exercises, or a paused workout.
                val badge = when {
                    paused -> "PAUSED"
                    restingBetweenExercises -> "EXERCISE REST"
                    isResting -> "REST BETWEEN SETS"
                    else -> null
                }
                if (badge != null) {
                    val badgeColor = if (paused) AppColors.Warning else AppColors.Accent
                    Text(
                        text = badge,
                        color = badgeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    text = when {
                        info.isRunning -> setTime
                        isResting -> restTime
                        else -> "READY"
                    },
                    color = if (info.isRunning || isResting) AppColors.TextPrimary else AppColors.TextSecondary,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                Text(
                    text = when {
                        info.isRunning -> "SET TIME"
                        isResting -> if (state.isExerciseRest) "REST BEFORE NEXT EXERCISE" else "REST BEFORE NEXT SET"
                        else -> "GET SET"
                    },
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(40.dp))
                Box(contentAlignment = Alignment.Center) {
                    if (info.isRunning && !paused) {
                        // Soft ring that keeps growing outwards and fading while the set runs.
                        Box(
                            Modifier
                                .size(200.dp)
                                .scale(pulse)
                                .alpha((1.35f - pulse) / 0.35f * 0.4f)
                                .border(BorderStroke(3.dp, AppColors.WarningAccent), CircleShape),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .scale(pop.value)
                            .shadow(
                                elevation = if (canStart) 24.dp else 0.dp,
                                shape = CircleShape,
                                ambientColor = buttonColor,
                                spotColor = buttonColor,
                            )
                            .clip(CircleShape)
                            .background(buttonColor)
                            .clickable {
                                // A set without reps cannot start: send the user back to the list to fill it in.
                                when {
                                    paused -> viewModel.startStopWorkout() // resume everything that was frozen
                                    canStart -> viewModel.onSetButton(info.exerciseIndex, info.setIndex)
                                    else -> onClose()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (canStart || paused) {
                            Icon(
                                imageVector = if (info.isRunning && !paused) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                contentDescription = when {
                                    paused -> "Resume"
                                    info.isRunning -> "Finish set"
                                    else -> "Start set"
                                },
                                tint = Color.Black,
                                modifier = Modifier.size(104.dp),
                            )
                        } else {
                            Text("SET REPS", color = AppColors.TextPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.5.sp)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = when {
                        paused -> "TAP TO RESUME"
                        info.isRunning -> "TAP TO FINISH SET"
                        canStart -> "TAP TO START SET"
                        else -> "TAP TO OPEN THE LIST"
                    },
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                // The same pause as the main button on the list: it freezes the workout, set and rest clocks together.
                if (state.isWorkoutRunning) {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(AppColors.Surface.copy(alpha = 0.85f))
                            .clickable { viewModel.startStopWorkout() }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Pause, contentDescription = null, tint = AppColors.Warning, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("PAUSE WORKOUT", color = AppColors.Warning, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

private const val WeightStep = 2.5

private enum class MetricEdit { Weight, Reps }

/**
 * The weight or reps of the current set, big, in the style of the rest of the app: tap the value to pick it on the
 * same wheel as in the exercise list, or nudge it with the round buttons (accent "+" like the add buttons elsewhere).
 */
@Composable
private fun MetricStepper(
    label: String,
    value: String,
    onEdit: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(AppColors.Surface)
            .border(BorderStroke(1.dp, AppColors.Field), shape)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(Icons.Rounded.Remove, "Decrease $label", onMinus, accent = false)
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit)
                .drawUnderline(AppColors.SetBorder)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, color = AppColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text(value, color = AppColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
        StepperButton(Icons.Rounded.Add, "Increase $label", onPlus, accent = true)
    }
}

/** Round buttons like the ones used across the app: the "+" is accent-filled, the "−" is the soft grey. */
@Composable
private fun StepperButton(icon: ImageVector, description: String, onClick: () -> Unit, accent: Boolean) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (accent) AppColors.Accent else AppColors.Field)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (accent) Color.Black else AppColors.TextPrimary, modifier = Modifier.size(20.dp))
    }
}
