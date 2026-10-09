package com.example.strivo.data

import com.example.strivo.data.analytics.ExtraActivity
import com.example.strivo.data.analytics.FoodEntry
import com.example.strivo.data.analytics.Meal
import com.example.strivo.data.analytics.WeightEntry
import com.example.strivo.data.model.ExerciseSet
import com.example.strivo.data.model.PerformedExercise
import com.example.strivo.data.model.WorkoutSession
import com.example.strivo.util.formatDuration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/**
 * Seven days of made-up training for trying out the analytics: five workouts and two rest days,
 * with weights creeping up when a plan comes round a second time. The same call always gives the same data.
 */
object DemoData {
    private data class Move(val name: String, val sets: Int, val reps: Int, val weight: Double)

    private class Day(val offset: Int, val plan: String, val moves: List<Move>)

    // offset = days before today (6 = a week ago, 0 = today)
    private val week = listOf(
        Day(
            6, "Chest & Triceps",
            listOf(
                Move("Bench Press", 4, 8, 60.0), Move("Incline Dumbbell Press", 3, 10, 22.0),
                Move("Chest Fly", 3, 12, 14.0), Move("Tricep Pushdown", 3, 12, 25.0),
            ),
        ),
        Day(
            5, "Back & Biceps",
            listOf(
                Move("Barbell Row", 4, 8, 55.0), Move("Lat Pulldown", 3, 10, 45.0),
                Move("Seated Cable Row", 3, 12, 40.0), Move("Barbell Curl", 3, 10, 25.0),
            ),
        ),
        Day(
            4, "Leg Day",
            listOf(
                Move("Squat", 4, 8, 80.0), Move("Leg Press", 3, 10, 140.0),
                Move("Romanian Deadlift", 3, 10, 60.0), Move("Calf Raise", 4, 15, 50.0),
            ),
        ),
        // Day 3 is a rest day.
        Day(
            2, "Shoulders & Abs",
            listOf(
                Move("Overhead Press", 4, 8, 40.0), Move("Lateral Raise", 3, 12, 10.0),
                Move("Face Pull", 3, 15, 20.0), Move("Crunches", 3, 20, 0.0),
            ),
        ),
        Day(
            1, "Chest & Triceps",
            listOf(
                Move("Bench Press", 4, 8, 62.5), Move("Incline Dumbbell Press", 3, 10, 24.0),
                Move("Chest Fly", 3, 12, 16.0), Move("Tricep Pushdown", 3, 12, 27.5),
            ),
        ),
    )

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

    // offset = days before today. Today is only part-logged and the rest day (3) is lighter, as real logging tends to be.
    private val menu = mapOf(
        6 to listOf(Eaten(Meal.Breakfast, oatmeal), Eaten(Meal.Lunch, chickenBowl), Eaten(Meal.Snack, shake), Eaten(Meal.Dinner, dalRice)),
        5 to listOf(Eaten(Meal.Breakfast, eggsToast), Eaten(Meal.Lunch, paneerWrap), Eaten(Meal.Snack, bananaPb), Eaten(Meal.Dinner, fish)),
        4 to listOf(Eaten(Meal.Breakfast, poha), Eaten(Meal.Lunch, chickenBowl), Eaten(Meal.Snack, shake), Eaten(Meal.Dinner, eggCurry)),
        3 to listOf(Eaten(Meal.Breakfast, oatmeal), Eaten(Meal.Lunch, dalRice), Eaten(Meal.Snack, yogurt), Eaten(Meal.Dinner, pulao)),
        2 to listOf(Eaten(Meal.Breakfast, eggsToast), Eaten(Meal.Lunch, chickenBowl), Eaten(Meal.Snack, bananaPb), Eaten(Meal.Dinner, fish)),
        1 to listOf(Eaten(Meal.Breakfast, oatmeal), Eaten(Meal.Lunch, paneerWrap), Eaten(Meal.Snack, shake), Eaten(Meal.Dinner, eggCurry)),
        0 to listOf(Eaten(Meal.Breakfast, poha), Eaten(Meal.Snack, yogurt)),
    )

    /** Seven days of meals with calories and macros. Portions vary a little from day to day. */
    fun food(today: LocalDate): List<FoodEntry> {
        val random = Random(7)
        return menu.entries.sortedByDescending { it.key }.flatMap { (offset, meals) ->
            meals.map { eaten ->
                val portion = 0.95 + random.nextDouble() * 0.1 // 95%-105%
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

    /** Activity outside the gym, added by hand: a walk on the rest day and a ride on the day after legs. */
    fun activities(today: LocalDate): List<ExtraActivity> = listOf(
        ExtraActivity(date = today.minusDays(3), name = "Evening walk", calories = 130, minutes = 40),
        ExtraActivity(date = today.minusDays(2), name = "Cycling", calories = 320, minutes = 45),
        ExtraActivity(date = today.minusDays(1), name = "Football with friends", calories = 450, minutes = 60),
    )

    // --- Weight ---

    /** Three weigh-ins over the week drifting down to [currentKg], which is the profile's real weight. */
    fun weights(today: LocalDate, currentKg: Double): List<WeightEntry> = listOf(
        WeightEntry(today.minusDays(6), currentKg + 0.8),
        WeightEntry(today.minusDays(3), currentKg + 0.4),
        WeightEntry(today, currentKg),
    )

    // --- Workouts ---

    fun sessions(today: LocalDate): List<WorkoutSession> {
        val random = Random(42)
        return week.map { day ->
            var activeSeconds = 0L
            val exercises = day.moves.map { move ->
                val sets = List(move.sets) { index ->
                    // The last set of an exercise is usually a rep short.
                    val reps = if (index == move.sets - 1) move.reps - random.nextInt(0, 3) else move.reps
                    val setSeconds = 25 + random.nextInt(0, 25)
                    val restSeconds = 60 + random.nextInt(0, 60)
                    activeSeconds += setSeconds + restSeconds
                    ExerciseSet(
                        weight = move.weight,
                        reps = reps.coerceAtLeast(1),
                        isCompleted = true,
                        setDuration = formatDuration(setSeconds * 1000L),
                        restTime = formatDuration(restSeconds * 1000L),
                    )
                }
                PerformedExercise(name = move.name, sets = sets)
            }
            WorkoutSession(
                planName = day.plan,
                date = today.minusDays(day.offset.toLong()).atTime(LocalTime.of(7, 30)),
                // Setting up, warming up and moving between machines on top of the sets themselves.
                totalTime = formatDuration((activeSeconds + 12 * 60) * 1000L),
                exercises = exercises,
            )
        }
    }
}
