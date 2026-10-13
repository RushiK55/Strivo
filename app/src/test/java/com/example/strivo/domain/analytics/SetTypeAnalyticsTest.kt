package com.example.strivo.domain.analytics

import com.example.strivo.data.demo.DemoData
import com.example.strivo.domain.model.ExerciseSet
import com.example.strivo.domain.model.PerformedExercise
import com.example.strivo.domain.model.SetType
import com.example.strivo.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SetTypeAnalyticsTest {
    private val today = LocalDate.of(2026, 10, 12)

    private fun set(kg: Double, reps: Int, type: SetType = SetType.Normal) =
        ExerciseSet(weight = kg, reps = reps, isCompleted = true, type = type)

    private fun workout(daysAgo: Long, vararg sets: ExerciseSet) = WorkoutSession(
        planName = "Chest",
        date = today.minusDays(daysAgo).atTime(LocalTime.of(8, 0)),
        totalTime = "00:45:00",
        exercises = listOf(PerformedExercise("Bench Press", sets.toList())),
    )

    // Warm-up 30 x 10, work 60 x 8, and a drop 55 x 12 that would out-estimate the work set if it counted.
    private val typed = workout(
        1,
        set(30.0, 10, SetType.WarmUp), set(60.0, 8), set(60.0, 8), set(55.0, 12, SetType.Drop),
    )

    @Test
    fun warmUpsAreLeftOutOfVolumeAndSetCounts() {
        val report = buildReport(listOf(typed), AnalyticsRange.Week, today)
        // 60 x 8 twice and the drop 55 x 12; the 30 x 10 warm-up adds nothing.
        assertEquals(60.0 * 8 * 2 + 55.0 * 12, report.volumeKg, 0.001)
        assertEquals(3, report.sets)
        assertEquals(8 + 8 + 12, report.reps)
        assertEquals(60.0, report.topExercises.single().bestWeightKg, 0.0)
    }

    @Test
    fun dropSetsAndWarmUpsNeverSetARecord() {
        val history = exerciseHistories(listOf(typed)).single()
        val session = history.sessions.single()
        assertEquals(60.0, session.topWeightKg, 0.0)
        // 55 x 12 would estimate higher (77) than 60 x 8 (76), so it must be ignored.
        assertEquals(76.0, session.bestEstimatedMaxKg!!, 0.001)
        assertEquals(8, session.mostReps)
        assertEquals(60.0 * 8 * 2 + 55.0 * 12, session.volumeKg, 0.001)
        assertEquals(3, session.workSetCount)

        assertEquals(60.0, history.records.first { it.type == RecordType.HeaviestWeight }.valueKg, 0.0)
        assertEquals(listOf(8), history.repMaxes.map { it.reps }) // no 10-rep (warm-up) or 12-rep (drop) entry
    }

    @Test
    fun aWorkoutOfOnlyDropSetsStillShows() {
        val history = exerciseHistories(listOf(workout(2, set(50.0, 10, SetType.Drop), set(40.0, 12, SetType.Drop)))).single()
        assertEquals(50.0, history.latest.topWeightKg, 0.0)
    }

    @Test
    fun aWorkoutOfOnlyWarmUpsIsNotAnExercise() {
        assertTrue(exerciseHistories(listOf(workout(2, set(30.0, 10, SetType.WarmUp)))).isEmpty())
    }

    @Test
    fun progressIgnoresTheTypedSets() {
        // Same working weight twice; the second day only differs by a heavier-looking drop set and a warm-up.
        val plain = workout(9, set(60.0, 8), set(60.0, 8))
        val history = exerciseHistories(listOf(plain, typed)).single()
        assertEquals(0.0, history.changeKg, 0.0)
        val strength = strengthProgress(listOf(plain, workout(1, set(30.0, 10, SetType.WarmUp), set(60.0, 8), set(80.0, 3, SetType.Drop))))
        assertEquals(0.0, strength.single().changeKg, 0.0)
    }

    @Test
    fun weeklyVolumeSkipsWarmUps() {
        // Yesterday was a Sunday, so it belongs to last week.
        val week = weeklyVolume(listOf(typed), today, weeks = 2).first()
        assertEquals(60.0 * 8 * 2 + 55.0 * 12, week.volumeKg, 0.001)
    }

    @Test
    fun demoDataHasWarmUpsAndDropsThatDoNotChangeTheProgress() {
        val sessions = DemoData.sessions(today)
        val sets = sessions.flatMap { it.exercises.flatMap { e -> e.sets } }
        assertTrue(sets.any { it.type == SetType.WarmUp })
        assertTrue(sets.any { it.type == SetType.Drop })
        // The set before a drop has no rest, since the drop follows straight on.
        sessions.flatMap { it.exercises }.forEach { exercise ->
            exercise.sets.zipWithNext().filter { (_, next) -> next.type == SetType.Drop }
                .forEach { (before, _) -> assertEquals("", before.restTime) }
        }
        val bench = exerciseHistories(sessions).first { it.name == "Bench Press" }
        assertEquals(5.0, bench.changeKg, 0.001)
        assertTrue(bench.sessions.all { s -> s.sets.any { it.type == SetType.WarmUp } })
    }
}
