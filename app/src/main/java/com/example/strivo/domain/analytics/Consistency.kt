package com.example.strivo.domain.analytics

import com.example.strivo.domain.model.WorkoutSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/*
 * How regularly the user trains: against their own weekly schedule, across the weekdays and the hours of the day.
 */

/** Scheduled training days that had a workout, out of all scheduled days so far. */
data class Adherence(val done: Int, val scheduled: Int) {
    val percent: Double get() = if (scheduled == 0) 0.0 else done * 100.0 / scheduled
}

enum class TimeOfDay(val label: String) {
    Morning("Morning"),
    Afternoon("Afternoon"),
    Evening("Evening"),
}

data class ConsistencyReport(
    /** Plan days (from the weekly schedule) that had a workout; null when nothing is scheduled. */
    val adherence: Adherence?,
    /** Workouts per weekday, Monday first. */
    val byWeekday: List<Int>,
    val byTimeOfDay: Map<TimeOfDay, Int>,
    /** Average over the weeks of the range that have started; at least one week. */
    val workoutsPerWeek: Double,
    /** Every day the user trained in the last [CalendarWeeks] weeks, for the calendar. */
    val trainedDays: Set<LocalDate>,
    val calendarStart: LocalDate,
)

/** How many weeks the training calendar shows. */
const val CalendarWeeks = 12

fun timeOfDay(hour: Int): TimeOfDay = when (hour) {
    in 4..11 -> TimeOfDay.Morning
    in 12..16 -> TimeOfDay.Afternoon
    else -> TimeOfDay.Evening
}

/**
 * [scheduledDays] are the weekdays that have a plan. Adherence only counts from the first workout the user
 * saved, so the weeks before they started using the app do not count as missed.
 */
fun buildConsistency(
    sessions: List<WorkoutSession>,
    scheduledDays: Set<DayOfWeek>,
    range: AnalyticsRange,
    today: LocalDate,
): ConsistencyReport {
    val start = today.minusDays(range.days - 1L)
    val inRange = sessions.filter { it.date.toLocalDate() in start..today }
    val trained = sessions.map { it.date.toLocalDate() }.toSet()

    val firstWorkout = sessions.minOfOrNull { it.date.toLocalDate() }
    val adherence = if (scheduledDays.isEmpty() || firstWorkout == null) null else {
        val from = maxOf(start, firstWorkout)
        // Today only counts once it is done, so an afternoon session not yet started is not a miss.
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= today }
            .filter { it.dayOfWeek in scheduledDays }
            .filter { it < today || it in trained }
            .toList()
        Adherence(done = days.count { it in trained }, scheduled = days.size).takeIf { it.scheduled > 0 }
    }

    val calendarStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(CalendarWeeks - 1L)
    val weeks = maxOf(1.0, range.days / 7.0)

    return ConsistencyReport(
        adherence = adherence,
        byWeekday = DayOfWeek.entries.map { day -> inRange.count { it.date.dayOfWeek == day } },
        byTimeOfDay = TimeOfDay.entries.associateWith { slot -> inRange.count { timeOfDay(it.date.hour) == slot } },
        workoutsPerWeek = inRange.size / weeks,
        trainedDays = trained.filter { it >= calendarStart }.toSet(),
        calendarStart = calendarStart,
    )
}
