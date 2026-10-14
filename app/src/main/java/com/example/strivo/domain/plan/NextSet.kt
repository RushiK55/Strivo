package com.example.strivo.domain.plan

import com.example.strivo.domain.model.Exercise

/** A set in a workout: which exercise and which of its sets. */
data class SetPosition(val exerciseIndex: Int, val setIndex: Int)

/**
 * The set a workout moves to next, when nothing is running.
 *
 * An exercise the user has started is finished first: if they did exercise 1, skipped 2 and began 3, then the next set is
 * the next one of 3, not the first set of 2. Only when that exercise is done does the workout go back to the earliest
 * exercise still to do (here 2). [currentExercise] is the exercise the user last started or chose themselves; it is null
 * when there is none, and then the earliest exercise still to do comes first. An exercise already ticked off is skipped.
 */
fun nextSet(exercises: List<Exercise>, currentExercise: Int?): SetPosition? {
    fun firstToDo(exerciseIndex: Int): SetPosition? {
        val exercise = exercises.getOrNull(exerciseIndex)?.takeIf { !it.isCheck } ?: return null
        return exercise.setsList.indexOfFirst { !it.isCompleted }.takeIf { it >= 0 }?.let { SetPosition(exerciseIndex, it) }
    }
    currentExercise?.let(::firstToDo)?.let { return it }
    return exercises.indices.firstNotNullOfOrNull(::firstToDo)
}
