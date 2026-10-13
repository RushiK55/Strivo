package com.example.strivo.feature.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.strivo.StrivoApp
import com.example.strivo.core.util.dayName
import com.example.strivo.domain.analytics.AnalyticsRange
import com.example.strivo.domain.analytics.AnalyticsReport
import com.example.strivo.domain.analytics.BodyProfile
import com.example.strivo.domain.analytics.BodyReport
import com.example.strivo.domain.analytics.ConsistencyReport
import com.example.strivo.domain.analytics.ExerciseHistory
import com.example.strivo.domain.analytics.ExpenditureStatus
import com.example.strivo.domain.analytics.ExtraActivity
import com.example.strivo.domain.analytics.FoodEntry
import com.example.strivo.domain.analytics.NutritionReport
import com.example.strivo.domain.analytics.RecordEvent
import com.example.strivo.domain.analytics.WeightEntry
import com.example.strivo.domain.analytics.buildBodyReport
import com.example.strivo.domain.analytics.buildConsistency
import com.example.strivo.domain.analytics.buildNutrition
import com.example.strivo.domain.analytics.buildReport
import com.example.strivo.domain.analytics.estimateExpenditure
import com.example.strivo.domain.analytics.exerciseHistories
import com.example.strivo.domain.analytics.recentRecords
import com.example.strivo.domain.analytics.weightAt
import com.example.strivo.domain.coach.Advice
import com.example.strivo.domain.coach.PlannedExercise
import com.example.strivo.domain.coach.acceptedIn
import com.example.strivo.domain.coach.coachAdvice
import com.example.strivo.domain.coach.exerciseKey
import com.example.strivo.domain.coach.isInPlans
import com.example.strivo.domain.model.EXTRA_PLAN_NAME
import com.example.strivo.domain.model.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

data class AnalyticsState(
    val isLoading: Boolean = true,
    val range: AnalyticsRange = AnalyticsRange.Week,
    val report: AnalyticsReport? = null,
    val body: BodyReport? = null,
    val nutrition: NutritionReport? = null,
    val consistency: ConsistencyReport? = null,
    /** Every exercise ever done, most recent first. */
    val exercises: List<ExerciseHistory> = emptyList(),
    /** Records broken in the last 30 days, newest first. */
    val records: List<RecordEvent> = emptyList(),
    val expenditure: ExpenditureStatus? = null,
    /** What the coach suggests doing next, most important first. */
    val advice: List<Advice> = emptyList(),
    /** Each exercise in the user's own plans, by exerciseKey: advice about these can be accepted into the plan. */
    val plannedExercises: Map<String, List<PlannedExercise>> = emptyMap(),
) {
    /** Where accepting this advice would change a plan, or null when there is no plan to change (so no Accept button). */
    fun acceptLabel(advice: Advice): String? = advice.acceptedIn(advice.exercise?.let { plannedExercises[exerciseKey(it)] })
}

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as StrivoApp
    private val repository = app.repository

    private var sessions: List<WorkoutSession> = emptyList()
    private var weightLog: List<WeightEntry> = emptyList()
    private var food: List<FoodEntry> = emptyList()
    private var activities: List<ExtraActivity> = emptyList()
    private var profile = BodyProfile(null, null, null, null)
    private var restDays: Set<DayOfWeek> = setOf(DayOfWeek.SUNDAY)
    /** Weekdays with a plan on the weekly schedule, for adherence. */
    private var scheduledDays: Set<DayOfWeek> = emptySet()
    /** Planned weight of each copy of each exercise in the user's plans, by exerciseKey. */
    private var planned: Map<String, List<PlannedExercise>> = emptyMap()

    private val _state = MutableStateFlow(AnalyticsState())
    val state: StateFlow<AnalyticsState> = _state

    /** Reads the saved workouts, profile and weigh-ins again; call whenever the screen opens so new data shows up. */
    fun load() {
        viewModelScope.launch {
            sessions = repository.getWorkoutSessions()
            weightLog = repository.readWeightLog()
            food = repository.readFood()
            planned = repository.plannedExercises()
            activities = repository.readActivities()
            scheduledDays = repository.readAllPlans()
                .filter { !it.isPrebuilt && it.planName != EXTRA_PLAN_NAME }
                .mapNotNull { plan -> DayOfWeek.entries.firstOrNull { dayName(it) == plan.planDay } }
                .toSet()
            profile = withContext(Dispatchers.IO) {
                val saved = app.userPrefs.getProfile()
                restDays = saved.restDays
                BodyProfile(saved.gender, saved.age, saved.height, saved.weight)
            }
            recompute(_state.value.range)
        }
    }

    /** Accepts the coach's advice: its weight becomes the planned weight of that exercise in every plan. */
    fun acceptAdvice(advice: Advice) {
        val name = advice.exercise ?: return
        val kg = advice.suggestedKg ?: return
        viewModelScope.launch {
            repository.setPlannedWeight(name, kg)
            load()
        }
    }

    /** Saves a new weight as the profile weight and as today's weigh-in, then refreshes the figures. */
    fun updateWeight(kg: Double) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.userPrefs.updateWeightOnly(kg) }
            repository.logWeight(kg)
            load()
        }
    }

    /** Saves a new height (cm) in the profile, leaving everything else in it as it was. */
    fun updateHeight(cm: Double) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val saved = app.userPrefs.getProfile()
                app.userPrefs.saveProfile(saved.gender ?: "Male", saved.age ?: 25, cm, saved.weight ?: 70.0)
            }
            repository.recordProfileUpdated()
            load()
        }
    }

    /** Adds calories burned outside a tracked workout (a run, a walk, sport) and refreshes the figures. */
    fun addActivity(entry: ExtraActivity) {
        viewModelScope.launch {
            repository.addActivity(entry)
            activities = repository.readActivities()
            recompute(_state.value.range)
        }
    }

    fun deleteActivity(id: Long) {
        viewModelScope.launch {
            repository.deleteActivity(id)
            activities = repository.readActivities()
            recompute(_state.value.range)
        }
    }

    fun setRange(range: AnalyticsRange) {
        viewModelScope.launch { recompute(range) }
    }

    private suspend fun recompute(range: AnalyticsRange) {
        val today = LocalDate.now()
        val next = withContext(Dispatchers.Default) {
            // A workout is costed with the weight the user had on that day, or today's profile weight.
            val weightOn = { date: LocalDate -> weightAt(weightLog, date) ?: profile.weightKg }
            val body = buildBodyReport(profile, weightLog, sessions, today)
            val exercises = exerciseHistories(sessions)
            AnalyticsState(
                isLoading = false,
                range = range,
                report = buildReport(sessions, range, today, activities, restDays, weightOn),
                body = body,
                nutrition = buildNutrition(food, range, today, body.maintenanceKcal),
                consistency = buildConsistency(sessions, scheduledDays, range, today),
                exercises = exercises,
                records = recentRecords(exercises, today.minusDays(29)),
                expenditure = estimateExpenditure(food, weightLog, today),
                // Advice the plans already follow has been accepted (or done by hand): nothing left to say.
                advice = coachAdvice(exercises, body, weightLog, food, sessions, today)
                    .filterNot { advice -> advice.exercise?.let { advice.isInPlans(planned[exerciseKey(it)]?.map { p -> p.weightKg }) } ?: false },
                plannedExercises = planned,
            )
        }
        _state.value = next
    }
}
