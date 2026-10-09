package com.example.strivo.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.strivo.data.analytics.ExtraActivity
import com.example.strivo.data.analytics.FoodEntry
import com.example.strivo.data.analytics.Meal
import com.example.strivo.data.model.Exercise
import com.example.strivo.data.sync.Change
import com.example.strivo.data.sync.SyncTable
import com.example.strivo.data.sync.SyncTables
import com.example.strivo.data.model.Plan
import com.example.strivo.data.model.WorkoutSession
import com.example.strivo.data.model.parseExerciseSets
import com.example.strivo.data.model.toJsonString
import com.example.strivo.util.nowIso
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * SQLite storage. The schema, file name and version intentionally match the previous
 * Flutter (sqflite) build, so existing user data keeps working after the upgrade.
 */
class StrivoDatabase(context: Context, uid: String) : SQLiteOpenHelper(context, fileNameFor(uid), null, 12) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE plans (
              planId INTEGER PRIMARY KEY AUTOINCREMENT,
              planName TEXT,
              planDay TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE exercises (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              planId INTEGER,
              name TEXT,
              weight TEXT,
              sets TEXT,
              reps TEXT,
              notes TEXT,
              dateTime TEXT,
              isCheck INTEGER,
              isExtra INTEGER DEFAULT 0,
              vanishEndOfDay INTEGER DEFAULT 0,
              restTime TEXT DEFAULT "",
              setsData TEXT,
              sortOrder INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE history (
              historyId INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT,
              weight TEXT,
              sets TEXT,
              reps TEXT,
              completedAt TEXT,
              restTime TEXT DEFAULT "",
              setsData TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE workout_sessions (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              planName TEXT,
              date TEXT,
              totalTime TEXT,
              exercises TEXT
            )
            """.trimIndent()
        )
        db.execSQL(CREATE_WEIGHT_LOG)
        db.execSQL(CREATE_FOOD_LOG)
        db.execSQL(CREATE_ACTIVITY_LOG)
        createSyncObjects(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 9) db.execSQL(CREATE_WEIGHT_LOG)
        if (oldVersion < 10) db.execSQL(CREATE_FOOD_LOG)
        if (oldVersion < 11) db.execSQL(CREATE_ACTIVITY_LOG)
        if (oldVersion < 12) {
            createSyncObjects(db)
            // Everything saved before syncing existed has to be uploaded once, so queue every existing row.
            SyncTables.forEach { t ->
                db.execSQL("INSERT INTO sync_log (tbl, row_id) SELECT '${t.table}', CAST(${t.pk} AS TEXT) FROM ${t.table}")
            }
        }
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS history (
                  historyId INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT,
                  weight TEXT,
                  sets TEXT,
                  reps TEXT,
                  completedAt TEXT
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE exercises ADD COLUMN isExtra INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE exercises ADD COLUMN vanishEndOfDay INTEGER DEFAULT 0")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE exercises ADD COLUMN setsData TEXT")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE history ADD COLUMN setsData TEXT")
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE exercises ADD COLUMN restTime TEXT DEFAULT \"\"")
            db.execSQL("ALTER TABLE history ADD COLUMN restTime TEXT DEFAULT \"\"")
        }
        if (oldVersion < 7) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS workout_sessions (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  planName TEXT,
                  date TEXT,
                  totalTime TEXT,
                  exercises TEXT
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE exercises ADD COLUMN sortOrder INTEGER DEFAULT 0")
            // Keep the order users already see (creation order).
            db.execSQL("UPDATE exercises SET sortOrder = id")
        }
    }

    // --- Cloud sync ---

    /**
     * A change log kept by SQLite triggers: whatever code touches a synced table, the row ends up in sync_log.
     * Changes made while a download from the cloud is being applied are not logged (sync_state.applying).
     */
    private fun createSyncObjects(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_state (k TEXT PRIMARY KEY, v TEXT)")
        db.execSQL("INSERT OR IGNORE INTO sync_state (k, v) VALUES ('applying', '0')")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_log (id INTEGER PRIMARY KEY AUTOINCREMENT, tbl TEXT NOT NULL, row_id TEXT NOT NULL)")
        SyncTables.forEach { t ->
            listOf("INSERT" to "NEW", "UPDATE" to "NEW", "DELETE" to "OLD").forEach { (event, row) ->
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS sync_${t.table}_${event.lowercase()} AFTER $event ON ${t.table} " +
                        "WHEN (SELECT v FROM sync_state WHERE k = 'applying') = '0' " +
                        "BEGIN INSERT INTO sync_log (tbl, row_id) VALUES ('${t.table}', CAST($row.${t.pk} AS TEXT)); END"
                )
            }
        }
    }

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
    fun insertWeightIfAbsent(date: String, kg: Double): Boolean {
        val values = ContentValues().apply {
            put("date", date)
            put("weight", kg)
        }
        return writableDatabase.insertWithOnConflict("weight_log", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun deleteWeights(dates: Collection<String>): Int {
        if (dates.isEmpty()) return 0
        val placeholders = dates.joinToString(",") { "?" }
        return writableDatabase.delete("weight_log", "date IN ($placeholders)", dates.toTypedArray())
    }

    // --- Food log ---

    fun insertFood(entry: FoodEntry): Long = writableDatabase.insert("food_log", null, entry.toValues())

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

    fun insertActivity(entry: ExtraActivity): Long {
        val values = ContentValues().apply {
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

    private fun FoodEntry.toValues() = ContentValues().apply {
        put("date", date.toString())
        put("meal", meal.name)
        put("name", name)
        put("calories", calories)
        if (proteinG != null) put("protein", proteinG) else putNull("protein")
        if (carbsG != null) put("carbs", carbsG) else putNull("carbs")
        if (fatG != null) put("fat", fatG) else putNull("fat")
    }

    private fun Cursor.toFoodEntry(): FoodEntry? = runCatching {
        fun optional(column: String): Double? = getColumnIndexOrThrow(column).let { if (isNull(it)) null else getDouble(it) }
        FoodEntry(
            id = getLong(getColumnIndexOrThrow("id")),
            date = java.time.LocalDate.parse(getString(getColumnIndexOrThrow("date"))),
            meal = Meal.valueOf(getString(getColumnIndexOrThrow("meal"))),
            name = getString(getColumnIndexOrThrow("name")),
            calories = getInt(getColumnIndexOrThrow("calories")),
            proteinG = optional("protein"),
            carbsG = optional("carbs"),
            fatG = optional("fat"),
        )
    }.getOrNull()

    // --- Plans ---

    fun createPlan(plan: Plan): Long = writableDatabase.insert("plans", null, plan.toValues())

    fun readAllPlans(): List<Plan> =
        readableDatabase.query("plans", null, null, null, null, null, null).use { c ->
            buildList { while (c.moveToNext()) add(c.toPlan()) }
        }

    fun getPlan(id: Long): Plan? =
        readableDatabase.query("plans", null, "planId = ?", arrayOf(id.toString()), null, null, null)
            .use { c -> if (c.moveToFirst()) c.toPlan() else null }

    fun updatePlan(plan: Plan): Int =
        writableDatabase.update("plans", plan.toValues(), "planId = ?", arrayOf(plan.planId.toString()))

    fun deletePlan(id: Long): Int {
        val db = writableDatabase
        // Also delete exercises associated with this plan
        db.delete("exercises", "planId = ?", arrayOf(id.toString()))
        return db.delete("plans", "planId = ?", arrayOf(id.toString()))
    }

    // --- Exercises ---

    fun createExercise(exercise: Exercise): Long {
        val db = writableDatabase
        val values = exercise.toValues()
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
        val weight = if (logged.isEmpty()) exercise.weight else (logged.lastOrNull { it.weight > 0 } ?: logged.first()).weight.toString()
        val reps = if (logged.isEmpty()) exercise.reps else (logged.lastOrNull { it.reps > 0 } ?: logged.first()).reps.toString()
        val values = ContentValues().apply {
            put("name", exercise.name)
            put("weight", weight)
            put("sets", if (logged.isEmpty()) exercise.sets else logged.size.toString())
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
    fun saveSession(session: WorkoutSession, replaceId: Long? = null): Long {
        val values = ContentValues().apply {
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

    /** Deletes every saved workout except those in [keepIds]. */
    fun deleteSessionsExcept(keepIds: Collection<Long>): Int {
        if (keepIds.isEmpty()) return writableDatabase.delete("workout_sessions", null, null)
        val placeholders = keepIds.joinToString(",") { "?" }
        return writableDatabase.delete("workout_sessions", "id NOT IN ($placeholders)", keepIds.map { it.toString() }.toTypedArray())
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

    // --- Row mapping ---

    private fun Plan.toValues() = ContentValues().apply {
        planId?.let { put("planId", it) }
        put("planName", planName)
        put("planDay", planDay)
    }

    private fun Cursor.toPlan() = Plan(
        planId = getLong(getColumnIndexOrThrow("planId")),
        planName = getStringOrEmpty("planName"),
        planDay = getStringOrEmpty("planDay"),
    )

    private fun Exercise.toValues() = ContentValues().apply {
        id?.let { put("id", it) }
        put("planId", planId)
        put("name", name)
        put("weight", weight)
        put("sets", sets)
        put("reps", reps)
        put("notes", notes)
        put("dateTime", dateTime)
        put("isCheck", if (isCheck) 1 else 0)
        put("isExtra", if (isExtra) 1 else 0)
        put("vanishEndOfDay", if (vanishEndOfDay) 1 else 0)
        put("restTime", restTime)
        put("setsData", setsList.toJsonString())
        if (sortOrder >= 0) put("sortOrder", sortOrder)
    }

    private fun Cursor.toExercise() = Exercise(
        id = getLong(getColumnIndexOrThrow("id")),
        planId = getColumnIndexOrThrow("planId").let { if (isNull(it)) -1L else getLong(it) },
        name = getStringOrEmpty("name"),
        weight = getStringOrEmpty("weight", "0"),
        sets = getStringOrEmpty("sets", "0"),
        reps = getStringOrEmpty("reps", "0"),
        notes = getStringOrEmpty("notes"),
        dateTime = getStringOrEmpty("dateTime"),
        isCheck = getIntOrZero("isCheck") == 1,
        isExtra = getIntOrZero("isExtra") == 1,
        vanishEndOfDay = getIntOrZero("vanishEndOfDay") == 1,
        restTime = getStringOrEmpty("restTime"),
        setsList = parseExerciseSets(getStringOrEmpty("setsData")),
        sortOrder = getIntOrZero("sortOrder"),
    )

    private fun Cursor.toSession() = WorkoutSession(
        id = getLong(getColumnIndexOrThrow("id")),
        planName = getStringOrEmpty("planName"),
        date = LocalDateTime.parse(getStringOrEmpty("date")),
        totalTime = getStringOrEmpty("totalTime"),
        exercises = WorkoutSession.exercisesFromJson(getStringOrEmpty("exercises")),
    )

    companion object {
        /** The database file for [uid]: every account keeps its own. */
        fun fileNameFor(uid: String) = "strivo_${uid.filter { it.isLetterOrDigit() || it == '_' }}.db"

        private const val CREATE_WEIGHT_LOG = "CREATE TABLE IF NOT EXISTS weight_log (date TEXT PRIMARY KEY, weight REAL NOT NULL)"
        private const val CREATE_ACTIVITY_LOG = """
            CREATE TABLE IF NOT EXISTS activity_log (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              date TEXT NOT NULL,
              name TEXT NOT NULL,
              calories INTEGER NOT NULL,
              minutes INTEGER
            )"""
        private const val CREATE_FOOD_LOG = """
            CREATE TABLE IF NOT EXISTS food_log (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              date TEXT NOT NULL,
              meal TEXT NOT NULL,
              name TEXT NOT NULL,
              calories INTEGER NOT NULL,
              protein REAL,
              carbs REAL,
              fat REAL
            )"""
    }

    private fun Cursor.getStringOrEmpty(column: String, default: String = ""): String {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) default else getString(index)
    }

    private fun Cursor.getIntOrZero(column: String): Int {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) 0 else getInt(index)
    }
}

data class PersonalRecords(
    val maxWeight: Double = 0.0,
    val maxReps: Double = 0.0,
    val maxVolume: Double = 0.0,
)
