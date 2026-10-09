package com.example.strivo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.strivo.StrivoApp
import com.example.strivo.data.db.PersonalRecords
import com.example.strivo.data.prefs.UserProfile
import com.example.strivo.data.sync.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class ProfileState(
    val profile: UserProfile = UserProfile(),
    val records: PersonalRecords = PersonalRecords(),
    val isLoading: Boolean = true,
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as StrivoApp
    private val prefs = app.userPrefs
    private val repository = app.repository

    private val sync = app.sync

    /** Whether this account's data is backed up to the cloud, and what is waiting. */
    val syncStatus: StateFlow<SyncStatus> = sync.status

    fun syncNow() = sync.requestSync(pull = true)

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state

    fun load() {
        viewModelScope.launch {
            val profile = withContext(Dispatchers.IO) { prefs.getProfile() }
            val records = repository.getPersonalRecords()
            _state.update { ProfileState(profile = profile, records = records, isLoading = false) }
        }
    }

    suspend fun saveProfile(gender: String, age: Int, height: Double, weight: Double) {
        val previousWeight = withContext(Dispatchers.IO) { prefs.getProfile().weight }
        withContext(Dispatchers.IO) { prefs.saveProfile(gender, age, height, weight) }
        // Editing gender, age or height hands the old weight back in; only a changed weight is a new weigh-in.
        if (previousWeight == null || previousWeight != weight) repository.logWeight(weight)
    }

    suspend fun updateWeightOnly(weight: Double) {
        withContext(Dispatchers.IO) { prefs.updateWeightOnly(weight) }
        repository.logWeight(weight)
    }

    fun updateWeight(weight: Double) {
        viewModelScope.launch { updateWeightOnly(weight) }
    }

    /** Debug builds only: adds 7 days of made-up workouts, meals and weigh-ins. Returns how many workouts were added. */
    suspend fun loadDemoData(): Int = repository.loadDemoData(LocalDate.now())

    /** Debug builds only: removes just the demo data (workouts, meals, weigh-ins). Returns how many workouts were removed. */
    suspend fun removeDemoData(): Int = repository.removeDemoData()

    /** Debug builds only: deletes all workout history except the demo workouts. */
    suspend fun clearHistoryKeepingDemo(): Int {
        val removed = repository.clearHistoryKeepingDemo()
        load()
        return removed
    }


    suspend fun shouldAskWeight(): Boolean = withContext(Dispatchers.IO) { prefs.shouldAskWeight() }
}
