package com.example.strivo.feature.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.AccentTitle
import com.example.strivo.core.ui.components.ActivitySheet
import com.example.strivo.core.ui.components.Card
import com.example.strivo.core.ui.components.StrivoAlertDialog
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.domain.analytics.AnalyticsReport
import com.example.strivo.domain.analytics.ExtraActivity

// --- Other activity: calories the user adds by hand ---

@Composable
internal fun OtherActivity(report: AnalyticsReport, weightKg: Double?, onAdd: (ExtraActivity) -> Unit, onDelete: (Long) -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ExtraActivity?>(null) }
    val entries = report.days.asReversed().flatMap { it.extras.asReversed() }

    Column {
        AccentTitle("Other activity")
        Spacer(Modifier.height(8.dp))
        Text(
            "Burned calories outside your tracked workouts: a run, a walk, a match. You can also add them from the Calories tab.",
            color = AppColors.TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        if (entries.isNotEmpty()) {
            Card {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                listOfNotNull(dayLabel(entry.date), entry.minutes?.let { "$it min" }).joinToString(" · "),
                                color = AppColors.TextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                        Text(kcal(entry.calories.toDouble()), color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IconButton(onClick = { toDelete = entry }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ${entry.name}", tint = AppColors.TextTertiary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        AccentButton(
            text = "Add activity",
            onClick = { showSheet = true },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Add,
        )
    }

    if (showSheet) {
        ActivitySheet(weightKg = weightKg, onDismiss = { showSheet = false }, onSave = { showSheet = false; onAdd(it) })
    }
    toDelete?.let { entry ->
        StrivoAlertDialog(
            title = "Delete activity?",
            onDismiss = { toDelete = null },
            confirmText = "Delete",
            confirmColor = AppColors.Danger,
            onConfirm = {
                toDelete = null
                entry.id?.let(onDelete)
            },
        ) {
            Text("Remove '${entry.name}' (${entry.calories} kcal)?", color = AppColors.TextSecondary)
        }
    }
}
