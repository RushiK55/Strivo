package com.example.strivo.data.analytics

import com.example.strivo.data.model.WorkoutSession
import java.time.DayOfWeek
import java.time.LocalDate

/** How far back the analytics look. */
enum class AnalyticsRange(val label: String, val days: Int) {
    Week("7 days", 7),
    Month("30 days", 30),
}

/** Everything done on one calendar day. A day without workouts has all zeros (a rest day). */
data class DayStat(
    val date: LocalDate,
    val workouts: Int = 0,
    val volumeKg: Double = 0.0,
    val sets: Int = 0,
    val reps: Int = 0,
    val seconds: Long = 0,
    val exercises: Int = 0,
    /** Estimated energy used, from body weight and workout time; 0 when no body weight is known. */
    val kcal: Double = 0.0,
    /** Calories the user added by hand for other activity (a run, a walk, sport) on this day. */
    val extraKcal: Double = 0.0,
    val extras: List<ExtraActivity> = emptyList(),
    val planNames: List<String> = emptyList(),
    val sessions: List<WorkoutSession> = emptyList(),
) {
    val isRestDay: Boolean get() = workouts == 0

    /** Workout estimate plus the calories added by hand. */
    val burnedKcal: Double get() = kcal + extraKcal
}

data class ExerciseStat(
    val name: String,
    val sets: Int,
    val reps: Int,
    val volumeKg: Double,
    val bestWeightKg: Double,
)

data class PlanStat(val name: String, val workouts: Int)

data class AnalyticsReport(
    val range: AnalyticsRange,
    /** One entry per day, oldest first, ending today. */
    val days: List<DayStat>,
    val workouts: Int,
    val activeDays: Int,
    val totalSeconds: Long,
    val volumeKg: Double,
    val sets: Int,
    val reps: Int,
    /** Consecutive training days counted back from today (or yesterday if today has none yet); Sundays off do not break it. */
    val streak: Int,
    val avgSessionSeconds: Long?,
    val avgSetSeconds: Long?,
    val avgRestSeconds: Long?,
    /** Volume compared with the previous period of the same length; null when there is nothing to compare. */
    val volumeChangePercent: Double?,
    /** Estimated calories burned in the range; null when no body weight is known to base it on. */
    val caloriesBurned: Double?,
    /** The workout part of [caloriesBurned]; null when no body weight is known to base it on. */
    val workoutCalories: Double?,
    /** The part the user added by hand. */
    val extraCalories: Double,
    val topExercises: List<ExerciseStat>,
    val plans: List<PlanStat>,
) {
    val isEmpty: Boolean get() = workouts == 0
}

/** "HH:MM:SS" (as stored by the stopwatch) to seconds; 0 for anything unreadable. */
fun parseDurationSeconds(text: String): Long {
    val parts = text.split(":")
    if (parts.size != 3) return 0
    val (h, m, s) = parts.map { it.trim().toLongOrNull() ?: return 0 }
    return h * 3600 + m * 60 + s
}

