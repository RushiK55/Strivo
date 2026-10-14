package com.example.strivo.domain.plan

import com.example.strivo.domain.activity.ActivityKind
import com.example.strivo.domain.activity.planAssigned
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlanAssignmentTest {
    private fun doc(vararg extra: Pair<String, Any?>): Map<String, Any?> = mapOf(
        "planName" to " Push Day ",
        "day" to "Monday",
        "status" to "sent",
        "note" to "Go light on the first set.",
        "sentByName" to "Coach Raj",
        "sentAt" to 1_700_000_000_000L,
        "exercises" to listOf(
            mapOf("name" to "Bench Press", "sets" to 4L, "reps" to 8L, "weightKg" to 60.5, "dropSets" to 1L, "notes" to "Pause on the chest"),
            mapOf("name" to "Push-up", "sets" to 3, "reps" to 15, "weightKg" to 0),
        ),
    ) + extra

    @Test
    fun aSentPlanIsReadWithItsExercisesInOrder() {
        val plan = PlanAssignment.fromMap("a1", doc())!!
        assertEquals("Push Day", plan.planName)
        assertEquals("Monday", plan.day)
        assertEquals("Coach Raj", plan.trainerName)
        assertEquals(listOf("Bench Press", "Push-up"), plan.exercises.map { it.name })
        val bench = plan.exercises.first()
        assertEquals("4", bench.sets)
        assertEquals("8", bench.reps)
        assertEquals("60.5", bench.weight)
        assertEquals(1, bench.dropSets)
        assertEquals("Pause on the chest", bench.notes)
        assertEquals("0.0", plan.exercises[1].weight)
    }

    @Test
    fun valuesOutsideTheUsualRangesAreClamped() {
        val plan = PlanAssignment.fromMap(
            "a1",
            doc("exercises" to listOf(mapOf("name" to "Squat", "sets" to 99, "reps" to 0, "weightKg" to 5000, "dropSets" to 40))),
        )!!
        val squat = plan.exercises.single()
        assertEquals("20", squat.sets)
        assertEquals("1", squat.reps)
        assertEquals("1000.0", squat.weight)
        assertEquals(5, squat.dropSets)
    }

    @Test
    fun onlyAPlanThatWasSentAndHasSomethingInItIsAdded() {
        assertNull(PlanAssignment.fromMap("a1", doc("status" to "delivered")))
        assertNull(PlanAssignment.fromMap("a1", doc("day" to "Someday")))
        assertNull(PlanAssignment.fromMap("a1", doc("planName" to "  ")))
        assertNull(PlanAssignment.fromMap("a1", doc("exercises" to listOf(mapOf("name" to "")))))
        assertNull(PlanAssignment.fromMap("a1", doc("exercises" to "not a list")))
        assertNotNull(PlanAssignment.fromMap("a1", doc("day" to "Prebuilt")))
    }

    @Test
    fun theActivityLineSaysWhoSentIt() {
        val event = planAssigned(PlanAssignment.fromMap("a1", doc())!!, at = 5L)
        assertEquals(ActivityKind.PlanAssigned, event.type)
        assertEquals("Got plan \"Push Day\" (Monday) from Coach Raj · 2 exercises", event.summary)
        val library = planAssigned(PlanAssignment.fromMap("a1", doc("day" to "Prebuilt", "sentByName" to ""))!!, at = 5L)
        assertEquals("Got plan \"Push Day\" (library) from their trainer · 2 exercises", library.summary)
    }

    @Test
    fun receivedPlansSurviveBeingStoredAndOnlyRecentOnesAreShown() {
        val day = 86_400_000L
        val plans = listOf(
            ReceivedPlan("old", 1, "Old", "Monday", "", "", receivedAt = 0),
            ReceivedPlan("new", 2, "New", "Friday", "Coach", "Note", receivedAt = 20 * day),
            ReceivedPlan("hidden", 3, "Hidden", "Friday", "", "", receivedAt = 20 * day, dismissed = true),
        )
        val restored = ReceivedPlan.listFromJson(ReceivedPlan.listToJson(plans))
        assertEquals(plans, restored)
        assertEquals(listOf("new"), restored.toShow(now = 21 * day).map { it.id })
        assertEquals(emptyList<ReceivedPlan>(), ReceivedPlan.listFromJson("not json"))
    }
}
