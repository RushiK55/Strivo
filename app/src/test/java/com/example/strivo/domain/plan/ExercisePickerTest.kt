package com.example.strivo.domain.plan

import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.LibraryExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExercisePickerTest {
    private fun lib(name: String, group: String, equipment: String = "") =
        LibraryExercise(name.lowercase().replace(' ', '-'), name, group, equipment, "", "", "", 3, 10, 0.0, "", true)

    private fun inPlan(name: String) = Exercise(planId = 1, name = name, weight = "0.0", sets = "3", reps = "10", notes = "")

    private val library = listOf(
        lib("Squat", "Legs", "Barbell"),
        lib("Leg Press", "Legs", "Machine"),
        lib("Calf Raise", "Legs"),
        lib("Hip Thrust", "Glutes", "Barbell"),
        lib("Bench Press", "Chest", "Barbell"),
    )

    @Test
    fun theGroupsAPlanTrainsComeFromItsExercisesMostUsedFirst() {
        val plan = listOf(inPlan("squat"), inPlan("Leg Press"), inPlan("Hip Thrust"), inPlan("Something Custom"))
        assertEquals(listOf("Legs", "Glutes"), ExercisePicker.groupsOf(plan, library))
        assertTrue(ExercisePicker.groupsOf(emptyList(), library).isEmpty())
    }

    @Test
    fun suggestedShowsThePlansGroupsWithWhatIsNotInThePlanFirst() {
        val plan = listOf(inPlan("Squat"))
        val names = ExercisePicker.list(library, plan, "", ExercisePicker.SUGGESTED).map { it.name }
        assertEquals(listOf("Calf Raise", "Leg Press", "Squat"), names)
    }

    @Test
    fun allAndAGroupChipListTheLibrary() {
        assertEquals(5, ExercisePicker.list(library, emptyList(), "", ExercisePicker.ALL).size)
        assertEquals(listOf("Bench Press"), ExercisePicker.list(library, emptyList(), "", "Chest").map { it.name })
    }

    @Test
    fun aSearchLooksAtTheWholeLibraryWhateverTheChip() {
        val found = ExercisePicker.list(library, listOf(inPlan("Squat")), "barbell", ExercisePicker.SUGGESTED).map { it.name }
        assertEquals(listOf("Bench Press", "Hip Thrust", "Squat"), found)
    }

    @Test
    fun anExerciseIsInThePlanWhateverItsSpelling() {
        assertTrue(ExercisePicker.isIn(listOf(inPlan("leg  press")), library[1]))
        assertFalse(ExercisePicker.isIn(listOf(inPlan("Squat")), library[1]))
    }
}
