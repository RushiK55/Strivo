package com.example.strivo.domain.activity

import com.example.strivo.domain.analytics.ExtraActivity
import com.example.strivo.domain.analytics.FoodEntry
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.Plan
import com.example.strivo.domain.model.PREBUILT_DAY
import com.example.strivo.domain.model.WorkoutSession
import com.example.strivo.domain.model.isWork
import com.example.strivo.domain.plan.PlanOffer
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The activity log: one line for each thing a user does that an admin may want to see, written in plain words.
 * The log is saved on the phone, backed up with the rest of the user's data, and read by the admin dashboard, which
 * understands the `type` keys below. Add a key here and the dashboard shows it with a neutral label until it learns it.
 */

object ActivityKind {
    const val PlanCreated = "plan_created"
    const val PlanDeleted = "plan_deleted"
    const val PlanCopied = "plan_copied"
    const val PlanAssigned = "plan_assigned"
    const val ExerciseAdded = "exercise_added"
    const val ExerciseEdited = "exercise_edited"
    const val ExerciseRemoved = "exercise_removed"
    const val WorkoutFinished = "workout_finished"
    const val FoodLogged = "food_logged"
    const val ActivityAdded = "activity_added"
    const val WeightLogged = "weight_logged"
    const val ProfileUpdated = "profile_updated"
}

/** One thing the user did. [at] is epoch milliseconds. */
data class ActivityEvent(
    val id: Long? = null,
    val at: Long,
    val type: String,
    val summary: String,
)

private fun kg(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.ROOT, "%.1f", value)

private fun planLabel(name: String, day: String): String = if (day == PREBUILT_DAY) "$name (library)" else "$name ($day)"

fun planCreated(plan: Plan, at: Long) =
    ActivityEvent(at = at, type = ActivityKind.PlanCreated, summary = "Created plan \"${plan.planName}\" for ${if (plan.isPrebuilt) "the library" else plan.planDay}")

fun planDeleted(plan: Plan, at: Long) =
    ActivityEvent(at = at, type = ActivityKind.PlanDeleted, summary = "Deleted plan ${planLabel("\"${plan.planName}\"", plan.planDay)}")

/** The user took a plan from the plan library into their own plans. */
fun planTaken(name: String, at: Long) =
    ActivityEvent(at = at, type = ActivityKind.PlanCreated, summary = "Took the plan \"$name\" from the plan library")

fun planCopied(source: Plan, day: String, at: Long) =
    ActivityEvent(at = at, type = ActivityKind.PlanCopied, summary = "Added plan \"${source.planName}\" to $day")

/**
 * What the user answered to a plan offer: "Accepted plan "Push Day" (Monday) from Coach Raj" or
 * "Declined 7 plans from Coach Raj (Mon to Sun)". One line for the whole offer, however many plans were in it.
 */
fun planOfferAnswered(offer: PlanOffer, accepted: Boolean, at: Long): ActivityEvent {
    val from = offer.trainerName.ifEmpty { "their trainer" }
    val verb = if (accepted) "Accepted" else "Declined"
    val what = if (offer.plans.size == 1) "plan " + planLabel("\"" + offer.plans.single().planName + "\"", offer.plans.single().day) else "${offer.plans.size} plans"
    val days = if (offer.plans.size == 1) "" else " (${offer.where})"
    return ActivityEvent(at = at, type = ActivityKind.PlanAssigned, summary = "$verb $what from $from$days")
}

private fun target(exercise: Exercise): String {
    val weight = exercise.weight.toDoubleOrNull() ?: 0.0
    val drops = if (exercise.dropSets > 0) " + ${exercise.dropSets} drop" else ""
    return "${exercise.sets} × ${exercise.reps}$drops" + if (weight > 0) " @ ${kg(weight)} kg" else ", bodyweight"
}

fun exerciseAdded(exercise: Exercise, plan: Plan?, at: Long, fromLibrary: Boolean = false) = ActivityEvent(
    at = at,
    type = ActivityKind.ExerciseAdded,
    summary = "Added \"${exercise.name}\" to ${plan?.let { planLabel(it.planName, it.planDay) } ?: "a plan"} · ${target(exercise)}" +
        if (fromLibrary) " (from the library)" else "",
)

fun exerciseEdited(exercise: Exercise, plan: Plan?, at: Long) = ActivityEvent(
    at = at,
    type = ActivityKind.ExerciseEdited,
    summary = "Changed \"${exercise.name}\" in ${plan?.let { planLabel(it.planName, it.planDay) } ?: "a plan"} · ${target(exercise)}",
)

fun exerciseRemoved(exercise: Exercise, plan: Plan?, at: Long) = ActivityEvent(
    at = at,
    type = ActivityKind.ExerciseRemoved,
    summary = "Removed \"${exercise.name}\" from ${plan?.let { planLabel(it.planName, it.planDay) } ?: "a plan"}",
)

/** "Finished "Push Day": 4 exercises, 4,203 kg, 42 min". Warm-ups are not counted in the volume. */
fun workoutFinished(session: WorkoutSession, at: Long): ActivityEvent {
    val volume = session.exercises.sumOf { e -> e.sets.filter { it.isWork }.sumOf { it.weight * it.reps } }
    val exercises = session.exercises.size
    val parts = buildList {
        add(if (exercises == 1) "1 exercise" else "$exercises exercises")
        if (volume > 0) add(String.format(Locale.ROOT, "%,.0f kg", volume))
        minutes(session.totalTime)?.let { add("$it min") }
    }
    return ActivityEvent(at = at, type = ActivityKind.WorkoutFinished, summary = "Finished \"${session.planName}\": ${parts.joinToString(", ")}")
}

/** "00:42:14" -> 42; null when the time is not readable or under a minute. */
internal fun minutes(clock: String): Int? {
    val parts = clock.split(":").map { it.trim().toIntOrNull() ?: return null }
    val seconds = when (parts.size) {
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        2 -> parts[0] * 60 + parts[1]
        else -> return null
    }
    return (seconds / 60).takeIf { it > 0 }
}

private val OldFoodLine = Regex("""(?s)^Logged ".*" \((\d+) kcal\) as (\w+)$""")

/**
 * Older versions wrote the food's name into the line ("Logged "Dal and rice" (560 kcal) as dinner"). Returns that line
 * without the name, or null when [summary] is not such a line (and needs no change).
 */
fun withoutFoodName(summary: String): String? =
    OldFoodLine.find(summary)?.let { "Logged ${it.groupValues[2]} (${it.groupValues[1]} kcal)" }

/** What was eaten is private: the line says which meal and how much, never the food. */
fun foodLogged(entry: FoodEntry, at: Long) = ActivityEvent(
    at = at,
    type = ActivityKind.FoodLogged,
    summary = "Logged ${entry.meal.label.lowercase()} (${entry.calories} kcal${entry.proteinG?.let { ", ${it.roundToInt()} g protein" }.orEmpty()})",
)

fun activityAdded(entry: ExtraActivity, at: Long) = ActivityEvent(
    at = at,
    type = ActivityKind.ActivityAdded,
    summary = "Added activity \"${entry.name}\" (${entry.calories} kcal${entry.minutes?.let { ", $it min" } ?: ""})",
)

fun weightLogged(weightKg: Double, at: Long) =
    ActivityEvent(at = at, type = ActivityKind.WeightLogged, summary = "Weighed in at ${kg(weightKg)} kg")

fun profileUpdated(at: Long) = ActivityEvent(at = at, type = ActivityKind.ProfileUpdated, summary = "Updated their profile")
