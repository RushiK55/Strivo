package com.example.strivo.data.analytics

import com.example.strivo.data.model.ExerciseSet
import com.example.strivo.data.model.PerformedExercise
import com.example.strivo.data.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class BodyMetricsTest {
    private val today = LocalDate.of(2026, 10, 8) // a Thursday

    private fun session(daysAgo: Int, exercise: String, vararg sets: Pair<Double, Int>) = WorkoutSession(
        planName = "Plan",
        date = today.minusDays(daysAgo.toLong()).atTime(LocalTime.NOON),
        totalTime = "00:45:00",
        exercises = listOf(PerformedExercise(exercise, sets.map { (w, r) -> ExerciseSet(w, r, isCompleted = true) })),
    )

    // --- BMI ---

    @Test
    fun bmiForAnAverageAdult() {
        val result = calculateBmi(175.0, 70.0)!!
        assertEquals(22.86, result.bmi, 0.01)
        assertEquals(BmiCategory.Normal, result.category)
        assertEquals(56.66, result.healthyMinKg, 0.01)
        assertEquals(76.26, result.healthyMaxKg, 0.01)
        assertEquals(0.0, result.kgToHealthyRange, 0.0)
    }

    @Test
    fun bmiCategoriesAndDistanceToHealthyRange() {
        val under = calculateBmi(175.0, 50.0)!!
        assertEquals(BmiCategory.Underweight, under.category)
        assertEquals(6.66, under.kgToHealthyRange, 0.01) // gain

        val over = calculateBmi(175.0, 85.0)!!
        assertEquals(BmiCategory.Overweight, over.category)
        assertEquals(-8.74, over.kgToHealthyRange, 0.01) // lose

        assertEquals(BmiCategory.Obese, calculateBmi(175.0, 100.0)!!.category)
        // 25.0 exactly is already overweight, 18.5 exactly is normal.
        assertEquals(BmiCategory.Overweight, calculateBmi(200.0, 100.0)!!.category)
        assertEquals(BmiCategory.Normal, calculateBmi(200.0, 74.0)!!.category)
    }

    @Test
    fun bmiNeedsPlausibleInputs() {
        assertNull(calculateBmi(null, 70.0))
        assertNull(calculateBmi(175.0, null))
        assertNull(calculateBmi(50.0, 70.0))
        assertNull(calculateBmi(175.0, 5.0))
    }

    // --- Calories ---

    @Test
    fun bmrUsesMifflinStJeor() {
        // 10*70 + 6.25*175 - 5*30 = 1643.75, then +5 / -161 / -78.
        assertEquals(1648.75, calculateBmr(BodyProfile("Male", 30, 175.0, 70.0))!!, 0.001)
        assertEquals(1482.75, calculateBmr(BodyProfile("Female", 30, 175.0, 70.0))!!, 0.001)
        assertEquals(1565.75, calculateBmr(BodyProfile("Other", 30, 175.0, 70.0))!!, 0.001)
    }

    @Test
    fun bmrIsNullWhenAnythingIsMissing() {
        assertNull(calculateBmr(BodyProfile("Male", null, 175.0, 70.0)))
        assertNull(calculateBmr(BodyProfile("Male", 30, null, 70.0)))
        assertNull(calculateBmr(BodyProfile("Male", 30, 175.0, null)))
        assertNull(calculateBmr(BodyProfile("Male", 3, 175.0, 70.0)))
    }

    @Test
    fun activityLevelFollowsTrainingDays() {
        assertEquals(1.2, activityLevelFor(0.0).factor, 0.0)
        assertEquals(1.375, activityLevelFor(2.0).factor, 0.0)
        assertEquals(1.375, activityLevelFor(3.0).factor, 0.0)
        assertEquals(1.55, activityLevelFor(4.5).factor, 0.0)
        assertEquals(1.725, activityLevelFor(6.0).factor, 0.0)
    }

    @Test
    fun workoutCaloriesAreMetTimesWeightTimesHours() {
        assertEquals(350.0, estimateWorkoutCalories(3600, 70.0), 0.001) // 5 x 70 x 1h
        assertEquals(175.0, estimateWorkoutCalories(1800, 70.0), 0.001)
    }

    @Test
    fun reportCostsWorkoutsWithTheWeightOfThatDay() {
        val sessions = listOf(session(1, "Bench Press", 60.0 to 8)) // 45 min
        val withWeight = buildReport(sessions, AnalyticsRange.Week, today) { 80.0 }
        assertEquals(5.0 * 80.0 * 0.75, withWeight.caloriesBurned!!, 0.001)

        // Without any known weight nothing is made up.
        assertNull(buildReport(sessions, AnalyticsRange.Week, today).caloriesBurned)
    }

    // --- Weight ---

    @Test
    fun weightOnADayIsTheLatestEntryOnOrBeforeIt() {
        val log = listOf(
            WeightEntry(LocalDate.of(2026, 10, 1), 80.0),
            WeightEntry(LocalDate.of(2026, 10, 5), 79.0),
        )
        assertEquals(80.0, weightAt(log, LocalDate.of(2026, 10, 4))!!, 0.0)
        assertEquals(79.0, weightAt(log, LocalDate.of(2026, 10, 8))!!, 0.0)
        assertEquals(80.0, weightAt(log, LocalDate.of(2026, 9, 1))!!, 0.0) // before the first entry
        assertNull(weightAt(emptyList(), today))
    }

    @Test
    fun weightProgressSummarisesTheLog() {
        val progress = weightProgress(
            listOf(
                WeightEntry(LocalDate.of(2026, 10, 8), 78.5),
                WeightEntry(LocalDate.of(2026, 10, 1), 80.0),
                WeightEntry(LocalDate.of(2026, 10, 4), 81.0),
            )
        )!!
        assertEquals(80.0, progress.first.kg, 0.0)
        assertEquals(78.5, progress.latest.kg, 0.0)
        assertEquals(-1.5, progress.changeKg, 0.001)
        assertEquals(81.0, progress.highKg, 0.0)
        assertEquals(78.5, progress.lowKg, 0.0)
        assertTrue(progress.hasTrend)
        assertTrue(!weightProgress(listOf(WeightEntry(today, 70.0)))!!.hasTrend)
        assertNull(weightProgress(emptyList()))
    }

    // --- Progress ---

    @Test
    fun strengthProgressComparesFirstAndLatestWorkout() {
        val sessions = listOf(
            session(14, "Bench Press", 60.0 to 8, 60.0 to 8),
            session(7, "Bench Press", 62.5 to 8),
            session(1, "Bench Press", 65.0 to 5, 60.0 to 10),
            session(1, "Plank", 0.0 to 1),
        )
        val bench = strengthProgress(sessions).single()
        assertEquals("Bench Press", bench.name)
        assertEquals(60.0, bench.firstBestKg, 0.0)
        assertEquals(65.0, bench.latestBestKg, 0.0)
        assertEquals(5.0, bench.changeKg, 0.0)
        assertEquals(3, bench.sessions)
        // The best estimate in the latest workout: 60 kg x 10 (80.0) beats 65 kg x 5 (75.8).
        assertEquals(60.0 * (1 + 10 / 30.0), bench.latestEstimatedMaxKg!!, 0.001)
    }

    @Test
    fun strengthProgressNeedsTwoWeightedWorkouts() {
        assertTrue(strengthProgress(listOf(session(1, "Squat", 80.0 to 5))).isEmpty())
        assertTrue(strengthProgress(listOf(session(2, "Plank", 0.0 to 1), session(1, "Plank", 0.0 to 1))).isEmpty())
    }

    @Test
    fun weeklyVolumeCoversMondayToSunday() {
        // Thursday 8 Oct 2026: this week started Monday 5 Oct.
        val sessions = listOf(
            session(0, "Bench Press", 100.0 to 10), // Thu 8 Oct: 1000
            session(3, "Bench Press", 50.0 to 10),  // Mon 5 Oct: 500
            session(4, "Bench Press", 40.0 to 10),  // Sun 4 Oct, the previous week: 400
        )
        val weeks = weeklyVolume(sessions, today, weeks = 3)
        assertEquals(3, weeks.size)
        assertEquals(LocalDate.of(2026, 10, 5), weeks.last().weekStart)
        assertEquals(1500.0, weeks.last().volumeKg, 0.001)
        assertEquals(2, weeks.last().workouts)
        assertEquals(400.0, weeks[1].volumeKg, 0.001)
        assertEquals(0.0, weeks[0].volumeKg, 0.0)
    }

    @Test
    fun bodyReportLeavesOutWhatItCannotCompute() {
        val report = buildBodyReport(BodyProfile(null, null, null, null), emptyList(), emptyList(), today)
        assertNull(report.bmi)
        assertNull(report.bmrKcal)
        assertNull(report.maintenanceKcal)
        assertNull(report.weight)
        assertTrue(report.strength.isEmpty())
        assertEquals(1.2, report.activity.factor, 0.0)
    }
}
