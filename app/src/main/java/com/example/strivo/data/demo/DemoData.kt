package com.example.strivo.data.demo

import com.example.strivo.core.util.dayName
import com.example.strivo.core.util.formatDuration
import com.example.strivo.domain.analytics.ExtraActivity
import com.example.strivo.domain.analytics.FoodEntry
import com.example.strivo.domain.analytics.Meal
import com.example.strivo.domain.analytics.WeightEntry
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.ExerciseSet
import com.example.strivo.domain.model.PerformedExercise
import com.example.strivo.domain.model.SetType
import com.example.strivo.domain.model.WorkoutSession
import com.example.strivo.domain.model.suggestedDropKg
import com.example.strivo.domain.model.suggestedWarmUpKg
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Four weeks of made-up training for trying out the analytics: five workouts most weeks, one missed day, and weights
 * that go up the way they do with double progression (a rep more next time, then more weight). Food is logged on most
 * days and weigh-ins drift down a couple of kilos, enough for the trend weight and the expenditure estimate to work.
 * The same call always gives the same data.
 */
/** How the demo weigh-ins go: slowly down (the default), or fast enough that the coach speaks up. */
enum class DemoWeightTrend(val label: String, internal val startAboveKg: Double, internal val foodScale: Double) {
    /** About 2 kg down over four weeks, a little over half a percent a week. */
    Steady("steady", 2.0, 1.0),

    /** About 1 kg a week down, 1.5% of the body weight a week, on a bit less food. */
    FastLoss("losing weight fast", 4.0, 0.92),

    /** About 0.6 kg a week up, close to 1% of the body weight a week, on a lot more food. */
    FastGain("gaining weight fast", -2.4, 1.75),
}

object DemoData {
    /** Days the demo covers, today included. */
    const val Days = 28

    /** [step] is the weight added every second time the plan comes round; 0 means bodyweight, where reps go up instead. */
    private data class Move(
        val name: String,
        val sets: Int,
        val reps: Int,
        val startKg: Double,
        val step: Double,
        /** Ends with a drop set; the plan has one too. */
        val drop: Boolean = false,
        /** How the lift goes over the weeks, so the coach has something to say about some of them. */
        val trend: Trend = Trend.Normal,
    )

    private enum class Trend {
        /** A rep more next time, then more weight. */
        Normal,

        /** The same weight and reps every time: the coach says go heavier. */
        Stalled,

        /** The same weight with fewer reps each time: the coach says a lighter week. */
        Declining,

        /** More weight, then two more reps on every set at it: the coach says go heavier now. */
        Climbing,
    }

    private class Plan(val name: String, val moves: List<Move>)

    private val chest = Plan(
        "Chest & Triceps",
        listOf(
            Move("Bench Press", 4, 8, 57.5, 2.5), Move("Incline Dumbbell Press", 3, 10, 20.0, 2.0),
            Move("Chest Fly", 3, 12, 12.0, 2.0), Move("Tricep Pushdown", 3, 12, 22.5, 2.5, drop = true),
        ),
    )
    private val back = Plan(
        "Back & Biceps",
        listOf(
            Move("Barbell Row", 4, 8, 50.0, 2.5, trend = Trend.Declining), Move("Lat Pulldown", 3, 10, 40.0, 2.5, trend = Trend.Climbing),
            Move("Seated Cable Row", 3, 12, 35.0, 2.5), Move("Barbell Curl", 3, 10, 20.0, 2.5, drop = true),
        ),
    )
    private val legs = Plan(
        "Leg Day",
        listOf(
            Move("Squat", 4, 8, 70.0, 5.0), Move("Leg Press", 3, 10, 120.0, 10.0),
            Move("Romanian Deadlift", 3, 10, 50.0, 5.0), Move("Calf Raise", 4, 15, 40.0, 5.0),
        ),
    )
    private val shoulders = Plan(
        "Shoulders & Abs",
        listOf(
            Move("Overhead Press", 4, 8, 40.0, 2.5, trend = Trend.Stalled), Move("Lateral Raise", 3, 12, 8.0, 1.0, drop = true),
            Move("Face Pull", 3, 15, 17.5, 2.5), Move("Crunches", 3, 16, 0.0, 0.0),
        ),
    )

    private class Day(val offset: Int, val plan: Plan, val start: LocalTime = LocalTime.of(7, 30))

    private val evening = LocalTime.of(18, 15)

    // offset = days before today (27 = four weeks ago, 0 = today). Each week runs chest, back, legs, a rest day,
    // shoulders, one more workout and a rest day. Fifteen days ago was missed, and shoulders are trained after work.
    // The last week is the same as the old seven-day demo: bench 60 then 62.5, squat 80, and so on.
    private val schedule = listOf(
        Day(27, chest), Day(26, back), Day(25, legs), Day(23, shoulders, evening), Day(22, back),
        Day(20, chest), Day(19, back), Day(18, legs, evening), Day(16, shoulders, evening),
        Day(13, chest), Day(12, back), Day(11, legs), Day(9, shoulders, evening), Day(8, legs, LocalTime.of(10, 0)),
        Day(6, chest), Day(5, back), Day(4, legs), Day(2, shoulders, evening), Day(1, chest),
    )

