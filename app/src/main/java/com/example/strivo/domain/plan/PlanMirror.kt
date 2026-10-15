package com.example.strivo.domain.plan

import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.ExerciseSet
import com.example.strivo.domain.model.MaxPlannedDropSets
import com.example.strivo.domain.model.SetType
import com.example.strivo.domain.model.plannedSets

/*
 * Keeping the user's own plan and the plan on a day the same. The day's plan is a copy of the user's plan (Plan.sourcePlanId, and
 * Exercise.sourceId on each exercise); whatever the user plans on the day (the weight of a set, the reps of the last one, a new
 * exercise) is copied into their plan, and each morning the day starts again from their plan. Only the plan is copied: whether
 * a set was done never is. Pure functions, covered by PlanMirrorTest.
 */

/** The sets this exercise plans, with none of the progress: a set logged, a rest taken. */
fun Exercise.planSets(): List<ExerciseSet> =
    setsList.ifEmpty { plannedSets() }.map { it.copy(isCompleted = false, isStarted = false, restTime = "", setDuration = "") }

private fun List<ExerciseSet>.signature() = map { Triple(it.weight, it.reps, it.type) }

/**
 * The sets, reps and weight written the way a plan names them: the sets that are not warm-ups or drops give the sets, the reps of the
 * first and the heaviest weight; the drop sets are counted. The sets themselves are kept next to this, so it is only the summary.
 */
fun summaryOf(sets: List<ExerciseSet>): Triple<String, String, String> {
    val working = sets.filter { it.type != SetType.Drop && it.type != SetType.WarmUp }.ifEmpty { sets.filter { it.type != SetType.Drop } }
    val count = working.size.coerceIn(1, 20)
    val reps = (working.firstOrNull()?.reps ?: 0).coerceIn(0, 100)
    val kg = (working.maxOfOrNull { it.weight } ?: 0.0).coerceIn(0.0, 1000.0)
    return Triple(count.toString(), reps.toString(), kg.toString())
}

fun dropSetCount(sets: List<ExerciseSet>): Int = sets.count { it.type == SetType.Drop }.coerceAtMost(MaxPlannedDropSets)

/**
 * This exercise with everything [source] plans (name, notes, and every set's weight and reps), or null when it already plans the
 * same, so a set ticked off or a rest taken is never a change. What this exercise has done today is left alone.
 */
fun Exercise.withPlanOf(source: Exercise): Exercise? {
    val sets = source.planSets()
    val (count, reps, kg) = summaryOf(sets)
    val drops = dropSetCount(sets)
    val same = name == source.name && notes == source.notes && planSets().signature() == sets.signature() &&
        this.sets.toIntOrNull() == count.toInt() && this.reps.toIntOrNull() == reps.toInt() &&
        weight.toDoubleOrNull() == kg.toDouble() && dropSets == drops
    if (same) return null
    return copy(name = source.name, notes = source.notes, sets = count, reps = reps, weight = kg, dropSets = drops, setsList = sets)
}

/** A fresh copy of the planned [source] for another plan: nothing done, nothing ticked, placed at the end. */
fun Exercise.freshCopyFor(planId: Long): Exercise {
    val sets = planSets()
    val (count, reps, kg) = summaryOf(sets)
    return copy(
        id = null,
        planId = planId,
        sourceId = id,
        isCheck = false,
        dateTime = "",
        restTime = "",
        sortOrder = -1,
        isExtra = false,
        vanishEndOfDay = false,
        sets = count,
        reps = reps,
        weight = kg,
        dropSets = dropSetCount(sets),
        setsList = sets,
    )
}

/** Whether an exercise belongs in the user's plan: one the user adds for just today (an extra, one that vanishes) does not. */
val Exercise.isPlanned: Boolean get() = !isExtra && !vanishEndOfDay
