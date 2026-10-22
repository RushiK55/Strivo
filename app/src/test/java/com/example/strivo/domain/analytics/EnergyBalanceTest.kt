package com.example.strivo.domain.analytics

import com.example.strivo.domain.model.ExerciseSet
import com.example.strivo.domain.model.PerformedExercise
import com.example.strivo.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EnergyBalanceTest {
    private val today = LocalDate.of(2026, 10, 8)

    private fun workout(daysAgo: Int, clock: String, sets: Int) = WorkoutSession(
        planName = "Push",
        date = today.minusDays(daysAgo.toLong()).atTime(LocalTime.NOON),
        totalTime = clock,
        exercises = listOf(PerformedExercise("Bench Press", List(sets) { ExerciseSet(60.0, 8, isCompleted = true) })),
    )

    private fun food(daysAgo: Int, kcal: Int) =
        FoodEntry(date = today.minusDays(daysAgo.toLong()), meal = Meal.Lunch, name = "Meal", calories = kcal)

    private fun activity(daysAgo: Int, kcal: Int) =
        ExtraActivity(date = today.minusDays(daysAgo.toLong()), name = "Run", calories = kcal)

    // --- Workout length ---

    @Test
    fun aTimedWorkoutUsesItsClock() {
        val session = workout(0, "00:45:00", sets = 6)
        assertEquals(2700L, workoutSecondsForCalories(session))
        assertFalse(isWorkoutLengthEstimated(session))
    }

    @Test
    fun aWorkoutLoggedAfterwardsIsCostedFromItsSets() {
        // Six sets logged with no timer leave a clock of two seconds: that is not a two-second workout.
        val session = workout(0, "00:00:02", sets = 6)
        assertEquals(6 * 150L, workoutSecondsForCalories(session))
        assertTrue(isWorkoutLengthEstimated(session))
    }

    // --- Activity ---

    @Test
    fun activityCaloriesAreMetAboveRestingTimesWeightTimesHours() {
        assertEquals(308, activityKcal(9.8, 70.0, 30)) // (9.8 - 1) x 70 x 0.5
        assertEquals(0, activityKcal(1.0, 70.0, 60))
    }

    // --- The base burn ---

    @Test
    fun theBaseIsTheFormulaTimesTheEverydayFactorUntilYourOwnDataIsLearned() {
        assertEquals(1920.0, baseBurnKcal(1600.0, null, 300.0)!!, 0.001)
        assertNull(baseBurnKcal(null, null, 300.0))
    }

    @Test
    fun aLearnedExpenditureHasTheExerciseTakenOutSoItIsNotCountedTwice() {
        val learned = ExpenditureEstimate(2600.0, 2400.0, -0.4, 20, 15)
        assertEquals(2300.0, baseBurnKcal(1600.0, learned, 300.0)!!, 0.001)
        // Never below resting, however much exercise was logged.
        assertEquals(1600.0, baseBurnKcal(1600.0, learned, 2000.0)!!, 0.001)
    }

    // --- A day ---

    @Test
    fun aDayAddsFoodWorkoutsAndActivityIntoOneBalance() {
        val day = buildEnergyDay(
            date = today,
            food = listOf(food(0, 600), food(0, 900), food(1, 5000)),
            sessions = listOf(workout(0, "01:00:00", sets = 6), workout(1, "01:00:00", sets = 6)),
            extras = listOf(activity(0, 300), activity(2, 999)),
            weightKgOn = { 70.0 },
            baseKcal = 2000.0,
        )
        assertEquals(1500, day.eaten)
        assertEquals(2, day.foodCount)
        assertEquals(280.0, day.workoutKcal, 0.001) // (5 - 1) x 70 x 1h
        assertEquals(300.0, day.extraKcal, 0.001)
        assertEquals(2580.0, day.burnedKcal!!, 0.001)
        assertEquals(-1080.0, day.balanceKcal!!, 0.001)
    }

    @Test
    fun withoutAWeightAWorkoutIsLeftUncostedAndSaysSo() {
        val day = buildEnergyDay(today, emptyList(), listOf(workout(0, "01:00:00", 6)), emptyList(), { null }, 2000.0)
        assertTrue(day.workoutNeedsWeight)
        assertEquals(0.0, day.workoutKcal, 0.0)
    }

    @Test
    fun withoutABaseThereIsNoBalance() {
        val day = buildEnergyDay(today, listOf(food(0, 500)), emptyList(), emptyList(), { 70.0 }, null)
        assertNull(day.burnedKcal)
        assertNull(day.balanceKcal)
    }

    @Test
    fun averageActiveCaloriesCoverTheWindow() {
        val avg = averageActiveKcal(listOf(workout(1, "01:00:00", 6)), listOf(activity(2, 560)), { 70.0 }, today, days = 28)
        assertEquals((280.0 + 560.0) / 28, avg, 0.001)
    }

    // --- A stretch of days ---

    @Test
    fun aDayWithNothingLoggedIsNotAFast() {
        val days = listOf(
            buildEnergyDay(today.minusDays(2), listOf(food(2, 1800)), emptyList(), emptyList(), { 70.0 }, 2000.0),
            buildEnergyDay(today.minusDays(1), emptyList(), emptyList(), emptyList(), { 70.0 }, 2000.0),
            buildEnergyDay(today, listOf(food(0, 2200)), emptyList(), emptyList(), { 70.0 }, 2000.0),
        )
        val summary = summarizeEnergy(days)
        assertEquals(2, summary.countedDays)
        assertEquals(0.0, summary.avgBalance!!, 0.001) // -200 and +200
        assertEquals(2000.0, summary.avgEaten!!, 0.001)
        assertEquals(0.0, summary.kgPerWeek!!, 0.001)
    }

    @Test
    fun aDeficitAddsUpToWeightLostAtSevenThousandSevenHundredPerKilo() {
        val days = (0 until 7).map { buildEnergyDay(today.minusDays(it.toLong()), listOf(food(it, 1500)), emptyList(), emptyList(), { 70.0 }, 2000.0) }
        val summary = summarizeEnergy(days)
        assertEquals(-500.0, summary.avgBalance!!, 0.001)
        assertEquals(-500.0 * 7 / 7700, summary.kgPerWeek!!, 0.0001)
        assertEquals(2000.0, summary.avgBaseKcal!!, 0.001)
        assertEquals(0.0, summary.avgActiveKcal!!, 0.001)
    }

    @Test
    fun todayIsLeftOutWhileItIsStillBeingEatenOnceThereAreOtherDays() {
        val earlier = listOf(2, 1).map { buildEnergyDay(today.minusDays(it.toLong()), listOf(food(it, 2000)), emptyList(), emptyList(), { 70.0 }, 2000.0) }
        val partialToday = buildEnergyDay(today, listOf(food(0, 400)), emptyList(), emptyList(), { 70.0 }, 2000.0)
        // Two settled days to go on: the half-eaten day does not drag the average down.
        assertEquals(0.0, summarizeEnergy(earlier + partialToday, today).avgBalance!!, 0.001)
        // Counted when it is all there is, or when it is not told which day is today.
        assertEquals(-1600.0, summarizeEnergy(listOf(partialToday), today).avgBalance!!, 0.001)
        // One settled day is not much to go on, so today still counts: (0 + -1600) / 2.
        assertEquals(-800.0, summarizeEnergy(listOf(earlier[0], partialToday), today).avgBalance!!, 0.001)
    }

    @Test
    fun nothingLoggedMeansNothingToSummarise() {
        val summary = summarizeEnergy(listOf(buildEnergyDay(today, emptyList(), emptyList(), emptyList(), { 70.0 }, 2000.0)))
        assertEquals(0, summary.countedDays)
        assertNull(summary.avgBalance)
    }

    // --- Goals and helpers ---

    @Test
    fun goalsAreHalfAKiloLossAndAQuarterKiloGainAWeek() {
        assertEquals(-550.0, CalorieGoal.Lose.offsetKcal, 0.001)
        assertEquals(275.0, CalorieGoal.Gain.offsetKcal, 0.001)
        assertEquals(CalorieGoal.Maintain, CalorieGoal.fromKey("nonsense"))
        assertEquals(CalorieGoal.Lose, CalorieGoal.fromKey("lose"))
    }

    @Test
    fun macrosAddUpToCaloriesAndProteinHasATarget() {
        assertEquals(500, macroKcal(30.0, 50.0, 20.0)) // 120 + 200 + 180
        assertEquals(126, proteinTargetG(70.0))
        assertNull(proteinTargetG(null))
    }

    @Test
    fun commonFoodsAreFoundByEveryWordOfTheQuery() {
        assertTrue(searchCommonFoods("chicken").any { it.name == "Chicken breast" })
        assertEquals(listOf("Chicken curry"), searchCommonFoods("chicken curry").map { it.name })
        assertTrue(searchCommonFoods("c").isEmpty())
        assertTrue(searchCommonFoods("zzzz").isEmpty())
        assertTrue(CommonFoods.all { it.calories > 0 && it.name.isNotBlank() })
    }
}
