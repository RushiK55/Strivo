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