fun buildReport(
    sessions: List<WorkoutSession>,
    range: AnalyticsRange,
    today: LocalDate,
    extra: List<ExtraActivity> = emptyList(),
    restDays: Set<DayOfWeek> = DefaultRestDays,
    weightKgOn: (LocalDate) -> Double? = { null },
): AnalyticsReport {
    val start = today.minusDays(range.days - 1L)
    val inRange = sessions.filter { it.date.toLocalDate() in start..today }
    val byDate = inRange.groupBy { it.date.toLocalDate() }
    val extraByDate = extra.filter { it.date in start..today }.groupBy { it.date }

    val days = (0 until range.days).map { offset ->
        val date = start.plusDays(offset.toLong())
        val daySessions = byDate[date].orEmpty().sortedBy { it.date }
        val sets = daySessions.flatMap { s -> s.exercises.flatMap { it.sets } }
        DayStat(
            date = date,
            workouts = daySessions.size,
            volumeKg = sets.sumOf { it.weight * it.reps },
            sets = sets.size,
            reps = sets.sumOf { it.reps },
            seconds = daySessions.sumOf { parseDurationSeconds(it.totalTime) },
            exercises = daySessions.sumOf { it.exercises.size },
            extraKcal = (extraByDate[date].orEmpty()).sumOf { it.calories }.toDouble(),
            extras = extraByDate[date].orEmpty(),
            kcal = daySessions.sumOf { session ->
                weightKgOn(date)?.let { estimateWorkoutCalories(parseDurationSeconds(session.totalTime), it) } ?: 0.0
            },
            planNames = daySessions.map { it.planName }.distinct(),
            sessions = daySessions,
        )
    }

    val allSets = inRange.flatMap { s -> s.exercises.flatMap { it.sets } }
    val setSeconds = allSets.map { parseDurationSeconds(it.setDuration) }.filter { it > 0 }
    val restSeconds = allSets.map { parseDurationSeconds(it.restTime) }.filter { it > 0 }
    val volume = days.sumOf { it.volumeKg }

    val previousStart = start.minusDays(range.days.toLong())
    val previousVolume = sessions
        .filter { it.date.toLocalDate() in previousStart..start.minusDays(1) }
        .sumOf { s -> s.exercises.sumOf { e -> e.sets.sumOf { it.weight * it.reps } } }

    val topExercises = inRange
        .flatMap { it.exercises }
        .groupBy { it.name }
        .map { (name, performed) ->
            val sets = performed.flatMap { it.sets }
            ExerciseStat(
                name = name,
                sets = sets.size,
                reps = sets.sumOf { it.reps },
                volumeKg = sets.sumOf { it.weight * it.reps },
                bestWeightKg = sets.maxOfOrNull { it.weight } ?: 0.0,
            )
        }
        // Bodyweight exercises have no volume, so fall back to reps to keep them in the ranking.
        .sortedWith(compareByDescending<ExerciseStat> { it.volumeKg }.thenByDescending { it.reps })

    return AnalyticsReport(
        range = range,
        days = days,
        workouts = inRange.size,
        activeDays = days.count { !it.isRestDay },
        totalSeconds = days.sumOf { it.seconds },
        volumeKg = volume,
        sets = allSets.size,
        reps = allSets.sumOf { it.reps },
        streak = currentStreak(sessions, today, restDays),
        avgSessionSeconds = inRange.map { parseDurationSeconds(it.totalTime) }.filter { it > 0 }.average().takeIf { !it.isNaN() }?.toLong(),
        avgSetSeconds = setSeconds.average().takeIf { !it.isNaN() }?.toLong(),
        avgRestSeconds = restSeconds.average().takeIf { !it.isNaN() }?.toLong(),
        volumeChangePercent = if (previousVolume > 0) (volume - previousVolume) / previousVolume * 100 else null,
        caloriesBurned = days.sumOf { it.extraKcal }.let { extraTotal ->
            if (weightKgOn(today) != null) days.sumOf { it.kcal } + extraTotal else extraTotal.takeIf { it > 0 }
        },
        workoutCalories = if (weightKgOn(today) != null) days.sumOf { it.kcal } else null,
        extraCalories = days.sumOf { it.extraKcal },
        topExercises = topExercises.take(5),
        plans = inRange.groupBy { it.planName }.map { (name, list) -> PlanStat(name, list.size) }
            .sortedByDescending { it.workouts },
    )
}

fun currentStreak(sessions: List<WorkoutSession>, today: LocalDate, restDays: Set<DayOfWeek> = DefaultRestDays): Int {
    val trainedDays = sessions.map { it.date.toLocalDate() }.toSet()
    // A workout not yet done today does not break a streak that ran up to yesterday.
    var day = if (today in trainedDays) today else today.minusDays(1)
    var streak = 0
    while (true) {
        when {
            day in trainedDays -> streak++
            // A rest day without a workout is skipped: it neither breaks the streak nor adds to it.
            day.dayOfWeek in restDays -> Unit
            else -> break
        }
        day = day.minusDays(1)
    }
    return streak
}

/** Gyms are mostly closed on Sundays, so a Sunday without a workout does not end a streak. */
val DefaultRestDays: Set<DayOfWeek> = setOf(DayOfWeek.SUNDAY)
