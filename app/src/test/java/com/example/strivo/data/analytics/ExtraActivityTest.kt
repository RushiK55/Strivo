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

class ExtraActivityTest {
    private val today = LocalDate.of(2026, 10, 8)

    private fun activity(daysAgo: Int, name: String, kcal: Int, minutes: Int? = null) =
        ExtraActivity(date = today.minusDays(daysAgo.toLong()), name = name, calories = kcal, minutes = minutes)

    // A 45-minute workout; at 80 kg that is 5 x 80 x 0.75 = 300 kcal.
    private val workout = WorkoutSession(
        planName = "Push",
        date = today.minusDays(1).atTime(LocalTime.NOON),
        totalTime = "00:45:00",
        exercises = listOf(PerformedExercise("Bench Press", listOf(ExerciseSet(60.0, 8, isCompleted = true)))),
    )

    @Test
    fun extraCaloriesAddToTheDayAndTheTotal() {
        val extras = listOf(activity(1, "Running", 400, 40), activity(1, "Walking", 100), activity(3, "Cycling", 250))
        val report = buildReport(listOf(workout), AnalyticsRange.Week, today, extras) { 80.0 }

        assertEquals(750.0, report.extraCalories, 0.001)
        assertEquals(300.0, report.workoutCalories!!, 0.001)
        assertEquals(1050.0, report.caloriesBurned!!, 0.001)
        val yesterday = report.days[5]
        assertEquals(500.0, yesterday.extraKcal, 0.001)
        assertEquals(800.0, yesterday.burnedKcal, 0.001) // workout 300 + extras 500
        assertEquals(2, yesterday.extras.size)
        assertEquals(250.0, report.days[3].burnedKcal, 0.001) // a day with only an extra
    }

    @Test
    fun extraCaloriesCountEvenWithoutABodyWeight() {
        // Nothing is estimated for the workout, but what the user typed in still counts.
        val report = buildReport(listOf(workout), AnalyticsRange.Week, today, listOf(activity(2, "Football", 450)))
        assertNull(report.workoutCalories)
        assertEquals(450.0, report.caloriesBurned!!, 0.001)
    }

    @Test
    fun noWeightAndNoExtrasMeansNothingIsInvented() {
        val report = buildReport(listOf(workout), AnalyticsRange.Week, today)
        assertNull(report.caloriesBurned)
        assertNull(report.workoutCalories)
        assertEquals(0.0, report.extraCalories, 0.0)
    }

    @Test
    fun activityOutsideTheRangeIsIgnored() {
        val report = buildReport(emptyList(), AnalyticsRange.Week, today, listOf(activity(10, "Old run", 500)), { 80.0 })
        assertEquals(0.0, report.extraCalories, 0.0)
        val month = buildReport(emptyList(), AnalyticsRange.Month, today, listOf(activity(10, "Old run", 500)), { 80.0 })
        assertEquals(500.0, month.extraCalories, 0.0)
    }

    @Test
    fun demoActivitiesFallWithinTheWeek() {
        val demo = DemoData.activities(today)
        assertTrue(demo.isNotEmpty())
        assertTrue(demo.all { it.date in today.minusDays(6)..today && it.calories > 0 })
        val report = buildReport(DemoData.sessions(today), AnalyticsRange.Week, today, demo) { 72.0 }
        assertEquals(demo.sumOf { it.calories }.toDouble(), report.extraCalories, 0.001)
        // The walk is on the rest day, so that day still shows something burned.
        assertTrue(report.days[3].isRestDay && report.days[3].burnedKcal > 0)
    }
}
