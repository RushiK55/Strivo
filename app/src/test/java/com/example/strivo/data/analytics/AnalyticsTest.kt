package com.example.strivo.data.analytics

import com.example.strivo.data.DemoData
import com.example.strivo.data.model.ExerciseSet
import com.example.strivo.data.model.PerformedExercise
import com.example.strivo.data.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AnalyticsTest {
    private val today = LocalDate.of(2026, 10, 8)

    private fun session(daysAgo: Int, plan: String, weight: Double, reps: Int, sets: Int, total: String = "00:30:00") =
        WorkoutSession(
            planName = plan,
            date = today.minusDays(daysAgo.toLong()).atTime(LocalTime.NOON),
            totalTime = total,
            exercises = listOf(
                PerformedExercise(
                    "Bench Press",
                    List(sets) { ExerciseSet(weight, reps, isCompleted = true, setDuration = "00:00:30", restTime = "00:01:30") },
                )
            ),
        )

    @Test
    fun parsesDurations() {
        assertEquals(3723L, parseDurationSeconds("01:02:03"))
        assertEquals(0L, parseDurationSeconds(""))
        assertEquals(0L, parseDurationSeconds("garbage"))
        assertEquals(0L, parseDurationSeconds("1:xx:3"))
    }

    @Test
    fun totalsAreSummedFromCompletedSets() {
        // 3 sets of 10 x 50 kg = 1500 kg on each of two days.
        val sessions = listOf(session(1, "Push", 50.0, 10, 3), session(2, "Push", 50.0, 10, 3))
        val report = buildReport(sessions, AnalyticsRange.Week, today)

        assertEquals(2, report.workouts)
        assertEquals(2, report.activeDays)
        assertEquals(3000.0, report.volumeKg, 0.001)
        assertEquals(6, report.sets)
        assertEquals(60, report.reps)
        assertEquals(3600L, report.totalSeconds)
        assertEquals(1800L, report.avgSessionSeconds)
        assertEquals(30L, report.avgSetSeconds)
        assertEquals(90L, report.avgRestSeconds)
        assertEquals(7, report.days.size)
        assertEquals(today, report.days.last().date)
    }

    @Test
    fun streakCountsBackFromTodayOrYesterday() {
        // Trained yesterday and the day before, not yet today: the streak is still alive.
        val alive = buildReport(listOf(session(1, "A", 10.0, 5, 1), session(2, "A", 10.0, 5, 1)), AnalyticsRange.Week, today)
        assertEquals(2, alive.streak)

        // Including today extends it.
        val withToday = buildReport(listOf(session(0, "A", 10.0, 5, 1), session(1, "A", 10.0, 5, 1)), AnalyticsRange.Week, today)
        assertEquals(2, withToday.streak)

        // A gap of a full day breaks it.
        val broken = buildReport(listOf(session(2, "A", 10.0, 5, 1), session(3, "A", 10.0, 5, 1)), AnalyticsRange.Week, today)
        assertEquals(0, broken.streak)
    }

    @Test
    fun comparesWithThePreviousPeriod() {
        val sessions = listOf(
            session(1, "A", 100.0, 10, 1), // this week: 1000 kg
            session(9, "A", 50.0, 10, 1),  // previous week: 500 kg
        )
        val report = buildReport(sessions, AnalyticsRange.Week, today)
        assertEquals(100.0, report.volumeChangePercent!!, 0.001)

        assertNull(buildReport(listOf(session(1, "A", 100.0, 10, 1)), AnalyticsRange.Week, today).volumeChangePercent)
    }

    @Test
    fun ignoresWorkoutsOutsideTheRange() {
        val report = buildReport(listOf(session(10, "Old", 50.0, 10, 3)), AnalyticsRange.Week, today)
        assertTrue(report.isEmpty)
        assertEquals(0.0, report.volumeKg, 0.0)
        assertEquals(1, buildReport(listOf(session(10, "Old", 50.0, 10, 3)), AnalyticsRange.Month, today).workouts)
    }

    @Test
    fun demoWeekHasFiveWorkoutsAndTwoRestDays() {
        val report = buildReport(DemoData.sessions(today), AnalyticsRange.Week, today)

        assertEquals(5, report.workouts)
        assertEquals(5, report.activeDays)
        assertEquals(2, report.days.count { it.isRestDay })
        assertTrue(report.days[3].isRestDay) // three days ago was a rest day
        assertTrue(report.days.last().isRestDay) // nothing yet today
        assertEquals(2, report.streak)
        assertTrue(report.volumeKg > 20_000)
        assertEquals(2, report.plans.first { it.name == "Chest & Triceps" }.workouts)
        assertEquals(5, report.topExercises.size)
        // The second chest day lifts more than the first, so the plan's best weight is the later one.
        assertEquals(62.5, report.topExercises.first { it.name == "Bench Press" }.bestWeightKg, 0.001)
    }

    @Test
    fun demoDataIsTheSameEveryTime() {
        assertEquals(DemoData.sessions(today).map { it.totalTime }, DemoData.sessions(today).map { it.totalTime })
    }
}
