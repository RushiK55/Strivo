package com.example.strivo.data.analytics

import com.example.strivo.data.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class StreakTest {
    // October 2026: Fri 9, Sat 10, Sun 11, Mon 12, Tue 13.
    private val fri = LocalDate.of(2026, 10, 9)
    private val sat = LocalDate.of(2026, 10, 10)
    private val sun = LocalDate.of(2026, 10, 11)
    private val mon = LocalDate.of(2026, 10, 12)
    private val tue = LocalDate.of(2026, 10, 13)

    private fun trained(vararg days: LocalDate) = days.map {
        WorkoutSession(planName = "Plan", date = it.atTime(LocalTime.NOON), totalTime = "00:30:00", exercises = emptyList())
    }

    @Test
    fun anUntrainedSundayDoesNotBreakTheStreak() {
        // Fri, Sat, (Sunday off), Mon: one streak of 3.
        assertEquals(3, currentStreak(trained(fri, sat, mon), today = mon))
    }

    @Test
    fun aSundayWorkoutStillCounts() {
        assertEquals(4, currentStreak(trained(fri, sat, sun, mon), today = mon))
    }

    @Test
    fun aMissedWeekdayStillBreaksIt() {
        // Friday was missed, so only Sat and Mon count.
        assertEquals(2, currentStreak(trained(sat, mon), today = mon))
    }

    @Test
    fun todayBeingASundayWithNoWorkoutKeepsTheStreakAlive() {
        assertEquals(2, currentStreak(trained(fri, sat), today = sun))
    }

    @Test
    fun aWorkoutNotYetDoneTodayDoesNotBreakItEither() {
        // Monday evening, nothing yet: still a streak through Saturday, Friday.
        assertEquals(2, currentStreak(trained(fri, sat), today = mon))
        // Tuesday with nothing on Monday is a real miss.
        assertEquals(0, currentStreak(trained(fri, sat), today = tue))
    }

    @Test
    fun anotherRestDayCanBeChosen() {
        // With Saturday as the day off, Fri + Sun count across it.
        assertEquals(2, currentStreak(trained(fri, sun), today = sun, restDays = setOf(DayOfWeek.SATURDAY)))
    }

    @Test
    fun theReportUsesTheChosenRestDays() {
        // Fri and Sun trained, Saturday off: with Saturday chosen as the rest day the streak runs through it.
        val sessions = trained(fri, sun)
        assertEquals(2, buildReport(sessions, AnalyticsRange.Week, sun, restDays = setOf(DayOfWeek.SATURDAY)).streak)
        // Without it, the missed Saturday ends the streak after Sunday.
        assertEquals(1, buildReport(sessions, AnalyticsRange.Week, sun, restDays = emptySet()).streak)
    }

    @Test
    fun noWorkoutsMeansNoStreakAndTheLoopEnds() {
        assertEquals(0, currentStreak(emptyList(), today = mon))
        assertEquals(0, currentStreak(emptyList(), today = sun))
    }
}
