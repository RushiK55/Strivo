package com.example.strivo.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.strivo.StrivoApp
import com.example.strivo.data.model.Exercise
import com.example.strivo.data.model.ExerciseSet
import com.example.strivo.data.model.PerformedExercise
import com.example.strivo.data.model.Plan
import com.example.strivo.data.model.WorkoutSession
import com.example.strivo.util.formatDuration
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** Index used for the rest that follows a whole exercise when it is finished by hand. */
private const val EXERCISE_REST_SLOT = 999

class Stopwatch {
    private var startedAt = 0L
    private var accumulated = 0L
    var isRunning = false
        private set

    val elapsedMillis: Long
        get() = accumulated + if (isRunning) SystemClock.elapsedRealtime() - startedAt else 0L

    fun start() {
        if (isRunning) return
        startedAt = SystemClock.elapsedRealtime()
        isRunning = true
    }

    fun stop() {
        if (!isRunning) return
        accumulated += SystemClock.elapsedRealtime() - startedAt
        isRunning = false
    }

    fun reset() {
        accumulated = 0L
        startedAt = SystemClock.elapsedRealtime()
    }
}

data class WorkoutState(
    val isLoading: Boolean = true,
    val exercises: List<Exercise> = emptyList(),
    val isWorkoutRunning: Boolean = false,
    /** The main button paused the workout: every clock (workout, set and rest) is frozen until it is resumed. */
    val isPaused: Boolean = false,
    val showRestTimer: Boolean = false,
    val isExerciseRest: Boolean = false,
    val activeSetExerciseIndex: Int? = null,
    val activeSetIndex: Int? = null,
    val activeRestExerciseIndex: Int? = null,
    val activeRestSetIndex: Int? = null,
    val expandedExercises: Set<Int> = emptySet(),
    /** What the last finished set was saved as, e.g. "Bench Press · Set 2: 40 kg × 10"; cleared when the next set starts. */
    val lastSavedSet: String? = null,
)

enum class MessageKind { Warning, Error }

sealed interface WorkoutEvent {
    data class Message(val text: String, val kind: MessageKind) : WorkoutEvent
    data class ShowSummary(val exerciseIndex: Int) : WorkoutEvent
    data object ShowComplete : WorkoutEvent

    /** Resuming after a pause: bring the full-screen set view back so the workout carries on from there. */
    data object FocusOnNextSet : WorkoutEvent
}