    /** The weight used the [round]th time the plan came round (0 the first time). */
    private fun weightAt(move: Move, round: Int): Double = when (move.trend) {
        Trend.Stalled, Trend.Declining -> move.startKg
        Trend.Climbing -> move.startKg + if (round >= 3) move.step else 0.0
        Trend.Normal -> move.startKg + move.step * (round / 2)
    }

    /** The reps aimed for on each working set the [round]th time. */
    private fun repsAt(move: Move, round: Int): Int = when (move.trend) {
        Trend.Stalled -> move.reps
        Trend.Declining -> move.reps - maxOf(0, round - 2)
        Trend.Climbing -> if (round >= 4) move.reps + 2 else move.reps
        Trend.Normal -> if (move.step == 0.0) move.reps + 2 * round else move.reps + round % 2
    }

    // The plans start today and run on from there, so the exercises the coach talks about are on Home right away.
    private val planOrder get() = listOf(back, shoulders, chest, legs)

    /**
     * The plans behind the workouts, for the plan screens and for the coach to change: one for each workout name, on
     * today and the days after, with the weights the latest workout used (so the coach's suggestions are one step ahead).
     */
    class DemoPlan(val name: String, val day: String, val exercises: List<Exercise>)

    fun plans(today: LocalDate): List<DemoPlan> = planOrder.mapIndexed { index, plan ->
        val round = schedule.count { it.plan === plan } - 1
        DemoPlan(
            name = plan.name,
            day = dayName(today.plusDays(index.toLong()).dayOfWeek),
            exercises = plan.moves.map { move ->
                Exercise(
                    planId = -1L,
                    name = move.name,
                    weight = weightAt(move, round).toString(),
                    sets = move.sets.toString(),
                    reps = move.reps.toString(),
                    notes = "",
                    dropSets = if (move.drop) 1 else 0,
                )
            },
        )
    }

    // --- Food ---

    private class Food(val name: String, val kcal: Int, val protein: Double, val carbs: Double, val fat: Double)

    private val oatmeal = Food("Oatmeal with banana", 380, 12.0, 68.0, 7.0)
    private val eggsToast = Food("Scrambled eggs on toast", 360, 22.0, 28.0, 18.0)
    private val poha = Food("Poha with peanuts", 330, 8.0, 52.0, 10.0)
    private val chickenBowl = Food("Chicken and rice bowl", 620, 45.0, 70.0, 14.0)
    private val dalRice = Food("Dal, rice and salad", 560, 20.0, 88.0, 12.0)
    private val paneerWrap = Food("Paneer wrap", 540, 26.0, 52.0, 24.0)
    private val yogurt = Food("Greek yogurt", 150, 15.0, 8.0, 4.0)
    private val shake = Food("Protein shake", 180, 25.0, 6.0, 3.0)
    private val bananaPb = Food("Banana and peanut butter", 250, 7.0, 30.0, 12.0)
    private val fish = Food("Grilled fish with vegetables", 480, 42.0, 22.0, 22.0)
    private val eggCurry = Food("Egg curry with chapati", 590, 30.0, 55.0, 26.0)
    private val pulao = Food("Veg pulao and raita", 520, 14.0, 80.0, 15.0)

    private class Eaten(val meal: Meal, val food: Food)

    // A week of full days, picked by offset % 7. Day 3 of each week is a rest day and lighter.
    private val menus = listOf(
        listOf(Eaten(Meal.Breakfast, poha), Eaten(Meal.Lunch, dalRice), Eaten(Meal.Snack, shake), Eaten(Meal.Dinner, pulao)),
        listOf(Eaten(Meal.Breakfast, oatmeal), Eaten(Meal.Lunch, paneerWrap), Eaten(Meal.Snack, shake), Eaten(Meal.Dinner, eggCurry)),
        listOf(Eaten(Meal.Breakfast, eggsToast), Eaten(Meal.Lunch, chickenBowl), Eaten(Meal.Snack, bananaPb), Eaten(Meal.Dinner, fish)),
        listOf(Eaten(Meal.Breakfast, oatmeal), Eaten(Meal.Lunch, dalRice), Eaten(Meal.Snack, yogurt), Eaten(Meal.Dinner, pulao)),
        listOf(Eaten(Meal.Breakfast, poha), Eaten(Meal.Lunch, chickenBowl), Eaten(Meal.Snack, shake), Eaten(Meal.Dinner, eggCurry)),
        listOf(Eaten(Meal.Breakfast, eggsToast), Eaten(Meal.Lunch, paneerWrap), Eaten(Meal.Snack, bananaPb), Eaten(Meal.Dinner, fish)),
        listOf(Eaten(Meal.Breakfast, oatmeal), Eaten(Meal.Lunch, chickenBowl), Eaten(Meal.Snack, shake), Eaten(Meal.Dinner, dalRice)),
    )

    // Today is only part-logged, and a few days were forgotten, as real logging tends to be.
    private val todaySoFar = listOf(Eaten(Meal.Breakfast, poha), Eaten(Meal.Snack, yogurt))
    private val unloggedDays = setOf(24, 15, 10)

