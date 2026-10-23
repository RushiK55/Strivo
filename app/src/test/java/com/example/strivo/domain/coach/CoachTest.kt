package com.example.strivo.domain.coach

import com.example.strivo.domain.analytics.BodyProfile
import com.example.strivo.domain.analytics.BodyReport
import com.example.strivo.domain.analytics.ExpenditureEstimate
import com.example.strivo.domain.analytics.ExpenditureStatus
import com.example.strivo.domain.analytics.FoodEntry
import com.example.strivo.domain.analytics.Meal
import com.example.strivo.domain.analytics.TrendPoint
import com.example.strivo.domain.analytics.WeightEntry
import com.example.strivo.domain.analytics.activityLevelFor
import com.example.strivo.domain.analytics.estimateExpenditure
import com.example.strivo.domain.analytics.exerciseHistories
import com.example.strivo.domain.analytics.weeklyWeightRate
import com.example.strivo.domain.analytics.weightTrend
import com.example.strivo.domain.model.ExerciseSet
import com.example.strivo.domain.model.PREBUILT_DAY
import com.example.strivo.domain.model.PerformedExercise
import com.example.strivo.domain.model.SetType
import com.example.strivo.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class CoachTest {
    private val today = LocalDate.of(2026, 10, 13)

    /** One bench workout [daysAgo] days ago: [sets] sets of [kg] x [reps]. */
    private fun bench(daysAgo: Long, kg: Double, reps: Int, sets: Int = 3, extra: List<ExerciseSet> = emptyList()) = WorkoutSession(
        planName = "Chest",
        date = today.minusDays(daysAgo).atTime(LocalTime.of(8, 0)),
        totalTime = "00:45:00",
        exercises = listOf(PerformedExercise("Bench Press", extra + List(sets) { ExerciseSet(kg, reps, isCompleted = true) })),
    )

    private fun adviceFor(vararg sessions: WorkoutSession): Advice? =
        progressionAdvice(exerciseHistories(sessions.toList()).single(), today)

    @Test
    fun fourWorkoutsAtTheSameWeightMeanGoHeavier() {
        val advice = adviceFor(bench(12, 60.0, 8), bench(9, 60.0, 8), bench(5, 60.0, 8), bench(2, 60.0, 9))
        assertNotNull(advice)
        assertEquals(AdviceKind.IncreaseWeight, advice!!.kind)
        assertEquals(62.5, advice.suggestedKg!!, 0.0)
        assertEquals("Bench Press", advice.exercise)
        assertTrue(advice.detail, advice.detail.contains("last 4 workouts"))
    }

    @Test
    fun threeAtTheSameWeightIsNotYetEnough() {
        assertNull(adviceFor(bench(9, 60.0, 8), bench(5, 60.0, 8), bench(2, 60.0, 8)))
        // A heavier weight in between resets the count.
        assertNull(adviceFor(bench(12, 60.0, 8), bench(9, 60.0, 8), bench(5, 62.5, 8), bench(2, 60.0, 8)))
    }

    @Test
    fun repsFallingThreeTimesInARowMeanALighterWeek() {
        val advice = adviceFor(bench(9, 60.0, 8), bench(5, 60.0, 7), bench(2, 60.0, 6))!!
        assertEquals(AdviceKind.Deload, advice.kind)
        assertEquals(55.0, advice.suggestedKg!!, 0.0) // 54 rounded to 2.5 kg steps
        assertTrue(advice.detail, advice.detail.contains("8, 7, 6"))
    }

    @Test
    fun twoMoreRepsOnEverySetMeanGoHeavierWithoutWaiting() {
        val advice = adviceFor(bench(5, 60.0, 8), bench(2, 60.0, 10))!!
        assertEquals(AdviceKind.IncreaseWeight, advice.kind)
        assertEquals(62.5, advice.suggestedKg!!, 0.0)
        assertTrue(advice.detail, advice.detail.contains("up from 8"))
        // One set short of +2 is not enough, and high reps alone are not either: 15 reps is the plan on some lifts.
        val short = WorkoutSession(
            planName = "Chest",
            date = today.minusDays(2).atTime(8, 0),
            totalTime = "00:45:00",
            exercises = listOf(PerformedExercise("Bench Press", listOf(ExerciseSet(60.0, 12, isCompleted = true), ExerciseSet(60.0, 10, isCompleted = true)))),
        )
        assertNull(adviceFor(bench(5, 60.0, 10), short))
        assertNull(adviceFor(bench(5, 60.0, 15), bench(2, 60.0, 15)))
    }

    @Test
    fun warmUpsAndDropSetsDoNotChangeTheAdvice() {
        // A warm-up at 30 kg x 12 is not "more reps at the top weight".
        val warm = listOf(ExerciseSet(30.0, 12, isCompleted = true, type = SetType.WarmUp))
        val advice = adviceFor(bench(12, 60.0, 8, extra = warm), bench(9, 60.0, 8, extra = warm), bench(5, 60.0, 8), bench(2, 60.0, 8))!!
        assertEquals(62.5, advice.suggestedKg!!, 0.0)
        assertNull(adviceFor(bench(5, 60.0, 8, extra = warm), bench(2, 60.0, 8, extra = warm)))
    }

    @Test
    fun liftsNotDoneLatelyAndBodyweightGetNothing() {
        assertNull(adviceFor(bench(60, 60.0, 8), bench(55, 60.0, 8), bench(50, 60.0, 8), bench(45, 60.0, 8)))
        assertNull(adviceFor(bench(12, 0.0, 20), bench(9, 0.0, 20), bench(5, 0.0, 20), bench(2, 0.0, 20)))
    }

    @Test
    fun stepsSuitTheWeight() {
        // Never a 1 kg jump: 2.5 kg, or 5 kg on the heaviest lifts.
        assertEquals(5.0, progressionStepKg(80.0), 0.0)
        assertEquals(5.0, progressionStepKg(100.0), 0.0)
        assertEquals(2.5, progressionStepKg(79.0), 0.0)
        assertEquals(2.5, progressionStepKg(12.0), 0.0)
        assertEquals(2.5, progressionStepKg(4.0), 0.0)
        assertEquals(90.0, deloadKg(100.0), 0.0)
        assertTrue(deloadKg(2.5) < 2.5)
        assertEquals(7.5, deloadKg(10.0), 0.0)
    }

    @Test
    fun aDeclinedTipIsRememberedByWhatItSaysAndComesBackWhenTheDataChanges() {
        val tip = Advice(AdviceKind.IncreaseWeight, "Go heavier on Bench", "x", "Bench", 62.5)
        val other = tip.copy(suggestedKg = 65.0)
        assertTrue(tip.declineKey != other.declineKey)
        val workout = { day: Long -> bench(day, 60.0, 8) }
        val before = tip.dataStamp(listOf(workout(3), workout(2)), emptyList(), emptyList())
        // The same workouts: still declined. A new workout: the coach works it out again.
        assertEquals(before, tip.dataStamp(listOf(workout(3), workout(2)), emptyList(), emptyList()))
        assertTrue(before != tip.dataStamp(listOf(workout(3), workout(2), workout(1)), emptyList(), emptyList()))
        // A weigh-in tip waits for a weigh-in, not for a workout.
        val weigh = Advice(AdviceKind.WeighIn, "Time to weigh in", "x")
        val log = listOf(WeightEntry(today.minusDays(9), 80.0))
        assertEquals(weigh.dataStamp(listOf(workout(1)), log, emptyList()), weigh.dataStamp(emptyList(), log, emptyList()))
        assertTrue(weigh.dataStamp(emptyList(), log, emptyList()) != weigh.dataStamp(emptyList(), log + WeightEntry(today, 79.5), emptyList()))
    }

    // --- Body and food ---

    private fun body(rateKg: Double?, trendKg: Double) = BodyReport(
        profile = BodyProfile("Male", 25, 175.0, trendKg),
        bmi = null, bmrKcal = null, activity = activityLevelFor(3.0), maintenanceKcal = null,
        weight = null, strength = emptyList(), weeks = emptyList(),
        trend = listOf(TrendPoint(today, trendKg, trendKg)),
        weeklyRateKg = rateKg,
    )

    @Test
    fun anOverdueWeighInIsAskedFor() {
        val log = listOf(WeightEntry(today.minusDays(9), 80.0))
        assertEquals(listOf(AdviceKind.WeighIn), bodyAdvice(body(null, 80.0), log, today).map { it.kind })
        assertTrue(bodyAdvice(body(null, 80.0), listOf(WeightEntry(today.minusDays(3), 80.0)), today).isEmpty())
    }

    @Test
    fun fastChangesInTheTrendAreFlagged() {
        val recent = listOf(WeightEntry(today, 80.0))
        assertEquals(listOf(AdviceKind.FastLoss), bodyAdvice(body(-1.0, 80.0), recent, today).map { it.kind }) // 1.25% a week
        assertEquals(listOf(AdviceKind.FastGain), bodyAdvice(body(0.5, 80.0), recent, today).map { it.kind }) // 0.6% a week
        assertTrue(bodyAdvice(body(-0.5, 80.0), recent, today).isEmpty()) // 0.6% down: fine
    }

    private fun meal(daysAgo: Long, protein: Double) =
        FoodEntry(date = today.minusDays(daysAgo), meal = Meal.Lunch, name = "Food", calories = 600, proteinG = protein, carbsG = 50.0, fatG = 20.0)

    @Test
    fun lowProteinIsFlaggedAgainstBodyWeight() {
        val low = (1L..4L).map { meal(it, 70.0) }
        val advice = proteinAdvice(low, weightKg = 80.0, today = today)!!
        assertEquals(AdviceKind.Protein, advice.kind)
        assertTrue(advice.detail, advice.detail.contains("128 g")) // 1.6 x 80
        assertNull(proteinAdvice((1L..4L).map { meal(it, 130.0) }, 80.0, today))
        // Two days are not enough to judge, and today (still being logged) does not count.
        assertNull(proteinAdvice((1L..2L).map { meal(it, 50.0) } + meal(0, 10.0), 80.0, today))
    }

    @Test
    fun aLongBreakIsMentionedOnceTheUserHasTrained() {
        val old = listOf(bench(20, 60.0, 8), bench(15, 60.0, 8), bench(6, 60.0, 8))
        assertEquals(AdviceKind.Inactive, activityAdvice(old, today)!!.kind)
        assertNull(activityAdvice(old + bench(1, 60.0, 8), today))
        assertNull(activityAdvice(listOf(bench(30, 60.0, 8)), today)) // a new user is not nagged
    }

    @Test
    fun theMostImportantAdviceComesFirst() {
        val sessions = listOf(bench(9, 60.0, 8), bench(5, 60.0, 7), bench(2, 60.0, 6))
        val all = coachAdvice(exerciseHistories(sessions), body(null, 80.0), listOf(WeightEntry(today.minusDays(10), 80.0)), emptyList(), sessions, today)
        assertEquals(listOf(AdviceKind.Deload, AdviceKind.WeighIn), all.map { it.kind })
    }

    @Test
    fun calorieTargetsComeFromYourOwnExpenditureFirst() {
        val estimate = ExpenditureStatus(ExpenditureEstimate(2400.0, 2000.0, -1.0, 21, 20), 20, 21, 28)
        val targets = calorieTargets(estimate, formulaKcal = 2600.0)!!
        assertTrue(targets.fromYourData)
        assertEquals(2400.0, targets.maintainKcal, 0.0)
        assertEquals(1850.0, targets.loseKcal, 0.0) // 0.5 kg x 7,700 / 7 = 550 a day
        assertEquals(2680.0, targets.gainKcal, 0.0) // + 275, rounded to 10
        val formula = calorieTargets(ExpenditureStatus(null, 3, 2, 28), 2600.0)!!
        assertEquals(2600.0, formula.maintainKcal, 0.0)
        assertTrue(!formula.fromYourData)
        assertNull(calorieTargets(null, null))
    }

    @Test
    fun theDemoGivesTheCoachOneOfEachTrainingRule() {
        val advice = exerciseHistories(com.example.strivo.data.demo.DemoData.sessions(today)).mapNotNull { progressionAdvice(it, today) }
            .associateBy { it.exercise }
        // Four workouts at 40 kg.
        assertEquals(AdviceKind.IncreaseWeight, advice.getValue("Overhead Press").kind)
        assertEquals(42.5, advice.getValue("Overhead Press").suggestedKg!!, 0.0)
        // Reps 8, 7, 6 at the same weight.
        assertEquals(AdviceKind.Deload, advice.getValue("Barbell Row").kind)
        assertEquals(45.0, advice.getValue("Barbell Row").suggestedKg!!, 0.0)
        // 10 reps then 12 on every set at 42.5 kg.
        assertEquals(AdviceKind.IncreaseWeight, advice.getValue("Lat Pulldown").kind)
        assertEquals(45.0, advice.getValue("Lat Pulldown").suggestedKg!!, 0.0)
        // The lifts that are progressing normally get nothing.
        assertEquals(setOf("Overhead Press", "Barbell Row", "Lat Pulldown"), advice.keys)
    }

    @Test
    fun theDemoPlansLetTheCoachSuggestionBeAccepted() {
        val plans = com.example.strivo.data.demo.DemoData.plans(today)
        assertEquals(setOf("Chest & Triceps", "Back & Biceps", "Leg Day", "Shoulders & Abs"), plans.map { it.name }.toSet())
        // They start today, so the exercises the coach talks about are on Home straight away.
        assertEquals(today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH), plans.first().day)
        assertEquals("Back & Biceps", plans.first().name)
        assertEquals(4, plans.map { it.day }.distinct().size)
        val planned = plans.flatMap { it.exercises }.groupBy({ exerciseKey(it.name) }, { it.weight.toDouble() })
        val sessions = com.example.strivo.data.demo.DemoData.sessions(today)
        exerciseHistories(sessions).forEach { history ->
            // The plan is at the weight the latest workout used, so a suggestion is one step ahead of it.
            assertEquals(history.name, history.latest.topWeightKg, planned.getValue(exerciseKey(history.name)).single(), 0.0)
            progressionAdvice(history, today)?.let { assertTrue(history.name, !it.isInPlans(planned[exerciseKey(history.name)])) }
        }
        // Accepting changes the plan weight, after which the suggestion is gone.
        val press = exerciseHistories(sessions).first { it.name == "Overhead Press" }
        val advice = progressionAdvice(press, today)!!
        assertTrue(advice.isInPlans(listOf(advice.suggestedKg!!)))
        // Triceps, curls and lateral raises are planned with a drop set.
        assertEquals(setOf("Tricep Pushdown", "Barbell Curl", "Lateral Raise"), plans.flatMap { it.exercises }.filter { it.dropSets == 1 }.map { it.name }.toSet())
    }

    @Test
    fun acceptedAdviceIsNoLongerShown() {
        val heavier = Advice(AdviceKind.IncreaseWeight, "Go heavier", "", "Bench Press", 62.5)
        assertTrue(!heavier.isInPlans(listOf(60.0))) // the plan still says 60: show it, with Accept
        assertTrue(heavier.isInPlans(listOf(62.5))) // accepted
        assertTrue(heavier.isInPlans(listOf(65.0))) // already planned heavier by hand
        assertTrue(!heavier.isInPlans(listOf(62.5, 60.0))) // one plan still behind
        assertTrue(!heavier.isInPlans(null)) // not in a plan: nothing to accept into

        val lighter = Advice(AdviceKind.Deload, "Lighter week", "", "Bench Press", 55.0)
        assertTrue(!lighter.isInPlans(listOf(60.0)))
        assertTrue(lighter.isInPlans(listOf(55.0)))

        val weighIn = Advice(AdviceKind.WeighIn, "Weigh in", "")
        assertTrue(!weighIn.isInPlans(listOf(60.0)))
        assertEquals("bench press", exerciseKey("  Bench Press "))
    }

    private fun bodyAdviceFor(trend: com.example.strivo.data.demo.DemoWeightTrend): List<AdviceKind> {
        val weights = com.example.strivo.data.demo.DemoData.weights(today, 71.0, trend)
        val rate = weeklyWeightRate(weights, today)
        val trendKg = weightTrend(weights).last().trendKg
        return bodyAdvice(body(rate, trendKg), weights, today).map { it.kind }
    }

    @Test
    fun theDemoWeightsShowFastLossFastGainOrNothing() {
        assertEquals(listOf(AdviceKind.FastLoss), bodyAdviceFor(com.example.strivo.data.demo.DemoWeightTrend.FastLoss))
        assertEquals(listOf(AdviceKind.FastGain), bodyAdviceFor(com.example.strivo.data.demo.DemoWeightTrend.FastGain))
        assertTrue(bodyAdviceFor(com.example.strivo.data.demo.DemoWeightTrend.Steady).isEmpty())
    }

    @Test
    fun theFastDemosKeepTheExpenditureEstimateBelievable() {
        com.example.strivo.data.demo.DemoWeightTrend.entries.forEach { trend ->
            val status = estimateExpenditure(com.example.strivo.data.demo.DemoData.food(today, trend), com.example.strivo.data.demo.DemoData.weights(today, 71.0, trend), today)
            val kcal = status.estimate?.kcal
            assertTrue("$trend: $status", kcal != null && kcal in 1800.0..3200.0)
        }
    }

    @Test
    fun acceptSaysWhichPlanItChanges() {
        val advice = Advice(AdviceKind.IncreaseWeight, "Go heavier", "", "Bench Press", 62.5)
        assertEquals("Chest (Mon)", advice.acceptedIn(listOf(PlannedExercise("Chest", "Monday", 60.0))))
        assertEquals("2 plans", advice.acceptedIn(listOf(PlannedExercise("Chest", "Monday", 60.0), PlannedExercise("Push", "Thursday", 60.0))))
        // The library of plans counts too, and is named as such.
        assertEquals("Chest (library)", advice.acceptedIn(listOf(PlannedExercise("Chest", PREBUILT_DAY, 60.0))))
        assertEquals("2 plans", advice.acceptedIn(listOf(PlannedExercise("Chest", "Monday", 60.0), PlannedExercise("Chest", PREBUILT_DAY, 60.0))))
        assertNull(advice.acceptedIn(null))
        assertNull(advice.acceptedIn(emptyList()))
        assertNull(Advice(AdviceKind.Protein, "Protein", "").acceptedIn(listOf(PlannedExercise("Chest", "Monday", 60.0))))
    }
}
