package com.example.strivo.domain.model

import kotlin.math.max
import kotlin.math.roundToInt

/** What a set is for. Warm-ups and drop sets are logged on their own so they never pass for working sets. */
enum class SetType(val key: String, val label: String, val badge: String?, val hint: String) {
    Normal("normal", "Normal set", null, "A regular working set."),
    WarmUp("warmup", "Warm-up", "W", "Lighter, to get ready. Left out of volume and records."),
    Drop("drop", "Drop set", "D", "5 kg lighter, straight after the last set, no rest."),
    Failure("failure", "To failure", "F", "Taken until you cannot do another rep.");

    companion object {
        fun fromKey(key: String?): SetType = entries.firstOrNull { it.key == key } ?: Normal
    }
}

/** A set that adds to the workout's volume, set count and reps. Everything except a warm-up. */
val ExerciseSet.isWork: Boolean get() = type != SetType.WarmUp

/**
 * A set that can count as a best: the weight, estimated max and rep records. A drop set is lighter and done tired,
 * so it never sets a record, though it still counts towards volume.
 */
val ExerciseSet.countsForRecords: Boolean get() = type == SetType.Normal || type == SetType.Failure

/** Short text for a set row: the number of a normal set (warm-ups and drops are not counted), or W / D / F. */
fun List<ExerciseSet>.setLabels(): List<String> {
    var number = 0
    return map { set -> set.type.badge ?: (++number).toString() }
}

/** The weights people load are not round percentages, so suggestions snap to the plates they would use. */
private fun stepFor(kg: Double): Double = when {
    kg >= 40 -> 2.5
    kg >= 10 -> 1.0
    else -> 0.5
}

/** How much lighter a drop set starts than the set before it. */
const val DropStepKg = 5.0

/**
 * A drop set starts [DropStepKg] below the set it follows. A set too light to lose that much loses 1 kg instead, but never
 * more than half, so the drop is always lighter and never reaches 0 (which would read as bodyweight). 0 stays 0.
 */
fun suggestedDropKg(previousKg: Double): Double = when {
    previousKg <= 0.0 -> 0.0
    previousKg > DropStepKg -> previousKg - DropStepKg
    else -> (previousKg - 1.0).coerceAtLeast(previousKg / 2)
}

/** About half the working weight, rounded to a loadable weight. 0 stays 0 (bodyweight). */
fun suggestedWarmUpKg(workingKg: Double): Double {
    if (workingKg <= 0.0) return 0.0
    val step = stepFor(workingKg)
    return max(step, (workingKg * 0.5 / step).roundToInt() * step)
}

/** The earlier set that still has to be finished before the set at [index] may start, or null when it is that set's turn. */
fun List<ExerciseSet>.blockingSetBefore(index: Int): Int? =
    take(index).indexOfFirst { !it.isCompleted }.takeIf { it >= 0 }

/** The most drop sets a plan can add to one exercise. */
const val MaxPlannedDropSets = 5

/**
 * The sets a workout starts with for a plan's exercise: `sets` working sets of weight × reps (one if the plan has
 * none), then the plan's drop sets, each [DropStepKg] lighter than the one before at the same reps.
 */
fun Exercise.plannedSets(): List<ExerciseSet> {
    val count = (sets.toIntOrNull() ?: 0).coerceIn(1, 20)
    val kg = weight.toDoubleOrNull() ?: 0.0
    val repCount = reps.toIntOrNull() ?: 0
    val working = List(count) { ExerciseSet(weight = kg, reps = repCount) }
    var previous = kg
    val drops = List(dropSets.coerceIn(0, MaxPlannedDropSets)) {
        previous = suggestedDropKg(previous)
        ExerciseSet(weight = previous, reps = repCount, type = SetType.Drop)
    }
    return working + drops
}

/** " + 2 drop" after a plan's sets × reps, or nothing when the plan has no drop sets. */
fun Exercise.dropSetsSuffix(): String = if (dropSets > 0) " + $dropSets drop" else ""

/** "Set 3", "Warm-up", "Drop set": how a set is named in messages. */
fun setName(sets: List<ExerciseSet>, index: Int): String {
    val set = sets.getOrNull(index) ?: return "Set"
    return if (set.type == SetType.Normal) "Set ${sets.setLabels()[index]}" else set.type.label
}
