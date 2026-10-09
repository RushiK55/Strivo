package com.example.strivo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.data.analytics.FoodEntry
import com.example.strivo.data.analytics.Meal
import com.example.strivo.ui.components.AccentButton
import com.example.strivo.ui.components.IconBadge
import com.example.strivo.ui.components.ScreenTopBar
import com.example.strivo.ui.components.StrivoAlertDialog
import com.example.strivo.ui.theme.AppColors
import com.example.strivo.viewmodel.FoodViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun FoodScreen(viewModel: FoodViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    // null = sheet closed; an entry without id = adding; an entry with id = editing.
    var editing by remember { mutableStateOf<FoodEntry?>(null) }
    var toDelete by remember { mutableStateOf<FoodEntry?>(null) }
    val today = LocalDate.now()

    Scaffold(
        containerColor = AppColors.Background,
        topBar = { ScreenTopBar(title = "Food", onBack = null) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = FoodEntry(date = state.date, meal = defaultMeal(), name = "", calories = 0) },
                containerColor = AppColors.Accent,
                contentColor = Color.Black,
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add food", modifier = Modifier.size(32.dp))
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Accent)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                DateNavigator(
                    date = state.date,
                    canGoForward = state.date < today,
                    onPrevious = { viewModel.setDate(state.date.minusDays(1)) },
                    onNext = { viewModel.setDate(state.date.plusDays(1)) },
                    today = today,
                )
            }
            item { DaySummary(state.entries, state.maintenanceKcal) }
            if (state.entries.isEmpty()) {
                item { EmptyDay(onAdd = { editing = FoodEntry(date = state.date, meal = defaultMeal(), name = "", calories = 0) }) }
            } else {
                Meal.entries.forEach { meal ->
                    val inMeal = state.entries.filter { it.meal == meal }
                    if (inMeal.isNotEmpty()) {
                        item { MealSection(meal, inMeal, onEdit = { editing = it }, onDelete = { toDelete = it }) }
                    }
                }
            }
        }
    }

    editing?.let { entry ->
        FoodSheet(
            initial = entry,
            recent = state.recent,
            onDismiss = { editing = null },
            onSave = { saved ->
                editing = null
                if (saved.id == null) viewModel.add(saved) else viewModel.update(saved)
            },
        )
    }

    toDelete?.let { entry ->
        StrivoAlertDialog(
            title = "Delete food?",
            onDismiss = { toDelete = null },
            confirmText = "Delete",
            confirmColor = AppColors.Danger,
            onConfirm = {
                toDelete = null
                entry.id?.let(viewModel::delete)
            },
        ) {
            Text("Remove '${entry.name}' (${entry.calories} kcal) from your log?", color = AppColors.TextSecondary)
        }
    }
}

/** A sensible meal for right now, so the sheet opens on what the user is probably eating. */
private fun defaultMeal(): Meal = when (java.time.LocalTime.now().hour) {
    in 4..10 -> Meal.Breakfast
    in 11..15 -> Meal.Lunch
    in 16..21 -> Meal.Dinner
    else -> Meal.Snack
}

// --- Day ---

@Composable
private fun DateNavigator(date: LocalDate, canGoForward: Boolean, onPrevious: () -> Unit, onNext: () -> Unit, today: LocalDate) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous day", tint = AppColors.TextPrimary)
        }
        Text(
            text = when (date) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            },
            modifier = Modifier.weight(1f),
            color = AppColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Next day",
                tint = if (canGoForward) AppColors.TextPrimary else AppColors.TextSecondary.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
