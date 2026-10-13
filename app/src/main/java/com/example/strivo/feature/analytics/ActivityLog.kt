package com.example.strivo.feature.analytics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.AccentTitle
import com.example.strivo.core.ui.components.Card
import com.example.strivo.core.ui.components.Chip
import com.example.strivo.core.ui.components.StrivoAlertDialog
import com.example.strivo.core.ui.components.SurfaceTextField
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.analytics.AnalyticsReport
import com.example.strivo.domain.analytics.ExtraActivity
import java.time.LocalDate

// --- Other activity: calories the user adds by hand ---

@Composable
internal fun OtherActivity(report: AnalyticsReport, onAdd: (ExtraActivity) -> Unit, onDelete: (Long) -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ExtraActivity?>(null) }
    val entries = report.days.asReversed().flatMap { it.extras.asReversed() }

    Column {
        AccentTitle("OTHER ACTIVITY")
        Spacer(Modifier.height(8.dp))
        Text(
            "Burned calories outside your tracked workouts: a run, a walk, a match. Enter the number from your watch or machine.",
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
            text = "ADD ACTIVITY",
            onClick = { showSheet = true },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Add,
        )
    }

    if (showSheet) {
        ActivitySheet(onDismiss = { showSheet = false }, onSave = { showSheet = false; onAdd(it) })
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivitySheet(onDismiss: () -> Unit, onSave: (ExtraActivity) -> Unit) {
    val today = LocalDate.now()
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Add activity", color = AppColors.TextPrimary, style = AppType.Title)
            // Names only, to save typing; no calorie values are suggested.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Walking", "Running", "Cycling", "Swimming", "Sports", "Yoga").forEach { option ->
                    Chip(option, selected = name == option) { name = option; error = null }
                }
            }
            SurfaceTextField("Activity", name, KeyboardType.Text) { name = it; error = null }
            SurfaceTextField("Calories burned (kcal)", calories, KeyboardType.Number) {
                calories = it.filter(Char::isDigit).take(5)
                error = null
            }
            SurfaceTextField("Minutes (optional)", minutes, KeyboardType.Number) {
                minutes = it.filter(Char::isDigit).take(4)
                error = null
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { date = date.minusDays(1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Earlier day", tint = AppColors.TextPrimary)
                }
                Text(
                    dayLabel(date),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { date = date.plusDays(1) }, enabled = date < today) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Later day",
                        tint = if (date < today) AppColors.TextPrimary else AppColors.TextSecondary.copy(alpha = 0.3f),
                    )
                }
            }
            error?.let { Text(it, color = AppColors.Danger, fontSize = 13.sp) }
            AccentButton(
                text = "SAVE",
                onClick = {
                    val kcal = calories.toIntOrNull()
                    when {
                        name.isBlank() -> error = "Enter what you did."
                        kcal == null || kcal !in 1..20000 -> error = "Enter the calories burned."
                        else -> onSave(ExtraActivity(date = date, name = name.trim(), calories = kcal, minutes = minutes.toIntOrNull()?.takeIf { it > 0 }))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                height = 56.dp,
            )
        }
    }
}
