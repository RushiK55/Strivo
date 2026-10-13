package com.example.strivo.data.local.db

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
