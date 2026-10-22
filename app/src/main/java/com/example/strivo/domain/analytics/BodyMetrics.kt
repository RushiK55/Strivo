package com.example.strivo.domain.analytics

import com.example.strivo.domain.model.WorkoutSession
import com.example.strivo.domain.model.countsForRecords
import com.example.strivo.domain.model.isWork
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.pow

/*
 * Body and progress figures worked out from what the user entered (gender, age, height, weight) and from
 * their saved workouts. Nothing here is invented: when an input is missing the result is null.
 */

/** Energy cost of resistance training, midway between "moderate" (3.5) and "vigorous" (6.0) in the Compendium of Physical Activities. */
const val STRENGTH_MET = 5.0

data class BodyProfile(
    val gender: String?,
    val age: Int?,
    val heightCm: Double?,
    val weightKg: Double?,
)

/** One dated weigh-in. */
data class WeightEntry(val date: LocalDate, val kg: Double)

enum class BmiCategory(val label: String) {
    Underweight("Underweight"),
    Normal("Normal weight"),
    Overweight("Overweight"),
    Obese("Obese"),
}

data class BmiResult(
    val bmi: Double,
    val category: BmiCategory,
    /** Weights that give a BMI of 18.5 and 24.9 at this height. */
    val healthyMinKg: Double,
    val healthyMaxKg: Double,
    /** Kilograms to gain (+) or lose (-) to reach the healthy range; 0 when already inside it. */
    val kgToHealthyRange: Double,
)

/** BMI = weight / height squared, with the WHO adult categories. Null for missing or implausible inputs. */
fun calculateBmi(heightCm: Double?, weightKg: Double?): BmiResult? {
    if (heightCm == null || weightKg == null) return null
    if (heightCm !in 100.0..250.0 || weightKg !in 20.0..400.0) return null
    val meters = heightCm / 100
    val bmi = weightKg / (meters * meters)
    val min = 18.5 * meters * meters
    val max = 24.9 * meters * meters
    return BmiResult(
        bmi = bmi,
        category = when {
            bmi < 18.5 -> BmiCategory.Underweight
            bmi < 25.0 -> BmiCategory.Normal
            bmi < 30.0 -> BmiCategory.Overweight
            else -> BmiCategory.Obese
        },
        healthyMinKg = min,
        healthyMaxKg = max,
        kgToHealthyRange = when {
            weightKg < min -> min - weightKg
            weightKg > max -> max - weightKg
            else -> 0.0
        },
    )
}

/** Basal metabolic rate in kcal/day by the Mifflin-St Jeor equation. Null unless every input is present and plausible. */
fun calculateBmr(profile: BodyProfile): Double? {
    val height = profile.heightCm
    val weight = profile.weightKg
    val age = profile.age
    if (height == null || weight == null || age == null) return null
    if (height !in 100.0..250.0 || weight !in 20.0..400.0 || age !in 10..100) return null
    val base = 10 * weight + 6.25 * height - 5 * age
    return base + when (profile.gender) {
        "Male" -> 5.0
        "Female" -> -161.0
        else -> -78.0 // midway between the two
    }
}

data class ActivityLevel(val factor: Double, val label: String)

/** Activity multiplier for maintenance calories, chosen from how many days a week the user really trains. */
fun activityLevelFor(trainingDaysPerWeek: Double): ActivityLevel = when {
    trainingDaysPerWeek < 1 -> ActivityLevel(1.2, "Little or no exercise")
    trainingDaysPerWeek <= 3 -> ActivityLevel(1.375, "Light: 1-3 days a week")
    trainingDaysPerWeek <= 5 -> ActivityLevel(1.55, "Moderate: 4-5 days a week")
    else -> ActivityLevel(1.725, "Hard: 6-7 days a week")
}

/**
 * Rough energy used by a strength workout above resting: (MET - 1) x body weight (kg) x hours. The 1 MET of resting is
 * already in the day's base burn, so counting it again would overstate what the workout added.
 */
fun estimateWorkoutCalories(seconds: Long, weightKg: Double): Double = (STRENGTH_MET - 1.0) * weightKg * seconds / 3600.0

/** The weight on [date]: the latest weigh-in on or before it, otherwise the earliest one. */
fun weightAt(entries: List<WeightEntry>, date: LocalDate): Double? {
    if (entries.isEmpty()) return null
    val sorted = entries.sortedBy { it.date }
    return (sorted.lastOrNull { it.date <= date } ?: sorted.first()).kg
}

data class WeightProgress(
    val entries: List<WeightEntry>,
    val first: WeightEntry,
    val latest: WeightEntry,
    val highKg: Double,
    val lowKg: Double,
) {
    /** Change since the first weigh-in; 0 when there is only one. */
    val changeKg: Double get() = latest.kg - first.kg
    val hasTrend: Boolean get() = entries.size >= 2
}

fun weightProgress(entries: List<WeightEntry>): WeightProgress? {
    if (entries.isEmpty()) return null
    val sorted = entries.sortedBy { it.date }
    return WeightProgress(
        entries = sorted,
        first = sorted.first(),
        latest = sorted.last(),
        highKg = sorted.maxOf { it.kg },
        lowKg = sorted.minOf { it.kg },
    )
}

/** A weigh-in with the smoothed "trend weight" at that point. */
data class TrendPoint(val date: LocalDate, val kg: Double, val trendKg: Double)

