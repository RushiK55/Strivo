package com.example.strivo.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlannerTest {
    @Test
    fun manyEditsToOneRowBecomeOneUpload() {
        val changes = listOf(
            Change(1, "exercises", "5"),
            Change(2, "exercises", "5"),
            Change(3, "plans", "5"),
            Change(4, "exercises", "6"),
            Change(5, "exercises", "5"),
        )
        val collapsed = SyncPlanner.collapse(changes)
        assertEquals(3, collapsed.size)
        assertEquals(listOf(1L, 2L, 5L), collapsed.getValue("exercises" to "5"))
        // Same id in a different table is a different row.
        assertEquals(listOf(3L), collapsed.getValue("plans" to "5"))
    }

    @Test
    fun cloudRowsAreRestoredAndLocalOnlyRowsAreDeleted() {
        val plan = SyncPlanner.planPull(remote = setOf("1", "2", "3"), local = setOf("2", "3", "4"), pending = emptySet())
        assertEquals(setOf("1", "2", "3"), plan.upsert)
        assertEquals(setOf("4"), plan.deleteLocal) // deleted on another device
        assertTrue(plan.requeue.isEmpty())
    }

    @Test
    fun aChangeWaitingToUploadIsNeverOverwrittenOrDeleted() {
        val plan = SyncPlanner.planPull(
            remote = setOf("1", "2"),
            local = setOf("2", "4"),
            pending = setOf("2", "4", "9"), // 2 edited here, 4 created here, 9 deleted here
        )
        assertEquals(setOf("1"), plan.upsert) // 2 keeps the local version, 9 is not brought back
        assertTrue(plan.deleteLocal.isEmpty()) // 4 is new here and has not been uploaded yet
    }

    @Test
    fun anEmptyCloudIsTreatedAsMissingDataNotAsEverythingDeleted() {
        val plan = SyncPlanner.planPull(remote = emptySet(), local = setOf("1", "2", "3"), pending = setOf("3"))
        assertTrue(plan.deleteLocal.isEmpty())
        assertTrue(plan.upsert.isEmpty())
        assertEquals(setOf("1", "2"), plan.requeue) // upload what the cloud lacks; 3 is already queued
    }

    @Test
    fun aNewDeviceRestoresEverything() {
        val plan = SyncPlanner.planPull(remote = setOf("1", "2"), local = emptySet(), pending = emptySet())
        assertEquals(setOf("1", "2"), plan.upsert)
        assertTrue(plan.deleteLocal.isEmpty() && plan.requeue.isEmpty())
    }

    @Test
    fun nothingToDoWhenBothSidesAreEmpty() {
        val plan = SyncPlanner.planPull(emptySet(), emptySet(), emptySet())
        assertTrue(plan.upsert.isEmpty() && plan.deleteLocal.isEmpty() && plan.requeue.isEmpty())
    }

    @Test
    fun everySyncedTableHasAUniqueCollection() {
        assertEquals(SyncTables.size, SyncTables.map { it.collection }.toSet().size)
        assertEquals(SyncTables.size, SyncTables.map { it.table }.toSet().size)
    }
}
