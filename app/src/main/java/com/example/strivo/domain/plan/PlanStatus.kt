package com.example.strivo.domain.plan

import com.example.strivo.domain.model.Exercise

/**
 * Whether today's plan is done. Ticking off every exercise finishes it; so does finishing the workout, but then an
 * exercise added to the plan afterwards brings it back as something to do.
 *
 * [workoutFinished] is whether a workout for the plan was saved today. [presentAtFinish] holds the ids of the
 * exercises the plan had at that moment (null when unknown, e.g. a workout saved before this was tracked): those
 * were done or skipped on purpose, so only newer ones matter. Without that record a regular plan stays done, but
 * extra exercises, which are added one by one through the day, need every one ticked.
 */
fun isPlanDoneToday(
    exercises: List<Exercise>,
    workoutFinished: Boolean,
    presentAtFinish: Set<Long>?,
    isExtraPlan: Boolean,
): Boolean {
    if (exercises.isEmpty()) return workoutFinished
    if (workoutFinished) {
        return when {
            presentAtFinish != null -> exercises.all { it.isCheck || it.id in presentAtFinish }
            isExtraPlan -> exercises.all { it.isCheck }
            else -> true
        }
    }
    return exercises.all { it.isCheck }
}
