package com.example.strivo.domain.analytics

import com.example.strivo.domain.model.WorkoutSession
import java.time.LocalDate
import kotlin.math.roundToInt

/*
 * Calories in against calories out, one day at a time.
 *
 *   burned = base + active
 *   base   = what the body uses with no exercise: the formula's resting rate (BMR) x 1.2 for everyday movement, or,
 *            once Strivo has learned what the user really burns (see estimateExpenditure), that figure minus the
 *            exercise already in it, so a workout is never counted twice
 *   active = estimated workouts + the activity the user added by hand, both counted above resting (MET - 1)
 *
 * Eating less than "burned" is a deficit; more is a surplus. All of it is an estimate and is worded as one.
 */

/** The lifestyle factor for a day without exercise: sitting, standing and walking about (Harris-Benedict "sedentary"). */
const val BASE_ACTIVITY_FACTOR = 1.2

/** A workout whose clock ran for less than this per set was not timed (it was logged afterwards), so its length is estimated. */
private const val MIN_TIMED_SECONDS_PER_SET = 60L

/** About one set and the rest after it. */
private const val ESTIMATED_SECONDS_PER_SET = 150L

/**
 * How long a workout counts for when working out its calories: the clock, unless that is far too short for the sets that
 * were done (an exercise logged afterwards adds its sets without any clock time), when it is sets x 2.5 minutes.
 */
fun workoutSecondsForCalories(session: WorkoutSession): Long {
    val clock = parseDurationSeconds(session.totalTime)
    val sets = session.exercises.sumOf { it.sets.size }
    return if (clock >= sets * MIN_TIMED_SECONDS_PER_SET) clock else sets * ESTIMATED_SECONDS_PER_SET
}

/** True when [workoutSecondsForCalories] had to estimate the length rather than use the clock. */
fun isWorkoutLengthEstimated(session: WorkoutSession): Boolean =
    workoutSecondsForCalories(session) != parseDurationSeconds(session.totalTime)

/** An activity and its energy cost in METs (the Compendium of Physical Activities), so calories can be estimated from minutes. */
data class ActivityType(val name: String, val met: Double)

val ActivityTypes: List<ActivityType> = listOf(
    ActivityType("Walking", 3.5),
    ActivityType("Brisk walk", 4.5),
    ActivityType("Jogging", 7.0),
    ActivityType("Running", 9.8),
    ActivityType("Cycling", 7.5),
    ActivityType("Swimming", 6.0),
    ActivityType("Football", 7.0),
    ActivityType("Cricket", 4.8),
    ActivityType("Badminton", 5.5),
    ActivityType("Basketball", 6.5),
    ActivityType("Tennis", 7.3),
    ActivityType("Skipping", 10.0),
    ActivityType("Hiking", 6.0),
    ActivityType("Stairs", 8.0),
    ActivityType("HIIT", 8.0),
    ActivityType("Dancing", 5.0),
    ActivityType("Yoga", 2.5),
)

/** Calories above resting for [minutes] of an activity at [met]: (MET - 1) x kg x hours. */
fun activityKcal(met: Double, weightKg: Double, minutes: Int): Int =
    ((met - 1.0) * weightKg * minutes / 60.0).roundToInt().coerceAtLeast(0)

/** Where the user wants their day to end up, as calories a day under or over what they burn. */
enum class CalorieGoal(val key: String, val label: String, val offsetKcal: Double) {
    /** 0.5 kg a week. */
    Lose("lose", "Lose", -0.5 * KcalPerKg / 7),
    Maintain("maintain", "Maintain", 0.0),

    /** 0.25 kg a week. */
    Gain("gain", "Gain", 0.25 * KcalPerKg / 7);

    companion object {
        fun fromKey(key: String?): CalorieGoal = entries.firstOrNull { it.key == key } ?: Maintain
    }
}

/** One workout's share of a day's burn. [kcal] is null when no body weight is known to work it out from. */
data class WorkoutBurn(val planName: String, val minutes: Int?, val kcal: Double?, val lengthEstimated: Boolean)

data class EnergyDay(
    val date: LocalDate,
    val eaten: Int,
    val foodCount: Int,
    /** What the body burns with no exercise; null until the profile (or learned expenditure) allows it. */
    val baseKcal: Double?,
    val workouts: List<WorkoutBurn>,
    val extras: List<ExtraActivity>,
) {
    val isLogged: Boolean get() = foodCount > 0
    val workoutKcal: Double get() = workouts.sumOf { it.kcal ?: 0.0 }
    val extraKcal: Double get() = extras.sumOf { it.calories }.toDouble()

    /** Calories above resting, from workouts and activity added by hand. */
    val activeKcal: Double get() = workoutKcal + extraKcal

    /** A workout is on the day but costs nothing yet because there is no body weight to work it out from. */
    val workoutNeedsWeight: Boolean get() = workouts.any { it.kcal == null }

    /** Base plus active; null while the base is unknown. */
    val burnedKcal: Double? get() = baseKcal?.plus(activeKcal)

    /** Eaten minus burned: negative is a deficit, positive a surplus. Null while the burn is unknown. */
    val balanceKcal: Double? get() = burnedKcal?.let { eaten - it }
}

