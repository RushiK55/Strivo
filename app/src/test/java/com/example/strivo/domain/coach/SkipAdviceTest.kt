package com.example.strivo.domain.coach

import com.example.strivo.domain.model.ExerciseSet
import com.example.strivo.domain.model.PerformedExercise
import com.example.strivo.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SkipAdviceTest {
    private val today = LocalDate.of(2026, 10, 17)

    /** A finished "Leg Day" [daysAgo] days ago that did a squat and left out [skipped]. */
    private fun legDay(daysAgo: Long, vararg skipped: String, plan: String = "Leg Day") = WorkoutSession(
        planName = plan,
        date = today.minusDays(daysAgo).atTime(LocalTime.of(18, 0)),
        totalTime = "00:40:00",
        exercises = listOf(PerformedExercise("Squat", List(3) { ExerciseSet(60.0, 8, isCompleted = true) })),
        skipped = skipped.toList(),
    )

    @Test
    fun anExerciseLeftOutOfMostOfTheLastWorkoutsIsPointedOut() {
        val advice = skipAdvice(listOf(legDay(1, "Leg Extension"), legDay(3, "Leg Extension"), legDay(5), legDay(7, "Leg Extension")), today)
        val skipped = advice.single()
        assertEquals(AdviceKind.Skipped, skipped.kind)
        assertEquals("Leg Extension", skipped.exercise)
        assertEquals("Leg Day", skipped.planName)
        assertEquals("You keep skipping Leg Extension", skipped.title)
        assertTrue(skipped.detail.contains("3 of your last 4 Leg Day workouts"))
    }

    @Test
    fun doingItInTheLatestWorkoutClearsTheAdvice() {
        val sessions = listOf(legDay(1), legDay(3, "Leg Extension"), legDay(5, "Leg Extension"), legDay(7, "Leg Extension"))
        assertTrue(skipAdvice(sessions, today).isEmpty())
    }

    @Test
    fun aFewSkipsOrAFewWorkoutsAreNotAHabit() {
        assertTrue(skipAdvice(listOf(legDay(1, "Leg Extension"), legDay(3, "Leg Extension"), legDay(5), legDay(7)), today).isEmpty())
        assertTrue(skipAdvice(listOf(legDay(1, "Leg Extension"), legDay(3, "Leg Extension")), today).isEmpty())
    }

    @Test
    fun onlyTheLastFourWorkoutsOfAPlanCountAndEachPlanIsLookedAtOnItsOwn() {
        // old skips fall out of the window of four
        val old = listOf(legDay(1, "Calf Raise"), legDay(2), legDay(3), legDay(4), legDay(5, "Calf Raise"), legDay(6, "Calf Raise"))
        assertTrue(skipAdvice(old, today).isEmpty())
        // another plan's workouts do not mix in
        val two = listOf(legDay(1, "Curl", plan = "Arms"), legDay(2, "Curl", plan = "Arms"), legDay(3, "Curl", plan = "Arms"), legDay(1, "Squat"), legDay(2))
        assertEquals(listOf("Arms"), skipAdvice(two, today).map { it.planName })
    }

    @Test
    fun workoutsOlderThanTwoMonthsAreIgnored() {
        assertTrue(skipAdvice(listOf(legDay(70, "Curl"), legDay(75, "Curl"), legDay(80, "Curl")), today).isEmpty())
    }

    @Test
    fun takingItOutOfThePlanIsWhereAcceptingChangesThings() {
        val advice = skipAdvice(listOf(legDay(1, "Leg Extension"), legDay(3, "Leg Extension"), legDay(5, "Leg Extension")), today).single()
        assertEquals("Leg Day", advice.acceptedIn(listOf(PlannedExercise("Leg Day", "Thursday", 30.0))))
        assertEquals(null, advice.acceptedIn(emptyList()))
    }

    @Test
    fun aWorkoutRemembersWhatItSkippedThroughItsStoredText() {
        val session = legDay(1, "Leg Extension", "Calf Raise")
        assertEquals(listOf("Leg Extension", "Calf Raise"), WorkoutSession.skippedFromJson(session.skippedJson()))
        assertTrue(WorkoutSession.skippedFromJson("").isEmpty())
        assertTrue(WorkoutSession.skippedFromJson("not json").isEmpty())
    }
}
