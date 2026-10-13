package com.example.strivo.domain.plan

import com.example.strivo.domain.model.Exercise
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanStatusTest {
    private fun exercise(id: Long, done: Boolean) = Exercise(
        id = id, planId = 1, name = "Exercise $id", weight = "40", sets = "3", reps = "10", notes = "", isCheck = done,
    )

    @Test
    fun aPlanIsDoneOnceEveryExerciseIsTickedOff() {
        assertTrue(isPlanDoneToday(listOf(exercise(1, true), exercise(2, true)), workoutFinished = false, presentAtFinish = null, isExtraPlan = false))
        assertFalse(isPlanDoneToday(listOf(exercise(1, true), exercise(2, false)), workoutFinished = false, presentAtFinish = null, isExtraPlan = false))
    }

    @Test
    fun aFinishedWorkoutKeepsTheExercisesYouSkippedDone() {
        // Finished early with exercise 2 left undone: it was there at the time, so the plan stays done.
        val exercises = listOf(exercise(1, true), exercise(2, false))
        assertTrue(isPlanDoneToday(exercises, workoutFinished = true, presentAtFinish = setOf(1L, 2L), isExtraPlan = false))
    }

    @Test
    fun anExerciseAddedAfterFinishingBringsThePlanBack() {
        val exercises = listOf(exercise(1, true), exercise(2, false), exercise(3, false))
        assertFalse(isPlanDoneToday(exercises, workoutFinished = true, presentAtFinish = setOf(1L, 2L), isExtraPlan = false))
        // And once that one is ticked off it is done again.
        val allDone = listOf(exercise(1, true), exercise(2, false), exercise(3, true))
        assertTrue(isPlanDoneToday(allDone, workoutFinished = true, presentAtFinish = setOf(1L, 2L), isExtraPlan = false))
    }

    @Test
    fun withoutARecordARegularPlanStaysDoneButExtrasNeedEveryTick() {
        val exercises = listOf(exercise(1, true), exercise(2, false))
        assertTrue(isPlanDoneToday(exercises, workoutFinished = true, presentAtFinish = null, isExtraPlan = false))
        assertFalse(isPlanDoneToday(exercises, workoutFinished = true, presentAtFinish = null, isExtraPlan = true))
    }

    @Test
    fun aPlanWithNoExercisesIsDoneOnlyIfItsWorkoutWas() {
        assertTrue(isPlanDoneToday(emptyList(), workoutFinished = true, presentAtFinish = null, isExtraPlan = false))
        assertFalse(isPlanDoneToday(emptyList(), workoutFinished = false, presentAtFinish = null, isExtraPlan = false))
    }
}
