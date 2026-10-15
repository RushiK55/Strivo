package com.example.strivo.domain.model

/** Stored in [Plan.planDay] for plans that live in the prebuilt library instead of on a weekday. */
const val PREBUILT_DAY = "Prebuilt"

/**
 * A plan. One on a weekday is what the user does that day. One in the user's own plans (the "prebuilt" ones) is the plan itself: where
 * it came from ([origin]) and every day it was put on ([sourcePlanId] on the day's plan) stay linked, so what the user changes on the
 * day (heavier weights, fewer reps on the last set, an exercise added) is kept in their plan, and the plan is what the next day starts
 * from. The trainer sees both, because both are backed up.
 */
data class Plan(
    val planId: Long? = null,
    val planName: String,
    val planDay: String,
    /** On a weekday's plan: the id of the user's plan it was made from; null for a plan nobody linked (made by hand on that day). */
    val sourcePlanId: Long? = null,
    /** On a user's plan: where it came from, "library:<id>" (the plan library) or "trainer:<id>" (a plan a trainer sent); empty when the user made it. */
    val origin: String = "",
) {
    val isPrebuilt: Boolean get() = planDay == PREBUILT_DAY

    /** Whether a plan sent by a trainer, or one from the plan library, is where this plan came from. */
    val fromTrainer: Boolean get() = origin.startsWith(TRAINER_ORIGIN)
    val fromLibrary: Boolean get() = origin.startsWith(LIBRARY_ORIGIN)
}

const val TRAINER_ORIGIN = "trainer:"
const val LIBRARY_ORIGIN = "library:"

/** The plan a day's extra exercises (done on top of, or instead of, the planned ones) are collected in. */
const val EXTRA_PLAN_NAME = "Extra exercises"
