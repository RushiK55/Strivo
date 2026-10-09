package com.example.strivo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.strivo.StrivoApp
import com.example.strivo.data.analytics.BodyProfile
import com.example.strivo.data.analytics.FoodEntry
import com.example.strivo.data.analytics.buildBodyReport
import com.example.strivo.data.analytics.recentFoods
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class FoodState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    /** What was eaten on [date], in the order it was logged. */
    val entries: List<FoodEntry> = emptyList(),
    /** The user's own earlier foods, for adding a regular meal in one tap. */
    val recent: List<FoodEntry> = emptyList(),
    /** Estimated daily calories to stay at the current weight; null until the profile has what it needs. */
    val maintenanceKcal: Double? = null,
)

class FoodViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as StrivoApp
    private val repository = app.repository

    private var allFood: List<FoodEntry> = emptyList()
    private var maintenanceKcal: Double? = null

    private val _state = MutableStateFlow(FoodState())
    val state: StateFlow<FoodState> = _state

    fun load() {
        viewModelScope.launch {
            allFood = repository.readFood()
            maintenanceKcal = withContext(Dispatchers.Default) {
                val saved = app.userPrefs.getProfile()
                val sessions = repository.getWorkoutSessions()
                buildBodyReport(BodyProfile(saved.gender, saved.age, saved.height, saved.weight), emptyList(), sessions, LocalDate.now()).maintenanceKcal
            }
            publish(_state.value.date)
        }
    }

    fun setDate(date: LocalDate) = publish(date)

    fun add(entry: FoodEntry) = change { repository.addFood(entry) }

    fun update(entry: FoodEntry) = change { repository.updateFood(entry) }

    fun delete(id: Long) = change { repository.deleteFood(id) }

    private fun change(write: suspend () -> Unit) {
        viewModelScope.launch {
            write()
            allFood = repository.readFood()
            publish(_state.value.date)
        }
    }

    private fun publish(date: LocalDate) {
        _state.value = FoodState(
            isLoading = false,
            date = date,
            entries = allFood.filter { it.date == date },
            recent = recentFoods(allFood),
            maintenanceKcal = maintenanceKcal,
        )
    }
}
