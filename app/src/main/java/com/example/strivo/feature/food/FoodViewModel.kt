package com.example.strivo.feature.food

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.strivo.StrivoApp
import com.example.strivo.domain.analytics.BodyProfile
import com.example.strivo.domain.analytics.CalorieGoal
import com.example.strivo.domain.analytics.EnergyDay
import com.example.strivo.domain.analytics.EnergySummary
import com.example.strivo.domain.analytics.ExtraActivity
import com.example.strivo.domain.analytics.FoodEntry
import com.example.strivo.domain.analytics.averageActiveKcal
import com.example.strivo.domain.analytics.baseBurnKcal
import com.example.strivo.domain.analytics.buildEnergyDay
import com.example.strivo.domain.analytics.calculateBmr
import com.example.strivo.domain.analytics.estimateExpenditure
import com.example.strivo.domain.analytics.recentFoods
import com.example.strivo.domain.analytics.summarizeEnergy
import com.example.strivo.domain.analytics.weightAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Where the day's base burn comes from, so the screen can say how far to trust it. */
enum class BaseSource { Learned, Formula, Unknown }

data class FoodState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    /** What was eaten on [date], in the order it was logged. */
    val entries: List<FoodEntry> = emptyList(),
    /** The user's own earlier foods, for adding a regular meal in one tap. */
    val recent: List<FoodEntry> = emptyList(),
    /** [date] in calories: eaten, burned (base + workouts + other activity) and the balance. */
    val day: EnergyDay? = null,
    val baseSource: BaseSource = BaseSource.Unknown,
    val goal: CalorieGoal = CalorieGoal.Maintain,
    /** The weight the estimates use; null until the profile or a weigh-in has one. */
    val weightKg: Double? = null,
    /** The seven days ending today, oldest first, for the week at a glance. */
    val week: EnergySummary? = null,
) {
    /** What to eat on [date] to follow the goal: what the day burns plus the goal's difference; null while the burn is unknown. */
    val targetKcal: Double? get() = day?.burnedKcal?.plus(goal.offsetKcal)
}

class FoodViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as StrivoApp
    private val repository = app.repository

    private var allFood: List<FoodEntry> = emptyList()
    private var activities: List<ExtraActivity> = emptyList()
    private var inputs: Inputs? = null
    private var goal = CalorieGoal.fromKey(app.userPrefs.calorieGoal)

    /** What the energy figures are worked out from; read again whenever the screen opens. */
    private class Inputs(
        val sessions: List<com.example.strivo.domain.model.WorkoutSession>,
        val weights: List<com.example.strivo.domain.analytics.WeightEntry>,
        val profile: BodyProfile,
        val base: Double?,
        val baseSource: BaseSource,
    )

    private val _state = MutableStateFlow(FoodState())
    val state: StateFlow<FoodState> = _state

    fun load() {
        viewModelScope.launch {
            allFood = repository.readFood()
            activities = repository.readActivities()
            val sessions = repository.getWorkoutSessions()
            val weights = repository.readWeightLog()
            inputs = withContext(Dispatchers.Default) {
                val saved = app.userPrefs.getProfile()
                val profile = BodyProfile(saved.gender, saved.age, saved.height, saved.weight)
                val today = LocalDate.now()
                val weightOn = { date: LocalDate -> weightAt(weights, date) ?: profile.weightKg }
                val learned = estimateExpenditure(allFood, weights, today).estimate
                val base = baseBurnKcal(calculateBmr(profile), learned, averageActiveKcal(sessions, activities, weightOn, today))
                Inputs(
                    sessions, weights, profile, base,
                    baseSource = when {
                        base == null -> BaseSource.Unknown
                        learned != null -> BaseSource.Learned
                        else -> BaseSource.Formula
                    },
                )
            }
            publish(_state.value.date)
        }
    }

    fun setDate(date: LocalDate) = publish(date)

    fun setGoal(value: CalorieGoal) {
        goal = value
        app.userPrefs.calorieGoal = value.key
        publish(_state.value.date)
    }

    fun add(entry: FoodEntry) = change { repository.addFood(entry) }

    fun update(entry: FoodEntry) = change { repository.updateFood(entry) }

    fun delete(id: Long) = change { repository.deleteFood(id) }

    fun addActivity(entry: ExtraActivity) = change { repository.addActivity(entry) }

    fun deleteActivity(id: Long) = change { repository.deleteActivity(id) }

    private fun change(write: suspend () -> Unit) {
        viewModelScope.launch {
            write()
            load()
        }
    }

    private fun publish(date: LocalDate) {
        val inputs = inputs
        val today = LocalDate.now()
        val weightOn = { day: LocalDate -> inputs?.let { weightAt(it.weights, day) ?: it.profile.weightKg } }
        val energy = { day: LocalDate ->
            buildEnergyDay(day, allFood, inputs?.sessions.orEmpty(), activities, weightOn, inputs?.base)
        }
        _state.value = FoodState(
            isLoading = false,
            date = date,
            entries = allFood.filter { it.date == date },
            recent = recentFoods(allFood),
            day = energy(date),
            baseSource = inputs?.baseSource ?: BaseSource.Unknown,
            goal = goal,
            weightKg = weightOn(today),
            week = summarizeEnergy((6 downTo 0).map { energy(today.minusDays(it.toLong())) }, today),
        )
    }
}
