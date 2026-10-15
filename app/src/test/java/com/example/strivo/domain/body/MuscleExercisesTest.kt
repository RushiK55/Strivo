package com.example.strivo.domain.body

import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.LibraryExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MuscleExercisesTest {
    private fun exercise(name: String) = Exercise(planId = 1, name = name, weight = "0.0", sets = "3", reps = "10", notes = "")
    private fun lib(name: String, group: String, primary: List<String> = emptyList(), secondary: List<String> = emptyList()) =
        LibraryExercise(name.lowercase().replace(' ', '-'), name, group, "", "", "", "", 3, 10, 0.0, "", true, 0, primary, secondary)

    private val library = listOf(
        lib("Pull-Up", "Back", listOf("lats"), listOf("biceps")),
        lib("Barbell Row", "Back", listOf("lats", "mid-back"), listOf("biceps")),
        lib("Barbell Curl", "Biceps", listOf("biceps")),
        lib("Bench Press", "Chest", listOf("chest"), listOf("triceps")),
    )

    @Test
    fun theExercisesForAMuscleListTheMainOnesFirstThenTheHelpersAndSayWhatTheDayHas() {
        val choices = MuscleExercises.forMuscle("biceps", library, listOf(exercise("pull-up"), exercise("Barbell Curl")))
        assertEquals(listOf("Barbell Curl", "Barbell Row", "Pull-Up"), choices.map { it.exercise.name })
        assertEquals(listOf(MuscleRole.Main, MuscleRole.Helper, MuscleRole.Helper), choices.map { it.role })
        assertEquals(listOf(true, false, true), choices.map { it.inDay })
        assertTrue(MuscleExercises.forMuscle("calves", library, emptyList()).isEmpty())
    }

    @Test
    fun noMoreThanTheLimitAreOffered() {
        val many = (1..30).map { lib("Move $it", "Back", listOf("lats")) }
        assertEquals(MuscleExercises.MAX_CHOICES, MuscleExercises.forMuscle("lats", many, emptyList()).size)
    }

    @Test
    fun aSampleDayComesFromTheLibraryAndSkipsWhatItLacks() {
        val day = MuscleExercises.sampleDay("Tuesday", library)
        assertEquals(listOf("Pull-Up", "Barbell Row", "Barbell Curl"), day.map { it.name })
        assertTrue(MuscleExercises.sampleDay("Tuesday", emptyList()).isEmpty())
        assertTrue(MuscleExercises.sampleDay("Someday", library).isEmpty())
    }

    @Test
    fun everyWeekdayHasASampleDayInTheStarterLibrary() {
        val starter = LibraryExercise.listFromJson(File("../admin/seed/library.json").readText())
        for (day in BodyDayNames) {
            val names = MuscleExercises.sampleDay(day, starter)
            assertEquals("$day has three sample exercises in the starter library", 3, names.size)
            assertFalse("$day trains something", names.all { it.primaryMuscles.isEmpty() })
        }
    }

    private companion object {
        val BodyDayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    }
}