private fun DaySummary(entries: List<FoodEntry>, maintenanceKcal: Double?) {
    val eaten = entries.sumOf { it.calories }
    Card {
        Text("EATEN", color = AppColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text("%,d kcal".format(Locale.getDefault(), eaten), color = AppColors.TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Black)
        if (maintenanceKcal != null && entries.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            val difference = eaten - maintenanceKcal
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AppColors.Field),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((eaten / maintenanceKcal).toFloat().coerceIn(0.02f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(AppColors.Accent),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (difference <= 0) {
                    "%,.0f kcal under your maintenance of about %,.0f".format(Locale.getDefault(), -difference, maintenanceKcal)
                } else {
                    "%,.0f kcal over your maintenance of about %,.0f".format(Locale.getDefault(), difference, maintenanceKcal)
                },
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
            )
        } else if (maintenanceKcal == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Add your gender, age, height and weight in Profile to compare with your daily needs.",
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
        // Macros only from foods that have all three entered, so the totals are never padded with guesses.
        val withMacros = entries.filter { it.hasMacros }
        if (withMacros.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MacroTile("Protein", withMacros.sumOf { it.proteinG!! }, Modifier.weight(1f))
                MacroTile("Carbs", withMacros.sumOf { it.carbsG!! }, Modifier.weight(1f))
                MacroTile("Fat", withMacros.sumOf { it.fatG!! }, Modifier.weight(1f))
            }
            if (withMacros.size < entries.size) {
                Spacer(Modifier.height(6.dp))
                Text("Macros from ${withMacros.size} of ${entries.size} foods.", color = AppColors.TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MacroTile(label: String, grams: Double, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Field)
            .padding(12.dp),
    ) {
        Text("${grams.toInt()} g", color = AppColors.TextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text(label, color = AppColors.TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun EmptyDay(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(
            icon = Icons.Rounded.Restaurant,
            tint = AppColors.Accent.copy(alpha = 0.5f),
            background = AppColors.Surface,
            padding = 28.dp,
            iconSize = 48.dp,
        )
        Spacer(Modifier.height(16.dp))
        Text("Nothing logged for this day", color = AppColors.TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(24.dp))
        AccentButton(text = "ADD FOOD", onClick = onAdd, modifier = Modifier.width(200.dp), icon = Icons.Rounded.Add)
    }
}

@Composable
private fun MealSection(meal: Meal, entries: List<FoodEntry>, onEdit: (FoodEntry) -> Unit, onDelete: (FoodEntry) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                meal.label.uppercase(),
                modifier = Modifier.weight(1f),
                color = AppColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Text("%,d kcal".format(Locale.getDefault(), entries.sumOf { it.calories }), color = AppColors.TextSecondary, fontSize = 13.sp)
        }
        Card {
            entries.forEachIndexed { index, entry ->
                if (index > 0) Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onEdit(entry) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            if (entry.hasMacros) {
                                "P ${entry.proteinG!!.toInt()} g · C ${entry.carbsG!!.toInt()} g · F ${entry.fatG!!.toInt()} g"
                            } else {
                                "No macros"
                            },
                            color = AppColors.TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Text("${entry.calories} kcal", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    IconButton(onClick = { onDelete(entry) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ${entry.name}", tint = AppColors.Danger)
                    }
                }
            }
        }
    }
}

// --- Add / edit ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodSheet(initial: FoodEntry, recent: List<FoodEntry>, onDismiss: () -> Unit, onSave: (FoodEntry) -> Unit) {
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
            Text(if (initial.id == null) "Add food" else "Edit food", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Meal.entries.forEach { option -> Chip(option.label, selected = option == meal) { meal = option } }
            }

            // Foods the user has logged before: tap one to fill the form with its values.
            if (initial.id == null && recent.isNotEmpty()) {
                Text("RECENT", color = AppColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
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

            FoodField("Food", name, KeyboardType.Text) { name = it; error = null }
            FoodField("Calories (kcal)", calories, KeyboardType.Number) { calories = it.filter(Char::isDigit).take(5); error = null }
            Text("Macros are optional. Fill in all three or leave them empty.", color = AppColors.TextSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FoodField("Protein g", protein, KeyboardType.Number, Modifier.weight(1f)) { protein = it.filter(Char::isDigit).take(4); error = null }
                FoodField("Carbs g", carbs, KeyboardType.Number, Modifier.weight(1f)) { carbs = it.filter(Char::isDigit).take(4); error = null }
                FoodField("Fat g", fat, KeyboardType.Number, Modifier.weight(1f)) { fat = it.filter(Char::isDigit).take(4); error = null }
            }
            error?.let { Text(it, color = AppColors.Danger, fontSize = 13.sp) }

            AccentButton(
                text = "SAVE",
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
                height = 56.dp,
            )
        }
    }
}

@Composable
internal fun FoodField(label: String, value: String, keyboard: KeyboardType, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppColors.TextPrimary,
            unfocusedTextColor = AppColors.TextPrimary,
            focusedContainerColor = AppColors.Field,
            unfocusedContainerColor = AppColors.Field,
            focusedBorderColor = AppColors.Accent,
            unfocusedBorderColor = AppColors.Field,
            focusedLabelColor = AppColors.TextSecondary,
            unfocusedLabelColor = AppColors.TextSecondary,
            cursorColor = AppColors.Accent,
        ),
    )
}
