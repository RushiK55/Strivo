package com.example.strivo.domain.body

import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.LibraryExercise

/** A library exercise that works a muscle: [role] says whether it is the main muscle or a helper, [inDay] whether the day has it already. */
data class MuscleChoice(val exercise: LibraryExercise, val role: MuscleRole, val inDay: Boolean)

/**
 * Which exercises to offer for a muscle, and a starter day for someone with no plan. Both come from the exercise library, so what a
 * trainer or admin sets there (which muscles an exercise works, new exercises) is what the person sees; nothing here is fixed in the app.
 */
object MuscleExercises {
    /** At most this many are offered for one muscle. */
    const val MAX_CHOICES = 12

    /** The exercises that work muscle [id]: the ones that train it mainly first, then the ones that help, each A to Z. */
    fun forMuscle(id: String, library: List<LibraryExercise>, dayExercises: List<Exercise>): List<MuscleChoice> {
        val inDay = dayExercises.map { TrainedMusclesFinder.normalise(it.name) }.toSet()
        fun choice(e: LibraryExercise, role: MuscleRole) = MuscleChoice(e, role, TrainedMusclesFinder.normalise(e.name) in inDay)
        val main = library.filter { id in it.primaryMuscles }.sortedBy { it.name.lowercase() }.map { choice(it, MuscleRole.Main) }
        val helping = library.filter { id !in it.primaryMuscles && id in it.secondaryMuscles }.sortedBy { it.name.lowercase() }.map { choice(it, MuscleRole.Helper) }
        return (main + helping).take(MAX_CHOICES)
    }

    /** A starter day for each weekday: push, pull, legs, shoulders and arms, then a mix. */
    private val sampleNames: Map<String, List<String>> = mapOf(
        "Monday" to listOf("Bench Press", "Overhead Press", "Lateral Raise"),
        "Tuesday" to listOf("Pull-Up", "Barbell Row", "Barbell Curl"),
        "Wednesday" to listOf("Squat", "Romanian Deadlift", "Calf Raise"),
        "Thursday" to listOf("Dumbbell Shoulder Press", "Face Pull", "Hammer Curl"),
        "Friday" to listOf("Deadlift", "Leg Press", "Plank"),
        "Saturday" to listOf("Incline Bench Press", "Lat Pulldown", "Tricep Pushdown"),
        "Sunday" to listOf("Cycling", "Glute Bridge", "Plank"),
    )

    /** The starter day's exercises that the library has (the library may have changed names), for [day]. */
    fun sampleDay(day: String, library: List<LibraryExercise>): List<LibraryExercise> {
        val byName = library.associateBy { TrainedMusclesFinder.normalise(it.name) }
        return sampleNames[day].orEmpty().mapNotNull { byName[TrainedMusclesFinder.normalise(it)] }
    }
}
