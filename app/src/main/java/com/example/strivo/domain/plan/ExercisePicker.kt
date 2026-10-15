package com.example.strivo.domain.plan

import com.example.strivo.domain.body.TrainedMusclesFinder
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.LibraryExercise

/**
 * What the exercise picker of a plan offers. It is built for adding to one plan: the muscle groups the plan already trains come
 * first ("Suggested"), what is already in the plan is marked and goes to the end, and the rest of the library is a tap away.
 * Pure functions, covered by ExercisePickerTest.
 */
object ExercisePicker {
    /** The chip that shows the exercises for the muscle groups the plan already trains. */
    const val SUGGESTED = "Suggested"
    const val ALL = "All"

    private fun key(name: String) = TrainedMusclesFinder.normalise(name)

    /** The muscle groups of the plan's exercises (found through the library by name), the most used first. */
    fun groupsOf(planExercises: List<Exercise>, library: List<LibraryExercise>): List<String> {
        val byName = library.associate { key(it.name) to it.muscleGroup }
        return planExercises.mapNotNull { byName[key(it.name)] }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
    }

    /** Whether the plan already has this exercise. */
    fun isIn(planExercises: List<Exercise>, exercise: LibraryExercise): Boolean =
        planExercises.any { key(it.name) == key(exercise.name) }

    /**
     * The exercises to list for the search [query] and the chosen [chip] ([SUGGESTED], [ALL] or a muscle group): not yet in the plan
     * first, then those already in it, each A to Z. A search looks at the whole library (name, muscle group or equipment) whatever
     * the chip, so what someone types is always found.
     */
    fun list(library: List<LibraryExercise>, planExercises: List<Exercise>, query: String, chip: String): List<LibraryExercise> {
        val text = query.trim()
        val suggested = groupsOf(planExercises, library).toSet()
        val matches = library.filter { e ->
            if (text.isNotEmpty()) listOf(e.name, e.muscleGroup, e.equipment).any { it.contains(text, ignoreCase = true) }
            else when (chip) {
                ALL -> true
                SUGGESTED -> e.muscleGroup in suggested
                else -> e.muscleGroup == chip
            }
        }
        return matches.sortedWith(compareBy<LibraryExercise> { isIn(planExercises, it) }.thenBy { it.name.lowercase() })
    }
}
