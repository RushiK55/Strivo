package com.example.strivo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.strivo.StrivoApp
import com.example.strivo.data.model.Plan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as StrivoApp).repository

    private val _plans = MutableStateFlow<List<Plan>>(emptyList())
    val plans: StateFlow<List<Plan>> = _plans

    fun refreshPlans() {
        viewModelScope.launch { _plans.value = repository.readAllPlans() }
    }

    suspend fun addPlan(plan: Plan): Long {
        val id = repository.createPlan(plan)
        _plans.value = repository.readAllPlans()
        return id
    }

    /** Copies a prebuilt plan, with its exercises, onto [day]. */
    suspend fun addPlanToDay(plan: Plan, day: String) {
        repository.copyPlanToDay(plan, day)
        _plans.value = repository.readAllPlans()
    }

    /** The id of [day]'s plan for extra exercises, created if this is the first one. */
    suspend fun extraPlanFor(day: String): Long {
        val id = repository.getOrCreateExtraPlan(day)
        _plans.value = repository.readAllPlans()
        return id
    }

    /** Drops extra-exercise plans nobody added anything to. */
    suspend fun removeEmptyExtraPlans() {
        if (repository.deleteEmptyExtraPlans() > 0) _plans.value = repository.readAllPlans()
    }

    suspend fun deletePlan(id: Long) {
        repository.deletePlan(id)
        _plans.value = repository.readAllPlans()
    }
}

fun List<Plan>.groupedByDay(): Map<String, List<Plan>> = groupBy { it.planDay }
