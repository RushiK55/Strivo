package com.example.strivo.data.analytics

import java.time.LocalDate

enum class Meal(val label: String) {
    Breakfast("Breakfast"),
    Lunch("Lunch"),
    Dinner("Dinner"),
    Snack("Snack"),
}

/** One thing the user ate. Calories are required; the macros are optional because the user may not know them. */
data class FoodEntry(
    val id: Long? = null,
    val date: LocalDate,
    val meal: Meal,
    val name: String,
    val calories: Int,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
) {
    val hasMacros: Boolean get() = proteinG != null && carbsG != null && fatG != null
}

/** What was eaten on one day. [calories] is only what was logged, so a day with nothing logged is not "0 kcal eaten". */
data class FoodDay(
    val date: LocalDate,
    val entries: List<FoodEntry> = emptyList(),
) {
    val calories: Int get() = entries.sumOf { it.calories }
    val isLogged: Boolean get() = entries.isNotEmpty()
}

data class MacroSplit(
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
) {
    // 4 kcal per gram of protein and carbohydrate, 9 per gram of fat.
    private val kcal get() = proteinG * 4 + carbsG * 4 + fatG * 9
    val proteinPercent: Double get() = if (kcal > 0) proteinG * 4 / kcal * 100 else 0.0
    val carbsPercent: Double get() = if (kcal > 0) carbsG * 4 / kcal * 100 else 0.0
    val fatPercent: Double get() = if (kcal > 0) fatG * 9 / kcal * 100 else 0.0
}

data class NutritionReport(
    val range: AnalyticsRange,
    /** One entry per day, oldest first, ending today. */
    val days: List<FoodDay>,
    val loggedDays: Int,
    val totalCalories: Int,
    /** Average over the days that have food logged; null when none do. */
    val avgCaloriesPerLoggedDay: Double?,
    /** Average eaten minus the estimated maintenance calories (negative = under); null if either is unknown. */
    val avgDifferenceFromMaintenance: Double?,
    val maintenanceKcal: Double?,
    /** Average grams per day from the foods that have all three macros entered; null when there are none. */
    val avgMacros: MacroSplit?,
    /** How many logged foods had macros, out of how many were logged. */
    val foodsWithMacros: Int,
    val foodsLogged: Int,
) {
    val isEmpty: Boolean get() = loggedDays == 0
}

fun buildNutrition(entries: List<FoodEntry>, range: AnalyticsRange, today: LocalDate, maintenanceKcal: Double?): NutritionReport {
    val start = today.minusDays(range.days - 1L)
    val byDate = entries.filter { it.date in start..today }.groupBy { it.date }
    val days = (0 until range.days).map { offset ->
        val date = start.plusDays(offset.toLong())
        FoodDay(date, byDate[date].orEmpty())
    }
    val logged = days.filter { it.isLogged }
    val average = if (logged.isEmpty()) null else logged.sumOf { it.calories }.toDouble() / logged.size

    // Macros: only foods with all three entered count, and the average is per day that has any such food.
    val withMacros = logged.map { day -> day.entries.filter { it.hasMacros } }.filter { it.isNotEmpty() }
    val avgMacros = if (withMacros.isEmpty()) null else MacroSplit(
        proteinG = withMacros.sumOf { day -> day.sumOf { it.proteinG!! } } / withMacros.size,
        carbsG = withMacros.sumOf { day -> day.sumOf { it.carbsG!! } } / withMacros.size,
        fatG = withMacros.sumOf { day -> day.sumOf { it.fatG!! } } / withMacros.size,
    )

    return NutritionReport(
        range = range,
        days = days,
        loggedDays = logged.size,
        totalCalories = logged.sumOf { it.calories },
        avgCaloriesPerLoggedDay = average,
        avgDifferenceFromMaintenance = if (average != null && maintenanceKcal != null) average - maintenanceKcal else null,
        maintenanceKcal = maintenanceKcal,
        avgMacros = avgMacros,
        foodsWithMacros = logged.sumOf { day -> day.entries.count { it.hasMacros } },
        foodsLogged = logged.sumOf { it.entries.size },
    )
}

/** The user's own earlier foods, newest first, one per name, so a meal they eat often can be added in one tap. */
fun recentFoods(entries: List<FoodEntry>, limit: Int = 12): List<FoodEntry> =
    entries.sortedWith(compareByDescending<FoodEntry> { it.date }.thenByDescending { it.id ?: 0L })
        .distinctBy { it.name.trim().lowercase() }
        .take(limit)

/** Calories the user burned outside a tracked workout and added by hand: a run, a walk, a football match. */
data class ExtraActivity(
    val id: Long? = null,
    val date: LocalDate,
    val name: String,
    val calories: Int,
    val minutes: Int? = null,
)
