package com.example.strivo.feature.plans

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.strivo.StrivoApp
import com.example.strivo.core.util.dayName
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.LibraryExercise
import com.example.strivo.domain.model.WorkoutSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class ExerciseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as StrivoApp).repository

    private val _exercises = MutableStateFlow<List<Exercise>>(emptyList())
    val exercises: StateFlow<List<Exercise>> = _exercises

    /** The exercise library, for the picker of a plan. */
    private val _library = MutableStateFlow<List<LibraryExercise>>(emptyList())
    val library: StateFlow<List<LibraryExercise>> = _library

    private val _todayExercises = MutableStateFlow<List<Exercise>>(emptyList())
    val todayExercises: StateFlow<List<Exercise>> = _todayExercises

    private val _extraExercises = MutableStateFlow<List<Exercise>>(emptyList())
    val extraExercises: StateFlow<List<Exercise>> = _extraExercises

    private val _history = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val history: StateFlow<List<WorkoutSession>> = _history

    /** Plan id to the ids of the exercises it held when its workout was finished today. */
    private val _finishedToday = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())
    val finishedToday: StateFlow<Map<Long, Set<Long>>> = _finishedToday

    private var currentLoadedDay = ""

    fun fetchHistory() {
        viewModelScope.launch {
            _history.value = repository.getWorkoutSessions()
            _finishedToday.value = repository.finishedPlans(LocalDate.now())
        }
    }

    fun loadExercisesByPlan(planId: Long) {
        viewModelScope.launch { _exercises.value = repository.readExercisesByPlan(planId) }
    }

    fun loadLibrary() {
        viewModelScope.launch { _library.value = repository.readLibrary() }
    }

    /** Puts a library exercise into a plan with its suggested sets, reps and weight; the plan's list is refreshed. */
    fun addFromLibrary(exercise: LibraryExercise, planId: Long) {
        viewModelScope.launch {
            repository.addLibraryExercise(exercise, planId)
            _exercises.value = repository.readExercisesByPlan(planId)
            reloadDay(selectedDay())
        }
    }

    fun loadExercisesByDay(day: String) {
        currentLoadedDay = day
        viewModelScope.launch { reloadDay(day) }
    }

    fun loadTodayExercises() = loadExercisesByDay(dayName(LocalDate.now().dayOfWeek))

    private suspend fun reloadDay(day: String) {
        repository.checkAndResetDaily()
        _todayExercises.value = repository.readExercisesByDay(day)
        _extraExercises.value = repository.readExtraExercises()
        _finishedToday.value = repository.finishedPlans(LocalDate.now())
    }

    private fun selectedDay() = currentLoadedDay.ifEmpty { dayName(LocalDate.now().dayOfWeek) }

    suspend fun addExercise(exercise: Exercise) {
        repository.createExercise(exercise)
        _exercises.value = repository.readExercisesByPlan(exercise.planId)
        reloadDay(selectedDay())
    }

    /**
     * Every exercise to pick an extra one from, one per name, A to Z: the ones the user has (their newest version, so their own sets,
     * reps and weight) and every exercise of the exercise library that they do not have yet (with its suggested sets, reps and weight).
     */
    suspend fun knownExercises(): List<Exercise> {
        val own = repository.readAllExercises()
            .sortedByDescending { it.id ?: 0L }
            .distinctBy { it.name.trim().lowercase() }
        val have = own.map { it.name.trim().lowercase() }.toSet()
        val fromLibrary = repository.readLibrary()
            .filter { it.name.trim().lowercase() !in have }
            .map { it.toPlanExercise(planId = -1L) }
        return (own + fromLibrary).sortedBy { it.name.lowercase() }
    }

    /** Adds several exercises (in order) to the end of their plan, refreshing the lists once. */
    suspend fun addExercises(exercises: List<Exercise>) {
        if (exercises.isEmpty()) return
        exercises.forEach { repository.createExercise(it) }
        _exercises.value = repository.readExercisesByPlan(exercises.first().planId)
        reloadDay(selectedDay())
    }

    /** Copies [exercise] into another plan; the original stays where it is. */
    suspend fun copyExerciseToPlan(exercise: Exercise, targetPlanId: Long) {
        repository.copyExerciseToPlan(exercise, targetPlanId)
        reloadDay(selectedDay())
    }

    /** Moves [exercise] out of [fromPlanId] into another plan. */
    suspend fun moveExerciseToPlan(exercise: Exercise, fromPlanId: Long, targetPlanId: Long) {
        val id = exercise.id ?: return
        repository.moveExerciseToPlan(id, targetPlanId)
        _exercises.value = repository.readExercisesByPlan(fromPlanId)
        reloadDay(selectedDay())
    }

    suspend fun deleteExercise(id: Long, planId: Long) {
        repository.deleteExercise(id)
        _exercises.value = repository.readExercisesByPlan(planId)
        reloadDay(selectedDay())
    }

    /** Saves the order the user dragged a plan's exercises into. */
    suspend fun reorderExercises(planId: Long, orderedIds: List<Long>) {
        repository.reorderExercises(planId, orderedIds)
        _exercises.value = repository.readExercisesByPlan(planId)
        reloadDay(selectedDay())
    }

    /** Saves an exercise the user edited in its plan. */
    suspend fun updateExercise(exercise: Exercise) {
        repository.editExercise(exercise)
        _exercises.value = repository.readExercisesByPlan(exercise.planId)
        reloadDay(selectedDay())
    }
}
