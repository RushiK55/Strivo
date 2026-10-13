package com.example.strivo.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.Card
import com.example.strivo.core.ui.components.IconBadge
import com.example.strivo.core.ui.components.ScreenTopBar
import com.example.strivo.core.ui.components.StatTile
import com.example.strivo.core.ui.components.StrivoAlertDialog
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.analytics.FoodEntry
import com.example.strivo.domain.analytics.Meal
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
    val addFood = { editing = FoodEntry(date = state.date, meal = defaultMeal(), name = "", calories = 0) }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = { ScreenTopBar(title = "Food", onBack = null) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = addFood,
                containerColor = AppColors.Accent,
                contentColor = Color.Black,
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add food", modifier = Modifier.size(28.dp))
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                item { EmptyDay(onAdd = addFood) }
            } else {
                Meal.entries.forEach { meal ->
                    val inMeal = state.entries.filter { it.meal == meal }
                    if (inMeal.isNotEmpty()) {
                        item { MealSection(meal, inMeal, onEdit = { editing = it }) }
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
            // Deleting lives in the edit sheet, so the list stays calm and nothing is removed by a stray tap.
            onDelete = if (entry.id == null) null else ({ editing = null; toDelete = entry }),
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
        RoundNavButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous day", enabled = true, onClick = onPrevious)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when (date) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    else -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
                },
                style = AppType.Headline,
                color = AppColors.TextPrimary,
            )
            Text(
                date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                style = AppType.Caption,
                color = AppColors.TextSecondary,
            )
        }
        RoundNavButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next day", enabled = canGoForward, onClick = onNext)
    }
}

@Composable
internal fun RoundNavButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(AppColors.Surface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) AppColors.TextPrimary else AppColors.TextTertiary.copy(alpha = 0.5f))
    }
}

@Composable
private fun DaySummary(entries: List<FoodEntry>, maintenanceKcal: Double?) {
    val eaten = entries.sumOf { it.calories }
    Card {
        Text("Eaten", style = AppType.Caption, color = AppColors.TextSecondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%,d".format(Locale.getDefault(), eaten), style = AppType.MetricLarge, color = AppColors.TextPrimary)
            Spacer(Modifier.width(6.dp))
            Text(
                if (maintenanceKcal != null) "/ %,.0f kcal".format(Locale.getDefault(), maintenanceKcal) else "kcal",
                style = AppType.Body,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (maintenanceKcal != null) {
            Spacer(Modifier.height(12.dp))
            val difference = eaten - maintenanceKcal
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AppColors.SurfaceHigh),
            ) {
                if (eaten > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth((eaten / maintenanceKcal).toFloat().coerceIn(0.02f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (difference > 0) AppColors.Warning else AppColors.Accent),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    entries.isEmpty() -> "Your maintenance is about %,.0f kcal a day.".format(Locale.getDefault(), maintenanceKcal)
                    difference <= 0 -> "%,.0f kcal left to maintenance".format(Locale.getDefault(), -difference)
                    else -> "%,.0f kcal over maintenance".format(Locale.getDefault(), difference)
                },
                style = AppType.Caption,
                color = AppColors.TextSecondary,
            )
        } else {
            Spacer(Modifier.height(4.dp))
            Text(
                "Add your gender, age, height and weight in Profile to compare with your daily needs.",
                style = AppType.Caption,
                color = AppColors.TextSecondary,
            )
        }
        // Macros only from foods that have all three entered, so the totals are never padded with guesses.
        val withMacros = entries.filter { it.hasMacros }
        if (withMacros.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MacroTile("Protein", withMacros.sumOf { it.proteinG!! }, Modifier.weight(1f))
                MacroTile("Carbs", withMacros.sumOf { it.carbsG!! }, Modifier.weight(1f))
                MacroTile("Fat", withMacros.sumOf { it.fatG!! }, Modifier.weight(1f))
            }
            if (withMacros.size < entries.size) {
                Spacer(Modifier.height(8.dp))
                Text("Macros from ${withMacros.size} of ${entries.size} foods.", style = AppType.Caption, color = AppColors.TextTertiary)
            }
        }
    }
}

@Composable
private fun MacroTile(label: String, grams: Double, modifier: Modifier = Modifier) {
    StatTile(label, "${grams.toInt()} g", modifier, background = AppColors.SurfaceHigh)
}

@Composable
private fun EmptyDay(onAdd: () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(icon = Icons.Rounded.Restaurant, padding = 16.dp, iconSize = 30.dp)
            Spacer(Modifier.height(14.dp))
            Text("Nothing logged for this day", style = AppType.Headline, color = AppColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Log a meal to see your calories and macros.",
                style = AppType.Caption,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            AccentButton(text = "Add food", onClick = onAdd, icon = Icons.Rounded.Add, height = 46.dp)
        }
    }
}

/** One meal: a small header with its total, then its foods as a grouped list. Tap a food to edit or delete it. */
@Composable
private fun MealSection(meal: Meal, entries: List<FoodEntry>, onEdit: (FoodEntry) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(meal.label, style = AppType.Headline, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
            Text("%,d kcal".format(Locale.getDefault(), entries.sumOf { it.calories }), style = AppType.Caption, color = AppColors.TextSecondary)
        }
        Card(padding = PaddingValues(vertical = 4.dp)) {
            entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 1.dp, color = AppColors.Divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(entry) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = AppType.BodyStrong, color = AppColors.TextPrimary, maxLines = 1)
                        Text(
                            if (entry.hasMacros) {
                                "P ${entry.proteinG!!.toInt()} · C ${entry.carbsG!!.toInt()} · F ${entry.fatG!!.toInt()} g"
                            } else {
                                "No macros"
                            },
                            style = AppType.Caption,
                            color = AppColors.TextSecondary,
                        )
                    }
                    Text(
                        "${entry.calories}",
                        style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"),
                        color = AppColors.TextPrimary,
                    )
                    Text(" kcal", style = AppType.Caption, color = AppColors.TextSecondary)
                }
            }
        }
    }
}
