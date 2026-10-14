package com.example.strivo.domain.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LibraryExerciseTest {
    @Test
    fun aLibraryDocumentFromTheCloudIsReadWhateverNumberTypeItHas() {
        val exercise = LibraryExercise.fromMap(
            "bench-press",
            mapOf(
                "name" to " Bench Press ", "muscleGroup" to "Chest", "equipment" to "Barbell", "level" to "Beginner",
                "instructions" to "Lie down and press.", "tips" to "Keep your wrists straight.",
                "defaultSets" to 4L, "defaultReps" to 8.0, "defaultWeightKg" to 40, "active" to true, "updatedAt" to 1_700_000_000_000L,
            ),
        )!!
        assertEquals("Bench Press", exercise.name)
        assertEquals(4, exercise.defaultSets)
        assertEquals(8, exercise.defaultReps)
        assertEquals(40.0, exercise.defaultWeightKg, 0.0)
        assertEquals(1_700_000_000_000L, exercise.updatedAt)
    }

    @Test
    fun anUnreadableDocumentIsSkippedAndSillyNumbersAreKeptInRange() {
        assertNull(LibraryExercise.fromMap("x", mapOf("muscleGroup" to "Chest")))
        assertNull(LibraryExercise.fromMap("", mapOf("name" to "No id")))
        val clamped = LibraryExercise.fromMap("y", mapOf("name" to "Odd", "defaultSets" to 500, "defaultReps" to -3, "defaultWeightKg" to 99999))!!
        assertEquals(20, clamped.defaultSets)
        assertEquals(1, clamped.defaultReps)
        assertEquals(1000.0, clamped.defaultWeightKg, 0.0)
        assertEquals("Other", clamped.muscleGroup)
        assertTrue(clamped.active) // visible unless an admin hides it
    }

    @Test
    fun aListKeepsTheGoodEntriesAndDropsTheBadOnes() {
        val list = LibraryExercise.listFromJson("""[{"id":"a","name":"A"},{"id":"","name":"No id"},{"id":"b","name":"B","active":false}]""")
        assertEquals(listOf("a", "b"), list.map { it.id })
        assertFalse(list[1].active)
        assertTrue(LibraryExercise.listFromJson("not json").isEmpty())
    }

    @Test
    fun anExerciseSurvivesBeingSavedAsJson() {
        val original = LibraryExercise("squat", "Squat", "Legs", "Barbell", "Intermediate", "Sit down and stand up.", "Chest up.", 4, 8, 50.0, "", true, 5)
        assertEquals(original, LibraryExercise.fromJson(JSONObject(original.toJson().toString())))
    }

    @Test
    fun addingToAPlanUsesTheSuggestedSetsRepsAndWeight() {
        val exercise = LibraryExercise("squat", "Squat", "Legs", "Barbell", "Intermediate", "x", "Chest up.", 4, 8, 50.0, "", true).toPlanExercise(planId = 7)
        assertEquals(7L, exercise.planId)
        assertEquals("Squat", exercise.name)
        assertEquals("4", exercise.sets)
        assertEquals("8", exercise.reps)
        assertEquals(50.0, exercise.weight.toDouble(), 0.0)
        assertEquals("Chest up.", exercise.notes)
    }

    // --- The starter library that ships in the app and that the admin dashboard imports ---

    private val seed: List<LibraryExercise> by lazy {
        val file = File("../admin/seed/library.json")
        check(file.isFile) { "Run from the app module: ${file.absolutePath} not found" }
        LibraryExercise.listFromJson(file.readText())
    }

    @Test
    fun theStarterLibraryIsReadableAndHasNoDuplicates() {
        assertTrue("expected a real library, got ${seed.size}", seed.size >= 60)
        assertEquals("ids must be unique", seed.size, seed.map { it.id }.toSet().size)
        assertEquals("names must be unique", seed.size, seed.map { it.name.lowercase() }.toSet().size)
    }

    @Test
    fun everyStarterExerciseIsCompleteAndInAKnownGroup() {
        seed.forEach { e ->
            assertTrue("${e.name}: unknown muscle group ${e.muscleGroup}", e.muscleGroup in LibraryMuscleGroups)
            assertTrue("${e.name}: instructions too short", e.instructions.length >= 30)
            assertTrue("${e.name}: needs a tip", e.tips.isNotEmpty())
            assertTrue("${e.name}: needs equipment", e.equipment.isNotEmpty())
            assertTrue("${e.name}: level", e.level in listOf("Beginner", "Intermediate", "Advanced"))
            assertTrue("${e.name}: sets", e.defaultSets in 1..20)
            assertTrue("${e.name}: reps", e.defaultReps in 1..100)
            assertTrue("${e.name}: id must be a slug", Regex("^[a-z0-9]+(-[a-z0-9]+)*$").matches(e.id))
            assertTrue("${e.name}: active", e.active)
        }
    }

    @Test
    fun everyMuscleGroupHasSomethingToChooseFrom() {
        LibraryMuscleGroups.forEach { group ->
            assertTrue("$group has too few exercises", seed.count { it.muscleGroup == group } >= 3)
        }
        assertNotNull(seed.firstOrNull { it.name == "Bench Press" })
    }

    // --- The muscles an exercise trains ---

    @Test
    fun theMusclesAnExerciseTrainsAreReadFromTheCloudAndCleaned() {
        val exercise = LibraryExercise.fromMap(
            "row",
            mapOf(
                "name" to "Row",
                "primaryMuscles" to listOf("lats", " mid-back ", "lats", "Not Valid", 5L, "delt-rear"),
                "secondaryMuscles" to listOf("biceps", "x1", "forearm-flexors"),
            ),
        )!!
        assertEquals(listOf("lats", "mid-back", "delt-rear"), exercise.primaryMuscles)
        assertEquals(listOf("biceps", "forearm-flexors"), exercise.secondaryMuscles)
        val none = LibraryExercise.fromMap("plank", mapOf("name" to "Plank"))!!
        assertTrue(none.primaryMuscles.isEmpty() && none.secondaryMuscles.isEmpty())
        val tooMany = LibraryExercise.fromMap("x", mapOf("name" to "X", "primaryMuscles" to ('a'..'z').map { "m$it" }))!!
        assertEquals(LibraryExercise.MAX_MUSCLES, tooMany.primaryMuscles.size)
    }

    @Test
    fun theMusclesSurviveJsonAndTheDatabaseText() {
        val original = LibraryExercise("row", "Row", "Back", "", "", "", "", 3, 10, 0.0, "", true, 0, listOf("lats", "mid-back"), listOf("biceps"))
        assertEquals(original, LibraryExercise.fromJson(JSONObject(original.toJson().toString())))
        assertEquals("lats,mid-back", LibraryExercise.musclesToText(original.primaryMuscles))
        assertEquals(listOf("lats", "mid-back"), LibraryExercise.musclesFromText("lats,mid-back"))
        assertTrue(LibraryExercise.musclesFromText("").isEmpty())
        assertTrue(LibraryExercise.musclesFromText(null).isEmpty())
        assertEquals(listOf("lats"), LibraryExercise.musclesFromText(",lats,,Bad,"))
    }

    @Test
    fun everyStarterExerciseSaysWhichMusclesItTrains() {
        assertTrue(seed.isNotEmpty())
        for (exercise in seed) {
            assertTrue("${exercise.name} has no main muscles", exercise.primaryMuscles.isNotEmpty())
            assertTrue("${exercise.name} lists a muscle twice", (exercise.primaryMuscles + exercise.secondaryMuscles).let { it.size == it.distinct().size })
        }
    }
}
