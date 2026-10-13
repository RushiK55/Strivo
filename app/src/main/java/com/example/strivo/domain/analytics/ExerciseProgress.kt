package com.example.strivo.domain.analytics

import com.example.strivo.domain.model.ExerciseSet
import com.example.strivo.domain.model.WorkoutSession
import com.example.strivo.domain.model.countsForRecords
import com.example.strivo.domain.model.isWork
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Per-exercise progress, worked out only from the sets the user saved. An exercise is matched by name
 * (ignoring case and spaces at the ends), so the same lift in two plans counts as one.
 */

/** Epley's estimate of a one-rep max. Only sets of 1 to 12 reps are used: beyond that the formula drifts. */
fun estimatedOneRepMax(weightKg: Double, reps: Int): Double? = when {
    weightKg <= 0.0 || reps <= 0 || reps > 12 -> null
    reps == 1 -> weightKg
    else -> weightKg * (1 + reps / 30.0)
}

/** One exercise in one workout. */
data class ExerciseSession(
    val date: LocalDate,
    val planName: String,
    val sets: List<ExerciseSet>,
) {
    // Warm-ups are not work at all. Drop sets are work (volume, reps) but never a best: they are lighter and done tired.
    private val counted = sets.filter { it.reps > 0 && it.isWork }
    /** The sets a best can come from; a workout of nothing but drop sets still has to show something. */
    val ranked: List<ExerciseSet> = counted.filter { it.countsForRecords }.ifEmpty { counted }

    /** The heaviest set; among equally heavy sets the one with the most reps. */
    val topSet: ExerciseSet? get() = ranked.maxWithOrNull(compareBy<ExerciseSet> { it.weight }.thenBy { it.reps })
    val topWeightKg: Double get() = topSet?.weight ?: 0.0
    val bestEstimatedMaxKg: Double? get() = bestEstimatedSet?.let { estimatedOneRepMax(it.weight, it.reps) }
    val bestEstimatedSet: ExerciseSet? get() = ranked.filter { estimatedOneRepMax(it.weight, it.reps) != null }
        .maxByOrNull { estimatedOneRepMax(it.weight, it.reps)!! }
    val volumeKg: Double get() = counted.sumOf { it.weight * it.reps }
    val reps: Int get() = counted.sumOf { it.reps }
    val mostReps: Int get() = ranked.maxOfOrNull { it.reps } ?: 0
    /** Sets that count (warm-ups left out), for the totals. */
    val workSetCount: Int get() = counted.size
}

enum class RecordType(val label: String) {
    HeaviestWeight("Heaviest weight"),
    EstimatedMax("Best estimated 1RM"),
    MostReps("Most reps in a set"),
    BestSetVolume("Best set (kg × reps)"),
    BestSessionVolume("Most volume in a workout"),
}

/** A best ever, with the set (when it is about one set) and the day it was done. */
data class PersonalRecord(
    val type: RecordType,
    val valueKg: Double,
    val set: ExerciseSet?,
    val date: LocalDate,
)

/** The heaviest weight lifted for a number of reps, e.g. 5 reps: 60 kg. */
data class RepMax(val reps: Int, val weightKg: Double, val date: LocalDate)

/** A record broken in one workout, for the "recent records" list. */
data class RecordEvent(
    val exercise: String,
    val type: RecordType,
    val date: LocalDate,
    val set: ExerciseSet?,
    val valueKg: Double,
    val previousKg: Double,
)

