package com.example.strivo.feature.body

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.strivo.StrivoApp
import com.example.strivo.core.util.dayName
import com.example.strivo.domain.body.BodyKey
import com.example.strivo.domain.body.GlbModel
import com.example.strivo.domain.body.GlbReader
import com.example.strivo.domain.body.MuscleCatalogue
import com.example.strivo.domain.body.MuscleNote
import com.example.strivo.domain.body.TrainedMuscles
import com.example.strivo.domain.body.TrainedMusclesFinder
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.LibraryExercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

/** Everything the Body screen and the Home card show. */
data class BodyUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val catalogue: MuscleCatalogue? = null,
    /** The body shown: the account's gender (female for female, male otherwise). */
    val body: BodyKey = BodyKey.Male,
    /** The gender the profile says, so the screen can suggest setting it when it says neither. */
    val gender: String? = null,
    val model: GlbModel? = null,
    /** The day whose training is shown (a weekday name) and today's. */
    val day: String = dayName(LocalDate.now().dayOfWeek),
    val today: String = dayName(LocalDate.now().dayOfWeek),
    val dayExercises: List<Exercise> = emptyList(),
    val library: List<LibraryExercise> = emptyList(),
    val trained: TrainedMuscles = TrainedMuscles.None,
    /** What admins wrote about the muscles, by muscle id. */
    val notes: Map<String, MuscleNote> = emptyMap(),
    val selected: String? = null,
    val skinOn: Boolean = true,
) {
    /** The labels of the muscles a day trains mainly, for a line of text ("Lats · Biceps"). */
    fun mainLabels(): List<String> = catalogue?.let { c -> trained.main.map { c.labelOf(it) } }.orEmpty()

    fun helperLabels(): List<String> = catalogue?.let { c -> trained.helper.map { c.labelOf(it) } }.orEmpty()
}

class BodyViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as StrivoApp

    private val _state = MutableStateFlow(BodyUiState())
    val state: StateFlow<BodyUiState> = _state

    /**
     * Reads what the screen needs: the catalogue, the account's gender, the library, the day's exercises and what admins wrote about
     * the muscles; with [withModel] also the 3D model of the right body (Home only needs the summary, not the model).
     */
    fun load(withModel: Boolean) {
        viewModelScope.launch {
            val previous = _state.value
            val next = withContext(Dispatchers.IO) {
                runCatching {
                    val catalogue = previous.catalogue ?: readCatalogue()
                    val gender = app.userPrefs.getProfile().gender
                    val body = BodyKey.forGender(gender)
                    val library = app.repository.readLibrary()
                    val exercises = app.repository.readExercisesByDay(previous.day)
                    val model = when {
                        !withModel -> previous.model.takeIf { previous.body == body }
                        previous.model != null && previous.body == body -> previous.model
                        else -> readModel(body)
                    }
                    previous.copy(
                        loading = false,
                        error = if (withModel && model == null) "The 3D body could not be loaded." else null,
                        catalogue = catalogue,
                        body = body,
                        gender = gender,
                        model = model,
                        today = dayName(LocalDate.now().dayOfWeek),
                        dayExercises = exercises,
                        library = library,
                        trained = TrainedMusclesFinder.find(exercises, library, catalogue),
                        notes = app.userPrefs.getMuscleNotes(),
                    )
                }.getOrElse { previous.copy(loading = false, error = "The 3D body could not be loaded.") }
            }
            // Keep a choice made while it was loading (a muscle picked, the skin switched).
            _state.update { current -> next.copy(selected = current.selected, skinOn = current.skinOn, day = current.day) }
        }
    }

    private fun readCatalogue(): MuscleCatalogue =
        getApplication<Application>().assets.open("muscles.json").use { MuscleCatalogue.fromJson(it.bufferedReader().readText()) }
            ?: error("The muscle catalogue is not readable")

    private fun readModel(body: BodyKey): GlbModel =
        getApplication<Application>().assets.open(body.file).use { GlbReader.read(it.readBytes()) } ?: error("The body model is not readable")

    /** Shows what a day of the week trains. */
    fun selectDay(day: String) {
        if (day == _state.value.day) return
        _state.update { it.copy(day = day, selected = null, skinOn = true) }
        viewModelScope.launch {
            val exercises = withContext(Dispatchers.IO) { app.repository.readExercisesByDay(day) }
            _state.update { current ->
                val catalogue = current.catalogue ?: return@update current
                if (current.day != day) current else current.copy(dayExercises = exercises, trained = TrainedMusclesFinder.find(exercises, current.library, catalogue))
            }
        }
    }

    /** Picks a muscle (or none). A picked muscle takes the skin off so it can be seen; none puts it back. */
    fun select(id: String?) {
        _state.update { it.copy(selected = id, skinOn = id == null) }
    }

    fun toggleSkin() {
        _state.update { it.copy(skinOn = !it.skinOn) }
    }

    companion object {
        /** The week, Monday first. */
        val Week: List<String> = DayOfWeek.entries.map { dayName(it) }
    }
}
