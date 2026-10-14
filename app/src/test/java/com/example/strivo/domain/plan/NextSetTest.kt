package com.example.strivo.domain.plan

import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.ExerciseSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextSetTest {
    private fun exercise(name: String, done: Int, total: Int, isCheck: Boolean = false) = Exercise(
        planId = 1, name = name, weight = "20", sets = total.toString(), reps = "10", notes = "", isCheck = isCheck,
        setsList = List(total) { ExerciseSet(weight = 20.0, reps = 10, isCompleted = it < done) },
    )

    @Test
    fun withNothingStartedTheEarliestExerciseComesFirst() {
        val exercises = listOf(exercise("A", 0, 3), exercise("B", 0, 3))
        assertEquals(SetPosition(0, 0), nextSet(exercises, currentExercise = null))
    }

    @Test
    fun anExerciseThatWasStartedIsFinishedBeforeGoingBackToASkippedOne() {
        // 1 is done, 2 was skipped, 3 was started and has one set done: the next set is set 2 of exercise 3.
        val exercises = listOf(exercise("A", 3, 3, isCheck = true), exercise("B", 0, 3), exercise("C", 1, 3))
        assertEquals(SetPosition(2, 1), nextSet(exercises, currentExercise = 2))
    }

    @Test
    fun whenThatExerciseIsDoneTheWorkoutGoesBackToTheSkippedOne() {
        val exercises = listOf(exercise("A", 3, 3, isCheck = true), exercise("B", 0, 3), exercise("C", 3, 3, isCheck = true))
        assertEquals(SetPosition(1, 0), nextSet(exercises, currentExercise = 2))
        // ... also when the exercise is done but not ticked off yet (all its sets are completed)
        val unticked = listOf(exercise("A", 3, 3, isCheck = true), exercise("B", 0, 3), exercise("C", 3, 3))
        assertEquals(SetPosition(1, 0), nextSet(unticked, currentExercise = 2))
    }

    @Test
    fun theUsersOwnChoiceIsFollowedWhenTheyChangeExerciseByHand() {
        // Exercise 3 was in progress, then the user starts exercise 2 themselves: 2 is now the one being worked on.
        val exercises = listOf(exercise("A", 3, 3, isCheck = true), exercise("B", 1, 3), exercise("C", 1, 3))
        assertEquals(SetPosition(1, 1), nextSet(exercises, currentExercise = 1))
        assertEquals(SetPosition(2, 1), nextSet(exercises, currentExercise = 2))
    }

    @Test
    fun anExerciseTickedOffIsNeverTheNextOneAndMissingIndexesAreIgnored() {
        val exercises = listOf(exercise("A", 3, 3, isCheck = true), exercise("B", 0, 2))
        assertEquals(SetPosition(1, 0), nextSet(exercises, currentExercise = 0))
        assertEquals(SetPosition(1, 0), nextSet(exercises, currentExercise = 7))
        assertEquals(SetPosition(1, 0), nextSet(exercises, currentExercise = -1))
    }

    @Test
    fun nothingIsNextWhenEverythingIsDone() {
        val exercises = listOf(exercise("A", 2, 2, isCheck = true), exercise("B", 2, 2))
        assertNull(nextSet(exercises, currentExercise = 1))
        assertNull(nextSet(emptyList(), currentExercise = null))
    }
}
