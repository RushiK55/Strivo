package com.example.strivo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.strivo.StrivoApp
import com.example.strivo.data.analytics.AnalyticsRange
import com.example.strivo.data.analytics.AnalyticsReport
import com.example.strivo.data.analytics.BodyProfile
import com.example.strivo.data.analytics.BodyReport
import com.example.strivo.data.analytics.ExtraActivity
import com.example.strivo.data.analytics.FoodEntry
import com.example.strivo.data.analytics.NutritionReport
import com.example.strivo.data.analytics.buildNutrition
import com.example.strivo.data.analytics.WeightEntry
import com.example.strivo.data.analytics.buildBodyReport
import com.example.strivo.data.analytics.buildReport
import com.example.strivo.data.analytics.weightAt
import com.example.strivo.data.model.WorkoutSession
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
)

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as StrivoApp
    private val repository = app.repository

    private var sessions: List<WorkoutSession> = emptyList()
    private var weightLog: List<WeightEntry> = emptyList()
    private var food: List<FoodEntry> = emptyList()
    private var activities: List<ExtraActivity> = emptyList()
    private var profile = BodyProfile(null, null, null, null)
    private var restDays: Set<DayOfWeek> = setOf(DayOfWeek.SUNDAY)

    private val _state = MutableStateFlow(AnalyticsState())
    val state: StateFlow<AnalyticsState> = _state

    /** Reads the saved workouts, profile and weigh-ins again; call whenever the screen opens so new data shows up. */
    fun load() {
        viewModelScope.launch {
            sessions = repository.getWorkoutSessions()
            weightLog = repository.readWeightLog()
            food = repository.readFood()
            activities = repository.readActivities()
            profile = withContext(Dispatchers.IO) {
                val saved = app.userPrefs.getProfile()
                restDays = saved.restDays
                BodyProfile(saved.gender, saved.age, saved.height, saved.weight)
            }
            recompute(_state.value.range)
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
        val result = withContext(Dispatchers.Default) {
            // A workout is costed with the weight the user had on that day, or today's profile weight.
            val weightOn = { date: LocalDate -> weightAt(weightLog, date) ?: profile.weightKg }
            val body = buildBodyReport(profile, weightLog, sessions, today)
            Triple(
                buildReport(sessions, range, today, activities, restDays, weightOn),
                body,
                buildNutrition(food, range, today, body.maintenanceKcal),
            )
        }
        _state.value = AnalyticsState(
            isLoading = false,
            range = range,
            report = result.first,
            body = result.second,
            nutrition = result.third,
        )
    }
}
