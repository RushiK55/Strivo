package com.example.strivo.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.core.ui.components.ActivitySheet
import com.example.strivo.core.ui.components.Card
import com.example.strivo.core.ui.components.ScreenTopBar
import com.example.strivo.core.ui.components.StrivoAlertDialog
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.analytics.ExtraActivity
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
    var addingActivity by remember { mutableStateOf(false) }
    var activityToDelete by remember { mutableStateOf<ExtraActivity?>(null) }
    val today = LocalDate.now()
    val addFood = { meal: Meal -> editing = FoodEntry(date = state.date, meal = meal, name = "", calories = 0) }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = { ScreenTopBar(title = "Calories", onBack = null) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Accent)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DateNavigator(
                    date = state.date,
                    canGoForward = state.date < today,
                    onPrevious = { viewModel.setDate(state.date.minusDays(1)) },
                    onNext = { viewModel.setDate(state.date.plusDays(1)) },
                    onToday = { viewModel.setDate(today) },
                    today = today,
                )
            }
            item { EnergyCard(state, onAddFood = { addFood(defaultMeal()) }, onAddActivity = { addingActivity = true }, onGoal = viewModel::setGoal) }
            Meal.entries.forEach { meal ->
                item(key = meal.name) { MealSection(meal, state.entries.filter { it.meal == meal }, onAdd = { addFood(meal) }, onEdit = { editing = it }) }
            }
            state.day?.let { day ->
                item { BurnedCard(day, onAdd = { addingActivity = true }, onDelete = { activityToDelete = it }) }
            }
            item { MacrosCard(state.entries, state.weightKg) }
            state.week?.let { week -> item { WeekCard(week, state.goal, state.date, onSelect = viewModel::setDate) } }
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

    if (addingActivity) {
        ActivitySheet(
            weightKg = state.weightKg,
            initialDate = state.date,
            onDismiss = { addingActivity = false },
            onSave = {
                addingActivity = false
                viewModel.addActivity(it)
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

    activityToDelete?.let { activity ->
        StrivoAlertDialog(
            title = "Delete activity?",
            onDismiss = { activityToDelete = null },
            confirmText = "Delete",
            confirmColor = AppColors.Danger,
            onConfirm = {
                activityToDelete = null
                activity.id?.let(viewModel::deleteActivity)
            },
        ) {
            Text("Remove '${activity.name}' (${activity.calories} kcal)?", color = AppColors.TextSecondary)
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
private fun DateNavigator(date: LocalDate, canGoForward: Boolean, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit, today: LocalDate) {
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
            if (date != today) {
                TextButton(onClick = onToday, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                    Text("Back to today", style = AppType.Caption, color = AppColors.Accent)
                }
            }
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

/**
 * One meal: a header with its calories and a plus to add to it, then its foods as a grouped list. A meal with nothing
 * in it is just the header, so adding to breakfast is one tap whatever the time. Tap a food to edit or delete it.
 */
@Composable
private fun MealSection(meal: Meal, entries: List<FoodEntry>, onAdd: () -> Unit, onEdit: (FoodEntry) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(start = 4.dp, end = 4.dp, bottom = if (entries.isEmpty()) 0.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(meal.label, style = AppType.Headline, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) {
                Text("%,d kcal".format(Locale.getDefault(), entries.sumOf { it.calories }), style = AppType.Caption, color = AppColors.TextSecondary)
            }
            Box(Modifier.padding(start = 10.dp).size(30.dp).clip(CircleShape).background(AppColors.Surface), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, contentDescription = "Add to ${meal.label}", tint = AppColors.Accent, modifier = Modifier.size(18.dp))
            }
        }
        if (entries.isEmpty()) return@Column
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
