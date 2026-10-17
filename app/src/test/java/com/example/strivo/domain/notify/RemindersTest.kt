package com.example.strivo.domain.notify

import com.example.strivo.domain.model.Plan
import com.example.strivo.domain.model.WorkoutSession
import com.example.strivo.domain.plan.PlanAssignment
import com.example.strivo.domain.plan.PlanOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class RemindersTest {
    private val thursday = LocalDate.of(2026, 10, 15)

    private fun plan(id: Long, name: String, day: String = "Thursday") = Plan(planId = id, planName = name, planDay = day)
    private fun session(name: String, date: LocalDate = thursday) = WorkoutSession(id = 1, planName = name, date = date.atTime(9, 0), totalTime = "00:30:00", exercises = emptyList())

    private fun offer(vararg names: String) = PlanOffer(
        names.mapIndexed { i, n -> PlanAssignment("id$i", n, "Monday", emptyList(), "", "Coach Sam", 1L + i) },
    )

    private fun content(
        plans: List<Plan> = emptyList(),
        counts: Map<Long, Int> = emptyMap(),
        sessions: List<WorkoutSession> = emptyList(),
        offers: List<PlanOffer> = emptyList(),
        lastWeighIn: LocalDate? = thursday,
        restDays: Set<DayOfWeek> = emptySet(),
    ) = Reminders.content(thursday, restDays, plans, counts, sessions, offers, lastWeighIn)

    @Test
    fun aWorkoutLeftToDoIsTheReminder() {
        val text = content(plans = listOf(plan(1, "Leg Day"), plan(2, "Upper Body")), counts = mapOf(1L to 6, 2L to 5), sessions = listOf(session("Leg Day")))
        assertEquals("Time to train", text!!.title)
        assertEquals("Upper Body · 5 exercises", text.body)
    }

    @Test
    fun severalPlansAreNamedTogetherAndOnlyTheDaysPlansCount() {
        val text = content(
            plans = listOf(plan(1, "Leg Day"), plan(2, "Core"), plan(3, "Monday Plan", day = "Monday"), plan(4, "Extra exercises")),
            counts = mapOf(1L to 6, 2L to 1, 3L to 4, 4L to 2),
        )
        assertEquals("Leg Day + Core · 7 exercises", text!!.body)
    }

    @Test
    fun nothingIsSaidWhenEverythingIsDoneOrItIsARestDay() {
        assertNull(content(plans = listOf(plan(1, "Leg Day")), counts = mapOf(1L to 6), sessions = listOf(session("Leg Day"))))
        assertNull(content(plans = listOf(plan(1, "Leg Day")), counts = mapOf(1L to 6), restDays = setOf(DayOfWeek.THURSDAY)))
        assertNull(content(plans = listOf(plan(1, "Empty plan")), counts = mapOf(1L to 0)))
        // a workout done on another day does not count
        assertNotNull(content(plans = listOf(plan(1, "Leg Day")), counts = mapOf(1L to 6), sessions = listOf(session("Leg Day", thursday.minusDays(1)))))
    }

    @Test
    fun aPlanFromTheTrainerIsMentionedWithTheWorkoutOrOnItsOwn() {
        val with = content(plans = listOf(plan(1, "Leg Day")), counts = mapOf(1L to 6), offers = listOf(offer("Heavy Push")))
        assertEquals("Time to train", with!!.title)
        assertTrue(with.body.contains("Coach Sam sent you a plan"))

        val alone = content(offers = listOf(offer("Heavy Push", "Heavy Pull")), restDays = setOf(DayOfWeek.THURSDAY))
        assertEquals("A plan from your trainer is waiting", alone!!.title)
        assertTrue(alone.body.contains("Coach Sam sent you 2 plans"))
    }

    @Test
    fun anOldWeighInIsTheLastThingWorthANudge() {
        assertNull(content(lastWeighIn = thursday.minusDays(3)))
        assertEquals("It has been 8 days since your last weigh-in.", content(lastWeighIn = thursday.minusDays(8))!!.body)
        assertEquals("Log your weight", content(lastWeighIn = null)!!.title)
        // a workout to do comes before it
        assertEquals("Time to train", content(plans = listOf(plan(1, "Leg Day")), counts = mapOf(1L to 6), lastWeighIn = null)!!.title)
    }

    @Test
    fun theNextReminderIsAtTheChosenTimeTodayOrTomorrow() {
        val at = { h: Int, m: Int -> LocalDateTime.of(2026, 10, 15, h, m, 30) }
        assertEquals(60 * 60L - 30, Reminders.delayUntilNext(at(17, 0), 18, 0).seconds)
        assertEquals(23 * 3600L - 30, Reminders.delayUntilNext(at(19, 0), 18, 0).seconds)
        // just gone off (or about to): tomorrow, never a reminder in the next minute
        assertEquals(24 * 3600L - 30, Reminders.delayUntilNext(at(18, 0), 18, 0).seconds)
    }

    @Test
    fun settingsShowTheirTime() {
        assertEquals("18:00", NotifySettings().timeText)
        assertEquals("07:05", NotifySettings(hour = 7, minute = 5).timeText)
    }
}
