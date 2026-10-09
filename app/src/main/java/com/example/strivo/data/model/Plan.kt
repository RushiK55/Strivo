package com.example.strivo.data.model

/** Stored in [Plan.planDay] for plans that live in the prebuilt library instead of on a weekday. */
const val PREBUILT_DAY = "Prebuilt"

data class Plan(
    val planId: Long? = null,
    val planName: String,
    val planDay: String,
) {
    val isPrebuilt: Boolean get() = planDay == PREBUILT_DAY
}

/** The plan a day's extra exercises (done on top of, or instead of, the planned ones) are collected in. */
const val EXTRA_PLAN_NAME = "Extra exercises"
