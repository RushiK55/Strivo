package com.example.strivo.domain.plan

import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.ExerciseSet
import com.example.strivo.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanMirrorTest {
    private fun bench(id: Long? = 1, planId: Long = 10, sets: List<ExerciseSet> = emptyList(), weight: String = "60.0", count: String = "3", reps: String = "10") =
        Exercise(id = id, planId = planId, name = "Bench Press", weight = weight, sets = count, reps = reps, notes = "", setsList = sets)

    private fun set(kg: Double, reps: Int, done: Boolean = false, type: SetType = SetType.Normal) =
        ExerciseSet(weight = kg, reps = reps, isCompleted = done, isStarted = done, restTime = if (done) "1:30" else "", type = type)

    @Test
    fun theSetsAPlanHoldsHaveNoProgressInThem() {
        val sets = bench(sets = listOf(set(60.0, 10, done = true), set(60.0, 10))).planSets()
        assertEquals(listOf(false, false), sets.map { it.isCompleted })
        assertEquals(listOf("", ""), sets.map { it.restTime })
    }

    @Test
    fun anExerciseWithNoSetsYetPlansTheOnesItsSummaryDescribes() {
        val sets = bench().planSets()
        assertEquals(3, sets.size)
        assertEquals(listOf(60.0, 60.0, 60.0), sets.map { it.weight })
    }

    @Test
    fun tickingOffASetIsNotAChangeToThePlan() {
        val plan = bench(id = 2, planId = 20, sets = listOf(set(60.0, 10), set(60.0, 10), set(60.0, 10)))
        val day = bench(sets = listOf(set(60.0, 10, done = true), set(60.0, 10), set(60.0, 10)))
        assertNull(plan.withPlanOf(day))
    }

    @Test
    fun aHeavierSetAndFewerRepsOnTheLastSetReachThePlanSetBySet() {
        val plan = bench(id = 2, planId = 20, sets = listOf(set(60.0, 10), set(60.0, 10), set(60.0, 10)))
        val day = bench(sets = listOf(set(60.0, 10, done = true), set(65.0, 10, done = true), set(60.0, 8, done = true)))
        val changed = plan.withPlanOf(day)
        assertNotNull(changed)
        changed!!
        assertEquals(listOf(60.0 to 10, 65.0 to 10, 60.0 to 8), changed.setsList.map { it.weight to it.reps })
        assertTrue(changed.setsList.none { it.isCompleted || it.isStarted })
        // the summary: three working sets, the first set's reps, the heaviest weight
        assertEquals("3", changed.sets)
        assertEquals("10", changed.reps)
        assertEquals("65.0", changed.weight)
        // what belongs to the plan itself is kept
        assertEquals(2L, changed.id)
        assertEquals(20L, changed.planId)
    }

    @Test
    fun theSummaryIgnoresWarmUpsAndCountsDropSets() {
        val sets = listOf(set(30.0, 12, type = SetType.WarmUp), set(60.0, 8), set(60.0, 8), set(55.0, 8, type = SetType.Drop))
        val (count, reps, kg) = summaryOf(sets)
        assertEquals("2", count)
        assertEquals("8", reps)
        assertEquals("60.0", kg)
        assertEquals(1, dropSetCount(sets))
    }

    @Test
    fun aRenamedExerciseAndItsNotesReachThePlan() {
        val plan = bench(id = 2, planId = 20)
        val day = bench().copy(name = "Flat Bench", notes = "Pause on the chest")
        val changed = plan.withPlanOf(day)!!
        assertEquals("Flat Bench", changed.name)
        assertEquals("Pause on the chest", changed.notes)
    }

    @Test
    fun aWeightWrittenWithoutADecimalIsTheSameWeight() {
        val plan = bench(id = 2, planId = 20, weight = "60")
        val day = bench(weight = "60.0")
        assertNull(plan.withPlanOf(day))
    }

    @Test
    fun aCopyForADayStartsFreshAndPointsBackAtThePlansExercise() {
        val plan = bench(id = 7, planId = 20, sets = listOf(set(60.0, 10, done = true), set(65.0, 8))).copy(isCheck = true, dateTime = "now", restTime = "1:00", sortOrder = 4)
        val copy = plan.freshCopyFor(planId = 30)
        assertNull(copy.id)
        assertEquals(30L, copy.planId)
        assertEquals(7L, copy.sourceId)
        assertFalse(copy.isCheck)
        assertEquals("", copy.dateTime)
        assertEquals(-1, copy.sortOrder)
        assertEquals(listOf(60.0 to 10, 65.0 to 8), copy.setsList.map { it.weight to it.reps })
        assertTrue(copy.setsList.none { it.isCompleted })
    }

    @Test
    fun onlyAnExerciseOfThePlanItselfIsKeptInThePlan() {
        assertTrue(bench().isPlanned)
        assertFalse(bench().copy(isExtra = true).isPlanned)
        assertFalse(bench().copy(vanishEndOfDay = true).isPlanned)
    }
}
