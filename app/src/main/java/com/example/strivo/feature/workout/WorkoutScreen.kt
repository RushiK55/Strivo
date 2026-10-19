package com.example.strivo.feature.workout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.domain.coach.exerciseKey
import com.example.strivo.core.ui.components.ScreenTopBar
import com.example.strivo.core.ui.components.SoftButton
import com.example.strivo.core.ui.components.StrivoAlertDialog
import com.example.strivo.core.ui.components.StrivoSnackbarHost
import com.example.strivo.core.ui.components.color
import com.example.strivo.core.ui.components.showColored
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.domain.model.Plan

internal const val ZeroTime = "00:00:00"
internal const val ZeroTimeMinSec = "00:00"

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
    // The exercise whose notes and details are open (its index in the workout), or none.
    var infoFor by remember { mutableStateOf<Int?>(null) }
    // The exercise being logged after the fact (done without the timer), by its index in the workout, or none.
    var logFor by remember { mutableStateOf<Int?>(null) }
    val library by viewModel.library.collectAsStateWithLifecycle()
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
                    when (event.kind) {
                        MessageKind.Info -> null
                        MessageKind.Warning -> AppColors.Warning
                        MessageKind.Error -> AppColors.Danger
                    },
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

    // The phone's screen timeout would blank the screen mid-set. While the full-screen timer view is open the screen
    // stays on; as soon as it closes (or the workout screen is left) the normal timeout applies again.
    val view = LocalView.current
    DisposableEffect(focusVisible) {
        view.keepScreenOn = focusVisible
        onDispose { view.keepScreenOn = false }
    }
    val blurRadius by animateDpAsState(if (focusVisible) 18.dp else 0.dp, tween(350), label = "focusBlur")

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.blur(blurRadius),
        containerColor = AppColors.Background,
        topBar = { ScreenTopBar(title = plan.planName, onBack = requestLeave) },
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            ) {
                item {
                    WorkoutTimerHeader(
                        viewModel = viewModel,
                        isRunning = state.isWorkoutRunning,
                        isPaused = state.isPaused,
                        isResting = state.showRestTimer && state.activeSetIndex == null && !state.isPaused,
                        doneSets = state.exercises.sumOf { ex -> ex.setsList.count { it.isCompleted } },
                        totalSets = state.exercises.sumOf { it.setsList.size },
                        doneExercises = state.exercises.count { it.isCheck },
                        totalExercises = state.exercises.size,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                        finished = state.isFinished,
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
                            onInfo = { infoFor = index },
                            onLog = { logFor = index },
                        )
                        PostExerciseRest(index, exercise, viewModel)
                    }
                }
                if (hasProgress && !workoutFinished) {
                    item {
                        SoftButton(
                            text = "Finish workout",
                            onClick = { showFinishEarly = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            icon = Icons.Rounded.Flag,
                            contentColor = AppColors.Accent,
                        )
                    }
                }
            }
        }
    }

    FocusOverlay(visible = focusVisible, info = lastFocus, viewModel = viewModel, onClose = { focusOpen = false }, onInfo = { infoFor = it })
    }

    infoFor?.let { index ->
        state.exercises.getOrNull(index)?.let { exercise ->
            ExerciseInfoSheet(
                exercise = exercise,
                details = library.firstOrNull { exerciseKey(it.name) == exerciseKey(exercise.name) },
                onDismiss = { infoFor = null },
            )
        }
    }

    logFor?.let { index ->
        state.exercises.getOrNull(index)?.let { exercise ->
            LogExerciseSheet(
                exercise = exercise,
                onDismiss = { logFor = null },
                onSave = { sets ->
                    logFor = null
                    viewModel.logExercise(index, sets)
                },
            )
        }
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
