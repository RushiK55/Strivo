package com.example.strivo.domain.coach

import com.example.strivo.domain.analytics.BodyReport
import com.example.strivo.domain.analytics.ExerciseHistory
import com.example.strivo.domain.analytics.ExpenditureStatus
import com.example.strivo.domain.analytics.FoodEntry
import com.example.strivo.domain.analytics.KcalPerKg
import com.example.strivo.domain.analytics.WeightEntry
import com.example.strivo.domain.model.PREBUILT_DAY
import com.example.strivo.domain.model.WorkoutSession
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The coach: what is worth doing next, worked out only from the user's own logs. Every rule is simple and its
 * message says why it fired, so a suggestion can always be checked against what the user remembers doing.
 */

// The order is the priority: a habit of skipping an exercise comes first, then the weights, then the body.
enum class AdviceKind { Skipped, IncreaseWeight, Deload, FastLoss, FastGain, Protein, WeighIn, Inactive }

data class Advice(
    val kind: AdviceKind,
    val title: String,
    val detail: String,
    /** Training advice is about one exercise, with the weight to use next time. */
    val exercise: String? = null,
    val suggestedKg: Double? = null,
    /** A skipped exercise is about one plan: its name, so the advice can take the exercise out of that plan. */
    val planName: String? = null,
) {
    /** Lower comes first: a stalled lift before a missing weigh-in. */
    val priority: Int get() = kind.ordinal
}

/** Workouts in a row at the same top weight before the coach says to go heavier. */
const val SameWeightWorkoutsToProgress = 4

/**
 * Reps gained on every set at the same weight that mean it has become too light, without waiting four workouts: the
 * top of the rep range, measured from where the user started at that weight, so it suits 5-rep and 15-rep lifts alike.
 */
const val ExtraRepsToProgress = 2

/** Grams of protein a day per kg of body weight that helps keep and build muscle. */
const val ProteinTargetPerKg = 1.6

/** Days without a weigh-in before the coach asks for one. */
const val WeighInEveryDays = 7

/** Days without a workout before the coach mentions it. */
const val InactiveDays = 5

/** Advice is only given for lifts done in this many days, so it is never about something long dropped. */
private const val RecentLiftDays = 28L

/** Weight from which the coach adds 5 kg at a time; below it, 2.5 kg. It never suggests a 1 kg jump: plates and dumbbells do not come that small. */
const val BigStepFromKg = 80.0

/** How much to add next time: 2.5 kg, or 5 kg on the heaviest lifts. */
fun progressionStepKg(kg: Double): Double = if (kg >= BigStepFromKg) 5.0 else 2.5

/** About 10% lighter, rounded to the same steps, for a lighter week. Always below [kg]. */
fun deloadKg(kg: Double): Double {
    val step = progressionStepKg(kg)
    val lighter = (kg * 0.9 / step).roundToInt() * step
    return if (lighter >= kg) (kg - step).coerceAtLeast(0.0) else lighter
}

/**
 * What to do next on one exercise, from its workouts (warm-ups and drop sets are already left out of the top set):
 * - reps fell three workouts in a row at the same weight: a lighter week;
 * - the same top weight for [SameWeightWorkoutsToProgress] workouts with reps holding: go heavier;
 * - every set at the top weight is [ExtraRepsToProgress] reps above the first workout at it: go heavier now.
 * Bodyweight exercises and lifts not done in the last four weeks get nothing.
 */
fun progressionAdvice(history: ExerciseHistory, today: LocalDate): Advice? {
    val weighted = history.sessions.filter { it.topWeightKg > 0 }
    val latest = weighted.lastOrNull() ?: return null
    if (ChronoUnit.DAYS.between(latest.date, today) > RecentLiftDays) return null
    val kg = latest.topWeightKg
    // The latest workouts at this same weight, oldest first.
    val streak = weighted.takeLastWhile { abs(it.topWeightKg - kg) < 0.01 }
    val reps = streak.map { it.topSet?.reps ?: 0 }
    val name = history.name

    if (streak.size >= 3) {
        val (a, b, c) = reps.takeLast(3)
        if (a > b && b > c) {
            val lighter = deloadKg(kg)
            return Advice(
                AdviceKind.Deload,
                "Take a lighter week on $name",
                "Your reps at ${kgText(kg)} kg went down three workouts in a row ($a, $b, $c). " +
                    "Do ${kgText(lighter)} kg for a week to recover, then build back up.",
                name,
                lighter,
            )
        }
    }

    val next = kg + progressionStepKg(kg)
    if (streak.size >= SameWeightWorkoutsToProgress && reps.last() >= reps.first()) {
        return Advice(
            AdviceKind.IncreaseWeight,
            "Go heavier on $name",
            "You've lifted ${kgText(kg)} kg in your last ${streak.size} workouts. Try ${kgText(next)} kg next time.",
            name,
            next,
        )
    }
    val atTop = latest.ranked.filter { abs(it.weight - kg) < 0.01 }
    val startReps = reps.first()
    if (streak.size >= 2 && atTop.isNotEmpty() && atTop.all { it.reps >= startReps + ExtraRepsToProgress }) {
        return Advice(
            AdviceKind.IncreaseWeight,
            "Go heavier on $name",
            "Every set at ${kgText(kg)} kg is now ${atTop.minOf { it.reps }}+ reps, up from $startReps. Try ${kgText(next)} kg next time.",
            name,
            next,
        )
    }
    return null
}