class WorkoutViewModel(
    application: Application,
    private val plan: Plan,
    private val initialExerciseIndex: Int = 0,
) : AndroidViewModel(application) {
    private val repository = (application as StrivoApp).repository

    private val workoutWatch = Stopwatch()
    private val restWatch = Stopwatch()
    private val setWatch = Stopwatch()
    private val saveMutex = Mutex()
    private var savedSessionId: Long? = null // set once the workout has been saved to history

    private val _state = MutableStateFlow(WorkoutState())
    val state: StateFlow<WorkoutState> = _state

    // The three clocks are separate flows so only the texts that show them recompose on every tick.
    private val _workoutTime = MutableStateFlow("00:00:00")
    val workoutTime: StateFlow<String> = _workoutTime
    private val _restTime = MutableStateFlow("00:00")
    val restTime: StateFlow<String> = _restTime
    private val _setTime = MutableStateFlow("00:00")
    val setTime: StateFlow<String> = _setTime

    private val eventChannel = Channel<WorkoutEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutEvent> = eventChannel.receiveAsFlow()

    init {
        load()
        viewModelScope.launch {
            while (isActive) {
                delay(100)
                if (workoutWatch.isRunning) _workoutTime.value = formatDuration(workoutWatch.elapsedMillis)
                if (restWatch.isRunning) _restTime.value = com.example.strivo.util.formatDurationMinSec(restWatch.elapsedMillis)
                if (setWatch.isRunning) _setTime.value = com.example.strivo.util.formatDurationMinSec(setWatch.elapsedMillis)
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val loaded = repository.readExercisesByPlan(plan.planId ?: -1L).map { exercise ->
                // A fresh exercise starts with the sets, reps and weight from the plan; they can be changed per set.
                if (exercise.setsList.isEmpty()) exercise.copy(setsList = plannedSets(exercise)) else exercise
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    exercises = loaded,
                    expandedExercises = if (initialExerciseIndex in loaded.indices) setOf(initialExerciseIndex) else emptySet(),
                )
            }
        }
    }

    // --- Workout clock ---

    /**
     * The main button. Before anything has happened it reads START and begins the first set that is still to do;
     * after that it is PAUSE (stops the clocks, including the running set's) and RESUME (starts them again).
     */
    fun startStopWorkout() {
        when {
            workoutWatch.isRunning -> pauseWorkout()
            workoutWatch.elapsedMillis > 0L -> resumeWorkout()
            else -> startFirstSet()
        }
    }

    /** Freezes every clock: the workout's, the running set's and the rest between sets or exercises. */
    private fun pauseWorkout() {
        workoutWatch.stop()
        setWatch.stop()
        restWatch.stop()
        syncWorkoutRunning()
    }

    /**
     * Unfreezes what was running and carries on: the set that was in progress continues, a rest that was running
     * continues (with the full-screen view back on the set that comes next); with neither, the next set starts.
     */
    private fun resumeWorkout() {
        val current = _state.value
        workoutWatch.start()
        when {
            current.activeSetIndex != null -> setWatch.start()
            current.showRestTimer -> restWatch.start()
        }
        syncWorkoutRunning()
        if (current.activeSetIndex != null || current.showRestTimer) {
            eventChannel.trySend(WorkoutEvent.FocusOnNextSet)
        } else {
            startFirstSet()
        }
    }

    private fun startFirstSet() {
        val exercises = _state.value.exercises
        val target = exercises.indices.firstNotNullOfOrNull { exerciseIndex ->
            val exercise = exercises[exerciseIndex]
            if (exercise.isCheck) return@firstNotNullOfOrNull null
            exercise.setsList.indexOfFirst { !it.isCompleted }.takeIf { it >= 0 }?.let { exerciseIndex to it }
        }
        if (target == null) {
            message("Everything in this plan is already done", MessageKind.Warning)
            return
        }
        // Starting the set also starts the workout clock (and checks the set has reps).
        onSetButton(target.first, target.second)
    }

    fun resetWorkout() {
        // Resetting a paused workout also drops what was frozen, so START begins cleanly.
        if (!workoutWatch.isRunning) {
            abandonActiveSet()
            stopRest()
        }
        workoutWatch.reset()
        _workoutTime.value = "00:00:00"
        syncWorkoutRunning()
    }

    private fun syncWorkoutRunning() {
        _state.update {
            it.copy(
                isWorkoutRunning = workoutWatch.isRunning,
                isPaused = !workoutWatch.isRunning && workoutWatch.elapsedMillis > 0L,
            )
        }
    }

    // --- Expansion ---

    fun toggleExpanded(index: Int) {
        _state.update {
            it.copy(expandedExercises = if (index in it.expandedExercises) it.expandedExercises - index else it.expandedExercises + index)
        }
    }

    private fun setExpanded(index: Int, expanded: Boolean) {
        _state.update {
            it.copy(expandedExercises = if (expanded) it.expandedExercises + index else it.expandedExercises - index)
        }
    }

    // --- Sets ---

    fun addSet(exerciseIndex: Int) {
        val exercise = _state.value.exercises.getOrNull(exerciseIndex) ?: return
        val last = exercise.setsList.lastOrNull()
        val newSet = ExerciseSet(weight = last?.weight ?: 0.0, reps = last?.reps ?: 0)
        commit(exerciseIndex, exercise.copy(setsList = exercise.setsList + newSet))
    }

    fun deleteSet(exerciseIndex: Int, setIndex: Int) {
        val exercise = _state.value.exercises.getOrNull(exerciseIndex) ?: return
        val set = exercise.setsList.getOrNull(setIndex) ?: return
        if (set.isCompleted || set.isStarted) return
        commit(exerciseIndex, exercise.copy(setsList = exercise.setsList.filterIndexed { i, _ -> i != setIndex }))
    }

    fun setWeight(exerciseIndex: Int, setIndex: Int, weight: Double) =
        updateSet(exerciseIndex, setIndex) { it.copy(weight = weight) }

    fun setReps(exerciseIndex: Int, setIndex: Int, reps: Int) =
        updateSet(exerciseIndex, setIndex) { it.copy(reps = reps) }

    private fun updateSet(exerciseIndex: Int, setIndex: Int, change: (ExerciseSet) -> ExerciseSet) {
        val exercise = _state.value.exercises.getOrNull(exerciseIndex) ?: return
        val set = exercise.setsList.getOrNull(setIndex) ?: return
        commit(exerciseIndex, exercise.copy(setsList = exercise.setsList.replaced(setIndex, change(set))))
    }

    /** The play / pause / check button on a set row. */
    fun onSetButton(exerciseIndex: Int, setIndex: Int) {
        // While paused nothing may start or finish; the tap just resumes the workout.
        if (_state.value.isPaused) {
            resumeWorkout()
            return
        }
        val current = _state.value
        val exercise = current.exercises.getOrNull(exerciseIndex) ?: return
        val set = exercise.setsList.getOrNull(setIndex) ?: return
        val isActive = current.activeSetExerciseIndex == exerciseIndex && current.activeSetIndex == setIndex
        val isLastSet = setIndex == exercise.setsList.lastIndex

        when {
            set.isCompleted -> {
                // Undo a completed set.
                var updated = exercise.copy(setsList = exercise.setsList.replaced(setIndex, set.copy(isCompleted = false)))
                if (isLastSet) updated = updated.copy(isCheck = false)
                commit(exerciseIndex, updated)
                stopRest()
            }

            isActive -> completeSet(exerciseIndex, exercise, setIndex, set, isLastSet)

            else -> startSet(exerciseIndex, exercise, setIndex, set)
        }
    }

    private fun startSet(exerciseIndex: Int, exercise: Exercise, setIndex: Int, set: ExerciseSet) {
        if (_state.value.activeSetIndex != null) {
            message("Please complete the current set before starting another one", MessageKind.Warning)
            return
        }
        // Weight may be 0 for bodyweight exercises; reps are always needed.
        if (set.reps <= 0) {
            message("Please enter Reps", MessageKind.Error)
            return
        }

        setWatch.reset()
        setWatch.start()
        _state.update { it.copy(activeSetExerciseIndex = exerciseIndex, activeSetIndex = setIndex, lastSavedSet = null) }
        commit(exerciseIndex, exercise.copy(setsList = exercise.setsList.replaced(setIndex, set.copy(isStarted = true))))
        stopRest()
        if (!workoutWatch.isRunning) workoutWatch.start()
        syncWorkoutRunning()
    }

    private fun completeSet(exerciseIndex: Int, exercise: Exercise, setIndex: Int, set: ExerciseSet, isLastSet: Boolean) {
        val duration = com.example.strivo.util.formatDurationMinSec(setWatch.elapsedMillis)
        setWatch.stop()
        val weightText = if (set.weight == 0.0) "bodyweight" else "${formatWeight(set.weight)} kg"
        _state.update {
            it.copy(
                activeSetIndex = null,
                activeSetExerciseIndex = null,
                lastSavedSet = "${exercise.name} · Set ${setIndex + 1}: $weightText × ${set.reps}",
            )
        }

        val completed = set.copy(isCompleted = true, isStarted = false, setDuration = duration)
        var updated = exercise.copy(setsList = exercise.setsList.replaced(setIndex, completed))

        if (!isLastSet) {
            commit(exerciseIndex, updated)
            triggerRest(exerciseIndex, setIndex, isExerciseRest = false)
            return
        }

        // Last set: the whole exercise is done.
        updated = updated.copy(isCheck = true)
        commit(exerciseIndex, updated)
        triggerRest(exerciseIndex, setIndex, isExerciseRest = true)
        setExpanded(exerciseIndex, false)

        val next = _state.value.exercises.indexOfFirst { !it.isCheck }
        if (next != -1) {
            setExpanded(next, true)
        } else {
            finishWorkout()
        }
    }

    // --- Finishing an exercise by hand ---

    fun requestFinishExercise(exerciseIndex: Int) {
        val exercise = _state.value.exercises.getOrNull(exerciseIndex) ?: return
        if (exercise.setsList.any { !it.isCompleted }) {
            message("Please complete all sets before finishing the exercise", MessageKind.Error)
            return
        }
        eventChannel.trySend(WorkoutEvent.ShowSummary(exerciseIndex))
    }

    fun confirmFinishExercise(exerciseIndex: Int) {
        stopRest()
        val exercises = _state.value.exercises
        val exercise = exercises.getOrNull(exerciseIndex) ?: return
        val isLastExercise = exercises.all { it.isCheck || it.id == exercise.id }

        commit(exerciseIndex, exercise.copy(isCheck = true))
        setExpanded(exerciseIndex, false)
        val next = _state.value.exercises.indices.firstOrNull { !_state.value.exercises[it].isCheck && it != exerciseIndex }
        if (next != null) setExpanded(next, true) else workoutWatch.stop()
        syncWorkoutRunning()
        triggerRest(exerciseIndex, EXERCISE_REST_SLOT, isExerciseRest = true)

        if (isLastExercise) finishWorkout()
    }

    private fun finishWorkout() {
        endWorkout()
        eventChannel.trySend(WorkoutEvent.ShowComplete)
    }

    /** True once at least one set has been completed, i.e. there is something worth saving. */
    fun hasProgress(): Boolean = _state.value.exercises.any { ex -> ex.setsList.any { it.isCompleted } }

    /** Ends the workout before every exercise is done (the plan couldn't be followed) and saves what was done. */
    fun finishEarly() {
        if (!hasProgress()) {
            message("Complete at least one set before finishing the workout", MessageKind.Warning)
            return
        }
        abandonActiveSet()
        stopRest()
        finishWorkout()
    }

    /** Saves the completed sets when the user leaves the screen mid-workout. */
    fun saveAndExit() {
        if (!hasProgress()) return
        abandonActiveSet()
        stopRest()
        endWorkout()
    }

    /** A set that was started but never completed goes back to "not started". */
    private fun abandonActiveSet() {
        val current = _state.value
        val exerciseIndex = current.activeSetExerciseIndex ?: return
        val setIndex = current.activeSetIndex ?: return
        setWatch.stop()
        _state.update { it.copy(activeSetExerciseIndex = null, activeSetIndex = null) }
        val exercise = current.exercises.getOrNull(exerciseIndex) ?: return
        val set = exercise.setsList.getOrNull(setIndex) ?: return
        commit(exerciseIndex, exercise.copy(setsList = exercise.setsList.replaced(setIndex, set.copy(isStarted = false))))
    }

    private fun endWorkout() {
        workoutWatch.stop()
        syncWorkoutRunning()
        val duration = formatDuration(workoutWatch.elapsedMillis)
        _workoutTime.value = duration
        saveCompletedWorkout(duration)
    }

    /** Only sets that were actually completed go into history; a re-finished workout updates its session. */
    private fun saveCompletedWorkout(duration: String) {
        val performed = _state.value.exercises.mapNotNull { exercise ->
            val done = exercise.setsList.filter { it.isCompleted }
            if (done.isEmpty()) null else PerformedExercise(name = exercise.name, sets = done)
        }
        if (performed.isEmpty()) return
        val session = WorkoutSession(
            planName = plan.planName,
            date = LocalDateTime.now(),
            totalTime = duration,
            exercises = performed,
        )
        // NonCancellable: leaving the screen clears this view model, and the save must still finish.
        viewModelScope.launch {
            withContext(NonCancellable) {
                saveMutex.withLock { savedSessionId = repository.saveSession(session, savedSessionId) }
            }
        }
    }

    // --- Rest ---

    private fun triggerRest(exerciseIndex: Int, setIndex: Int, isExerciseRest: Boolean) {
        restWatch.reset()
        restWatch.start()
        _state.update {
            it.copy(
                activeRestExerciseIndex = exerciseIndex,
                activeRestSetIndex = setIndex,
                showRestTimer = true,
                isExerciseRest = isExerciseRest,
            )
        }
    }

    fun stopRest() {
        val current = _state.value
        val exerciseIndex = current.activeRestExerciseIndex
        val setIndex = current.activeRestSetIndex
        if (exerciseIndex != null && setIndex != null) {
            current.exercises.getOrNull(exerciseIndex)?.let { exercise ->
                val rest = com.example.strivo.util.formatDurationMinSec(restWatch.elapsedMillis)
                val updated = when {
                    current.isExerciseRest -> exercise.copy(restTime = rest)
                    setIndex < exercise.setsList.size ->
                        exercise.copy(setsList = exercise.setsList.replaced(setIndex, exercise.setsList[setIndex].copy(restTime = rest)))
                    else -> exercise
                }
                commit(exerciseIndex, updated)
            }
        }
        restWatch.stop()
        _state.update {
            it.copy(
                showRestTimer = false,
                activeRestExerciseIndex = null,
                activeRestSetIndex = null,
                isExerciseRest = false,
            )
        }
    }

    // --- Persistence ---

    /**
     * Applies [exercise] to the screen state and saves it. Only the logged sets change; the exercise's own
     * sets / reps / weight stay as planned, so changing a set today never rewrites the plan.
     */
    private fun commit(exerciseIndex: Int, exercise: Exercise) {
        _state.update { it.copy(exercises = it.exercises.replaced(exerciseIndex, exercise)) }
        viewModelScope.launch { withContext(NonCancellable) { saveMutex.withLock { repository.updateExercise(exercise) } } }
    }

    private fun message(text: String, kind: MessageKind) {
        eventChannel.trySend(WorkoutEvent.Message(text, kind))
    }
}

/** The sets the plan asks for: `sets` copies of weight × reps (one empty set if the plan has none). */
private fun plannedSets(exercise: Exercise): List<ExerciseSet> {
    val count = (exercise.sets.toIntOrNull() ?: 0).coerceIn(1, 20)
    val weight = exercise.weight.toDoubleOrNull() ?: 0.0
    val reps = exercise.reps.toIntOrNull() ?: 0
    return List(count) { ExerciseSet(weight = weight, reps = reps) }
}

private fun <T> List<T>.replaced(index: Int, value: T): List<T> =
    mapIndexed { i, old -> if (i == index) value else old }

/** 40.0 -> "40", 42.5 -> "42.5". */
fun formatWeight(kg: Double): String = if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString()
