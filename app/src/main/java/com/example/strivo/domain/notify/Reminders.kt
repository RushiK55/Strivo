package com.example.strivo.domain.notify

import com.example.strivo.domain.model.EXTRA_PLAN_NAME
import com.example.strivo.domain.model.Plan
import com.example.strivo.domain.model.WorkoutSession
import com.example.strivo.domain.plan.PlanOffer
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * What the user wants to be told about. Plans from the trainer arrive as a notification when the app finds them; the daily reminder
 * comes at [hour]:[minute] and says what is left to do today. Both can be turned off in Profile.
 */
data class NotifySettings(
    val reminders: Boolean = true,
    val hour: Int = 18,
    val minute: Int = 0,
    val trainerPlans: Boolean = true,
) {
    /** "18:00" */
    val timeText: String get() = "%02d:%02d".format(hour, minute)
}

/** The words of one notification. */
data class ReminderText(val title: String, val body: String)

/**
 * The daily reminder, decided from what the app knows (pure, covered by RemindersTest). A workout still to do today comes first; a
 * plan from the trainer waiting for an answer is added to it, or is the reminder on its own on a rest day; a weigh-in that is a week
 * old is the last thing worth a nudge. Nothing to say means no notification, so the reminder never nags for the sake of it.
 */
object Reminders {
    /** Days without a weigh-in before the reminder asks for one. */
    const val WEIGH_IN_AFTER_DAYS = 7

    /** How long until the next [hour]:[minute], at least a minute away (a reminder that has just gone off is set for tomorrow). */
    fun delayUntilNext(now: LocalDateTime, hour: Int, minute: Int): Duration {
        var next = now.truncatedTo(ChronoUnit.MINUTES).withHour(hour.coerceIn(0, 23)).withMinute(minute.coerceIn(0, 59))
        if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    fun content(
        today: LocalDate,
        restDays: Set<DayOfWeek>,
        plans: List<Plan>,
        exerciseCounts: Map<Long, Int>,
        sessions: List<WorkoutSession>,
        offers: List<PlanOffer>,
        lastWeighIn: LocalDate?,
    ): ReminderText? {
        val offerLine = offers.firstOrNull()?.let { "${it.title}: tap to see it." }
        val isRestDay = today.dayOfWeek in restDays
        val done = sessions.filter { it.date.toLocalDate() == today }.map { it.planName }.toSet()
        val todo = if (isRestDay) emptyList() else plans.filter { plan ->
            plan.planDay == today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH) && plan.planName != EXTRA_PLAN_NAME && plan.planName !in done &&
                (exerciseCounts[plan.planId] ?: 0) > 0
        }
        if (todo.isNotEmpty()) {
            val count = todo.sumOf { exerciseCounts[it.planId] ?: 0 }
            val names = todo.joinToString(" + ") { it.planName }
            val what = if (count == 1) "1 exercise" else "$count exercises"
            return ReminderText("Time to train", listOfNotNull("$names · $what", offerLine).joinToString("\n"))
        }
        if (offerLine != null) return ReminderText("A plan from your trainer is waiting", offerLine)
        val days = lastWeighIn?.let { ChronoUnit.DAYS.between(it, today).toInt() }
        if (days == null || days >= WEIGH_IN_AFTER_DAYS) {
            return ReminderText("Log your weight", if (days == null) "Weigh in to track how your body changes." else "It has been $days days since your last weigh-in.")
        }
        return null
    }
}
