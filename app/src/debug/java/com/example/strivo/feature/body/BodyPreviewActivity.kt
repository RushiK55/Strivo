package com.example.strivo.feature.body

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.strivo.core.ui.theme.StrivoTheme
import com.example.strivo.domain.body.BodyKey
import com.example.strivo.domain.body.GlbReader
import com.example.strivo.domain.body.MuscleCatalogue
import com.example.strivo.domain.body.MuscleExercises
import com.example.strivo.domain.body.MuscleNote
import com.example.strivo.domain.body.TrainedMusclesFinder
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.LibraryExercise

/**
 * A debug-only way to see the 3D body screen on a device without signing in: it shows the real screen with a made-up week of
 * training taken from the starter library. It is not in the release app (this is the debug source set).
 *
 *   adb shell am start -n strivo.app/com.example.strivo.feature.body.BodyPreviewActivity --es gender Female --es day Tuesday --es select lats
 *
 * Extras (all optional): gender (Male or Female), day (a weekday), select (a muscle id), skin (true or false), note (a subtitle
 * for the selected muscle, to see how an admin's note shows).
 */
class BodyPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val gender = intent.getStringExtra("gender") ?: "Male"
        val body = BodyKey.forGender(gender)
        val catalogue = assets.open("muscles.json").use { MuscleCatalogue.fromJson(it.bufferedReader().readText()) }!!
        val model = assets.open(body.file).use { GlbReader.read(it.readBytes()) }
        val library = assets.open("library.json").use { LibraryExercise.listFromJson(it.bufferedReader().readText()) }

        // A week made of starter exercises: push, pull, legs, and so on.
        val names = mapOf(
            "Monday" to listOf("Bench Press", "Overhead Press", "Lateral Raise"),
            "Tuesday" to listOf("Pull-Up", "Barbell Row"),
            "Wednesday" to listOf("Squat", "Romanian Deadlift"),
            "Thursday" to listOf("Overhead Press", "Lateral Raise"),
            "Friday" to listOf("Deadlift", "Pull-Up"),
        )
        fun exercisesOn(day: String): List<Exercise> = names[day].orEmpty().mapNotNull { name ->
            library.firstOrNull { it.name.equals(name, ignoreCase = true) }?.toPlanExercise(planId = 1)
        }
        val day = intent.getStringExtra("day") ?: "Monday"
        val selected = intent.getStringExtra("select")
        val subtitle = intent.getStringExtra("note")

        setContent {
            StrivoTheme {
                var state by remember {
                    val exercises = exercisesOn(day)
                    mutableStateOf(
                        BodyUiState(
                            loading = false,
                            error = if (model == null) "The 3D body could not be loaded." else null,
                            catalogue = catalogue,
                            body = body,
                            gender = gender,
                            model = model,
                            day = day,
                            today = day,
                            dayExercises = exercises,
                            library = library,
                            trained = TrainedMusclesFinder.find(exercises, library, catalogue),
                            notes = if (selected != null && subtitle != null) mapOf(selected to MuscleNote(subtitle, "")) else emptyMap(),
                            selected = selected,
                            skinOn = intent.getBooleanExtra("skin", selected == null),
                            skeletonOn = intent.getBooleanExtra("skeleton", false),
                        ),
                    )
                }
                BodyContent(
                    ui = state,
                    onBack = { finish() },
                    onSelectDay = { newDay ->
                        val exercises = exercisesOn(newDay)
                        state = state.copy(day = newDay, selected = null, skinOn = true, dayExercises = exercises, trained = TrainedMusclesFinder.find(exercises, library, catalogue))
                    },
                    onSelect = { id -> state = state.copy(selected = id, skinOn = id == null) },
                    onToggleSkin = { state = state.copy(skinOn = !state.skinOn) },
                    onToggleSkeleton = { state = state.copy(skeletonOn = !state.skeletonOn, skinOn = if (!state.skeletonOn) false else state.selected == null) },
                    onAddExercise = { added ->
                        val exercises = state.dayExercises + added.toPlanExercise(planId = 1)
                        state = state.copy(dayExercises = exercises, trained = TrainedMusclesFinder.find(exercises, library, catalogue))
                    },
                    onSampleDay = {
                        val exercises = state.dayExercises + MuscleExercises.sampleDay(state.day, library).map { it.toPlanExercise(planId = 1) }
                        state = state.copy(dayExercises = exercises, trained = TrainedMusclesFinder.find(exercises, library, catalogue))
                    },
                )
            }
        }
    }
}
