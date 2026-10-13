package com.example.strivo.domain.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetTypeTest {
    @Test
    fun setsSavedBeforeSetTypesExistedAreNormal() {
        val old = JSONObject("""{"weight":60.0,"reps":8,"isCompleted":true,"isStarted":false,"restTime":"","setDuration":""}""")
        assertEquals(SetType.Normal, ExerciseSet.fromJson(old).type)
        assertEquals(SetType.Normal, SetType.fromKey("something else"))
        assertEquals(SetType.Normal, SetType.fromKey(null))
    }

    @Test
    fun theTypeSurvivesBeingSavedAndLoaded() {
        SetType.entries.forEach { type ->
            val set = ExerciseSet(weight = 50.0, reps = 8, type = type)
            assertEquals(type, ExerciseSet.fromJson(set.toJson()).type)
            assertEquals(type, parseExerciseSets(listOf(set).toJsonString()).single().type)
        }
        // A normal set is stored exactly as before, so nothing grows or changes for existing data.
        assertFalse(ExerciseSet(50.0, 8).toJson().has("type"))
    }

    @Test
    fun warmUpsAndDropsAreNotNumbered() {
        val types = listOf(SetType.WarmUp, SetType.Normal, SetType.Normal, SetType.Drop, SetType.Normal, SetType.Failure)
        val labels = types.map { ExerciseSet(10.0, 5, type = it) }.setLabels()
        assertEquals(listOf("W", "1", "2", "D", "3", "F"), labels)
    }

    @Test
    fun onlyWarmUpsAreLeftOutOfWork() {
        assertFalse(ExerciseSet(20.0, 10, type = SetType.WarmUp).isWork)
        assertTrue(ExerciseSet(20.0, 10, type = SetType.Drop).isWork)
        // Records come from normal and failure sets only.
        assertTrue(ExerciseSet(60.0, 8).countsForRecords)
        assertTrue(ExerciseSet(60.0, 8, type = SetType.Failure).countsForRecords)
        assertFalse(ExerciseSet(48.0, 10, type = SetType.Drop).countsForRecords)
        assertFalse(ExerciseSet(30.0, 10, type = SetType.WarmUp).countsForRecords)
    }

    @Test
    fun aDropSetIsFiveKgLighter() {
        assertEquals(95.0, suggestedDropKg(100.0), 0.0)
        assertEquals(75.0, suggestedDropKg(80.0), 0.0)
        assertEquals(57.5, suggestedDropKg(62.5), 0.0)
        assertEquals(17.0, suggestedDropKg(22.0), 0.0)
        assertEquals(3.0, suggestedDropKg(8.0), 0.0)
        assertEquals(0.0, suggestedDropKg(0.0), 0.0) // bodyweight stays bodyweight
        // Too light to lose 5 kg: lose 1 kg, but never reach 0.
        assertEquals(4.0, suggestedDropKg(5.0), 0.0)
        assertEquals(1.5, suggestedDropKg(2.5), 0.0)
        assertEquals(0.5, suggestedDropKg(1.0), 0.0)
        listOf(0.5, 1.0, 2.5, 5.0, 5.5, 9.5, 12.5, 42.5, 140.0).forEach { kg ->
            val drop = suggestedDropKg(kg)
            assertTrue("$kg -> $drop", drop < kg && drop > 0)
        }
    }

    @Test
    fun aWarmUpStartsAtAboutHalf() {
        assertEquals(30.0, suggestedWarmUpKg(60.0), 0.0)
        assertEquals(32.5, suggestedWarmUpKg(62.5), 0.0)
        assertEquals(0.0, suggestedWarmUpKg(0.0), 0.0)
        assertTrue(suggestedWarmUpKg(1.0) > 0)
    }

    @Test
    fun setsGoInOrder() {
        val sets = listOf(
            ExerciseSet(40.0, 10, isCompleted = true),
            ExerciseSet(40.0, 10),
            ExerciseSet(40.0, 10),
        )
        assertEquals(null, sets.blockingSetBefore(0))
        assertEquals(null, sets.blockingSetBefore(1)) // set 1 is done, so set 2 is next
        assertEquals(1, sets.blockingSetBefore(2)) // set 3 has to wait for set 2
        assertEquals(0, sets.map { it.copy(isCompleted = false) }.blockingSetBefore(2)) // nothing done yet: the first set is the one to do
    }

    private fun planned(sets: String, reps: String, kg: String, drops: Int) =
        Exercise(planId = 1, name = "Bench Press", weight = kg, sets = sets, reps = reps, notes = "", dropSets = drops).plannedSets()

    @Test
    fun aPlanWithDropSetsStartsTheWorkoutWithThem() {
        val sets = planned("3", "10", "40.0", drops = 2)
        assertEquals(listOf(40.0, 40.0, 40.0, 35.0, 30.0), sets.map { it.weight })
        assertEquals(listOf(SetType.Normal, SetType.Normal, SetType.Normal, SetType.Drop, SetType.Drop), sets.map { it.type })
        assertTrue(sets.all { it.reps == 10 && !it.isCompleted && !it.isStarted })
        assertEquals(listOf("1", "2", "3", "D", "D"), sets.setLabels())
    }

    @Test
    fun aPlanWithoutDropSetsIsUnchanged() {
        assertEquals(listOf(60.0, 60.0, 60.0, 60.0), planned("4", "8", "60.0", drops = 0).map { it.weight })
        // Bodyweight stays bodyweight, and silly values are kept in range.
        assertEquals(listOf(0.0, 0.0), planned("1", "15", "0.0", drops = 1).map { it.weight })
        assertEquals(1 + MaxPlannedDropSets, planned("1", "8", "100.0", drops = 99).size)
    }
}
