package com.example.strivo.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.Chip
import com.example.strivo.core.ui.components.SurfaceTextField
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.analytics.CommonFood
import com.example.strivo.domain.analytics.FoodEntry
import com.example.strivo.domain.analytics.Meal
import com.example.strivo.domain.analytics.macroKcal
import com.example.strivo.domain.analytics.searchCommonFoods
import kotlin.math.abs
import kotlin.math.roundToInt

/** One serving of something the user picked, so the amounts can be scaled by "servings" instead of retyped. */
private data class Serving(val name: String, val label: String?, val calories: Int, val proteinG: Double?, val carbsG: Double?, val fatG: Double?)

private fun CommonFood.serving() = Serving(name, serving, calories, proteinG, carbsG, fatG)

private fun FoodEntry.serving() = Serving(name, null, calories, proteinG, carbsG, fatG)

private fun Double.text(): String = if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)

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

    // A food picked from the lists keeps its one-serving amounts here; typing in any amount lets go of it.
    var picked by remember { mutableStateOf<Serving?>(null) }
    var servings by remember { mutableStateOf(1.0) }

    fun fill(serving: Serving, count: Double) {
        fun scaled(value: Double?) = value?.times(count)?.roundToInt()?.toString().orEmpty()
        calories = (serving.calories * count).roundToInt().toString()
        protein = scaled(serving.proteinG)
        carbs = scaled(serving.carbsG)
        fat = scaled(serving.fatG)
    }

    fun pick(serving: Serving) {
        picked = serving
        servings = 1.0
        name = serving.name
        fill(serving, 1.0)
        error = null
    }

    val suggestions = remember(name, picked) {
        if (initial.id != null || picked != null || name.trim().length < 2) {
            emptyList()
        } else {
            val own = recent.filter { it.name.contains(name.trim(), ignoreCase = true) }.take(3).map { it.serving() }
            val common = searchCommonFoods(name).map { it.serving() }
            (own + common).distinctBy { it.name.lowercase() }.take(6)
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
            if (initial.id == null && recent.isNotEmpty() && name.isBlank()) {
                Text("Recent", style = AppType.Caption, color = AppColors.TextSecondary)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { food -> Chip("${food.name} · ${food.calories}", selected = false) { pick(food.serving()) } }
                }
            }

            SurfaceTextField("Food", name, KeyboardType.Text) {
                name = it
                picked = null
                error = null
            }

            // Typing a name offers the user's own earlier foods and everyday ones with typical amounts.
            if (suggestions.isNotEmpty()) {
                Column(Modifier.clip(AppShapes.Control).background(AppColors.SurfaceHigh)) {
                    suggestions.forEachIndexed { index, food ->
                        if (index > 0) HorizontalDivider(Modifier.padding(start = 14.dp), thickness = 1.dp, color = AppColors.Divider)
                        Row(
                            Modifier.fillMaxWidth().clickable { pick(food) }.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(food.name, style = AppType.BodyStrong, color = AppColors.TextPrimary, maxLines = 1)
                                Text(food.label ?: "From your log", style = AppType.Caption, color = AppColors.TextSecondary, maxLines = 1)
                            }
                            Text("${food.calories} kcal", style = AppType.Caption, color = AppColors.TextSecondary)
                        }
                    }
                }
            }

            picked?.let { serving ->
                ServingsRow(serving.label, servings) { count ->
                    servings = count
                    fill(serving, count)
                }
            }

            SurfaceTextField("Calories (kcal)", calories, KeyboardType.Number) {
                calories = it.filter(Char::isDigit).take(5)
                picked = null
                error = null
            }
            Text("Macros are optional. Fill in all three or leave them empty.", style = AppType.Caption, color = AppColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SurfaceTextField("Protein g", protein, KeyboardType.Number, Modifier.weight(1f)) { protein = it.filter(Char::isDigit).take(4); picked = null; error = null }
                SurfaceTextField("Carbs g", carbs, KeyboardType.Number, Modifier.weight(1f)) { carbs = it.filter(Char::isDigit).take(4); picked = null; error = null }
                SurfaceTextField("Fat g", fat, KeyboardType.Number, Modifier.weight(1f)) { fat = it.filter(Char::isDigit).take(4); picked = null; error = null }
            }

            // The macros say how many calories they hold: fill a blank, or warn when what was typed does not match.
            val fromMacros = if (listOf(protein, carbs, fat).all { it.isNotEmpty() }) {
                macroKcal(protein.toDouble(), carbs.toDouble(), fat.toDouble())
            } else {
                null
            }
            val typed = calories.toIntOrNull()
            if (fromMacros != null && fromMacros > 0) {
                val off = typed != null && abs(typed - fromMacros) > maxOf(40, fromMacros / 5)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            typed == null -> "These macros add up to about $fromMacros kcal."
                            off -> "Heads up: these macros add up to about $fromMacros kcal, not $typed."
                            else -> "These macros add up to about $fromMacros kcal."
                        },
                        style = AppType.Caption,
                        color = if (off) AppColors.Warning else AppColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    if (typed == null || off) {
                        TextButton(onClick = { calories = fromMacros.toString(); picked = null }) {
                            Text("Use $fromMacros", style = AppType.BodyStrong, color = AppColors.Accent)
                        }
                    }
                }
            }
            error?.let { Text(it, style = AppType.Caption, color = AppColors.Danger) }

            AccentButton(
                text = "Save",
                onClick = {
                    val kcal = calories.toIntOrNull() ?: fromMacros
                    val macros = listOf(protein, carbs, fat)
                    when {
                        name.isBlank() -> error = "Enter what you ate."
                        kcal == null || kcal !in 1..20000 -> error = "Enter the calories."
                        macros.any { it.isNotEmpty() } && macros.any { it.isEmpty() } -> error = "Enter all three macros, or leave them empty."
                        else -> onSave(
                            initial.copy(
                                meal = meal,
                                // More than one serving of something picked from the lists says so in its name.
                                name = name.trim() + if (picked != null && servings != 1.0) " ×${servings.text()}" else "",
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

/** How many servings of the picked food: the amounts above follow it. */
@Composable
private fun ServingsRow(label: String?, servings: Double, onChange: (Double) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(AppShapes.Control).background(AppColors.SurfaceHigh).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Servings", style = AppType.BodyStrong, color = AppColors.TextPrimary)
            label?.let { Text("1 serving = $it", style = AppType.Caption, color = AppColors.TextSecondary, maxLines = 1) }
        }
        IconButton(onClick = { onChange((servings - 0.5).coerceAtLeast(0.5)) }, enabled = servings > 0.5) {
            Icon(Icons.Rounded.Remove, contentDescription = "Fewer servings", tint = AppColors.TextPrimary, modifier = Modifier.size(20.dp))
        }
        Text(servings.text(), style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary, modifier = Modifier.width(36.dp), maxLines = 1)
        IconButton(onClick = { onChange((servings + 0.5).coerceAtMost(20.0)) }) {
            Icon(Icons.Rounded.Add, contentDescription = "More servings", tint = AppColors.Accent, modifier = Modifier.size(20.dp))
        }
    }
}
