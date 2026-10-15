package com.example.strivo.feature.plans

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.strivo.StrivoApp
import com.example.strivo.domain.model.Plan
import com.example.strivo.domain.plan.LibraryPlan
import com.example.strivo.domain.plan.PlanOffer
import com.example.strivo.domain.plan.acceptedNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as StrivoApp).repository

    private val _plans = MutableStateFlow<List<Plan>>(emptyList())
    val plans: StateFlow<List<Plan>> = _plans

    /** The plan library: plans an admin published (or the starter ones), to put on a day. */
    private val _libraryPlans = MutableStateFlow<List<LibraryPlan>>(emptyList())
    val libraryPlans: StateFlow<List<LibraryPlan>> = _libraryPlans

    /** How many exercises each plan has, by plan id, for the lists of plans. */
    private val _exerciseCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val exerciseCounts: StateFlow<Map<Long, Int>> = _exerciseCounts

    /** What the trainer is offering that the user has not answered, for the card on Home (a week's plans are one offer). */
    private val _offers = MutableStateFlow<List<PlanOffer>>(emptyList())
    val offers: StateFlow<List<PlanOffer>> = _offers

    /** A line saying what accepting did, shown once ("Added 7 plans to your week"). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    fun refreshPlans() {
        viewModelScope.launch {
            _plans.value = repository.readAllPlans()
            _offers.value = repository.planOffers()
            _libraryPlans.value = repository.readLibraryPlans()
            _exerciseCounts.value = repository.readAllExercises().groupingBy { it.planId }.eachCount()
        }
    }

    /** Adds every plan of the offer to the day the trainer chose. */
    fun acceptOffer(offer: PlanOffer) {
        viewModelScope.launch {
            val added = repository.acceptOffer(offer)
            _plans.value = repository.readAllPlans()
            _offers.value = repository.planOffers()
            if (added > 0) _notice.value = acceptedNotice(offer, added)
        }
    }

    fun declineOffer(offer: PlanOffer) {
        viewModelScope.launch {
            repository.declineOffer(offer)
            _offers.value = repository.planOffers()
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    suspend fun addPlan(plan: Plan): Long {
        val id = repository.createPlan(plan)
        _plans.value = repository.readAllPlans()
        return id
    }

    /** Puts one of the user's own plans, with its exercises, on [day]; what is changed there is kept in the plan. */
    suspend fun addPlanToDay(plan: Plan, day: String) {
        repository.copyPlanToDay(plan, day)
        _plans.value = repository.readAllPlans()
    }

    /** Takes a plan of the plan library into the user's own plans (once) and puts it on [day]. */
    suspend fun addLibraryPlanToDay(plan: LibraryPlan, day: String) {
        repository.addLibraryPlanToDay(plan, day)
        _plans.value = repository.readAllPlans()
        _exerciseCounts.value = repository.readAllExercises().groupingBy { it.planId }.eachCount()
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
