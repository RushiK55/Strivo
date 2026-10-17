package com.example.strivo.core.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.strivo.StrivoApp
import com.example.strivo.domain.notify.NotifySettings
import com.example.strivo.domain.notify.Reminders
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Background work for notifications, run by the system even when the app is closed (and kept across a restart of the phone):
 * a check every hour for plans the trainer sent, and the workout reminder once a day at the time the user chose.
 */
object NotificationWork {
    private const val CHECK = "plans-check"
    private const val REMINDER = "daily-reminder"

    /** The hourly check for plans from the trainer; it does nothing while nobody is signed in. */
    fun scheduleCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<PlansCheckWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(CHECK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Sets the daily reminder to the user's time, or takes it away when they turned it off. It is a one-time job for the next
     * time (a repeating job would wait a whole day before its first run), and each run sets up the next one.
     */
    fun scheduleReminder(context: Context, settings: NotifySettings, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val work = WorkManager.getInstance(context)
        if (!settings.reminders) {
            work.cancelUniqueWork(REMINDER)
            return
        }
        val delay = Reminders.delayUntilNext(LocalDateTime.now(), settings.hour, settings.minute)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS).build()
        work.enqueueUniqueWork(REMINDER, policy, request)
    }

    fun cancelReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(REMINDER)
    }
}

/** Reads the cloud for plans the trainer sent (the sync tells the user about each new one), so a plan arrives with the app closed. */
class PlansCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as StrivoApp
        if (!app.restoreActiveUser()) return Result.success()
        return if (app.sync.syncNowAndWait()) Result.success() else Result.retry()
    }
}

/** Once a day: says what is left to do today (see [Reminders.content]), or nothing when there is nothing to say. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as StrivoApp
        if (!app.restoreActiveUser()) return Result.success()
        val prefs = app.userPrefs
        val settings = prefs.getNotifySettings()
        // Tomorrow's reminder first, so a problem today never ends the daily reminders.
        NotificationWork.scheduleReminder(applicationContext, settings, ExistingWorkPolicy.APPEND_OR_REPLACE)
        if (!settings.reminders || !Notifier.canNotify(applicationContext)) return Result.success()
        val repository = app.repository
        val today = LocalDate.now()
        val plans = repository.readAllPlans().filter { !it.isPrebuilt }
        val counts = repository.readAllExercises().groupingBy { it.planId }.eachCount()
        val text = Reminders.content(
            today = today,
            restDays = prefs.getProfile().restDays,
            plans = plans,
            exerciseCounts = counts,
            sessions = repository.getWorkoutSessions(),
            offers = repository.planOffers(),
            lastWeighIn = repository.readWeightLog().maxOfOrNull { it.date },
        )
        text?.let { Notifier.reminder(applicationContext, it) }
        return Result.success()
    }
}
