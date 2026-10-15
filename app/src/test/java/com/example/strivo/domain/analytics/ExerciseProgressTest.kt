package com.example.strivo.domain.analytics

import com.example.strivo.domain.model.ExerciseSet
import com.example.strivo.domain.model.PerformedExercise
import com.example.strivo.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ExerciseProgressTest {
    private val today = LocalDate.of(2026, 10, 12) // a Monday

    private fun set(kg: Double, reps: Int, rest: String = "", time: String = "") =
        ExerciseSet(kg, reps, isCompleted = true, restTime = rest, setDuration = time)

    private fun session(daysAgo: Int, vararg exercises: Pair<String, List<ExerciseSet>>, hour: Int = 12) = WorkoutSession(
        planName = "Push",
        date = today.minusDays(daysAgo.toLong()).atTime(LocalTime.of(hour, 0)),
        totalTime = "00:40:00",
        exercises = exercises.map { (name, sets) -> PerformedExercise(name, sets) },
    )

    @Test
    fun epleyEstimate() {
        assertEquals(100.0, estimatedOneRepMax(100.0, 1)!!, 0.001)
        assertEquals(80.0, estimatedOneRepMax(60.0, 10)!!, 0.001)
        assertNull(estimatedOneRepMax(60.0, 15)) // too many reps to trust
        assertNull(estimatedOneRepMax(0.0, 10)) // bodyweight
    }

    @Test
    fun parsesMinuteAndHourClocks() {
        assertEquals(75L, parseClockSeconds("01:15"))
        assertEquals(3723L, parseClockSeconds("01:02:03"))
        assertEquals(0L, parseClockSeconds(""))
        assertEquals(0L, parseClockSeconds("x:10"))
    }

    @Test
    fun groupsByNameIgnoringCaseAndTracksWeightIncrease() {
        val sessions = listOf(
            session(21, "Bench Press" to listOf(set(40.0, 10), set(40.0, 10))),
            session(14, "bench press " to listOf(set(42.5, 10), set(42.5, 8))),
            session(0, "Bench Press" to listOf(set(47.5, 8), set(45.0, 10))),
        )
        val history = exerciseHistories(sessions).single()

        assertEquals(3, history.sessions.size)
        assertEquals(7.5, history.changeKg, 0.001)
        assertEquals(18.75, history.changePercent!!, 0.001)
        // Top sets 40, 42.5, 47.5 over three weeks: about 2.5 kg a week.
        assertEquals(2.5, history.kgPerWeek!!, 0.3)
    }

    @Test
    fun weeklyRateNeedsThreeWorkoutsTwoWeeksApart() {
        val twoWorkouts = exerciseHistories(listOf(
            session(14, "Squat" to listOf(set(60.0, 5))),
            session(0, "Squat" to listOf(set(70.0, 5))),
        )).single()
        assertNull(twoWorkouts.kgPerWeek)
        assertEquals(10.0, twoWorkouts.changeKg, 0.001)

        val tooClose = exerciseHistories(listOf(
            session(6, "Squat" to listOf(set(60.0, 5))),
            session(3, "Squat" to listOf(set(62.5, 5))),
            session(0, "Squat" to listOf(set(65.0, 5))),
        )).single()
        assertNull(tooClose.kgPerWeek)
    }

    @Test
    fun recordsAndRepMaxes() {
        val history = exerciseHistories(listOf(
            session(7, "Row" to listOf(set(50.0, 10, rest = "01:30"), set(55.0, 6, rest = "02:00"))),
            session(0, "Row" to listOf(set(52.5, 10), set(52.5, 12))),
        )).single()
        val byType = history.records.associateBy { it.type }

        assertEquals(55.0, byType.getValue(RecordType.HeaviestWeight).valueKg, 0.001)
        // 52.5 x 12 = 73.5 beats 55 x 6 = 66 as an estimated max.
        assertEquals(52.5 * 1.4, byType.getValue(RecordType.EstimatedMax).valueKg, 0.001)
        assertEquals(12.0, byType.getValue(RecordType.MostReps).valueKg, 0.001)
        assertEquals(630.0, byType.getValue(RecordType.BestSetVolume).valueKg, 0.001)
        assertEquals(1155.0, byType.getValue(RecordType.BestSessionVolume).valueKg, 0.001)

        assertEquals(listOf(6, 10, 12), history.repMaxes.map { it.reps })
        assertEquals(52.5, history.repMaxes.first { it.reps == 10 }.weightKg, 0.001)
        assertEquals(105L, history.avgRestSeconds)
    }

    @Test
    fun bodyweightExerciseTracksReps() {
        val history = exerciseHistories(listOf(
            session(7, "Pull Up" to listOf(set(0.0, 6), set(0.0, 5))),
            session(0, "Pull Up" to listOf(set(0.0, 9))),
        )).single()
        assertTrue(!history.isWeighted)
        assertEquals(3, history.changeReps)
        assertEquals(listOf(RecordType.MostReps), history.records.map { it.type })
    }

    @Test
    fun recentRecordsSkipTheFirstWorkoutAndOldOnes() {
        val histories = exerciseHistories(listOf(
            session(40, "Bench" to listOf(set(40.0, 10))),
            session(35, "Bench" to listOf(set(45.0, 8))), // a record, but too old
            session(5, "Bench" to listOf(set(45.0, 10))), // same weight, more reps: a better estimated max
            session(0, "Bench" to listOf(set(50.0, 5))), // heavier: a weight record
            session(0, "Curl" to listOf(set(15.0, 10))), // first time: nothing to beat
        ))
        val events = recentRecords(histories, today.minusDays(30))

        assertEquals(listOf(RecordType.HeaviestWeight, RecordType.EstimatedMax), events.map { it.type })
        assertEquals(45.0, events.first().previousKg, 0.001)
    }

    @Test
    fun adherenceCountsScheduledDaysSinceTheFirstWorkout() {
        val sessions = listOf(session(14), session(7), session(2), session(0)) // Mondays and a Saturday
        val report = buildConsistency(sessions, mapOf(DayOfWeek.MONDAY to 1, DayOfWeek.WEDNESDAY to 1), AnalyticsRange.Month, today)

        // Scheduled Mon/Wed from 14 days ago: Mon(14) Wed(12) Mon(7) Wed(5) Mon(0) -> 3 of 5 done.
        val adherence = report.adherence!!
        assertEquals(3, adherence.done)
        assertEquals(5, adherence.scheduled)
        assertEquals(3, report.byWeekday[0])
        assertEquals(1, report.byWeekday[5])
        assertEquals(4, report.byTimeOfDay.getValue(TimeOfDay.Afternoon))
    }

    @Test
    fun aDayWithTwoPlansIsTwoWorkoutsToDo() {
        // Mondays 14 and 7 days ago and today; two plans on Mondays. Both were done 14 days ago, one 7 days ago, none yet today.
        val report = buildConsistency(listOf(session(14), session(14), session(7)), mapOf(DayOfWeek.MONDAY to 2), AnalyticsRange.Month, today)
        assertEquals(Adherence(done = 3, scheduled = 4), report.adherence)
    }

    @Test
    fun todayIsNotAMissBeforeTheWorkout() {
        val report = buildConsistency(listOf(session(7)), mapOf(DayOfWeek.MONDAY to 1), AnalyticsRange.Month, today)
        assertEquals(Adherence(done = 1, scheduled = 1), report.adherence)
    }

    @Test
    fun trendWeightSmoothsSwings() {
        val trend = weightTrend(listOf(
            WeightEntry(today.minusDays(2), 80.0),
            WeightEntry(today.minusDays(1), 82.0), // a water spike
            WeightEntry(today, 80.0),
        ))
        assertEquals(80.0, trend.first().trendKg, 0.001)
        assertEquals(80.2, trend[1].trendKg, 0.001)
        assertTrue(trend.last().trendKg in 80.0..80.2)
    }

    @Test
    fun weeklyRateNeedsAWeekOfWeighIns() {
        assertNull(weeklyWeightRate(listOf(WeightEntry(today.minusDays(3), 80.0), WeightEntry(today, 79.0)), today))
        val rate = weeklyWeightRate(listOf(WeightEntry(today.minusDays(14), 80.0), WeightEntry(today, 79.0)), today)
        assertEquals(-0.5, rate!!, 0.001)
    }

    @Test
    fun expenditureFromIntakeAndWeightChange() {
        val food = (0 until 14).map { FoodEntry(date = today.minusDays(it.toLong()), meal = Meal.Lunch, name = "Food", calories = 2400) }
        // Steady loss of 0.5 kg over 14 days, so the trend moves too.
        val weights = (0..14).map { WeightEntry(today.minusDays(14L - it), 80.0 - it * 0.5 / 14) }
        val status = estimateExpenditure(food, weights, today)
        val estimate = status.estimate
        assertNotNull(estimate)
        assertEquals(2400.0, estimate!!.avgIntakeKcal, 0.001)
        // Eating 2,400 and losing weight means burning more than 2,400.
        assertTrue(estimate.kcal > 2400)
        assertTrue(estimate.kcal < 2400 + 0.5 * KcalPerKg / 14 + 1)
    }

    @Test
    fun expenditureWaitsForEnoughData() {
        val food = (0 until 5).map { FoodEntry(date = today.minusDays(it.toLong()), meal = Meal.Lunch, name = "Food", calories = 2000) }
        val status = estimateExpenditure(food, listOf(WeightEntry(today.minusDays(20), 80.0), WeightEntry(today, 79.0)), today)
        assertNull(status.estimate)
        assertEquals(5, status.loggedDays)
        assertEquals(20, status.weighInSpanDays)
    }
}
