package com.example.strivo.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.strivo.StrivoApp
import com.example.strivo.core.notify.Notifier
import com.example.strivo.core.ui.components.StrivoAlertDialog
import com.example.strivo.core.ui.theme.AppColors

/**
 * Once, the first time the user reaches Home: explains what the notifications are for (their trainer's plans, the daily workout
 * reminder) before the phone's own question, so the yes is an informed one. Asked once only; Profile has the switches.
 */
@Composable
internal fun AskToAllowNotifications() {
    val context = LocalContext.current
    val prefs = (context.applicationContext as StrivoApp).userPrefs
    var show by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !prefs.askedNotifications && !Notifier.canNotify(context)) show = true
    }
    if (!show) return

    StrivoAlertDialog(
        title = "Stay on track",
        onDismiss = { prefs.askedNotifications = true; show = false },
        confirmText = "Allow",
        dismissText = "Not now",
        onConfirm = {
            prefs.askedNotifications = true
            show = false
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
    ) {
        Text(
            "Strivo can remind you of today's workout and tell you as soon as your trainer sends you a plan. You can change this any time in Profile.",
            color = AppColors.TextSecondary,
        )
    }
}
