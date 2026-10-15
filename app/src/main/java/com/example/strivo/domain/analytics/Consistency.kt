package com.example.strivo.domain.analytics

import com.example.strivo.domain.model.WorkoutSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/*
 * How regularly the user trains: against their own weekly schedule, across the weekdays and the hours of the day.
 */

/** Planned workouts that were done, out of all the plans scheduled so far (a day with two plans counts two). */
data class Adherence(val done: Int, val scheduled: Int) {
    val percent: Double get() = if (scheduled == 0) 0.0 else done * 100.0 / scheduled
}

enum class TimeOfDay(val label: String) {
    Morning("Morning"),
    Afternoon("Afternoon"),
    Evening("Evening"),
}

data class ConsistencyReport(
    /** Planned workouts (from the weekly schedule, every plan of a day) that were done; null when nothing is scheduled. */
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
 * [scheduledPlans] is how many plans each weekday has (a weekday without a plan is left out). A day with two plans is two workouts to do,
 * and each workout saved that day covers one of them. Adherence only counts from the first workout the user saved, so the weeks before
 * they started using the app do not count as missed.
 */
fun buildConsistency(
    sessions: List<WorkoutSession>,
    scheduledPlans: Map<DayOfWeek, Int>,
    range: AnalyticsRange,
    today: LocalDate,
): ConsistencyReport {
    val start = today.minusDays(range.days - 1L)
    val inRange = sessions.filter { it.date.toLocalDate() in start..today }
    val trained = sessions.map { it.date.toLocalDate() }.toSet()

    val firstWorkout = sessions.minOfOrNull { it.date.toLocalDate() }
    val perDay = sessions.groupingBy { it.date.toLocalDate() }.eachCount()
    val adherence = if (scheduledPlans.isEmpty() || firstWorkout == null) null else {
        val from = maxOf(start, firstWorkout)
        var done = 0
        var scheduled = 0
        generateSequence(from) { it.plusDays(1) }.takeWhile { it <= today }.forEach { date ->
            val plans = scheduledPlans[date.dayOfWeek] ?: return@forEach
            val finished = minOf(plans, perDay[date] ?: 0)
            // Today only counts what is done, so an afternoon session not yet started is not a miss.
            scheduled += if (date < today) plans else finished
            done += finished
        }
        Adherence(done = done, scheduled = scheduled).takeIf { it.scheduled > 0 }
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