/** The base burn for a day: learned from the user's data when there is enough of it, otherwise from the formula. Null when neither is known. */
fun baseBurnKcal(bmr: Double?, expenditure: ExpenditureEstimate?, avgActiveKcal: Double): Double? {
    if (expenditure != null) {
        // What the user really burns already holds their usual exercise: take that out, but never go below resting.
        return (expenditure.kcal - avgActiveKcal).coerceAtLeast(bmr ?: 0.0)
    }
    return bmr?.let { it * BASE_ACTIVITY_FACTOR }
}

private fun workoutBurns(sessions: List<WorkoutSession>, weightKg: Double?): List<WorkoutBurn> =
    sessions.sortedBy { it.date }.map { session ->
        val seconds = workoutSecondsForCalories(session)
        WorkoutBurn(
            planName = session.planName,
            minutes = (seconds / 60).toInt().takeIf { it > 0 },
            kcal = weightKg?.let { estimateWorkoutCalories(seconds, it) },
            lengthEstimated = isWorkoutLengthEstimated(session),
        )
    }

fun buildEnergyDay(
    date: LocalDate,
    food: List<FoodEntry>,
    sessions: List<WorkoutSession>,
    extras: List<ExtraActivity>,
    weightKgOn: (LocalDate) -> Double?,
    baseKcal: Double?,
): EnergyDay {
    val eaten = food.filter { it.date == date }
    return EnergyDay(
        date = date,
        eaten = eaten.sumOf { it.calories },
        foodCount = eaten.size,
        baseKcal = baseKcal,
        workouts = workoutBurns(sessions.filter { it.date.toLocalDate() == date }, weightKgOn(date)),
        extras = extras.filter { it.date == date },
    )
}

/** Average active calories a day over the [days] ending [today]: what a learned expenditure already contains. */
fun averageActiveKcal(
    sessions: List<WorkoutSession>,
    extras: List<ExtraActivity>,
    weightKgOn: (LocalDate) -> Double?,
    today: LocalDate,
    days: Int = 28,
): Double {
    val start = today.minusDays(days - 1L)
    val workouts = sessions.filter { it.date.toLocalDate() in start..today }.sumOf { session ->
        weightKgOn(session.date.toLocalDate())?.let { estimateWorkoutCalories(workoutSecondsForCalories(session), it) } ?: 0.0
    }
    return (workouts + extras.filter { it.date in start..today }.sumOf { it.calories }) / days
}

/** A stretch of days in energy terms. Only days with food logged and a known burn count, so a day left empty is not a fast. */
data class EnergySummary(
    val days: List<EnergyDay>,
    val countedDays: Int,
    val avgEaten: Double?,
    val avgBurned: Double?,
    /** Average eaten minus burned over the counted days. */
    val avgBalance: Double?,
    /** What that pace adds up to in a week, at 7,700 kcal per kg; null when nothing was counted. */
    val kgPerWeek: Double?,
    /** The average burn split into what the body uses at rest and what exercise added; null when nothing was counted. */
    val avgBaseKcal: Double? = null,
    val avgActiveKcal: Double? = null,
)

fun summarizeEnergy(days: List<EnergyDay>, today: LocalDate? = null): EnergySummary {
    val loggedDays = days.filter { it.isLogged && it.burnedKcal != null }
    // Today is still being eaten, so a half-logged day would look like a fast: once there are other days to go on, leave it out.
    val settled = loggedDays.filter { it.date != today }
    val counted = if (today != null && settled.size >= 2) settled else loggedDays
    if (counted.isEmpty()) return EnergySummary(days, 0, null, null, null, null)
    val avgBalance = counted.sumOf { it.balanceKcal!! } / counted.size
    return EnergySummary(
        days = days,
        countedDays = counted.size,
        avgEaten = counted.sumOf { it.eaten }.toDouble() / counted.size,
        avgBurned = counted.sumOf { it.burnedKcal!! } / counted.size,
        avgBalance = avgBalance,
        kgPerWeek = avgBalance * 7 / KcalPerKg,
        avgBaseKcal = counted.sumOf { it.baseKcal!! } / counted.size,
        avgActiveKcal = counted.sumOf { it.activeKcal } / counted.size,
    )
}

/** What the macros add up to: 4 kcal a gram of protein and carbohydrate, 9 of fat. */
fun macroKcal(proteinG: Double, carbsG: Double, fatG: Double): Int = (proteinG * 4 + carbsG * 4 + fatG * 9).roundToInt()

/** A sensible protein aim for building or keeping muscle, in grams a day: 1.8 g per kg of body weight (the usual 1.6 to 2.2 range). */
fun proteinTargetG(weightKg: Double?): Int? = weightKg?.takeIf { it in 20.0..400.0 }?.let { (it * 1.8).roundToInt() }
