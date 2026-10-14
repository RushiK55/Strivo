package com.example.strivo.data.local.db

import android.database.sqlite.SQLiteDatabase
import com.example.strivo.domain.activity.ActivityKind
import com.example.strivo.domain.activity.withoutFoodName

/**
 * The tables of the app database: how they are created, and how a database made by an older version is brought up to
 * date. The queries that use them are in [StrivoDatabase].
 */
internal object Schema {
    /** Bump when a table changes, and add a step for it to [upgrade]. */
    const val VERSION = 18

    /** The tables demo data writes to, which carry the isDemo mark. */
    val DemoTables = listOf("workout_sessions", "exercises", "plans", "food_log", "activity_log", "weight_log")

    private const val CREATE_WEIGHT_LOG = "CREATE TABLE IF NOT EXISTS weight_log (date TEXT PRIMARY KEY, weight REAL NOT NULL)"
    /** What the user did, in plain words. Backed up to the cloud, where the admin dashboard reads it. */
    private const val CREATE_ACTIVITY_EVENTS = """
        CREATE TABLE IF NOT EXISTS activity_events (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          at INTEGER NOT NULL,
          type TEXT NOT NULL,
          summary TEXT NOT NULL
        )"""

    /** The exercise library: a copy of what admins publish (or the starter library), read-only on the phone. */
    private const val CREATE_LIBRARY = """
        CREATE TABLE IF NOT EXISTS library_exercises (
          id TEXT PRIMARY KEY,
          name TEXT NOT NULL,
          muscleGroup TEXT NOT NULL,
          equipment TEXT NOT NULL,
          level TEXT NOT NULL,
          instructions TEXT NOT NULL,
          tips TEXT NOT NULL,
          defaultSets INTEGER NOT NULL,
          defaultReps INTEGER NOT NULL,
          defaultWeightKg REAL NOT NULL,
          videoUrl TEXT NOT NULL,
          active INTEGER NOT NULL,
          updatedAt INTEGER NOT NULL,
          primaryMuscles TEXT NOT NULL DEFAULT '',
          secondaryMuscles TEXT NOT NULL DEFAULT ''
        )"""

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

    /** Creates every table and the change log that sync reads. */
    fun create(db: SQLiteDatabase) {
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
        db.execSQL(CREATE_ACTIVITY_EVENTS)
        db.execSQL(CREATE_LIBRARY)
        addDemoColumns(db)
        addPlannedDropSets(db)
        createSyncObjects(db)
    }

    /** Brings a database made by an older version up to [VERSION], one step per version. */
    fun upgrade(db: SQLiteDatabase, oldVersion: Int) {
        if (oldVersion < 9) db.execSQL(CREATE_WEIGHT_LOG)
        if (oldVersion < 10) db.execSQL(CREATE_FOOD_LOG)
        if (oldVersion < 11) db.execSQL(CREATE_ACTIVITY_LOG)
        // Before the sync step below, which queues every synced table, this one included.
        if (oldVersion < 16) {
            db.execSQL(CREATE_ACTIVITY_EVENTS)
            db.execSQL(CREATE_LIBRARY)
        }
        // The library gained the muscles each exercise trains. A library made by version 16 or 17 has no such columns: add them
        // and empty the table, so the starter library (which has the muscles) is loaded again on the next start and the cloud's is
        // read again on the next sync. (An older one gets the new table above, which already has them.) Nothing of the user's is lost:
        // the library is only a copy of what the admins publish.
        if (oldVersion in 16..17) {
            db.execSQL("ALTER TABLE library_exercises ADD COLUMN primaryMuscles TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE library_exercises ADD COLUMN secondaryMuscles TEXT NOT NULL DEFAULT ''")
            db.execSQL("DELETE FROM library_exercises")
        }
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
        // Last, once every table above exists.
        if (oldVersion < 13) addDemoColumns(db)
        if (oldVersion < 14) addPlannedDropSets(db)
        // Plans and exercises carry the demo mark too, so demo plans can be removed with the rest of the demo data.
        if (oldVersion < 15) addDemoColumns(db)
        // The sync step above only ran for databases older than 12; this adds the triggers for the activity table.
        if (oldVersion in 12..15) createSyncObjects(db)
        // The activity lines an older version wrote with a food's name in them are rewritten without it (and uploaded again
        // by the sync, which sees the updates), because the trainer's dashboard may read those lines but not what was eaten.
        if (oldVersion < 17) removeFoodNamesFromEvents(db)
    }

    private fun removeFoodNamesFromEvents(db: SQLiteDatabase) {
        val rewrites = db.rawQuery("SELECT id, summary FROM activity_events WHERE type = ?", arrayOf(ActivityKind.FoodLogged)).use { c ->
            buildList { while (c.moveToNext()) withoutFoodName(c.getString(1))?.let { add(c.getLong(0) to it) } }
        }
        rewrites.forEach { (id, summary) -> db.execSQL("UPDATE activity_events SET summary = ? WHERE id = ?", arrayOf<Any>(summary, id)) }
    }

    /**
     * Marks the rows that demo data added (debug builds), so they can be told apart from the user's own anywhere. The
     * mark is part of the row, so it goes to the cloud with it and is still there after a reinstall or on a new phone.
     */
    private fun addDemoColumns(db: SQLiteDatabase) {
        DemoTables.forEach { addColumnIfMissing(db, it, "isDemo", "INTEGER NOT NULL DEFAULT 0") }
    }

    /** How many drop sets a plan adds after an exercise's working sets (see Exercise.dropSets). */
    private fun addPlannedDropSets(db: SQLiteDatabase) = addColumnIfMissing(db, "exercises", "dropSets", "INTEGER NOT NULL DEFAULT 0")

    private fun addColumnIfMissing(db: SQLiteDatabase, table: String, column: String, definition: String) {
        val has = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.any { it == column }
        }
        if (!has) db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
    }

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
}
