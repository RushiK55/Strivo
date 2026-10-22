package com.example.strivo.core.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.analytics.ActivityTypes
import com.example.strivo.domain.analytics.ExtraActivity
import com.example.strivo.domain.analytics.activityKcal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Adds calories burned outside a tracked workout: a run, a walk, a match. Pick the activity and the minutes and the
 * calories are estimated from the user's weight; or type the number from a watch or machine, which always wins.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitySheet(
    weightKg: Double?,
    onDismiss: () -> Unit,
    onSave: (ExtraActivity) -> Unit,
    initialDate: LocalDate = LocalDate.now(),
) {
    val today = LocalDate.now()
    var name by remember { mutableStateOf("") }
    var met by remember { mutableStateOf<Double?>(null) }
    var calories by remember { mutableStateOf("") }
    var caloriesTyped by remember { mutableStateOf(false) } // a number the user typed is never replaced by an estimate
    var minutes by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate.coerceAtMost(today)) }
    var error by remember { mutableStateOf<String?>(null) }

    // Works the estimate out again while the user has not typed their own number.
    fun reestimate() {
        if (caloriesTyped) return
        val m = met
        val mins = minutes.toIntOrNull()
        calories = if (m != null && weightKg != null && mins != null && mins > 0) {
            activityKcal(m, weightKg, mins).takeIf { it > 0 }?.toString().orEmpty()
        } else {
            ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface,
        shape = AppShapes.Sheet,
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
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityTypes.forEach { type ->
                    Chip(type.name, selected = name == type.name) {
                        name = type.name
                        met = type.met
                        error = null
                        reestimate()
                    }
                }
            }
            SurfaceTextField("Activity", name, KeyboardType.Text) {
                name = it
                met = ActivityTypes.firstOrNull { type -> type.name.equals(it.trim(), ignoreCase = true) }?.met
                error = null
                reestimate()
            }
            SurfaceTextField("Minutes", minutes, KeyboardType.Number) {
                minutes = it.filter(Char::isDigit).take(4)
                error = null
                reestimate()
            }
            SurfaceTextField("Calories burned (kcal)", calories, KeyboardType.Number) {
                calories = it.filter(Char::isDigit).take(5)
                caloriesTyped = calories.isNotEmpty()
                error = null
            }
            Text(
                when {
                    caloriesTyped -> "Your number is used as it is."
                    met != null && weightKg != null ->
                        "Estimated from the minutes and your weight of ${"%.0f".format(weightKg)} kg. Type the number from your watch if you have it."
                    met != null -> "Add your weight in Profile to get an estimate, or type the calories yourself."
                    else -> "Pick an activity and the minutes for an estimate, or type the calories from your watch or machine."
                },
                style = AppType.Caption,
                color = AppColors.TextSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { date = date.minusDays(1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Earlier day", tint = AppColors.TextPrimary)
                }
                Text(
                    activityDayLabel(date, today),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = AppColors.TextPrimary,
                    style = AppType.BodyStrong,
                )
                IconButton(onClick = { date = date.plusDays(1) }, enabled = date < today) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Later day",
                        tint = if (date < today) AppColors.TextPrimary else AppColors.TextSecondary.copy(alpha = 0.3f),
                    )
                }
            }
            error?.let { Text(it, color = AppColors.Danger, style = AppType.Caption) }
            AccentButton(
                text = "Save",
                onClick = {
                    val kcal = calories.toIntOrNull()
                    when {
                        name.isBlank() -> error = "Enter what you did."
                        kcal == null || kcal !in 1..20000 -> error = "Enter the calories burned, or the minutes for an estimate."
                        else -> onSave(ExtraActivity(date = date, name = name.trim(), calories = kcal, minutes = minutes.toIntOrNull()?.takeIf { it > 0 }))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                height = 56.dp,
            )
        }
    }
}

private fun activityDayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}
