package com.example.strivo.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.data.sync.SyncStatus

/** The cloud backup state: what is waiting to upload, whether the phone is offline, or what went wrong. */
@Composable
internal fun SyncRow(status: SyncStatus, onClick: () -> Unit) {
    val (icon, title, detail) = when {
        status.isSyncing -> Triple(Icons.Rounded.CloudSync, "Syncing…", "Backing up to the cloud")
        status.error != null -> Triple(Icons.Rounded.CloudOff, "Cloud sync problem", status.error)
        status.pendingChanges > 0 && !status.isOnline -> Triple(
            Icons.Rounded.CloudOff,
            "Saved on this phone",
            "${status.pendingChanges} changes will upload when you are online",
        )
        status.pendingChanges > 0 -> Triple(Icons.Rounded.CloudSync, "${status.pendingChanges} changes waiting", "Tap to sync now")
        else -> Triple(
            Icons.Rounded.CloudDone,
            "Backed up to the cloud",
            status.lastSyncMillis?.let { "Last synced " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(it) } ?: "Tap to sync now",
        )
    }
    val tint = if (status.error != null) AppColors.Danger else AppColors.Success
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.Body, color = AppColors.TextPrimary)
            Text(detail, style = AppType.Caption, color = AppColors.TextSecondary)
        }
    }
}
