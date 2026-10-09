package com.example.strivo.data

import com.example.strivo.data.analytics.ExtraActivity
import com.example.strivo.data.analytics.FoodEntry
import com.example.strivo.data.analytics.WeightEntry
import com.example.strivo.data.db.PersonalRecords
import com.example.strivo.data.db.StrivoDatabase
import com.example.strivo.data.model.Exercise
import com.example.strivo.data.model.Plan
import com.example.strivo.data.model.WorkoutSession
import com.example.strivo.data.prefs.UserPrefs
import com.example.strivo.util.nowIso
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
     * Replaces any earlier demo data with a fresh 7 days of made-up workouts, meals and weigh-ins. Weigh-ins end at
     * the profile's real weight, and a day that already has a real weigh-in is left alone. Returns how many workouts were added.
     */
    suspend fun loadDemoData(today: LocalDate): Int = io {
        deleteDemoRows()
        val sessionIds = DemoData.sessions(today).map { db.saveSession(it) }
        prefs.setDemoSessionIds(sessionIds.toSet())
        prefs.setDemoFoodIds(DemoData.food(today).map { db.insertFood(it) }.toSet())
        prefs.setDemoActivityIds(DemoData.activities(today).map { db.insertActivity(it) }.toSet())
        val profileWeight = prefs.getProfile().weight
        if (profileWeight != null) {
            val added = DemoData.weights(today, profileWeight)
                .filter { db.insertWeightIfAbsent(it.date.toString(), it.kg) }
                .map { it.date.toString() }
            prefs.setDemoWeightDates(added.toSet())
        }
        sessionIds.size
    }

    /** Removes only what [loadDemoData] added; the user's own workouts, food and weigh-ins are kept. */
    suspend fun removeDemoData(): Int = io { deleteDemoRows() }

    private fun deleteDemoRows(): Int {
        val removed = db.deleteSessions(prefs.getDemoSessionIds())
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
}
