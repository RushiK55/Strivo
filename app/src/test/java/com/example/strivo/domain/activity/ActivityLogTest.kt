package com.example.strivo.domain.activity

import com.example.strivo.domain.analytics.ExtraActivity
import com.example.strivo.domain.analytics.FoodEntry
import com.example.strivo.domain.analytics.Meal
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.ExerciseSet
import com.example.strivo.domain.model.PREBUILT_DAY
import com.example.strivo.domain.model.PerformedExercise
import com.example.strivo.domain.model.Plan
import com.example.strivo.domain.model.SetType
import com.example.strivo.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ActivityLogTest {
    private val at = 1_700_000_000_000L
    private val plan = Plan(planId = 1, planName = "Push Day", planDay = "Monday")
    private val bench = Exercise(planId = 1, name = "Bench Press", weight = "62.5", sets = "4", reps = "8", notes = "")

    @Test
    fun plansAreDescribedWithTheirDay() {
        assertEquals("Created plan \"Push Day\" for Monday", planCreated(plan, at).summary)
        assertEquals("Created plan \"Push Day\" for the library", planCreated(plan.copy(planDay = PREBUILT_DAY), at).summary)
        assertEquals("Deleted plan \"Push Day\" (Monday)", planDeleted(plan, at).summary)
        assertEquals("Deleted plan \"Push Day\" (library)", planDeleted(plan.copy(planDay = PREBUILT_DAY), at).summary)
        assertEquals("Added plan \"Push Day\" to Friday", planCopied(plan, "Friday", at).summary)
        assertEquals(ActivityKind.PlanCreated, planCreated(plan, at).type)
    }

    @Test
    fun exercisesShowTheirTargetAndWhereTheyWent() {
        assertEquals("Added \"Bench Press\" to Push Day (Monday) · 4 × 8 @ 62.5 kg", exerciseAdded(bench, plan, at).summary)
        assertEquals(
            "Added \"Bench Press\" to Push Day (Monday) · 4 × 8 + 2 drop @ 62.5 kg (from the library)",
            exerciseAdded(bench.copy(dropSets = 2), plan, at, fromLibrary = true).summary,
        )
        assertEquals("Added \"Plank\" to a plan · 3 × 1, bodyweight", exerciseAdded(bench.copy(name = "Plank", weight = "0.0", sets = "3", reps = "1"), null, at).summary)
        assertEquals("Changed \"Bench Press\" in Push Day (Monday) · 4 × 8 @ 62.5 kg", exerciseEdited(bench, plan, at).summary)
        assertEquals("Removed \"Bench Press\" from Push Day (Monday)", exerciseRemoved(bench, plan, at).summary)
    }

    @Test
    fun aFinishedWorkoutSaysHowBigItWasAndLeavesWarmUpsOut() {
        val session = WorkoutSession(
            planName = "Push Day",
            date = java.time.LocalDateTime.of(2026, 10, 13, 8, 0),
            totalTime = "00:42:14",
            exercises = listOf(
                PerformedExercise("Bench Press", listOf(ExerciseSet(30.0, 10, type = SetType.WarmUp), ExerciseSet(60.0, 8), ExerciseSet(60.0, 8))),
                PerformedExercise("Push-Up", listOf(ExerciseSet(0.0, 15))),
            ),
        )
        // 60 x 8 twice = 960 kg; the 30 kg warm-up is not counted.
        assertEquals("Finished \"Push Day\": 2 exercises, 960 kg, 42 min", workoutFinished(session, at).summary)
        assertEquals(ActivityKind.WorkoutFinished, workoutFinished(session, at).type)
        val bodyweightOnly = session.copy(totalTime = "00:00:30", exercises = session.exercises.takeLast(1))
        assertEquals("Finished \"Push Day\": 1 exercise", workoutFinished(bodyweightOnly, at).summary)
    }

    @Test
    fun anOlderLineThatNamedTheFoodIsRewrittenWithoutIt() {
        assertEquals("Logged dinner (560 kcal)", withoutFoodName("Logged \"Dal and rice\" (560 kcal) as dinner"))
        assertEquals("Logged snack (90 kcal)", withoutFoodName("Logged \"Apple (green) \"fresh\"\" (90 kcal) as snack"))
        // Lines that name no food are left alone.
        assertNull(withoutFoodName("Logged dinner (560 kcal)"))
        assertNull(withoutFoodName("Logged lunch (620 kcal, 41 g protein)"))
        assertNull(withoutFoodName("Added \"Bench Press\" to Push Day (Monday)"))
    }

    @Test
    fun foodWeightAndActivityAreOneLineEach() {
        val food = FoodEntry(date = LocalDate.of(2026, 10, 13), meal = Meal.Dinner, name = "Dal and rice", calories = 560)
        // What was eaten is private: the line never names the food.
        assertEquals("Logged dinner (560 kcal)", foodLogged(food, at).summary)
        assertEquals("Logged dinner (560 kcal, 32 g protein)", foodLogged(food.copy(proteinG = 31.6), at).summary)
        assertFalse(foodLogged(food, at).summary.contains("Dal"))
        assertEquals("Added activity \"Cycling\" (320 kcal, 45 min)", activityAdded(ExtraActivity(date = LocalDate.of(2026, 10, 13), name = "Cycling", calories = 320, minutes = 45), at).summary)
        assertEquals("Added activity \"Walk\" (130 kcal)", activityAdded(ExtraActivity(date = LocalDate.of(2026, 10, 13), name = "Walk", calories = 130), at).summary)
        assertEquals("Weighed in at 71 kg", weightLogged(71.0, at).summary)
        assertEquals("Weighed in at 70.4 kg", weightLogged(70.4, at).summary)
        assertEquals(ActivityKind.ProfileUpdated, profileUpdated(at).type)
    }

    @Test
    fun clockTimesBecomeMinutes() {
        assertEquals(42, minutes("00:42:14"))
        assertEquals(5, minutes("05:10"))
        assertNull(minutes("00:00:30"))
        assertNull(minutes("soon"))
    }
}
