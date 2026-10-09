package com.example.strivo.data.analytics

import com.example.strivo.data.DemoData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NutritionTest {
    private val today = LocalDate.of(2026, 10, 8)

    private fun food(daysAgo: Int, name: String, kcal: Int, macros: Triple<Double, Double, Double>? = null, id: Long? = null, meal: Meal = Meal.Lunch) =
        FoodEntry(id, today.minusDays(daysAgo.toLong()), meal, name, kcal, macros?.first, macros?.second, macros?.third)

    @Test
    fun averagesCountOnlyDaysThatHaveFoodLogged() {
        val entries = listOf(
            food(1, "Lunch", 1000),
            food(1, "Dinner", 1000), // yesterday: 2000
            food(3, "Lunch", 1500),  // three days ago: 1500
        )
        val report = buildNutrition(entries, AnalyticsRange.Week, today, maintenanceKcal = 2000.0)

        assertEquals(7, report.days.size)
        assertEquals(2, report.loggedDays)
        assertEquals(3500, report.totalCalories)
        // (2000 + 1500) / 2 logged days, not / 7: an unlogged day is not zero calories.
        assertEquals(1750.0, report.avgCaloriesPerLoggedDay!!, 0.001)
        assertEquals(-250.0, report.avgDifferenceFromMaintenance!!, 0.001)
        assertTrue(report.days.last().entries.isEmpty()) // today, nothing logged
    }

    @Test
    fun nothingLoggedMeansNoAveragesAreInvented() {
        val report = buildNutrition(emptyList(), AnalyticsRange.Week, today, maintenanceKcal = 2200.0)
        assertTrue(report.isEmpty)
        assertNull(report.avgCaloriesPerLoggedDay)
        assertNull(report.avgDifferenceFromMaintenance)
        assertNull(report.avgMacros)
        assertEquals(0, report.totalCalories)
    }

    @Test
    fun differenceIsUnknownWithoutMaintenance() {
        val report = buildNutrition(listOf(food(0, "Rice", 500)), AnalyticsRange.Week, today, maintenanceKcal = null)
        assertEquals(500.0, report.avgCaloriesPerLoggedDay!!, 0.0)
        assertNull(report.avgDifferenceFromMaintenance)
    }

    @Test
    fun ignoresFoodOutsideTheRange() {
        val report = buildNutrition(listOf(food(10, "Old", 900)), AnalyticsRange.Week, today, null)
        assertTrue(report.isEmpty)
        assertEquals(1, buildNutrition(listOf(food(10, "Old", 900)), AnalyticsRange.Month, today, null).loggedDays)
    }

    @Test
    fun macrosUseOnlyFoodsWithAllThreeEntered() {
        val entries = listOf(
            food(1, "Chicken", 500, Triple(40.0, 50.0, 10.0)),
            food(1, "Mystery snack", 300), // no macros: must not count as zero grams
            food(2, "Rice", 600, Triple(10.0, 150.0, 2.0)),
        )
        val report = buildNutrition(entries, AnalyticsRange.Week, today, null)
        val macros = report.avgMacros!!
        // Day 1 has 40/50/10 and day 2 has 10/150/2: the average of the two days.
        assertEquals(25.0, macros.proteinG, 0.001)
        assertEquals(100.0, macros.carbsG, 0.001)
        assertEquals(6.0, macros.fatG, 0.001)
        assertEquals(2, report.foodsWithMacros)
        assertEquals(3, report.foodsLogged)
        // Percentages come from 4/4/9 kcal per gram and add up to 100.
        assertEquals(100.0, macros.proteinPercent + macros.carbsPercent + macros.fatPercent, 0.001)
        assertEquals(25 * 4 / (25 * 4 + 100 * 4 + 6 * 9.0) * 100, macros.proteinPercent, 0.001)
    }

    @Test
    fun hasMacrosNeedsAllThree() {
        assertTrue(food(0, "A", 100, Triple(1.0, 2.0, 3.0)).hasMacros)
        assertTrue(!FoodEntry(null, today, Meal.Snack, "B", 100, proteinG = 5.0).hasMacros)
    }

    @Test
    fun recentFoodsAreDistinctByNameNewestFirst() {
        val entries = listOf(
            food(5, "Oats", 300, id = 1),
            food(2, "oats ", 350, id = 2), // same food, different case and spacing: newer entry wins
            food(3, "Eggs", 200, id = 3),
        )
        val recent = recentFoods(entries)
        assertEquals(listOf("oats ", "Eggs"), recent.map { it.name })
        assertEquals(350, recent.first().calories)
        assertEquals(1, recentFoods(entries, limit = 1).size)
    }

    // --- Demo data ---

    @Test
    fun demoFoodCoversSevenDaysWithMacros() {
        val demo = DemoData.food(today)
        assertEquals(7, demo.map { it.date }.distinct().size)
        assertTrue(demo.all { it.hasMacros && it.calories > 0 })
        assertTrue(demo.all { it.date in today.minusDays(6)..today })

        val report = buildNutrition(demo, AnalyticsRange.Week, today, maintenanceKcal = 2300.0)
        assertEquals(7, report.loggedDays)
        // Full days land in a believable range; today is only part-logged.
        report.days.dropLast(1).forEach { assertTrue("${it.date}: ${it.calories}", it.calories in 1400..2800) }
        assertTrue(report.days.last().calories < 1000)
        assertTrue(report.avgMacros != null)
    }

    @Test
    fun demoFoodIsTheSameEveryTime() {
        assertEquals(DemoData.food(today).map { it.calories }, DemoData.food(today).map { it.calories })
    }

    @Test
    fun demoWeighInsEndAtTheCurrentWeight() {
        val weights = DemoData.weights(today, 72.0)
        assertEquals(3, weights.size)
        assertEquals(72.0, weights.last().kg, 0.0)
        assertEquals(today, weights.last().date)
        assertTrue(weights.zipWithNext().all { (a, b) -> a.kg > b.kg }) // drifting down
        assertEquals(-0.8, weightProgress(weights)!!.changeKg, 0.001)
    }
}