/** A weigh-in that is overdue, and a trend moving faster than is good for muscle (losing) or for fat (gaining). */
fun bodyAdvice(body: BodyReport, weightLog: List<WeightEntry>, today: LocalDate): List<Advice> = buildList {
    weightLog.maxOfOrNull { it.date }?.let { last ->
        val days = ChronoUnit.DAYS.between(last, today)
        if (days >= WeighInEveryDays) {
            add(
                Advice(
                    AdviceKind.WeighIn,
                    "Time to weigh in",
                    "Your last weigh-in was $days days ago. Weighing in a few times a week keeps your trend weight " +
                        "and energy expenditure accurate.",
                )
            )
        }
    }
    val rate = body.weeklyRateKg ?: return@buildList
    val trendKg = body.trend.lastOrNull()?.trendKg ?: return@buildList
    val percent = rate / trendKg * 100
    val perWeek = "${kgText(abs(rate), decimals = 1)} kg a week (${"%.1f".format(abs(percent))}% of your weight)"
    if (percent < -1.0) {
        add(
            Advice(
                AdviceKind.FastLoss,
                "You're losing weight fast",
                "Your trend weight is going down $perWeek. Faster than about 1% a week, you risk losing muscle; " +
                    "eating a little more would slow it down.",
            )
        )
    } else if (percent > 0.5) {
        add(
            Advice(
                AdviceKind.FastGain,
                "You're gaining weight fast",
                "Your trend weight is going up $perWeek. Faster than about 0.5% a week, most of it is fat; " +
                    "eating a little less would slow it down.",
            )
        )
    }
}

/**
 * Protein, from the last week's logged food that has macros. Needs three such days, so a single light day
 * does not trigger it.
 */
fun proteinAdvice(food: List<FoodEntry>, weightKg: Double?, today: LocalDate): Advice? {
    if (weightKg == null || weightKg <= 0) return null
    val days = food.filter { it.date in today.minusDays(6)..today && it.proteinG != null }.groupBy { it.date }
    // Today is usually still being logged, so it would pull the average down.
    val complete = days.filterKeys { it != today }
    if (complete.size < 3) return null
    val average = complete.values.sumOf { day -> day.sumOf { it.proteinG ?: 0.0 } } / complete.size
    val target = weightKg * ProteinTargetPerKg
    if (average >= target * 0.9) return null
    return Advice(
        AdviceKind.Protein,
        "Eat more protein",
        "You average ${average.roundToInt()} g a day. About ${target.roundToInt()} g ($ProteinTargetPerKg g per kg of " +
            "body weight) helps you keep and build muscle.",
    )
}

/** A nudge after [InactiveDays] days without a workout, for someone who has trained before. */
fun activityAdvice(sessions: List<WorkoutSession>, today: LocalDate): Advice? {
    if (sessions.size < 3) return null
    val last = sessions.maxOf { it.date.toLocalDate() }
    val days = ChronoUnit.DAYS.between(last, today)
    if (days < InactiveDays) return null
    return Advice(
        AdviceKind.Inactive,
        "It's been $days days since your last workout",
        "Even a short session keeps the habit going.",
    )
}

/** Workouts of one plan the coach looks at when it checks for a habit of skipping. */
const val SkipLookback = 4

/** Times an exercise must have been skipped in those workouts, the latest one included, for the coach to say something. */
const val SkipsToSpeak = 3

private const val SkipWindowDays = 60L

/**
 * An exercise that keeps being left out of a plan's workouts: skipped in at least [SkipsToSpeak] of the last [SkipLookback] times
 * the plan was finished, and in the latest one. Doing it once clears the advice. It asks the user to decide (lower it, swap it, take
 * it out of the plan) so skipping it again and again does not go on unnoticed.
 */
fun skipAdvice(sessions: List<WorkoutSession>, today: LocalDate): List<Advice> {
    val since = today.minusDays(SkipWindowDays)
    return sessions
        .filter { it.date.toLocalDate() >= since }
        .groupBy { it.planName }
        .flatMap { (plan, all) ->
            val recent = all.sortedByDescending { it.date }.take(SkipLookback)
            if (recent.size < SkipsToSpeak) return@flatMap emptyList()
            recent.first().skipped.mapNotNull { name ->
                val key = exerciseKey(name)
                val times = recent.count { session -> session.skipped.any { exerciseKey(it) == key } }
                if (times < SkipsToSpeak) return@mapNotNull null
                Advice(
                    AdviceKind.Skipped,
                    "You keep skipping $name",
                    "You left it out of $times of your last ${recent.size} $plan workouts. Skipping an exercise again and again " +
                        "leaves that muscle behind: lower the sets, swap it for one you will do, or take it out of the plan.",
                    exercise = name,
                    planName = plan,
                )
            }
        }
}

