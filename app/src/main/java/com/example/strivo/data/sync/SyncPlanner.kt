package com.example.strivo.data.sync

/** A table that is kept in the cloud: its local name, its primary key column and its Firestore collection. */
data class SyncTable(val table: String, val pk: String, val collection: String)

val SyncTables = listOf(
    SyncTable("plans", "planId", "plans"),
    SyncTable("exercises", "id", "exercises"),
    SyncTable("workout_sessions", "id", "sessions"),
    SyncTable("history", "historyId", "history"),
    SyncTable("food_log", "id", "food"),
    SyncTable("activity_log", "id", "activities"),
    SyncTable("weight_log", "date", "weights"),
)

/** One entry of the local change log: some row of some table was inserted, updated or deleted. */
data class Change(val logId: Long, val table: String, val rowId: String)

/**
 * The decisions behind syncing, with no Android or Firebase in them so they can be tested.
 * The rule that runs through all of it: a change made on this phone and not yet uploaded always wins.
 */
object SyncPlanner {

    /** Many edits to one row become one upload: returns every log id for each (table, row). */
    fun collapse(changes: List<Change>): Map<Pair<String, String>, List<Long>> =
        changes.groupBy({ it.table to it.rowId }, { it.logId })

    data class PullPlan(
        /** Remote rows to write into the local database. */
        val upsert: Set<String>,
        /** Local rows that no longer exist in the cloud (deleted elsewhere) and have nothing waiting to upload. */
        val deleteLocal: Set<String>,
        /** Local rows to upload again because the cloud has none of this table's data. */
        val requeue: Set<String>,
    )

    /**
     * Compares one table's cloud ids with its local ids.
     * [pending] are the rows with a local change still waiting to be uploaded; they are never overwritten or deleted.
     */
    fun planPull(remote: Set<String>, local: Set<String>, pending: Set<String>): PullPlan {
        // An empty cloud next to local rows means the cloud is missing the data (new project, wiped collection),
        // not that every row was deleted: keep the local rows and upload them.
        if (remote.isEmpty() && local.isNotEmpty()) {
            return PullPlan(upsert = emptySet(), deleteLocal = emptySet(), requeue = local - pending)
        }
        return PullPlan(
            upsert = remote - pending,
            deleteLocal = local - remote - pending,
            requeue = emptySet(),
        )
    }
}