/**
 * A smoothed trend weight: each weigh-in moves the trend only part of the way towards it, further the longer it has
 * been since the one before (10% for a day, about half for a week). Day-to-day swings from water and food then do not
 * hide the real direction.
 */
fun weightTrend(entries: List<WeightEntry>): List<TrendPoint> {
    val sorted = entries.sortedBy { it.date }
    if (sorted.isEmpty()) return emptyList()
    var trend = sorted.first().kg
    var previous = sorted.first().date
    return sorted.mapIndexed { index, entry ->
        if (index > 0) {
            val days = ChronoUnit.DAYS.between(previous, entry.date).coerceAtLeast(1)
            val pull = 1 - 0.9.pow(days.toDouble())
            trend += (entry.kg - trend) * pull
            previous = entry.date
        }
        TrendPoint(entry.date, entry.kg, trend)
    }
}

/**
 * Change in kg per week over the last four weeks: the slope of a straight line through those weigh-ins. Null until
 * there are two weigh-ins at least a week apart.
 */
fun weeklyWeightRate(entries: List<WeightEntry>, today: LocalDate): Double? {
    val recent = entries.filter { it.date in today.minusDays(27)..today }.sortedBy { it.date }
    if (recent.size < 2) return null
    val start = recent.first().date
    if (ChronoUnit.DAYS.between(start, recent.last().date) < 7) return null
    val xs = recent.map { ChronoUnit.DAYS.between(start, it.date).toDouble() }
    return slope(xs, recent.map { it.kg })?.let { it * 7 }
}

data class StrengthProgress(
    val name: String,
    val firstBestKg: Double,
    val latestBestKg: Double,
    val sessions: Int,
    /** Best estimated one-rep max among the latest workout's sets (Epley, sets of up to 12 reps); null when there is none. */
    val latestEstimatedMaxKg: Double?,
) {
    val changeKg: Double get() = latestBestKg - firstBestKg
}

/** Heaviest weight per exercise, first session against latest. Only exercises done with weight in at least two workouts. */
fun strengthProgress(sessions: List<WorkoutSession>): List<StrengthProgress> {
    val perExercise = sessions.sortedBy { it.date }
        .flatMap { session -> session.exercises.map { exercise -> exercise.name to exercise.sets.filter { it.countsForRecords } } }
        .groupBy({ it.first }, { it.second })
    return perExercise.mapNotNull { (name, setLists) ->
        val weighted = setLists.map { sets -> sets.filter { it.weight > 0 && it.reps > 0 } }.filter { it.isNotEmpty() }
        if (weighted.size < 2) return@mapNotNull null
        val latestSets = weighted.last()
        val estimate = latestSets.filter { it.reps <= 12 }.maxOfOrNull { it.weight * (1 + it.reps / 30.0) }
        StrengthProgress(
            name = name,
            firstBestKg = weighted.first().maxOf { it.weight },
            latestBestKg = latestSets.maxOf { it.weight },
            sessions = weighted.size,
            latestEstimatedMaxKg = estimate,
        )
    }.sortedWith(compareByDescending<StrengthProgress> { it.changeKg }.thenByDescending { it.sessions })
}

data class WeekVolume(val weekStart: LocalDate, val volumeKg: Double, val workouts: Int)

/** Volume per Monday-to-Sunday week for the last [weeks] weeks, oldest first, ending with the current week. */
fun weeklyVolume(sessions: List<WorkoutSession>, today: LocalDate, weeks: Int = 8): List<WeekVolume> {
    val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (weeks - 1 downTo 0).map { back ->
        val start = thisWeek.minusWeeks(back.toLong())
        val inWeek = sessions.filter { it.date.toLocalDate() in start..start.plusDays(6) }
        WeekVolume(
            weekStart = start,
            volumeKg = inWeek.sumOf { s -> s.exercises.sumOf { e -> e.sets.filter { it.isWork }.sumOf { it.weight * it.reps } } },
            workouts = inWeek.size,
        )
    }
}

/** Everything the Body, Progress and Calories pages show. Each part is null when its inputs are missing. */
data class BodyReport(
    val profile: BodyProfile,
    val bmi: BmiResult?,
    val bmrKcal: Double?,
    val activity: ActivityLevel,
    val maintenanceKcal: Double?,
    val weight: WeightProgress?,
    val strength: List<StrengthProgress>,
    val weeks: List<WeekVolume>,
    val trend: List<TrendPoint> = emptyList(),
    /** kg per week over the last four weeks; null without enough weigh-ins. */
    val weeklyRateKg: Double? = null,
)

fun buildBodyReport(profile: BodyProfile, weightLog: List<WeightEntry>, sessions: List<WorkoutSession>, today: LocalDate): BodyReport {
    val bmr = calculateBmr(profile)
    // Training days per week over the last four weeks, so one quiet week does not change the level much.
    val from = today.minusDays(27)
    val trainingDays = sessions.map { it.date.toLocalDate() }.filter { it in from..today }.toSet().size
    val activity = activityLevelFor(trainingDays / 4.0)
    return BodyReport(
        profile = profile,
        bmi = calculateBmi(profile.heightCm, profile.weightKg),
        bmrKcal = bmr,
        activity = activity,
        maintenanceKcal = bmr?.let { it * activity.factor },
        weight = weightProgress(weightLog),
        strength = strengthProgress(sessions),
        weeks = weeklyVolume(sessions, today, weeks = 12),
        trend = weightTrend(weightLog),
        weeklyRateKg = weeklyWeightRate(weightLog, today),
    )
}