    /** Four weeks of meals with calories and macros. Portions vary a little from day to day. */
    fun food(today: LocalDate, trend: DemoWeightTrend = DemoWeightTrend.Steady): List<FoodEntry> {
        val random = Random(7)
        return (Days - 1 downTo 0).filter { it !in unloggedDays }.flatMap { offset ->
            val meals = if (offset == 0) todaySoFar else menus[offset % 7]
            meals.map { eaten ->
                val portion = (0.95 + random.nextDouble() * 0.1) * trend.foodScale // 95%-105%, more or less in the fast-change demos
                FoodEntry(
                    date = today.minusDays(offset.toLong()),
                    meal = eaten.meal,
                    name = eaten.food.name,
                    calories = (eaten.food.kcal * portion).toInt(),
                    proteinG = (eaten.food.protein * portion).toInt().toDouble(),
                    carbsG = (eaten.food.carbs * portion).toInt().toDouble(),
                    fatG = (eaten.food.fat * portion).toInt().toDouble(),
                )
            }
        }
    }

    // --- Other activity ---

    /** Activity outside the gym, added by hand, mostly on rest days. */
    fun activities(today: LocalDate): List<ExtraActivity> = listOf(
        ExtraActivity(date = today.minusDays(24), name = "Evening walk", calories = 120, minutes = 35),
        ExtraActivity(date = today.minusDays(17), name = "Badminton", calories = 280, minutes = 50),
        ExtraActivity(date = today.minusDays(10), name = "Evening walk", calories = 140, minutes = 40),
        ExtraActivity(date = today.minusDays(3), name = "Evening walk", calories = 130, minutes = 40),
        ExtraActivity(date = today.minusDays(2), name = "Cycling", calories = 320, minutes = 45),
        ExtraActivity(date = today.minusDays(1), name = "Football with friends", calories = 450, minutes = 60),
    )

    // --- Weight ---

    private val weighInDays = listOf(27, 25, 23, 20, 18, 16, 13, 11, 9, 6, 4, 3, 1, 0)

    /**
     * Weigh-ins every two or three days over four weeks, drifting down by about the demo trend to [currentKg], which is
     * the profile's real weight. Each one is a little off the line, the way water and food move the scale.
     */
    fun weights(today: LocalDate, currentKg: Double, trend: DemoWeightTrend = DemoWeightTrend.Steady): List<WeightEntry> {
        val random = Random(3)
        return weighInDays.map { offset ->
            val noise = if (offset == 0) 0.0 else (random.nextDouble() - 0.5) * 0.7
            val kg = currentKg + trend.startAboveKg * offset / (Days - 1) + noise
            WeightEntry(today.minusDays(offset.toLong()), (kg * 10).roundToInt() / 10.0)
        }
    }

    // --- Workouts ---

    fun sessions(today: LocalDate): List<WorkoutSession> {
        val random = Random(42)
        val timesDone = mutableMapOf<Plan, Int>()
        return schedule.map { day ->
            // How many times this plan was done before: one more rep each time, and more weight every second time.
            val round = timesDone.getOrDefault(day.plan, 0)
            timesDone[day.plan] = round + 1
            var activeSeconds = 0L
            val exercises = day.plan.moves.mapIndexed { moveIndex, move ->
                val weight = weightAt(move, round)
                val targetReps = repsAt(move, round)
                fun logged(kg: Double, reps: Int, type: SetType, rests: Boolean = true): ExerciseSet {
                    val setSeconds = 25 + random.nextInt(0, 25)
                    val restSeconds = if (rests) 60 + random.nextInt(0, 60) else 0
                    activeSeconds += setSeconds + restSeconds
                    return ExerciseSet(
                        weight = kg,
                        reps = reps.coerceAtLeast(1),
                        isCompleted = true,
                        setDuration = formatDuration(setSeconds * 1000L),
                        restTime = if (rests) formatDuration(restSeconds * 1000L) else "",
                        type = type,
                    )
                }
                val sets = buildList {
                    // The first lift of the day starts with a light warm-up set.
                    if (moveIndex == 0 && move.step > 0) add(logged(suggestedWarmUpKg(weight), 10, SetType.WarmUp))
                    repeat(move.sets) { index ->
                        // The last set of an exercise is usually a rep short.
                        val reps = if (index == move.sets - 1 && move.trend != Trend.Climbing) targetReps - random.nextInt(0, 3) else targetReps
                        // A drop set follows straight on, so there is no rest before it.
                        add(logged(weight, reps, SetType.Normal, rests = !(move.drop && index == move.sets - 1)))
                    }
                    if (move.drop) add(logged(suggestedDropKg(weight), targetReps + 4, SetType.Drop))
                }
                PerformedExercise(name = move.name, sets = sets)
            }
            WorkoutSession(
                planName = day.plan.name,
                date = today.minusDays(day.offset.toLong()).atTime(day.start),
                // Setting up, warming up and moving between machines on top of the sets themselves.
                totalTime = formatDuration((activeSeconds + 12 * 60) * 1000L),
                exercises = exercises,
            )
        }
    }
}
