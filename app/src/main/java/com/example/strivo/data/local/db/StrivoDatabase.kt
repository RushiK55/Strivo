package com.example.strivo.data.local.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.strivo.core.util.nowIso
import com.example.strivo.domain.analytics.ExtraActivity
import com.example.strivo.domain.analytics.FoodEntry
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.Plan
import com.example.strivo.domain.model.WorkoutSession
import com.example.strivo.domain.model.countsForRecords
import com.example.strivo.domain.model.isWork
import com.example.strivo.domain.model.toJsonString
import java.time.format.DateTimeFormatter

/**
 * SQLite storage. The schema, file name and version intentionally match the previous
 * Flutter (sqflite) build, so existing user data keeps working after the upgrade.
 */
class StrivoDatabase(context: Context, uid: String) : SQLiteOpenHelper(context, fileNameFor(uid), null, Schema.VERSION) {

    override fun onCreate(db: SQLiteDatabase) = Schema.create(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Schema.upgrade(db, oldVersion)

    /** Deletes every row demo data added; returns how many workouts that was. */
    fun deleteDemoRows(): Int {
        val db = writableDatabase
        val sessions = db.delete("workout_sessions", "isDemo = 1", null)
        // Exercises before the plans that hold them.
        Schema.DemoTables.filter { it != "workout_sessions" }.forEach { db.delete(it, "isDemo = 1", null) }
        return sessions
    }

    // --- Cloud sync ---

    fun pendingChanges(): List<Change> =
        readableDatabase.query("sync_log", null, null, null, null, null, "id").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Change(c.getLong(c.getColumnIndexOrThrow("id")), c.getString(c.getColumnIndexOrThrow("tbl")), c.getString(c.getColumnIndexOrThrow("row_id"))))
                }
            }
        }

    fun pendingChangeCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(DISTINCT tbl || ':' || row_id) FROM sync_log", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    /** Removes log entries once their rows have reached the cloud. Entries added meanwhile are kept. */
    fun clearChanges(logIds: Collection<Long>) {
        logIds.chunked(500).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            writableDatabase.delete("sync_log", "id IN ($placeholders)", chunk.map { it.toString() }.toTypedArray())
        }
    }

    /** Ids of the rows of [table] that have a change waiting to be uploaded. */
    fun pendingRowIds(table: String): Set<String> =
        readableDatabase.rawQuery("SELECT DISTINCT row_id FROM sync_log WHERE tbl = ?", arrayOf(table)).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    fun localRowIds(spec: SyncTable): Set<String> =
        readableDatabase.rawQuery("SELECT CAST(${spec.pk} AS TEXT) FROM ${spec.table}", null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    /** Queues rows for upload again. */
    fun enqueueRows(table: String, rowIds: Collection<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            rowIds.forEach { db.execSQL("INSERT INTO sync_log (tbl, row_id) VALUES (?, ?)", arrayOf(table, it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** One row as column name to value, or null if it no longer exists. */
    fun readRowAsMap(spec: SyncTable, rowId: String): Map<String, Any?>? =
        readableDatabase.rawQuery("SELECT * FROM ${spec.table} WHERE ${spec.pk} = ?", arrayOf(rowId)).use { c ->
            if (!c.moveToFirst()) return null
            buildMap {
                for (i in 0 until c.columnCount) {
                    put(
                        c.getColumnName(i),
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                            Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                            Cursor.FIELD_TYPE_STRING -> c.getString(i)
                            else -> null
                        },
                    )
                }
            }
        }

    /** Writes rows downloaded from the cloud and removes rows deleted elsewhere, without adding them to the change log. */
    fun applyRemote(spec: SyncTable, rows: List<Map<String, Any?>>, deleteIds: Collection<String>) {
        if (rows.isEmpty() && deleteIds.isEmpty()) return
        val db = writableDatabase
        val columns = db.rawQuery("SELECT * FROM ${spec.table} LIMIT 0", null).use { it.columnNames.toSet() }
        db.beginTransaction()
        try {
            // Re-checked inside the transaction: a change made on this phone a moment ago must not be overwritten.
            val pendingNow = db.rawQuery("SELECT DISTINCT row_id FROM sync_log WHERE tbl = ?", arrayOf(spec.table)).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            db.execSQL("UPDATE sync_state SET v = '1' WHERE k = 'applying'")
            rows.filter { row -> row[spec.pk]?.toString() !in pendingNow }.forEach { row ->
                val values = ContentValues()
                row.forEach { (column, value) ->
                    if (column !in columns) return@forEach // a column from a newer version of the app
                    when (value) {
                        null -> values.putNull(column)
                        is Long -> values.put(column, value)
                        is Int -> values.put(column, value)
                        is Double -> values.put(column, value)
                        is Float -> values.put(column, value.toDouble())
                        is Boolean -> values.put(column, if (value) 1 else 0)
                        else -> values.put(column, value.toString())
                    }
                }
                db.insertWithOnConflict(spec.table, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            deleteIds.filter { it !in pendingNow }.forEach { db.delete(spec.table, "${spec.pk} = ?", arrayOf(it)) }
            db.execSQL("UPDATE sync_state SET v = '0' WHERE k = 'applying'")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // --- Weight log ---

    /** One weigh-in per day: a second one on the same day replaces the first. */
    fun upsertWeight(date: String, kg: Double) {
        val values = ContentValues().apply {
            put("date", date)
            put("weight", kg)
        }
        writableDatabase.insertWithOnConflict("weight_log", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** All weigh-ins, oldest first, as (ISO date, kg). */
    fun readWeightLog(): List<Pair<String, Double>> =
        readableDatabase.query("weight_log", null, null, null, null, null, "date").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("date")) to c.getDouble(c.getColumnIndexOrThrow("weight"))) }
        }

    /** Adds a weigh-in only if that day has none; true if it was added. Used for demo data so real weigh-ins are never replaced. */
    fun insertWeightIfAbsent(date: String, kg: Double, isDemo: Boolean = false): Boolean {
        val values = ContentValues().apply {
            put("date", date)
            put("weight", kg)
            put("isDemo", if (isDemo) 1 else 0)
        }
        return writableDatabase.insertWithOnConflict("weight_log", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun deleteWeights(dates: Collection<String>): Int {
        if (dates.isEmpty()) return 0
        val placeholders = dates.joinToString(",") { "?" }
        return writableDatabase.delete("weight_log", "date IN ($placeholders)", dates.toTypedArray())
    }

    // --- Food log ---

    fun insertFood(entry: FoodEntry, isDemo: Boolean = false): Long =
        writableDatabase.insert("food_log", null, entry.toValues().apply { put("isDemo", if (isDemo) 1 else 0) })

    fun updateFood(entry: FoodEntry): Int =
        writableDatabase.update("food_log", entry.toValues(), "id = ?", arrayOf(entry.id.toString()))

    fun deleteFood(id: Long): Int = writableDatabase.delete("food_log", "id = ?", arrayOf(id.toString()))

    fun deleteFoods(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        val placeholders = ids.joinToString(",") { "?" }
        return writableDatabase.delete("food_log", "id IN ($placeholders)", ids.map { it.toString() }.toTypedArray())
    }

    /** Every logged food, oldest first. */
    fun readFood(): List<FoodEntry> =
        readableDatabase.query("food_log", null, null, null, null, null, "date, id").use { c ->
            buildList { while (c.moveToNext()) c.toFoodEntry()?.let { add(it) } }
        }

    // --- Other activity (calories added by hand) ---

    fun insertActivity(entry: ExtraActivity, isDemo: Boolean = false): Long {
        val values = ContentValues().apply {
            put("isDemo", if (isDemo) 1 else 0)
            put("date", entry.date.toString())
            put("name", entry.name)
            put("calories", entry.calories)
            if (entry.minutes != null) put("minutes", entry.minutes) else putNull("minutes")
        }
        return writableDatabase.insert("activity_log", null, values)
    }

    fun deleteActivity(id: Long): Int = writableDatabase.delete("activity_log", "id = ?", arrayOf(id.toString()))

    fun deleteActivities(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        val placeholders = ids.joinToString(",") { "?" }
        return writableDatabase.delete("activity_log", "id IN ($placeholders)", ids.map { it.toString() }.toTypedArray())
    }

    /** Every activity added by hand, oldest first. */
    fun readActivities(): List<ExtraActivity> =
        readableDatabase.query("activity_log", null, null, null, null, null, "date, id").use { c ->
            buildList {
                while (c.moveToNext()) {
                    runCatching {
                        val minutes = c.getColumnIndexOrThrow("minutes")
                        ExtraActivity(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            date = java.time.LocalDate.parse(c.getString(c.getColumnIndexOrThrow("date"))),
                            name = c.getString(c.getColumnIndexOrThrow("name")),
                            calories = c.getInt(c.getColumnIndexOrThrow("calories")),
                            minutes = if (c.isNull(minutes)) null else c.getInt(minutes),
                        )
                    }.getOrNull()?.let { add(it) }
                }
            }
        }

    // --- Plans ---

    fun createPlan(plan: Plan, isDemo: Boolean = false): Long =
        writableDatabase.insert("plans", null, plan.toValues().apply { if (isDemo) put("isDemo", 1) })

    fun readAllPlans(): List<Plan> =
        readableDatabase.query("plans", null, null, null, null, null, null).use { c ->
            buildList { while (c.moveToNext()) add(c.toPlan()) }
        }

    fun getPlan(id: Long): Plan? =
        readableDatabase.query("plans", null, "planId = ?", arrayOf(id.toString()), null, null, null)
            .use { c -> if (c.moveToFirst()) c.toPlan() else null }

    /** Removes extra-exercise plans that ended up with no exercises (the user opened the add screen and backed out). */
    fun deleteEmptyExtraPlans(name: String): Int =
        writableDatabase.delete(
            "plans",
            "planName = ? AND planId NOT IN (SELECT DISTINCT planId FROM exercises)",
            arrayOf(name),
        )

    fun updatePlan(plan: Plan): Int =
        writableDatabase.update("plans", plan.toValues(), "planId = ?", arrayOf(plan.planId.toString()))

    fun deletePlan(id: Long): Int {
        val db = writableDatabase
        // Also delete exercises associated with this plan
        db.delete("exercises", "planId = ?", arrayOf(id.toString()))
        return db.delete("plans", "planId = ?", arrayOf(id.toString()))
    }

    // --- Exercises ---

    fun createExercise(exercise: Exercise, isDemo: Boolean = false): Long {
        val db = writableDatabase
        val values = exercise.toValues().apply { if (isDemo) put("isDemo", 1) }
        if (exercise.sortOrder < 0) {
            // New exercises go to the end of their plan.
            val next = db.rawQuery(
                "SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM exercises WHERE planId = ?",
                arrayOf(exercise.planId.toString()),
            ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            values.put("sortOrder", next)
        }
        return db.insert("exercises", null, values)
    }

    /** Moves an exercise to the end of another plan. */
    fun moveExerciseToPlan(id: Long, targetPlanId: Long) {
        val db = writableDatabase
        val next = db.rawQuery(
            "SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM exercises WHERE planId = ?",
            arrayOf(targetPlanId.toString()),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        val values = ContentValues().apply {
            put("planId", targetPlanId)
            put("sortOrder", next)
        }
        db.update("exercises", values, "id = ?", arrayOf(id.toString()))
    }

    /** Saves [orderedIds] as the order of a plan's exercises. */
    fun setExerciseOrder(planId: Long, orderedIds: List<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Rewrite a dense 0..n order so ties and gaps can't build up.
            orderedIds.forEachIndexed { position, exerciseId ->
                val values = ContentValues().apply { put("sortOrder", position) }
                db.update("exercises", values, "id = ? AND planId = ?", arrayOf(exerciseId.toString(), planId.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun readAllExercises(): List<Exercise> =
        readableDatabase.query("exercises", null, null, null, null, null, "sortOrder, id").use { c ->
            buildList { while (c.moveToNext()) add(c.toExercise()) }
        }

    fun readExercisesByDay(day: String): List<Exercise> =
        readableDatabase.rawQuery(
            """
            SELECT exercises.* FROM exercises
            JOIN plans ON exercises.planId = plans.planId
            WHERE plans.planDay = ?
            ORDER BY exercises.sortOrder, exercises.id
            """.trimIndent(),
            arrayOf(day),
        ).use { c -> buildList { while (c.moveToNext()) add(c.toExercise()) } }

    fun readExercisesByPlan(planId: Long): List<Exercise> =
        readableDatabase.query("exercises", null, "planId = ?", arrayOf(planId.toString()), null, null, "sortOrder, id")
            .use { c -> buildList { while (c.moveToNext()) add(c.toExercise()) } }

    fun readExtraExercises(): List<Exercise> =
        readableDatabase.query("exercises", null, "isExtra = ?", arrayOf("1"), null, null, null)
            .use { c -> buildList { while (c.moveToNext()) add(c.toExercise()) } }

    fun getExercise(id: Long): Exercise? =
        readableDatabase.query("exercises", null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { c -> if (c.moveToFirst()) c.toExercise() else null }

    fun updateExercise(exercise: Exercise): Int =
        writableDatabase.update("exercises", exercise.toValues(), "id = ?", arrayOf(exercise.id.toString()))

    fun resetAllExerciseChecks(): Int {
        val values = ContentValues().apply {
            put("isCheck", 0)
            put("setsData", "") // Clear the sets logged in the previous session; the planned sets/reps/weight stay
            put("restTime", "")
        }
        return writableDatabase.update("exercises", values, null, null)
    }

    fun deleteDailyExercises(): Int =
        // Delete both "vanish" and "extra" exercises
        writableDatabase.delete("exercises", "vanishEndOfDay = ? OR isExtra = ?", arrayOf("1", "1"))

    fun deleteExercise(id: Long): Int =
        writableDatabase.delete("exercises", "id = ?", arrayOf(id.toString()))

    // --- Personal records ---

    fun getPersonalRecords(): PersonalRecords =
        readableDatabase.rawQuery(
            """
            SELECT
              MAX(CAST(weight AS REAL)) as maxWeight,
              MAX(CAST(reps AS INTEGER)) as maxReps,
              MAX(CAST(weight AS REAL) * CAST(sets AS INTEGER) * CAST(reps AS INTEGER)) as maxVolume
            FROM history
            """.trimIndent(),
            null,
        ).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) {
                PersonalRecords(
                    maxWeight = c.getDouble(0),
                    maxReps = if (c.isNull(1)) 0.0 else c.getDouble(1),
                    maxVolume = if (c.isNull(2)) 0.0 else c.getDouble(2),
                )
            } else {
                PersonalRecords()
            }
        }

    // --- History ---

    fun addToHistory(exercise: Exercise): Long {
        // Record what was actually lifted (the logged sets), not the plan.
        val logged = exercise.setsList
        // A warm-up or a drop set is not what the user lifted: the row stands for their working sets.
        val working = logged.filter { it.countsForRecords }.ifEmpty { logged.filter { it.isWork } }.ifEmpty { logged }
        val weight = if (logged.isEmpty()) exercise.weight else (working.lastOrNull { it.weight > 0 } ?: working.first()).weight.toString()
        val reps = if (logged.isEmpty()) exercise.reps else (working.lastOrNull { it.reps > 0 } ?: working.first()).reps.toString()
        val values = ContentValues().apply {
            put("name", exercise.name)
            put("weight", weight)
            put("sets", if (logged.isEmpty()) exercise.sets else logged.count { it.isWork }.coerceAtLeast(1).toString())
            put("reps", reps)
            put("completedAt", nowIso())
            put("restTime", exercise.restTime)
            put("setsData", exercise.setsList.toJsonString())
        }
        return writableDatabase.insert("history", null, values)
    }

    /** Removes the entry if an exercise is unchecked on the same day it was completed. */
    fun removeFromHistory(name: String, date: String): Int =
        writableDatabase.delete(
            "history",
            "name = ? AND completedAt LIKE ?",
            arrayOf(name, "${date.substring(0, 10)}%"),
        )

    // --- Workout sessions ---

    /** Inserts the session, or overwrites the one with [replaceId] (a workout that was finished again). */
    fun saveSession(session: WorkoutSession, replaceId: Long? = null, isDemo: Boolean = false): Long {
        val values = ContentValues().apply {
            put("isDemo", if (isDemo) 1 else 0)
            put("planName", session.planName)
            put("date", DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(session.date))
            put("totalTime", session.totalTime)
            put("exercises", session.exercisesJson())
        }
        val db = writableDatabase
        if (replaceId != null && db.update("workout_sessions", values, "id = ?", arrayOf(replaceId.toString())) > 0) {
            return replaceId
        }
        return db.insert("workout_sessions", null, values)
    }

    /** Deletes every saved workout except demo ones (marked, or listed in [keepIds] from before the mark existed). */
    fun deleteSessionsExcept(keepIds: Collection<Long>): Int {
        if (keepIds.isEmpty()) return writableDatabase.delete("workout_sessions", "isDemo = 0", null)
        val placeholders = keepIds.joinToString(",") { "?" }
        return writableDatabase.delete("workout_sessions", "isDemo = 0 AND id NOT IN ($placeholders)", keepIds.map { it.toString() }.toTypedArray())
    }

    /** Empties the per-exercise history (the log behind the personal records). Plans and exercises are untouched. */
    fun clearExerciseHistory(): Int = writableDatabase.delete("history", null, null)

    fun deleteSessions(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        val placeholders = ids.joinToString(",") { "?" }
        return writableDatabase.delete("workout_sessions", "id IN ($placeholders)", ids.map { it.toString() }.toTypedArray())
    }

    fun getWorkoutSessions(): List<WorkoutSession> =
        readableDatabase.query("workout_sessions", null, null, null, null, null, "date DESC").use { c ->
            buildList {
                while (c.moveToNext()) {
                    runCatching { c.toSession() }.onSuccess { add(it) }
                }
            }
        }

    companion object {
        /** The database file for [uid]: every account keeps its own. */
        fun fileNameFor(uid: String) = "strivo_${uid.filter { it.isLetterOrDigit() || it == '_' }}.db"
    }
}
