package com.example.strivo.feature.workout

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.strivo.StrivoApp
import com.example.strivo.core.util.formatDuration
import com.example.strivo.core.util.formatWeight
import com.example.strivo.domain.analytics.exerciseHistories
import com.example.strivo.domain.coach.Advice
import com.example.strivo.domain.coach.exerciseKey
import com.example.strivo.domain.coach.progressionAdvice
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.LibraryExercise
import com.example.strivo.domain.model.ExerciseSet
import com.example.strivo.domain.model.PerformedExercise
import com.example.strivo.domain.model.Plan
import com.example.strivo.domain.model.SetType
import com.example.strivo.domain.model.WorkoutSession
import com.example.strivo.domain.model.blockingSetBefore
import com.example.strivo.domain.model.countsForRecords
import com.example.strivo.domain.model.isWork
import com.example.strivo.domain.model.plannedSets
import com.example.strivo.domain.model.setName
import com.example.strivo.domain.model.suggestedDropKg
import com.example.strivo.domain.model.suggestedWarmUpKg
import com.example.strivo.domain.plan.nextSet
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
import java.time.LocalDate
import java.time.LocalDateTime

/** Index used for the rest that follows a whole exercise when it is finished by hand. */
private const val EXERCISE_REST_SLOT = 999

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

    /** The exercise library, for the words about an exercise (how to do it, the tip, the muscles). */
    private val _library = MutableStateFlow<List<LibraryExercise>>(emptyList())
    val library: StateFlow<List<LibraryExercise>> = _library

    private val eventChannel = Channel<WorkoutEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutEvent> = eventChannel.receiveAsFlow()

    /** The coach's advice for this plan's exercises, by lower-case name. Advice used or dismissed here is dropped. */
    private val _advice = MutableStateFlow<Map<String, Advice>>(emptyMap())
    val advice: StateFlow<Map<String, Advice>> = _advice

    init {
        load()
        viewModelScope.launch {
            while (isActive) {
                delay(100)
                if (workoutWatch.isRunning) _workoutTime.value = formatDuration(workoutWatch.elapsedMillis)
                if (restWatch.isRunning) _restTime.value = com.example.strivo.core.util.formatDurationMinSec(restWatch.elapsedMillis)
                if (setWatch.isRunning) _setTime.value = com.example.strivo.core.util.formatDurationMinSec(setWatch.elapsedMillis)
            }
        }
    }

    private fun load() {
        viewModelScope.launch { _library.value = repository.readLibrary() }
        viewModelScope.launch {
            // This workout is not saved yet, so the advice comes from earlier ones only.
            _advice.value = exerciseHistories(repository.getWorkoutSessions())
                .mapNotNull { progressionAdvice(it, LocalDate.now()) }
                .associateBy { exerciseKey(it.exercise.orEmpty()) }
        }
        viewModelScope.launch {
            val loaded = repository.readExercisesByPlan(plan.planId ?: -1L).map { exercise ->
                // A fresh exercise starts with the sets, reps and weight from the plan; they can be changed per set.
                if (exercise.setsList.isEmpty()) exercise.copy(setsList = exercise.plannedSets()) else exercise
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    exercises = loaded,
                    expandedExercises = if (initialExerciseIndex in loaded.indices) setOf(initialExerciseIndex) else emptySet(),
                    // Opening the workout at an exercise is the user's choice of where to start.
                    currentExerciseIndex = initialExerciseIndex.takeIf { it in loaded.indices },
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
        val current = _state.value
        val target = nextSet(current.exercises, current.currentExerciseIndex)
        if (target == null) {
            message("Everything in this plan is already done", MessageKind.Warning)
            return
        }
        // Starting the set also starts the workout clock (and checks the set has reps).
        onSetButton(target.exerciseIndex, target.setIndex)
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

    /**
     * Adds a set at the end. A drop set starts a fifth lighter than the set before it, so the weight is already right;
     * any other set repeats the last working set, so a drop or warm-up in between never changes what the next one starts at.
     */
    fun addSet(exerciseIndex: Int, type: SetType = SetType.Normal) {
        val exercise = _state.value.exercises.getOrNull(exerciseIndex) ?: return
        val sets = exercise.setsList
        val last = sets.lastOrNull()
        val newSet = if (type == SetType.Drop) {
            ExerciseSet(weight = suggestedDropKg(last?.weight ?: 0.0), reps = last?.reps ?: 0, type = type)
        } else {
            val template = sets.lastOrNull { it.isWork && it.type != SetType.Drop } ?: last
            ExerciseSet(weight = template?.weight ?: 0.0, reps = template?.reps ?: 0, type = type)
        }
        commit(exerciseIndex, exercise.copy(setsList = sets + newSet))
    }

    /**
     * Accepts the coach's weight: it goes on the working sets still to do (drop sets after them follow it down, warm-ups
     * and sets already done stay as they are) and becomes the planned weight of this exercise in every plan.
     */
    fun applyAdvice(exerciseIndex: Int) {
        val exercise = _state.value.exercises.getOrNull(exerciseIndex) ?: return
        val kg = _advice.value[exerciseKey(exercise.name)]?.suggestedKg ?: return
        var previous: Double? = null
        val sets = exercise.setsList.map { set ->
            val updated = when {
                set.isCompleted || set.isStarted -> set
                set.countsForRecords -> set.copy(weight = kg)
                set.type == SetType.Drop -> previous?.let { set.copy(weight = suggestedDropKg(it)) } ?: set
                else -> set
            }
            previous = updated.weight
            updated
        }
        commit(exerciseIndex, exercise.copy(weight = kg.toString(), setsList = sets))
        dismissAdvice(exercise.name)
        // This row is saved by commit above; the same exercise in other plans changes too.
        viewModelScope.launch { repository.setPlannedWeight(exercise.name, kg, skipIds = setOfNotNull(exercise.id)) }
    }

    fun dismissAdvice(exerciseName: String) {
        _advice.update { it - exerciseKey(exerciseName) }
    }

    /**
     * Changes what a set is. A set still to do also gets a sensible weight for its new type (a drop set lighter than the
     * one before it, a warm-up at about half, a working set back at the working weight); one already done keeps its numbers.
     */
    fun setType(exerciseIndex: Int, setIndex: Int, type: SetType) {
        val exercise = _state.value.exercises.getOrNull(exerciseIndex) ?: return
        val sets = exercise.setsList
        val set = sets.getOrNull(setIndex) ?: return
        if (set.type == type) return
        var changed = set.copy(type = type)
        if (!set.isCompleted && !set.isStarted) {
            val others = sets.filterIndexed { i, other -> i != setIndex && other.countsForRecords }
            val weight = when (type) {
                SetType.Drop -> sets.getOrNull(setIndex - 1)?.weight
                    ?.takeIf { it > 0 && set.weight >= it }?.let(::suggestedDropKg)
                SetType.WarmUp -> set.weight.takeIf { w -> w > 0 && others.all { w >= it.weight * 0.75 } }?.let(::suggestedWarmUpKg)
                else -> (sets.take(setIndex).lastOrNull { it.countsForRecords } ?: sets.drop(setIndex + 1).firstOrNull { it.countsForRecords })?.weight
            }
            if (weight != null) changed = changed.copy(weight = weight)
        }
        commit(exerciseIndex, exercise.copy(setsList = sets.replaced(setIndex, changed)))
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
                // Reopening a set is the user going back to that exercise.
                _state.update { it.copy(currentExerciseIndex = exerciseIndex) }
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
        // Sets go in order: set 3 cannot start while set 1 or 2 is still to do.
        exercise.setsList.blockingSetBefore(setIndex)?.let { blocking ->
            message("Complete ${setName(exercise.setsList, blocking)} first", MessageKind.Warning)
            return
        }
        // Weight may be 0 for bodyweight exercises; reps are always needed.
        if (set.reps <= 0) {
            message("Please enter Reps", MessageKind.Error)
            return
        }

        setWatch.reset()
        setWatch.start()
        // Starting a set makes its exercise the one being worked on, wherever in the plan it is: it is finished before the
        // workout goes back to an exercise that was skipped. (Changing exercise by hand works the same way: the new one is now current.)
        _state.update {
            it.copy(activeSetExerciseIndex = exerciseIndex, activeSetIndex = setIndex, lastSavedSet = null, currentExerciseIndex = exerciseIndex)
        }
        commit(exerciseIndex, exercise.copy(setsList = exercise.setsList.replaced(setIndex, set.copy(isStarted = true))))
        stopRest()
        if (!workoutWatch.isRunning) workoutWatch.start()
        syncWorkoutRunning()
    }

    private fun completeSet(exerciseIndex: Int, exercise: Exercise, setIndex: Int, set: ExerciseSet, isLastSet: Boolean) {
        val duration = com.example.strivo.core.util.formatDurationMinSec(setWatch.elapsedMillis)
        setWatch.stop()
        val weightText = if (set.weight == 0.0) "bodyweight" else "${formatWeight(set.weight)} kg"
        _state.update {
            it.copy(
                activeSetIndex = null,
                activeSetExerciseIndex = null,
                lastSavedSet = "${exercise.name} · ${setName(exercise.setsList, setIndex)}: $weightText × ${set.reps}",
            )
        }

        val completed = set.copy(isCompleted = true, isStarted = false, setDuration = duration)
        var updated = exercise.copy(setsList = exercise.setsList.replaced(setIndex, completed))

        if (!isLastSet) {
            commit(exerciseIndex, updated)
            // A drop set follows straight on: dropping the weight is the rest.
            if (exercise.setsList[setIndex + 1].type != SetType.Drop) triggerRest(exerciseIndex, setIndex, isExerciseRest = false)
            return
        }

        // Last set: the whole exercise is done, so the workout is free to go back to the earliest exercise still to do.
        updated = updated.copy(isCheck = true)
        commit(exerciseIndex, updated)
        _state.update { it.copy(currentExerciseIndex = null) }
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
        _state.update { it.copy(currentExerciseIndex = null) }
        setExpanded(exerciseIndex, false)
        val next = _state.value.exercises.indices.firstOrNull { !_state.value.exercises[it].isCheck && it != exerciseIndex }
        if (next != null) setExpanded(next, true) else workoutWatch.stop()
        syncWorkoutRunning()
        triggerRest(exerciseIndex, EXERCISE_REST_SLOT, isExerciseRest = true)

        if (isLastExercise) finishWorkout()
    }

    private fun finishWorkout() {
        endWorkout(finished = true)
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
        endWorkout(finished = false)
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

    /**
     * Stops the clock and saves the completed sets. [finished] is a real end (every exercise done, or finished early on
     * purpose); leaving the screen to come back later is not, and leaves the exercises not ticked off still to do.
     */
    private fun endWorkout(finished: Boolean) {
        workoutWatch.stop()
        syncWorkoutRunning()
        val duration = formatDuration(workoutWatch.elapsedMillis)
        _workoutTime.value = duration
        saveCompletedWorkout(duration, finished)
    }

    /** Only sets that were actually completed go into history; a re-finished workout updates its session. */
    private fun saveCompletedWorkout(duration: String, finished: Boolean) {
        val performed = _state.value.exercises.mapNotNull { exercise ->
            val done = exercise.setsList.filter { it.isCompleted }
            if (done.isEmpty()) null else PerformedExercise(name = exercise.name, sets = done)
        }
        if (performed.isEmpty()) return
        val date = LocalDateTime.now()
        // The exercises this workout dealt with: all of them when it was finished, only those ticked off when it was left.
        val planExerciseIds = _state.value.exercises.filter { finished || it.isCheck }.mapNotNull { it.id }.toSet()
        // NonCancellable: leaving the screen clears this view model, and the save must still finish.
        viewModelScope.launch {
            withContext(NonCancellable) {
                saveMutex.withLock {
                    // What the workout dealt with: anything added to the plan later, or left for later, is still to do.
                    plan.planId?.let { repository.markPlanFinished(it, planExerciseIds, date.toLocalDate()) }
                    // A plan can be worked on more than once in a day (extra exercises are added all day). What an earlier
                    // workout of the day already saved is not saved again, or its volume would count twice.
                    val saved = repository.getWorkoutSessions()
                        .filter { it.id != savedSessionId && it.planName == plan.planName && it.date.toLocalDate() == date.toLocalDate() }
                        .flatMap { s -> s.exercises.map { it.name.trim().lowercase() } }
                        .toSet()
                    val exercises = performed.filter { it.name.trim().lowercase() !in saved }
                    if (exercises.isEmpty()) return@withLock
                    val session = WorkoutSession(planName = plan.planName, date = date, totalTime = duration, exercises = exercises)
                    savedSessionId = repository.saveSession(session, savedSessionId)
                }
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
                val rest = com.example.strivo.core.util.formatDurationMinSec(restWatch.elapsedMillis)
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

private fun <T> List<T>.replaced(index: Int, value: T): List<T> =
    mapIndexed { i, old -> if (i == index) value else old }
