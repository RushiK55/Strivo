package com.example.strivo.feature.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.strivo.StrivoApp
import com.example.strivo.domain.model.LibraryExercise
import com.example.strivo.domain.model.LibraryMuscleGroups
import com.example.strivo.domain.model.Plan
import com.example.strivo.domain.plan.LibraryPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun groupOrder(group: String): Int = LibraryMuscleGroups.indexOf(group).let { if (it < 0) LibraryMuscleGroups.size else it }

data class LibraryState(
    val isLoading: Boolean = true,
    val exercises: List<LibraryExercise> = emptyList(),
    /** The plans an exercise can be added to, weekday plans first. */
    val plans: List<Plan> = emptyList(),
    /** The plan the library was opened from, when there is one: adding goes straight there. */
    val targetPlan: Plan? = null,
    /** The plan library: ready-made plans (an admin's, or the starter ones) to put on a day. */
    val libraryPlans: List<LibraryPlan> = emptyList(),
    /** Which list is shown: the exercises or the plans. */
    val showPlans: Boolean = false,
    val query: String = "",
    /** The muscle group chosen to filter by; null shows everything. */
    val group: String? = null,
) {
    /** The muscle groups that have exercises, in the usual order. */
    val groups: List<String>
        get() {
            val present = exercises.map { it.muscleGroup }.toSet()
            return LibraryMuscleGroups.filter { it in present } + present.filter { it !in LibraryMuscleGroups }.sorted()
        }

    /** The plans that match the search text (by name, level, description or an exercise in them), A to Z. */
    val shownPlans: List<LibraryPlan>
        get() {
            val text = query.trim()
            return libraryPlans.filter { p ->
                text.isEmpty() || (listOf(p.name, p.level, p.description) + p.exercises.map { it.name }).any { it.contains(text, ignoreCase = true) }
            }.sortedBy { it.name.lowercase() }
        }

    /** What matches the search text and the chosen group, in the usual muscle group order and A to Z within it. */
    val shown: List<LibraryExercise>
        get() {
            val text = query.trim()
            return exercises.filter { e ->
                (group == null || e.muscleGroup == group) &&
                    (text.isEmpty() || listOf(e.name, e.muscleGroup, e.equipment).any { it.contains(text, ignoreCase = true) })
            }.sortedWith(compareBy<LibraryExercise> { groupOrder(it.muscleGroup) }.thenBy { it.name.lowercase() })
        }
}

/** Browsing the exercise library. [planId] is the plan it was opened from, or -1 when it was opened on its own. */
class LibraryViewModel(application: Application, private val planId: Long) : AndroidViewModel(application) {
    private val app = application as StrivoApp

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state

    init {
        load()
        // A library that arrives from the cloud while the screen is open shows up without leaving it.
        viewModelScope.launch {
            var seen = app.cloudDataVersion.value
            app.cloudDataVersion.collect { version -> if (version != seen) { seen = version; load() } }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val repository = app.repository
            val exercises = repository.readLibrary()
            val libraryPlans = repository.readLibraryPlans()
            val plans = repository.readAllPlans().filter { it.planId != null }
                .sortedWith(compareBy<Plan> { it.isPrebuilt }.thenBy { it.planDay }.thenBy { it.planName.lowercase() })
            _state.update { it.copy(isLoading = false, exercises = exercises, libraryPlans = libraryPlans, plans = plans, targetPlan = plans.firstOrNull { p -> p.planId == planId }) }
        }
    }

    fun showPlans(plans: Boolean) = _state.update { it.copy(showPlans = plans) }

    /** Takes a plan of the plan library into the user's own plans (once) and puts it on [day]. */
    suspend fun addPlanToDay(plan: LibraryPlan, day: String) {
        app.repository.addLibraryPlanToDay(plan, day)
    }

    fun setQuery(text: String) = _state.update { it.copy(query = text) }

    fun setGroup(group: String?) = _state.update { it.copy(group = if (it.group == group) null else group) }

    /** Adds the exercise to [plan] with its suggested sets, reps and weight. */
    suspend fun addTo(exercise: LibraryExercise, plan: Plan): Boolean {
        val id = plan.planId ?: return false
        app.repository.addLibraryExercise(exercise, id)
        return true
    }
}