data class ExerciseHistory(
    val name: String,
    /** Oldest first. */
    val sessions: List<ExerciseSession>,
) {
    val first: ExerciseSession get() = sessions.first()
    val latest: ExerciseSession get() = sessions.last()
    val lastDone: LocalDate get() = latest.date

    /** Done with a weight at least once; bodyweight-only exercises are tracked by reps instead. */
    val isWeighted: Boolean get() = sessions.any { it.topWeightKg > 0 }

    /** Top-set weight, latest workout against the first. 0 with fewer than two workouts. */
    val changeKg: Double get() = if (sessions.size < 2) 0.0 else latest.topWeightKg - first.topWeightKg
    val changePercent: Double? get() =
        if (sessions.size < 2 || first.topWeightKg <= 0) null else changeKg / first.topWeightKg * 100

    /** Most reps in a set, latest workout against the first (for bodyweight exercises). */
    val changeReps: Int get() = if (sessions.size < 2) 0 else latest.mostReps - first.mostReps

    /**
     * How fast the top-set weight is going up, in kg per week: the slope of a straight line through every
     * workout's top set. Null until there are three workouts at least two weeks apart, so one heavy day
     * does not read as a trend.
     */
    val kgPerWeek: Double? get() {
        val points = sessions.filter { it.topWeightKg > 0 }
        if (points.size < 3) return null
        val start = points.first().date
        val span = ChronoUnit.DAYS.between(start, points.last().date)
        if (span < 14) return null
        val xs = points.map { ChronoUnit.DAYS.between(start, it.date).toDouble() }
        val ys = points.map { it.topWeightKg }
        return slope(xs, ys)?.let { it * 7 }
    }

    val records: List<PersonalRecord> get() = buildList {
        fun <T : Comparable<T>> best(selector: (ExerciseSession) -> T?) =
            sessions.filter { selector(it) != null }.maxByOrNull { selector(it)!! }

        val allSets = sessions.flatMap { s -> s.ranked.map { s.date to it } }
        if (isWeighted) {
            allSets.filter { it.second.weight > 0 }
                .maxWithOrNull(compareBy<Pair<LocalDate, ExerciseSet>> { it.second.weight }.thenBy { it.second.reps })
                ?.let { (date, set) -> add(PersonalRecord(RecordType.HeaviestWeight, set.weight, set, date)) }
            allSets.mapNotNull { (date, set) -> estimatedOneRepMax(set.weight, set.reps)?.let { Triple(date, set, it) } }
                .maxByOrNull { it.third }
                ?.let { (date, set, kg) -> add(PersonalRecord(RecordType.EstimatedMax, kg, set, date)) }
            allSets.maxByOrNull { it.second.weight * it.second.reps }
                ?.takeIf { it.second.weight > 0 }
                ?.let { (date, set) -> add(PersonalRecord(RecordType.BestSetVolume, set.weight * set.reps, set, date)) }
            best { it.volumeKg.takeIf { v -> v > 0 } }
                ?.let { add(PersonalRecord(RecordType.BestSessionVolume, it.volumeKg, null, it.date)) }
        }
        allSets.maxByOrNull { it.second.reps }
            ?.let { (date, set) -> add(PersonalRecord(RecordType.MostReps, set.reps.toDouble(), set, date)) }
    }

    /** The heaviest weight for each number of reps the user has done (1 to 15), fewest reps first. */
    val repMaxes: List<RepMax> get() = sessions
        .flatMap { s -> s.ranked.filter { it.weight > 0 && it.reps in 1..15 }.map { s.date to it } }
        .groupBy { it.second.reps }
        .map { (reps, sets) ->
            val (date, set) = sets.maxWith(compareBy<Pair<LocalDate, ExerciseSet>> { it.second.weight }.thenBy { it.first })
            RepMax(reps, set.weight, date)
        }
        .sortedBy { it.reps }

    val avgRestSeconds: Long? get() = sessions.flatMap { it.sets }.map { parseClockSeconds(it.restTime) }.filter { it > 0 }
        .average().takeIf { !it.isNaN() }?.toLong()

    val avgSetSeconds: Long? get() = sessions.flatMap { it.sets }.map { parseClockSeconds(it.setDuration) }.filter { it > 0 }
        .average().takeIf { !it.isNaN() }?.toLong()
}

/** Every exercise the user has done, most recently done first. */
fun exerciseHistories(sessions: List<WorkoutSession>): List<ExerciseHistory> =
    sessions.sortedBy { it.date }
        .flatMap { session ->
            session.exercises
                .filter { exercise -> exercise.sets.any { it.reps > 0 && it.isWork } }
                .map { exercise -> exercise.name.trim() to ExerciseSession(session.date.toLocalDate(), session.planName, exercise.sets) }
        }
        .groupBy { it.first.lowercase() }
        .map { (_, entries) -> ExerciseHistory(name = entries.last().first, sessions = entries.map { it.second }) }
        .sortedWith(compareByDescending<ExerciseHistory> { it.lastDone }.thenBy { it.name.lowercase() })

/**
 * Records broken on or after [since], newest first. A record needs something to beat, so an exercise's first
 * workout never counts; only the heaviest weight and the estimated 1RM are tracked, as the two that matter most.
 */
fun recentRecords(histories: List<ExerciseHistory>, since: LocalDate): List<RecordEvent> =
    histories.flatMap { history ->
        val events = mutableListOf<RecordEvent>()
        var bestWeight = 0.0
        var bestMax = 0.0
        history.sessions.forEachIndexed { index, session ->
            val top = session.topSet
            val max = session.bestEstimatedMaxKg
            if (index > 0 && session.date >= since) {
                if (top != null && top.weight > bestWeight && bestWeight > 0) {
                    events += RecordEvent(history.name, RecordType.HeaviestWeight, session.date, top, top.weight, bestWeight)
                } else if (max != null && max > bestMax + 0.05 && bestMax > 0) {
                    // Same weight but more reps still shows up, as a better estimated max.
                    val set = session.bestEstimatedSet
                    events += RecordEvent(history.name, RecordType.EstimatedMax, session.date, set, max, bestMax)
                }
            }
            if (top != null) bestWeight = maxOf(bestWeight, top.weight)
            if (max != null) bestMax = maxOf(bestMax, max)
        }
        events
    }.sortedWith(compareByDescending<RecordEvent> { it.date }.thenBy { it.exercise })

/** "MM:SS" or "HH:MM:SS" to seconds; 0 for anything unreadable. */
fun parseClockSeconds(text: String): Long {
    val parts = text.split(":").map { it.trim().toLongOrNull() ?: return 0 }
    return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> 0
    }
}

/** Least-squares slope of y against x; null when x does not vary. */
internal fun slope(xs: List<Double>, ys: List<Double>): Double? {
    if (xs.size < 2 || xs.size != ys.size) return null
    val mx = xs.average()
    val my = ys.average()
    val sxx = xs.sumOf { (it - mx) * (it - mx) }
    if (sxx == 0.0) return null
    return xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / sxx
}
