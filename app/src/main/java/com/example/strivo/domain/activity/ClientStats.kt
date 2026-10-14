package com.example.strivo.domain.activity

import com.example.strivo.domain.analytics.FoodEntry
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * What a trainer may see about what a client eats: the totals of a day, never the foods. This is the only thing from
 * the food log that leaves the phone for the trainer's dashboard (the food log itself is the client's own backup).
 */
data class DayNutrition(
    val date: LocalDate,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val meals: Int,
) {
    fun toMap(): Map<String, Any> = mapOf(
        "date" to date.toString(), "calories" to calories, "protein" to proteinG, "carbs" to carbsG, "fat" to fatG, "meals" to meals,
    )
}

/** One total per day that has something logged, oldest first. Macros the client left out count as nothing. */
fun nutritionByDay(entries: List<FoodEntry>): List<DayNutrition> =
    entries.groupBy { it.date }.toSortedMap().map { (date, day) ->
        DayNutrition(
            date = date,
            calories = day.sumOf { it.calories },
            proteinG = day.sumOf { it.proteinG ?: 0.0 }.roundToInt(),
            carbsG = day.sumOf { it.carbsG ?: 0.0 }.roundToInt(),
            fatG = day.sumOf { it.fatG ?: 0.0 }.roundToInt(),
            meals = day.size,
        )
    }

/** A short progress summary of one client, kept on their user document so a trainer can list everyone cheaply. */
data class ClientStats(
    val workouts7d: Int,
    val workouts30d: Int,
    val lastWorkoutAt: Long?,
    /** Weekdays the client has a plan for. */
    val plannedDays: Int,
    val weightKg: Double?,
    /** Latest weight minus the first weigh-in of the last 30 days; null with fewer than two weigh-ins in that time. */
    val weightChange30dKg: Double?,
    val avgCalories7d: Int?,
    val avgProteinG7d: Int?,
) {
    /** Only the figures that exist; the others are removed from the cloud by the caller (see [FIELDS]). */
    fun toMap(): Map<String, Any> = buildMap {
        put("workouts7d", workouts7d)
        put("workouts30d", workouts30d)
        put("plannedDays", plannedDays)
        lastWorkoutAt?.let { put("lastWorkoutAt", it) }
        weightKg?.let { put("weightKg", it) }
        weightChange30dKg?.let { put("weightChange30dKg", it) }
        avgCalories7d?.let { put("avgCalories7d", it) }
        avgProteinG7d?.let { put("avgProteinG7d", it) }
    }

    companion object {
        val FIELDS = listOf(
            "workouts7d", "workouts30d", "plannedDays", "lastWorkoutAt", "weightKg", "weightChange30dKg", "avgCalories7d", "avgProteinG7d",
        )
    }
}

/**
 * Builds the summary from real data only (the caller leaves demo rows out). A "week" is the last 7 days including
 * [today]; weigh-ins are (ISO date, kg) pairs.
 */
fun clientStats(
    sessions: List<LocalDateTime>,
    plannedDays: Int,
    weights: List<Pair<String, Double>>,
    nutrition: List<DayNutrition>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): ClientStats {
    fun sessionsSince(days: Long) = sessions.count { !it.toLocalDate().isBefore(today.minusDays(days - 1)) && !it.toLocalDate().isAfter(today) }

    val ordered = weights.sortedBy { it.first }
    val latest = ordered.lastOrNull()
    val firstOfMonth = ordered.firstOrNull { LocalDate.parse(it.first) >= today.minusDays(30) }
    val change = if (latest != null && firstOfMonth != null && firstOfMonth.first != latest.first) latest.second - firstOfMonth.second else null

    val week = nutrition.filter { !it.date.isBefore(today.minusDays(6)) && !it.date.isAfter(today) }
    val withProtein = week.filter { it.proteinG > 0 }
    return ClientStats(
        workouts7d = sessionsSince(7),
        workouts30d = sessionsSince(30),
        lastWorkoutAt = sessions.maxOrNull()?.atZone(zone)?.toInstant()?.toEpochMilli(),
        plannedDays = plannedDays,
        weightKg = latest?.second,
        weightChange30dKg = change?.let { (it * 10).roundToInt() / 10.0 },
        avgCalories7d = if (week.isEmpty()) null else week.map { it.calories }.average().roundToInt(),
        avgProteinG7d = if (withProtein.isEmpty()) null else withProtein.map { it.proteinG }.average().roundToInt(),
    )
}
