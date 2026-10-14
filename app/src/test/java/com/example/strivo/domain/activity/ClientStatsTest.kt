package com.example.strivo.domain.activity

import com.example.strivo.domain.analytics.FoodEntry
import com.example.strivo.domain.analytics.Meal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class ClientStatsTest {
    private val today = LocalDate.of(2026, 10, 13)

    private fun food(day: LocalDate, calories: Int, protein: Double? = null, name: String = "Dal") =
        FoodEntry(date = day, meal = Meal.Lunch, name = name, calories = calories, proteinG = protein, carbsG = null, fatG = null)

    @Test
    fun aDaysNutritionIsOnlyTotals() {
        val days = nutritionByDay(
            listOf(
                food(today, 500, 30.0, "Secret meal"),
                food(today, 300, 12.4),
                food(today.minusDays(1), 800),
            ),
        )
        assertEquals(listOf(today.minusDays(1), today), days.map { it.date })
        val last = days.last()
        assertEquals(800, last.calories)
        assertEquals(42, last.proteinG) // 42.4 rounded
        assertEquals(2, last.meals)
        // Nothing in what is uploaded says what was eaten.
        assertFalse(last.toMap().values.any { it.toString().contains("Secret") })
        assertEquals(setOf("date", "calories", "protein", "carbs", "fat", "meals"), last.toMap().keys)
    }

    @Test
    fun workoutsAreCountedForTheLastWeekAndMonth() {
        val sessions = listOf(
            today.atTime(7, 30), // today
            today.minusDays(6).atTime(18, 0), // still in the week
            today.minusDays(7).atTime(18, 0), // just outside it
            today.minusDays(29).atTime(18, 0), // still in the month
            today.minusDays(30).atTime(18, 0), // outside it
            today.plusDays(1).atTime(8, 0), // the future (a wrong clock) is not counted
        )
        val stats = clientStats(sessions, plannedDays = 4, weights = emptyList(), nutrition = emptyList(), today = today, zone = ZoneOffset.UTC)
        assertEquals(2, stats.workouts7d)
        assertEquals(4, stats.workouts30d)
        assertEquals(4, stats.plannedDays)
        assertEquals(today.plusDays(1).atTime(8, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), stats.lastWorkoutAt)
    }

    @Test
    fun aClientWhoNeverTrainedHasNoLastWorkout() {
        val stats = clientStats(emptyList(), 0, emptyList(), emptyList(), today)
        assertEquals(0, stats.workouts7d)
        assertNull(stats.lastWorkoutAt)
        assertNull(stats.weightKg)
        assertNull(stats.avgCalories7d)
        assertEquals(setOf("workouts7d", "workouts30d", "plannedDays"), stats.toMap().keys)
    }

    @Test
    fun bodyWeightChangeIsOverTheLastMonth() {
        val weights = listOf("2026-08-01" to 90.0, "2026-09-20" to 84.0, "2026-10-01" to 82.5, "2026-10-12" to 81.0)
        val stats = clientStats(emptyList(), 0, weights, emptyList(), today)
        assertEquals(81.0, stats.weightKg!!, 0.0)
        assertEquals(-3.0, stats.weightChange30dKg!!, 0.0) // 81.0 now, 84.0 on the first weigh-in of the last 30 days

        val single = clientStats(emptyList(), 0, listOf("2026-10-12" to 81.0), emptyList(), today)
        assertNull(single.weightChange30dKg)
        val old = clientStats(emptyList(), 0, listOf("2026-05-01" to 90.0), emptyList(), today)
        assertEquals(90.0, old.weightKg!!, 0.0)
        assertNull(old.weightChange30dKg)
    }

    @Test
    fun intakeAveragesCoverDaysWithSomethingLogged() {
        val nutrition = listOf(
            DayNutrition(today, 2000, 120, 200, 70, 4),
            DayNutrition(today.minusDays(2), 1600, 0, 0, 0, 2), // calories only: protein was not recorded
            DayNutrition(today.minusDays(3), 1800, 100, 0, 0, 3),
            DayNutrition(today.minusDays(9), 5000, 500, 0, 0, 9), // too old
        )
        val stats = clientStats(emptyList(), 0, emptyList(), nutrition, today)
        assertEquals(1800, stats.avgCalories7d) // (2000 + 1600 + 1800) / 3
        assertEquals(110, stats.avgProteinG7d) // (120 + 100) / 2, the day without protein is left out
    }

    @Test
    fun theUploadedMapListsExactlyTheDocumentedFields() {
        val stats = clientStats(listOf(today.atTime(9, 0)), 3, listOf("2026-10-01" to 80.0, "2026-10-12" to 79.0), listOf(DayNutrition(today, 2000, 100, 0, 0, 3)), today)
        assertEquals(ClientStats.FIELDS.toSet(), stats.toMap().keys)
        assertTrue(stats.toMap().values.all { it is Number })
    }
}
