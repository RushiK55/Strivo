package com.example.strivo.feature.food

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.Chip
import com.example.strivo.core.ui.components.SurfaceTextField
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.analytics.FoodEntry
import com.example.strivo.domain.analytics.Meal

// --- Add / edit ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoodSheet(
    initial: FoodEntry,
    recent: List<FoodEntry>,
    onDismiss: () -> Unit,
    onSave: (FoodEntry) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var meal by remember { mutableStateOf(initial.meal) }
    var name by remember { mutableStateOf(initial.name) }
    var calories by remember { mutableStateOf(if (initial.calories > 0) initial.calories.toString() else "") }
    var protein by remember { mutableStateOf(initial.proteinG?.toInt()?.toString().orEmpty()) }
    var carbs by remember { mutableStateOf(initial.carbsG?.toInt()?.toString().orEmpty()) }
    var fat by remember { mutableStateOf(initial.fatG?.toInt()?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (initial.id == null) "Add food" else "Edit food", style = AppType.Title, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", style = AppType.BodyStrong, color = AppColors.Danger) }
                }
            }

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Meal.entries.forEach { option -> Chip(option.label, selected = option == meal) { meal = option } }
            }

            // Foods the user has logged before: tap one to fill the form with its values.
            if (initial.id == null && recent.isNotEmpty()) {
                Text("Recent", style = AppType.Caption, color = AppColors.TextSecondary)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { food ->
                        Chip("${food.name} · ${food.calories}", selected = false) {
                            name = food.name
                            calories = food.calories.toString()
                            protein = food.proteinG?.toInt()?.toString().orEmpty()
                            carbs = food.carbsG?.toInt()?.toString().orEmpty()
                            fat = food.fatG?.toInt()?.toString().orEmpty()
                            error = null
                        }
                    }
                }
            }

            SurfaceTextField("Food", name, KeyboardType.Text) { name = it; error = null }
            SurfaceTextField("Calories (kcal)", calories, KeyboardType.Number) { calories = it.filter(Char::isDigit).take(5); error = null }
            Text("Macros are optional. Fill in all three or leave them empty.", style = AppType.Caption, color = AppColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SurfaceTextField("Protein g", protein, KeyboardType.Number, Modifier.weight(1f)) { protein = it.filter(Char::isDigit).take(4); error = null }
                SurfaceTextField("Carbs g", carbs, KeyboardType.Number, Modifier.weight(1f)) { carbs = it.filter(Char::isDigit).take(4); error = null }
                SurfaceTextField("Fat g", fat, KeyboardType.Number, Modifier.weight(1f)) { fat = it.filter(Char::isDigit).take(4); error = null }
            }
            error?.let { Text(it, style = AppType.Caption, color = AppColors.Danger) }

            AccentButton(
                text = "Save",
                onClick = {
                    val kcal = calories.toIntOrNull()
                    val macros = listOf(protein, carbs, fat)
                    when {
                        name.isBlank() -> error = "Enter what you ate."
                        kcal == null || kcal !in 1..20000 -> error = "Enter the calories."
                        macros.any { it.isNotEmpty() } && macros.any { it.isEmpty() } -> error = "Enter all three macros, or leave them empty."
                        else -> onSave(
                            initial.copy(
                                meal = meal,
                                name = name.trim(),
                                calories = kcal,
                                proteinG = protein.toDoubleOrNull(),
                                carbsG = carbs.toDoubleOrNull(),
                                fatG = fat.toDoubleOrNull(),
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp,
            )
        }
    }
}
