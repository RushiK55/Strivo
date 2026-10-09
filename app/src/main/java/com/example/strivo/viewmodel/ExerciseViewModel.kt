package com.example.strivo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.strivo.StrivoApp
import com.example.strivo.data.model.Exercise
import com.example.strivo.data.model.WorkoutSession
import com.example.strivo.util.dayName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class ExerciseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as StrivoApp).repository

    private val _exercises = MutableStateFlow<List<Exercise>>(emptyList())
    val exercises: StateFlow<List<Exercise>> = _exercises

    private val _todayExercises = MutableStateFlow<List<Exercise>>(emptyList())
    val todayExercises: StateFlow<List<Exercise>> = _todayExercises

    private val _extraExercises = MutableStateFlow<List<Exercise>>(emptyList())
    val extraExercises: StateFlow<List<Exercise>> = _extraExercises

    private val _history = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val history: StateFlow<List<WorkoutSession>> = _history

    private var currentLoadedDay = ""

    fun fetchHistory() {
        viewModelScope.launch { _history.value = repository.getWorkoutSessions() }
    }

    fun loadExercisesByPlan(planId: Long) {
        viewModelScope.launch { _exercises.value = repository.readExercisesByPlan(planId) }
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
    }

    private fun selectedDay() = currentLoadedDay.ifEmpty { dayName(LocalDate.now().dayOfWeek) }

    suspend fun addExercise(exercise: Exercise) {
        repository.createExercise(exercise)
        _exercises.value = repository.readExercisesByPlan(exercise.planId)
        reloadDay(selectedDay())
    }

    /** Every exercise the user has, one per name (its newest version), A to Z: the list to pick an extra exercise from. */
    suspend fun knownExercises(): List<Exercise> =
        repository.readAllExercises()
            .sortedByDescending { it.id ?: 0L }
            .distinctBy { it.name.trim().lowercase() }
            .sortedBy { it.name.lowercase() }

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

    suspend fun updateExercise(exercise: Exercise) {
        repository.updateExercise(exercise)
        _exercises.value = repository.readExercisesByPlan(exercise.planId)
        reloadDay(selectedDay())
    }
}
