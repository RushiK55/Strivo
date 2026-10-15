package com.example.strivo.data.repository

import com.example.strivo.core.util.nowIso
import com.example.strivo.data.demo.DemoData
import com.example.strivo.data.demo.DemoWeightTrend
import com.example.strivo.data.local.db.PersonalRecords
import com.example.strivo.data.local.db.StrivoDatabase
import com.example.strivo.data.local.prefs.UserPrefs
import com.example.strivo.domain.activity.ActivityEvent
import com.example.strivo.domain.activity.activityAdded
import com.example.strivo.domain.activity.exerciseAdded
import com.example.strivo.domain.activity.exerciseEdited
import com.example.strivo.domain.activity.exerciseRemoved
import com.example.strivo.domain.activity.foodLogged
import com.example.strivo.domain.activity.planCopied
import com.example.strivo.domain.activity.planCreated
import com.example.strivo.domain.activity.planDeleted
import com.example.strivo.domain.activity.planOfferAnswered
import com.example.strivo.domain.activity.planTaken
import com.example.strivo.domain.activity.profileUpdated
import com.example.strivo.domain.activity.weightLogged
import com.example.strivo.domain.activity.workoutFinished
import com.example.strivo.domain.analytics.ExtraActivity
import com.example.strivo.domain.analytics.FoodEntry
import com.example.strivo.domain.analytics.WeightEntry
import com.example.strivo.domain.coach.PlannedExercise
import com.example.strivo.domain.coach.exerciseKey
import com.example.strivo.domain.model.EXTRA_PLAN_NAME
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.LibraryExercise
import com.example.strivo.domain.model.PREBUILT_DAY
import com.example.strivo.domain.model.Plan
import com.example.strivo.domain.model.TRAINER_ORIGIN
import com.example.strivo.domain.model.WorkoutSession
import com.example.strivo.domain.plan.LibraryPlan
import com.example.strivo.domain.plan.PlanAssignment
import com.example.strivo.domain.plan.PlanOffer
import com.example.strivo.domain.plan.ReceivedPlan
import com.example.strivo.domain.plan.freshCopyFor
import com.example.strivo.domain.plan.groupOffers
import com.example.strivo.domain.plan.isPlanned
import com.example.strivo.domain.plan.withPlanOf
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

    private fun now(): Long = System.currentTimeMillis()

    /** Writes a line to the activity log, which is backed up with the rest of the user's data. */
    private fun record(event: ActivityEvent) {
        db.insertEvent(event)
    }

    /** The newest lines of the activity log, newest first. */
    suspend fun readEvents(limit: Int = 100): List<ActivityEvent> = io { db.readEvents(limit) }

    suspend fun recordProfileUpdated() = io { record(profileUpdated(now())) }

    // --- Plans ---

    suspend fun readAllPlans(): List<Plan> = io { db.readAllPlans() }

    suspend fun createPlan(plan: Plan): Long = io {
        db.createPlan(plan).also { record(planCreated(plan, now())) }
    }

    suspend fun deletePlan(id: Long) = io {
        val plan = db.getPlan(id)
        db.deletePlan(id)
        plan?.let { record(planDeleted(it, now())) }
    }

    /** The plan holding [day]'s extra exercises; created the first time one is added. */
    suspend fun getOrCreateExtraPlan(day: String): Long = io {
        db.readAllPlans().firstOrNull { it.planDay == day && it.planName == EXTRA_PLAN_NAME }?.planId
            ?: db.createPlan(Plan(planName = EXTRA_PLAN_NAME, planDay = day))
    }

    suspend fun deleteEmptyExtraPlans(): Int = io { db.deleteEmptyExtraPlans(EXTRA_PLAN_NAME) }

    /**
     * Puts one of the user's own plans on [day]: a copy of it and its exercises (fresh, nothing ticked off) that stays linked to the
     * plan. What the user changes on the day is kept in their plan, and the plan is what the day starts from each morning.
     * Returns the new plan's id.
     */
    suspend fun copyPlanToDay(source: Plan, day: String): Long = io { putPlanOnDay(source, day, log = true) }

    private fun putPlanOnDay(source: Plan, day: String, log: Boolean): Long {
        val sourceId = source.planId ?: return -1L
        val newId = db.createPlan(Plan(planName = source.planName, planDay = day, sourcePlanId = sourceId))
        db.readExercisesByPlan(sourceId).filter { it.isPlanned }.forEach { db.createExercise(it.freshCopyFor(newId)) }
        if (log) record(planCopied(source, day, now()))
        return newId
    }

    /**
     * Puts a plan of the plan library on [day]. It becomes one of the user's own plans first (once: a plan taken before is used again,
     * with the changes the user made to it), and the day gets a linked copy of that. Returns the day's plan id.
     */
    suspend fun addLibraryPlanToDay(plan: LibraryPlan, day: String): Long = io {
        val own = db.readAllPlans().firstOrNull { it.isPrebuilt && it.origin == plan.origin }
            ?: db.getPlan(db.importPlan(Plan(planName = plan.name, planDay = PREBUILT_DAY, origin = plan.origin), plan.exercises, planTaken(plan.name, now())))
            ?: return@io -1L
        putPlanOnDay(own, day, log = true)
    }

    /**
     * What the trainer is offering that the user has not answered, grouped so that a week's plans sent together are one offer.
     * The user's own answers count at once, before the cloud has been told, so an answered plan never comes back.
     */
    suspend fun planOffers(): List<PlanOffer> = io {
        val answered = prefs.getReceivedPlans().map { it.id }.toSet() + prefs.getDeclinedPlanIds()
        groupOffers(prefs.getOffers().filter { it.id !in answered })
    }

    /**
     * The user accepted: every plan of the offer is added to their plans, on the day the trainer chose, and the cloud is told
     * on the next sync. A plan already added is not added again (a double tap, a retry). Returns how many plans were added.
     */
    suspend fun acceptOffer(offer: PlanOffer): Int = io {
        val received = prefs.getReceivedPlans().map { it.id }.toSet()
        val fresh = offer.plans.filter { it.id !in received }
        if (fresh.isEmpty()) return@io 0
        fresh.forEach { assignment ->
            // It becomes one of the user's own plans, which they can add to any day from the Add plan screen; when the trainer chose a
            // weekday it is put there too, linked, so the user's changes on that day are kept in the plan (and the trainer sees them).
            val planId = db.importPlan(Plan(planName = assignment.planName, planDay = PREBUILT_DAY, origin = TRAINER_ORIGIN + assignment.id), assignment.exercises, null)
            if (assignment.day != PREBUILT_DAY) db.getPlan(planId)?.let { putPlanOnDay(it, assignment.day, log = false) }
            prefs.addReceivedPlan(ReceivedPlan.from(assignment, planId, now()))
        }
        prefs.addPendingAnswers(fresh.associate { it.id to PlanAssignment.STATUS_ACCEPTED })
        record(planOfferAnswered(PlanOffer(fresh), accepted = true, at = now()))
        fresh.size
    }

    /** The user said no: nothing is added, the plans never come back, and the cloud is told on the next sync. */
    suspend fun declineOffer(offer: PlanOffer) = io {
        val answered = prefs.getReceivedPlans().map { it.id }.toSet() + prefs.getDeclinedPlanIds()
        val fresh = offer.plans.filter { it.id !in answered }
        if (fresh.isEmpty()) return@io
        prefs.addDeclinedPlans(fresh.map { it.id })
        prefs.addPendingAnswers(fresh.associate { it.id to PlanAssignment.STATUS_DECLINED })
        record(planOfferAnswered(PlanOffer(fresh), accepted = false, at = now()))
    }

    // --- Exercises ---

    suspend fun readExercisesByPlan(planId: Long): List<Exercise> = io { db.readExercisesByPlan(planId) }

    suspend fun readExercisesByDay(day: String): List<Exercise> = io { db.readExercisesByDay(day) }

    suspend fun readExtraExercises(): List<Exercise> = io { db.readExtraExercises() }

    suspend fun readAllExercises(): List<Exercise> = io { db.readAllExercises() }

    suspend fun createExercise(exercise: Exercise, fromLibrary: Boolean = false): Long = io {
        db.createExercise(exercise).also { id ->
            addToUsersPlan(id, exercise)
            record(exerciseAdded(exercise, db.getPlan(exercise.planId), now(), fromLibrary))
        }
    }

    suspend fun deleteExercise(id: Long) = io {
        val exercise = db.getExercise(id)
        db.deleteExercise(id)
        exercise?.let {
            removeFromUsersPlan(it)
            record(exerciseRemoved(it, db.getPlan(it.planId), now()))
        }
    }

    suspend fun moveExerciseToPlan(id: Long, targetPlanId: Long) = io {
        val exercise = db.getExercise(id)
        exercise?.let { removeFromUsersPlan(it) }
        db.moveExerciseToPlan(id, targetPlanId)
        db.setSourceId(id, null)
        db.getExercise(id)?.let { addToUsersPlan(id, it) }
    }

    // The plan on a day is linked to the user's own plan it was made from (Plan.sourcePlanId). These keep the two the same: what is
    // added to, taken out of or changed in the day's plan is done to the user's plan too, and each morning the day starts from it.

    /** The id of the user's plan that [planId] (a weekday's plan) was made from, when that plan is still there. */
    private fun usersPlanOf(planId: Long): Long? =
        db.getPlan(planId)?.sourcePlanId?.takeIf { db.getPlan(it) != null }

    /** An exercise added to a day's plan is added to the user's plan too, and linked to it. */
    private fun addToUsersPlan(id: Long, exercise: Exercise) {
        if (!exercise.isPlanned || exercise.sourceId != null) return
        val usersPlan = usersPlanOf(exercise.planId) ?: return
        val added = db.createExercise(exercise.copy(id = id).freshCopyFor(usersPlan).copy(sourceId = null))
        db.setSourceId(id, added)
    }

    private fun removeFromUsersPlan(exercise: Exercise) {
        val source = exercise.sourceId ?: return
        if (exercise.isPlanned && usersPlanOf(exercise.planId) != null) db.deleteExercise(source)
    }

    /** What an exercise plans on a day (every set's weight and reps, its name, its notes) is written to the user's plan. */
    private fun keepUsersPlanInStep(exercise: Exercise) {
        val source = exercise.sourceId ?: return
        if (!exercise.isPlanned || usersPlanOf(exercise.planId) == null) return
        db.getExercise(source)?.withPlanOf(exercise)?.let { db.updateExercise(it) }
    }

    /**
     * Each morning a day starts again from the user's plan it was made from: the weights and reps the plan holds, an exercise the
     * user added to the plan since, and none that was taken out. (Changes made on a day reach the plan at once; this is how a change to
     * the plan itself reaches the days.)
     */
    private fun startDaysFromUsersPlans() {
        val plans = db.readAllPlans()
        val own = plans.filter { it.isPrebuilt }.associateBy { it.planId }
        for (day in plans.filter { it.sourcePlanId != null && !it.isPrebuilt }) {
            val dayId = day.planId ?: continue
            val usersPlan = own[day.sourcePlanId]?.planId ?: continue
            val planned = db.readExercisesByPlan(usersPlan)
            val onDay = db.readExercisesByPlan(dayId).filter { it.isPlanned }
            planned.forEach { wanted ->
                val mine = onDay.firstOrNull { it.sourceId == wanted.id }
                if (mine == null) db.createExercise(wanted.freshCopyFor(dayId)) else mine.withPlanOf(wanted)?.let { db.updateExercise(it) }
            }
            val kept = planned.mapNotNull { it.id }.toSet()
            onDay.filter { it.sourceId != null && it.sourceId !in kept }.forEach { exercise -> exercise.id?.let { db.deleteExercise(it) } }
        }
    }

    /** Adds a fresh copy of [exercise] (planned sets / reps / weight, nothing logged) to the end of another plan. */
    suspend fun copyExerciseToPlan(exercise: Exercise, targetPlanId: Long): Long = io {
        val copy = exercise.copy(
            id = null,
            planId = targetPlanId,
            isCheck = false,
            dateTime = "",
            restTime = "",
            sortOrder = -1,
            setsList = emptyList(),
        )
        db.createExercise(copy).also { id ->
            addToUsersPlan(id, copy)
            record(exerciseAdded(copy, db.getPlan(targetPlanId), now()))
        }
    }

    /** Adds a library exercise to a plan with its suggested sets, reps and weight. */
    suspend fun addLibraryExercise(exercise: LibraryExercise, planId: Long): Long = createExercise(exercise.toPlanExercise(planId), fromLibrary = true)

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

    /** Saves an exercise the user edited in a plan (not a set logged in a workout) and notes the change. */
    suspend fun editExercise(exercise: Exercise) {
        updateExercise(exercise)
        io { record(exerciseEdited(exercise, db.getPlan(exercise.planId), now())) }
    }

    /** Saves the exercise and keeps the history table in sync with its checked state. */
    suspend fun updateExercise(exercise: Exercise) = io {
        db.updateExercise(exercise)
        keepUsersPlanInStep(exercise)
        // One history row per exercise per day: replace today's row instead of piling up copies.
        db.removeFromHistory(exercise.name, nowIso())
        if (exercise.isCheck) db.addToHistory(exercise)
    }

    /** On the first load of each calendar day, un-check everything and drop the one-day exercises. */
    suspend fun checkAndResetDaily() = io {
        if (prefs.consumeDailyReset()) {
            db.resetAllExerciseChecks()
            db.deleteDailyExercises()
            startDaysFromUsersPlans()
        }
    }

    // --- Weight log ---

    suspend fun logWeight(kg: Double, date: LocalDate = LocalDate.now()) = io {
        db.upsertWeight(date.toString(), kg)
        record(weightLogged(kg, now()))
    }

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

    suspend fun addFood(entry: FoodEntry): Long = io {
        db.insertFood(entry).also { record(foodLogged(entry, now())) }
    }

    suspend fun updateFood(entry: FoodEntry) = io { db.updateFood(entry) }

    suspend fun deleteFood(id: Long) = io { db.deleteFood(id) }

    // --- Other activity ---

    suspend fun readActivities(): List<ExtraActivity> = io { db.readActivities() }

    suspend fun addActivity(entry: ExtraActivity): Long = io {
        db.insertActivity(entry).also { record(activityAdded(entry, now())) }
    }

    suspend fun deleteActivity(id: Long) = io { db.deleteActivity(id) }

    suspend fun saveSession(session: WorkoutSession, replaceId: Long? = null): Long = io {
        db.saveSession(session, replaceId).also {
            // Finishing the same workout again updates it; only a new one is something the user did.
            if (replaceId == null) record(workoutFinished(session, now()))
        }
    }

    suspend fun getWorkoutSessions(): List<WorkoutSession> = io { db.getWorkoutSessions() }

    /** Remembers which exercises [planId] held when its workout was finished today (see [com.example.strivo.domain.plan.isPlanDoneToday]). */
    suspend fun markPlanFinished(planId: Long, exerciseIds: Set<Long>, today: LocalDate) = io { prefs.setFinishedPlan(today, planId, exerciseIds) }

    suspend fun finishedPlans(today: LocalDate): Map<Long, Set<Long>> = io { prefs.getFinishedPlans(today) }

    // --- Exercise library ---

    /** The exercises users can browse: the ones an admin has not hidden. */
    suspend fun readLibrary(): List<LibraryExercise> = io { db.readLibrary().filter { it.active } }

    // --- Plan library ---

    /** The plans users can browse: the ones an admin has not hidden. */
    suspend fun readLibraryPlans(): List<LibraryPlan> = io { db.readLibraryPlans().filter { it.active } }

    /** Puts the starter plans in place on a phone that has none yet; the cloud's plan library replaces them when it arrives. */
    suspend fun seedPlanLibraryIfEmpty(starter: List<LibraryPlan>) = io {
        if (db.libraryPlanCount() == 0 && starter.isNotEmpty()) db.replaceLibraryPlans(starter)
    }

    /** Puts the starter library in place on a phone that has none yet; the cloud's library replaces it when it arrives. */
    suspend fun seedLibraryIfEmpty(starter: List<LibraryExercise>) = io {
        if (db.libraryCount() == 0 && starter.isNotEmpty()) db.replaceLibrary(starter)
    }
}
