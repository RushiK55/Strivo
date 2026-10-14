package com.example.strivo.domain.body

import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.LibraryExercise

/** One of the user's exercises, and how it trains a particular muscle. */
data class MuscleUse(val exercise: Exercise, val role: MuscleRole)

/**
 * Works out which muscles a list of the user's exercises (a day's plan) trains, from the exercise library: an exercise in the
 * plan is matched to the library by name, and the library says which muscles it trains mainly and which it helps.
 *
 * An exercise the library knows only by its broad group (no muscles set on it yet: "Back") lights that group's surface muscles as
 * helpers, which is honest about it being approximate. One the library does not know at all (an exercise the user made up) says
 * nothing, so it never lights a muscle by guesswork.
 */
object TrainedMusclesFinder {
    /** Letters and digits only, lower case: "Lat Pull-Down" and "lat pulldown" are the same name. */
    fun normalise(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    private fun index(library: List<LibraryExercise>): Map<String, LibraryExercise> {
        val byName = HashMap<String, LibraryExercise>()
        for (exercise in library) {
            val key = normalise(exercise.name)
            val known = byName[key]
            // Two exercises with one name: the one that says which muscles it trains is the more useful.
            if (known == null || (known.primaryMuscles.isEmpty() && exercise.primaryMuscles.isNotEmpty())) byName[key] = exercise
        }
        return byName
    }

    fun find(exercises: List<Exercise>, library: List<LibraryExercise>, catalogue: MuscleCatalogue): TrainedMuscles {
        val byName = index(library)
        val main = linkedSetOf<String>()
        val helper = linkedSetOf<String>()
        for (exercise in exercises) {
            val known = byName[normalise(exercise.name)] ?: continue
            if (known.primaryMuscles.isNotEmpty()) {
                main += known.primaryMuscles
                helper += known.secondaryMuscles
            } else {
                helper += catalogue.groups.filter { it.libraryGroup == known.muscleGroup && it.layer == MuscleLayer.Surface }.map { it.id }
            }
        }
        helper -= main
        return TrainedMuscles(main, helper)
    }

    /** The user's exercises that train muscle [id], the ones that train it mainly first. */
    fun usesOf(id: String, exercises: List<Exercise>, library: List<LibraryExercise>, catalogue: MuscleCatalogue): List<MuscleUse> {
        val byName = index(library)
        val groupOfId = catalogue.group(id)
        val uses = exercises.mapNotNull { exercise ->
            val known = byName[normalise(exercise.name)] ?: return@mapNotNull null
            when {
                id in known.primaryMuscles -> MuscleUse(exercise, MuscleRole.Main)
                id in known.secondaryMuscles -> MuscleUse(exercise, MuscleRole.Helper)
                known.primaryMuscles.isEmpty() && groupOfId != null && groupOfId.layer == MuscleLayer.Surface && groupOfId.libraryGroup == known.muscleGroup ->
                    MuscleUse(exercise, MuscleRole.Helper)
                else -> null
            }
        }
        return uses.sortedBy { it.role.ordinal }
    }
}
