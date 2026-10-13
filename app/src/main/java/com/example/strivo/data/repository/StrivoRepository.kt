package com.example.strivo.data.repository

import com.example.strivo.core.util.nowIso
import com.example.strivo.data.demo.DemoData
import com.example.strivo.data.demo.DemoWeightTrend
import com.example.strivo.data.local.db.PersonalRecords
import com.example.strivo.data.local.db.StrivoDatabase
import com.example.strivo.data.local.prefs.UserPrefs
import com.example.strivo.domain.analytics.ExtraActivity
import com.example.strivo.domain.analytics.FoodEntry
import com.example.strivo.domain.analytics.WeightEntry
import com.example.strivo.domain.coach.PlannedExercise
import com.example.strivo.domain.coach.exerciseKey
import com.example.strivo.domain.model.EXTRA_PLAN_NAME
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.PREBUILT_DAY
import com.example.strivo.domain.model.Plan
import com.example.strivo.domain.model.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Off-main-thread access to the database; also owns the daily reset rule. */
class StrivoRepository(
    private val db: StrivoDatabase,
    private val prefs: UserPrefs,
) {
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    // --- Plans ---

    suspend fun readAllPlans(): List<Plan> = io { db.readAllPlans() }

    suspend fun createPlan(plan: Plan): Long = io { db.createPlan(plan) }

    suspend fun deletePlan(id: Long) = io { db.deletePlan(id) }

    /** The plan holding [day]'s extra exercises; created the first time one is added. */
    suspend fun getOrCreateExtraPlan(day: String): Long = io {
        db.readAllPlans().firstOrNull { it.planDay == day && it.planName == EXTRA_PLAN_NAME }?.planId
            ?: db.createPlan(Plan(planName = EXTRA_PLAN_NAME, planDay = day))
    }

    suspend fun deleteEmptyExtraPlans(): Int = io { db.deleteEmptyExtraPlans(EXTRA_PLAN_NAME) }

    /** Copies [source] and its exercises (fresh, nothing ticked off) onto [day]; returns the new plan's id. */
    suspend fun copyPlanToDay(source: Plan, day: String): Long = io {
        val newId = db.createPlan(Plan(planName = source.planName, planDay = day))
        db.readExercisesByPlan(source.planId ?: -1L).forEach { exercise ->
            db.createExercise(
                exercise.copy(
                    id = null,
                    planId = newId,
                    isCheck = false,
                    dateTime = "",
                    sortOrder = -1,
                    setsList = exercise.setsList.map { it.copy(isCompleted = false, isStarted = false) },
                )
            )
        }
        newId
    }

    // --- Exercises ---

    suspend fun readExercisesByPlan(planId: Long): List<Exercise> = io { db.readExercisesByPlan(planId) }

    suspend fun readExercisesByDay(day: String): List<Exercise> = io { db.readExercisesByDay(day) }

    suspend fun readExtraExercises(): List<Exercise> = io { db.readExtraExercises() }

    suspend fun readAllExercises(): List<Exercise> = io { db.readAllExercises() }

    suspend fun createExercise(exercise: Exercise): Long = io { db.createExercise(exercise) }

    suspend fun deleteExercise(id: Long) = io { db.deleteExercise(id) }

    suspend fun moveExerciseToPlan(id: Long, targetPlanId: Long) = io { db.moveExerciseToPlan(id, targetPlanId) }

    /** Adds a fresh copy of [exercise] (planned sets / reps / weight, nothing logged) to the end of another plan. */
    suspend fun copyExerciseToPlan(exercise: Exercise, targetPlanId: Long): Long = io {
        db.createExercise(
            exercise.copy(
                id = null,
                planId = targetPlanId,
                isCheck = false,
                dateTime = "",
                restTime = "",
                sortOrder = -1,
                setsList = emptyList(),
            )
        )
    }

    /**
     * Makes [kg] the planned weight of every exercise called [name], in every plan: each weekday's plan and the library
     * of plans they are copied from, so the change is global and does not come back with the next plan added from the
     * library. This is the coach's advice, accepted. Sets nobody has started follow the new weight; logged ones are kept.
     * [skipIds] are rows the caller saves itself. Returns how many plan exercises changed.
     */
    suspend fun setPlannedWeight(name: String, kg: Double, skipIds: Set<Long> = emptySet()): Int = io {
        val planIds = db.readAllPlans().mapNotNull { it.planId }.toSet()
        val key = exerciseKey(name)
        val targets = db.readAllExercises().filter { it.planId in planIds && exerciseKey(it.name) == key && it.id !in skipIds }
        targets.forEach { exercise ->
            val logged = exercise.setsList.any { it.isStarted || it.isCompleted }
            db.updateExercise(exercise.copy(weight = kg.toString(), setsList = if (logged) exercise.setsList else emptyList()))
        }
        targets.size
    }

    /** Each copy of each exercise in any plan, library included, with its plan and planned weight, by [exerciseKey]. */
    suspend fun plannedExercises(): Map<String, List<PlannedExercise>> = io {
        val plans = db.readAllPlans().filter { it.planId != null }.associateBy { it.planId }
        db.readAllExercises().mapNotNull { exercise ->
            val plan = plans[exercise.planId] ?: return@mapNotNull null
            exerciseKey(exercise.name) to PlannedExercise(plan.planName, plan.planDay, exercise.weight.toDoubleOrNull() ?: 0.0)
        }.groupBy({ it.first }, { it.second })
    }

    suspend fun reorderExercises(planId: Long, orderedIds: List<Long>) = io { db.setExerciseOrder(planId, orderedIds) }

    /** Saves the exercise and keeps the history table in sync with its checked state. */
    suspend fun updateExercise(exercise: Exercise) = io {
        db.updateExercise(exercise)
        // One history row per exercise per day: replace today's row instead of piling up copies.
        db.removeFromHistory(exercise.name, nowIso())
        if (exercise.isCheck) db.addToHistory(exercise)
    }

    /** On the first load of each calendar day, un-check everything and drop the one-day exercises. */
    suspend fun checkAndResetDaily() = io {
        if (prefs.consumeDailyReset()) {
            db.resetAllExerciseChecks()
            db.deleteDailyExercises()
        }
    }

    // --- Weight log ---

    suspend fun logWeight(kg: Double, date: LocalDate = LocalDate.now()) = io { db.upsertWeight(date.toString(), kg) }

    /**
     * All dated weigh-ins. If none were recorded yet (weights entered before the log existed), the current weight
     * is saved under the day it was entered, so the log starts with one real data point.
     */
    suspend fun readWeightLog(): List<WeightEntry> = io {
        var log = db.readWeightLog()
        val current = prefs.getProfile().weight
        if (log.isEmpty() && current != null) {
            val enteredOn = prefs.getLastWeightUpdateMillis()
                ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                ?: LocalDate.now()
            db.upsertWeight(enteredOn.toString(), current)
            log = db.readWeightLog()
        }
        log.mapNotNull { (date, kg) -> runCatching { WeightEntry(LocalDate.parse(date), kg) }.getOrNull() }
    }

    suspend fun getPersonalRecords(): PersonalRecords = io { db.getPersonalRecords() }

    // --- Workout sessions ---

    /**
     * Replaces any earlier demo data with a fresh four weeks of made-up workouts, meals and weigh-ins. Weigh-ins end at
     * the profile's real weight, and a day that already has a real weigh-in is left alone. Every row is marked as demo
     * in the row itself, so it is found again after a reinstall or on another phone. Returns how many workouts were added.
     */
    suspend fun loadDemoData(today: LocalDate, trend: DemoWeightTrend = DemoWeightTrend.Steady): Int = io {
        deleteDemoRows()
        val sessions = DemoData.sessions(today).map { db.saveSession(it, isDemo = true) }
        // The plans behind the workouts, so the plan screens and the coach's Accept have something to work on.
        // Each is in the library too, the plans a day's plan is copied from, so Accept can be seen changing both.
        DemoData.plans(today).forEach { plan ->
            listOf(plan.day, PREBUILT_DAY).forEach { day ->
                val planId = db.createPlan(Plan(planName = plan.name, planDay = day), isDemo = true)
                plan.exercises.forEach { db.createExercise(it.copy(planId = planId), isDemo = true) }
            }
        }
        DemoData.food(today, trend).forEach { db.insertFood(it, isDemo = true) }
        DemoData.activities(today).forEach { db.insertActivity(it, isDemo = true) }
        prefs.getProfile().weight?.let { current ->
            DemoData.weights(today, current, trend).forEach { db.insertWeightIfAbsent(it.date.toString(), it.kg, isDemo = true) }
        }
        sessions.size
    }

    /** Removes only what [loadDemoData] added; the user's own workouts, food and weigh-ins are kept. */
    suspend fun removeDemoData(): Int = io { deleteDemoRows() }

    private fun deleteDemoRows(): Int {
        val removed = db.deleteDemoRows() +
            // Demo data loaded before rows carried the mark was only remembered on this phone, by id.
            db.deleteSessions(prefs.getDemoSessionIds())
        db.deleteFoods(prefs.getDemoFoodIds())
        db.deleteActivities(prefs.getDemoActivityIds())
        db.deleteWeights(prefs.getDemoWeightDates())
        prefs.setDemoSessionIds(emptySet())
        prefs.setDemoFoodIds(emptySet())
        prefs.setDemoActivityIds(emptySet())
        prefs.setDemoWeightDates(emptySet())
        return removed
    }

    /**
     * Wipes the workout history but keeps the demo workouts. Plans, exercises, food and the account are not touched.
     * Returns how many saved workouts were deleted.
     */
    suspend fun clearHistoryKeepingDemo(): Int = io {
        val removed = db.deleteSessionsExcept(prefs.getDemoSessionIds())
        db.clearExerciseHistory()
        removed
    }

    // --- Food log ---

    suspend fun readFood(): List<FoodEntry> = io { db.readFood() }

    suspend fun addFood(entry: FoodEntry): Long = io { db.insertFood(entry) }

    suspend fun updateFood(entry: FoodEntry) = io { db.updateFood(entry) }

    suspend fun deleteFood(id: Long) = io { db.deleteFood(id) }

    // --- Other activity ---

    suspend fun readActivities(): List<ExtraActivity> = io { db.readActivities() }

    suspend fun addActivity(entry: ExtraActivity): Long = io { db.insertActivity(entry) }

    suspend fun deleteActivity(id: Long) = io { db.deleteActivity(id) }

    suspend fun saveSession(session: WorkoutSession, replaceId: Long? = null): Long =
        io { db.saveSession(session, replaceId) }

    suspend fun getWorkoutSessions(): List<WorkoutSession> = io { db.getWorkoutSessions() }

    /** Remembers which exercises [planId] held when its workout was finished today (see [com.example.strivo.domain.plan.isPlanDoneToday]). */
    suspend fun markPlanFinished(planId: Long, exerciseIds: Set<Long>, today: LocalDate) = io { prefs.setFinishedPlan(today, planId, exerciseIds) }

    suspend fun finishedPlans(today: LocalDate): Map<Long, Set<Long>> = io { prefs.getFinishedPlans(today) }
}
