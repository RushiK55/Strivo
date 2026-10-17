package com.example.strivo.feature.profile

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.example.strivo.StrivoApp
import com.example.strivo.core.notify.NotificationWork
import com.example.strivo.core.notify.Notifier
import com.example.strivo.core.ui.components.AppCard
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.notify.NotifySettings

/**
 * What the app may tell the user about: the daily workout reminder (and when), and plans from the trainer. Turning one on asks for
 * the phone's permission to show notifications where it is needed (Android 13 and later), and a phone that has them switched off for
 * Strivo says so, with a way to switch them on.
 */
@Composable
internal fun NotificationsSection() {
    val context = LocalContext.current
    val app = context.applicationContext as StrivoApp
    var settings by remember { mutableStateOf(app.userPrefs.getNotifySettings()) }
    var allowed by remember { mutableStateOf(Notifier.canNotify(context)) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = Notifier.canNotify(context) }

    fun change(next: NotifySettings) {
        settings = next
        app.userPrefs.setNotifySettings(next)
        NotificationWork.scheduleReminder(context, next)
    }

    /** Asks for the permission the first time something is turned on, when the phone has not got it yet. */
    fun needPermission() {
        if (Notifier.canNotify(context)) return
        if (Build.VERSION.SDK_INT >= 33) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    SectionHeader("Notifications")
    AppCard(padding = PaddingValues(vertical = 4.dp)) {
        if (!allowed) {
            SettingsRow(Icons.Rounded.NotificationsOff, "Notifications are off", value = "Allow", tint = AppColors.Warning) {
                if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(context)) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                // If the phone will not ask again, its own settings are the way.
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setData(Uri.fromParts("package", context.packageName, null)),
                )
            }
            RowDivider()
        }
        SwitchRow(Icons.Rounded.Notifications, "Workout reminder", "A daily nudge when today's workout is still to do", settings.reminders) {
            if (it) needPermission()
            change(settings.copy(reminders = it))
        }
        if (settings.reminders) {
            RowDivider()
            SettingsRow(Icons.Rounded.Schedule, "Reminder time", value = settings.timeText) {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> change(settings.copy(hour = hour, minute = minute)) },
                    settings.hour,
                    settings.minute,
                    true,
                ).show()
            }
        }
        RowDivider()
        SwitchRow(Icons.Rounded.Person, "Plans from my trainer", "Tell me when a plan arrives", settings.trainerPlans) {
            if (it) needPermission()
            change(settings.copy(trainerPlans = it))
        }
    }
}

/** A row with an icon tile, a label with a line under it, and a switch. */
@Composable
private fun SwitchRow(icon: ImageVector, label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(AppColors.Accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, style = AppType.Body, color = AppColors.TextPrimary, maxLines = 1)
            Text(hint, style = AppType.Caption, color = AppColors.TextSecondary, maxLines = 2)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.Black,
                checkedTrackColor = AppColors.Accent,
                uncheckedThumbColor = AppColors.TextSecondary,
                uncheckedTrackColor = AppColors.SurfaceHigh,
                uncheckedBorderColor = AppColors.Divider,
            ),
        )
    }
}
