package com.example.strivo.core.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.strivo.MainActivity
import com.example.strivo.R
import com.example.strivo.domain.notify.ReminderText
import com.example.strivo.domain.plan.PlanOffer

/**
 * The notifications Strivo shows: a plan from the trainer, and the daily workout reminder. Each kind has its own channel, so the user
 * can silence one in the phone's settings and keep the other. Nothing is shown unless the user allowed notifications.
 */
object Notifier {
    private const val CHANNEL_PLANS = "plans"
    private const val CHANNEL_REMINDERS = "reminders"
    private const val ID_PLANS = 1001
    private const val ID_REMINDER = 1002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PLANS, "Plans from your trainer", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "When your trainer sends you a plan" },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Workout reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "A daily reminder of what is left to do today" },
        )
    }

    /** Whether a notification can be shown: Android 13 and later need the user's yes, and the app's notifications must not be switched off. */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Tells the user a trainer sent them a plan (several plans sent together are one notification). */
    fun trainerPlans(context: Context, offers: List<PlanOffer>) {
        val offer = offers.firstOrNull() ?: return
        val more = offers.drop(1).sumOf { it.plans.size }
        val names = offer.names() + if (more > 0) " +$more more" else ""
        show(context, ID_PLANS, CHANNEL_PLANS, offer.title, "$names · tap to see it and accept or decline", NotificationCompat.PRIORITY_HIGH)
    }

    fun reminder(context: Context, text: ReminderText) =
        show(context, ID_REMINDER, CHANNEL_REMINDERS, text.title, text.body, NotificationCompat.PRIORITY_DEFAULT)

    private fun show(context: Context, id: Int, channel: String, title: String, body: String, priority: Int) {
        if (!canNotify(context)) return
        createChannels(context)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_strivo)
            .setContentTitle(title)
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        @Suppress("MissingPermission") // checked by canNotify above
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
