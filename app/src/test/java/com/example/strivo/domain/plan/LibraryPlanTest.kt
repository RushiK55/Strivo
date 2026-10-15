package com.example.strivo.domain.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LibraryPlanTest {
    private fun doc(vararg extra: Pair<String, Any?>): Map<String, Any?> = mapOf(
        "name" to " Push Day ",
        "description" to "Chest, shoulders and triceps.",
        "level" to "Beginner",
        "active" to true,
        "updatedAt" to 1_700_000_000_000L,
        "exercises" to listOf(
            mapOf("name" to "Bench Press", "sets" to 4L, "reps" to 8L, "weightKg" to 40.0, "dropSets" to 0L, "notes" to "Pause"),
            mapOf("name" to "Push-Up", "sets" to 3, "reps" to 15),
        ),
    ) + extra

    @Test
    fun aPlanIsReadWithItsExercisesInOrder() {
        val plan = LibraryPlan.fromMap("push-day", doc())!!
        assertEquals("Push Day", plan.name)
        assertEquals("Beginner", plan.level)
        assertEquals(listOf("Bench Press", "Push-Up"), plan.exercises.map { it.name })
        assertEquals("4", plan.exercises.first().sets)
        assertEquals("40.0", plan.exercises.first().weight)
        assertEquals("library:push-day", plan.origin)
    }

    @Test
    fun aPlanWithoutANameOrWithoutExercisesIsSkipped() {
        assertNull(LibraryPlan.fromMap("a", doc("name" to " ")))
        assertNull(LibraryPlan.fromMap("a", doc("exercises" to emptyList<Any>())))
        assertNull(LibraryPlan.fromMap("a", doc("exercises" to listOf(mapOf("name" to "")))))
        assertNull(LibraryPlan.fromMap("", doc()))
    }

    @Test
    fun valuesAreClampedAndAHiddenPlanStaysHidden() {
        val plan = LibraryPlan.fromMap("a", doc("active" to false, "exercises" to listOf(mapOf("name" to "Squat", "sets" to 99, "reps" to 0, "weightKg" to 5000))))!!
        assertFalse(plan.active)
        assertEquals("20", plan.exercises.single().sets)
        assertEquals("1", plan.exercises.single().reps)
        assertEquals("1000.0", plan.exercises.single().weight)
    }

    @Test
    fun aPlanSurvivesBeingStoredOnThePhone() {
        val plan = LibraryPlan.fromMap("push-day", doc())!!
        val back = LibraryPlan.exercisesFromJson(plan.exercisesJson())
        assertEquals(plan.exercises.map { it.name to it.reps }, back.map { it.name to it.reps })
        val listed = LibraryPlan.listFromJson("[" + plan.toJson() + "]")
        assertEquals(1, listed.size)
        assertEquals(plan.name, listed.single().name)
    }

    @Test
    fun theExercisesBecomeAPlanOfTheUsersWithFreshIds() {
        val plan = LibraryPlan.fromMap("push-day", doc())!!
        val exercises = plan.exercisesFor(planId = 12)
        assertTrue(exercises.all { it.planId == 12L && it.id == null })
    }

    @Test
    fun theStarterPlansThatShipWithTheAppAreAllValidAndUseLibraryExercises() {
        val seed = listOf("../admin/seed/plan_library.json", "admin/seed/plan_library.json").map { File(it) }.first { it.exists() }
        val names = Regex("\"name\": \"([^\"]+)\"").findAll(File(seed.parentFile, "library.json").readText()).map { it.groupValues[1] }.toSet()
        val plans = LibraryPlan.listFromJson(seed.readText())
        assertTrue("expected several starter plans", plans.size >= 6)
        assertEquals("ids must be unique", plans.size, plans.map { it.id }.toSet().size)
        for (plan in plans) {
            assertTrue("${plan.name} has exercises", plan.exercises.size in 3..LibraryPlan.MAX_EXERCISES)
            for (exercise in plan.exercises) assertTrue("${exercise.name} is in the exercise library", exercise.name in names)
        }
    }
}