/** Everything the coach has to say, most important first. */
fun coachAdvice(
    histories: List<ExerciseHistory>,
    body: BodyReport,
    weightLog: List<WeightEntry>,
    food: List<FoodEntry>,
    sessions: List<WorkoutSession>,
    today: LocalDate,
): List<Advice> {
    val weightKg = body.trend.lastOrNull()?.trendKg ?: body.profile.weightKg
    return (
        histories.mapNotNull { progressionAdvice(it, today) } +
            skipAdvice(sessions, today) +
            bodyAdvice(body, weightLog, today) +
            listOfNotNull(proteinAdvice(food, weightKg, today), activityAdvice(sessions, today))
        ).sortedWith(compareBy<Advice> { it.priority }.thenBy { it.exercise.orEmpty().lowercase() })
}

/** Daily calories to lose 0.5 kg a week, to hold steady, and to gain 0.25 kg a week. */
data class CalorieTargets(
    val loseKcal: Double,
    val maintainKcal: Double,
    val gainKcal: Double,
    /** True when based on the user's own expenditure estimate rather than the formula. */
    val fromYourData: Boolean,
)

/** From the expenditure estimate when there is one, otherwise from the formula's maintenance. */
fun calorieTargets(expenditure: ExpenditureStatus?, formulaKcal: Double?): CalorieTargets? {
    val estimate = expenditure?.estimate?.kcal
    val base = estimate ?: formulaKcal ?: return null
    fun round10(kcal: Double) = (kcal / 10).roundToInt() * 10.0
    return CalorieTargets(
        loseKcal = round10(base - 0.5 * KcalPerKg / 7),
        maintainKcal = round10(base),
        gainKcal = round10(base + 0.25 * KcalPerKg / 7),
        fromYourData = estimate != null,
    )
}

/** 60.0 -> "60", 62.5 -> "62.5". */
private fun kgText(kg: Double, decimals: Int = 2): String {
    val rounded = Math.round(kg * 100) / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else "%.${decimals}f".format(rounded).trimEnd('0').trimEnd('.')
}

/**
 * Whether the user's plans already use this advice's weight, so there is nothing left to accept: every planned
 * copy of the exercise is at least the heavier weight, or at most the lighter-week weight. [plannedKg] holds the
 * planned weight of each copy of the exercise in the user's plans; with none, there is no plan to change.
 */
fun Advice.isInPlans(plannedKg: List<Double>?): Boolean {
    val kg = suggestedKg ?: return false
    if (plannedKg.isNullOrEmpty()) return false
    return when (kind) {
        AdviceKind.IncreaseWeight -> plannedKg.all { it >= kg - 0.01 }
        AdviceKind.Deload -> plannedKg.all { it <= kg + 0.01 }
        else -> false
    }
}

/** What a declined tip is remembered by: what it says and about what, so a different suggestion is a new tip. */
val Advice.declineKey: String
    get() = listOf(kind.name, exercise.orEmpty(), planName.orEmpty(), suggestedKg?.toString().orEmpty()).joinToString(":")

/**
 * The data this tip is worked out from. A declined tip stays closed while this is unchanged; once it changes (a new workout, a new
 * weigh-in, food logged on a new day) the coach works it out again, and if it is still true the tip comes back.
 */
fun Advice.dataStamp(sessions: List<WorkoutSession>, weightLog: List<WeightEntry>, food: List<FoodEntry>): String = when (kind) {
    AdviceKind.WeighIn, AdviceKind.FastLoss, AdviceKind.FastGain -> "weigh:${weightLog.size}:${weightLog.maxOfOrNull { it.date }}"
    AdviceKind.Protein -> "food:${food.maxOfOrNull { it.date }}"
    else -> "workouts:${sessions.size}:${sessions.maxOfOrNull { it.date }}"
}

/** Advice is matched to exercises by name, the way analytics groups them. */
fun exerciseKey(name: String): String = name.trim().lowercase()

/** One exercise in one of the user's own plans, with the weight it is planned at. */
data class PlannedExercise(val planName: String, val planDay: String, val weightKg: Double)

/**
 * Where accepting this advice would change things, in a few words: "Back & Biceps (Tue)", "Push Day (library)" for the
 * library of plans, or "3 plans" (the weekday plans and the library together). Null if nowhere.
 */
fun Advice.acceptedIn(planned: List<PlannedExercise>?): String? {
    // Taking a skipped exercise out of its plan changes that plan, whatever the weight.
    if (kind == AdviceKind.Skipped) return planName?.takeIf { !planned.isNullOrEmpty() }
    if (suggestedKg == null || planned.isNullOrEmpty()) return null
    val plans = planned.distinctBy { it.planName to it.planDay }
    val only = plans.singleOrNull() ?: return "${plans.size} plans"
    return "${only.planName} (${if (only.planDay == PREBUILT_DAY) "library" else only.planDay.take(3)})"
}
