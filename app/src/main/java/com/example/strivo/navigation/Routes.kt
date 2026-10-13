package com.example.strivo.navigation

import androidx.compose.runtime.getValue

internal object Routes {
    const val HOME = "home"
    const val PROFILE = "profile"
    const val HISTORY = "history"
    const val ANALYTICS = "analytics"
    const val FOOD = "food"
    const val HISTORY_DETAILS = "historyDetails/{sessionId}"
    const val SAVE_PLAN = "savePlan"
    const val PICK_PLAN = "pickPlan/{day}"
    const val DAY_PLANS = "dayPlans/{day}"
    const val PLAN_DETAILS = "planDetails/{planId}"
    const val SAVE_EXERCISE = "saveExercise/{planId}?exerciseId={exerciseId}&isExtra={isExtra}"
    const val EXERCISE_DETAILS = "exerciseDetails/{planId}/{exerciseId}"
    const val WORKOUT = "workout/{planId}"
    const val LIBRARY = "library?planId={planId}"

    fun historyDetails(sessionId: Long) = "historyDetails/$sessionId"
    fun pickPlan(day: String) = "pickPlan/$day"
    fun dayPlans(day: String) = "dayPlans/$day"
    fun planDetails(planId: Long) = "planDetails/$planId"
    fun saveExercise(planId: Long, exerciseId: Long? = null, isExtra: Boolean = false) =
        "saveExercise/$planId?exerciseId=${exerciseId ?: -1L}&isExtra=$isExtra"

    fun exerciseDetails(planId: Long, exerciseId: Long) = "exerciseDetails/$planId/$exerciseId"
    fun workout(planId: Long) = "workout/$planId"

    /** The exercise library; with a [planId] it was opened from that plan, so adding goes straight there. */
    fun library(planId: Long? = null) = "library?planId=${planId ?: -1L}"
}
