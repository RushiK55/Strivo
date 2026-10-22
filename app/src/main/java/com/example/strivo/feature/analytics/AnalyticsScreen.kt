package com.example.strivo.feature.analytics

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.core.ui.components.AccentTitle
import com.example.strivo.core.ui.components.Card
import com.example.strivo.core.ui.components.Chip
import com.example.strivo.core.ui.components.IconBadge
import com.example.strivo.core.ui.components.ScreenTopBar
import com.example.strivo.core.ui.components.SegmentedControl
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.core.util.formatMinutes
import com.example.strivo.core.util.formatKg
import com.example.strivo.domain.analytics.AnalyticsRange
import com.example.strivo.domain.analytics.DayStat
import com.example.strivo.domain.model.WorkoutSession
import com.example.strivo.feature.coach.AdviceCard
import java.time.LocalDate
import java.util.Locale
import java.time.format.TextStyle as DateTextStyle

/** What the daily chart measures. */
internal enum class ChartMetric(val label: String) {
    Volume("Volume"),
    Time("Time"),
    Sets("Sets");

    fun value(day: DayStat): Double = when (this) {
        Volume -> day.volumeKg
        Time -> day.seconds / 60.0
        Sets -> day.sets.toDouble()
    }

    fun format(value: Double): String = when (this) {
        Volume -> formatKg(value)
        Time -> formatMinutes(value * 60)
        Sets -> "${value.toInt()} sets"
    }
}

private enum class AnalyticsTab(val label: String) {
    Overview("Overview"),
    Exercises("Exercises"),
    Body("Body"),
    Nutrition("Nutrition"),
}

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    onOpenSession: (WorkoutSession) -> Unit,
    onOpenFood: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(AnalyticsTab.Overview) }
    // The exercise whose own page is open, by name; null shows the tabs.
    var openExercise by rememberSaveable { mutableStateOf<String?>(null) }
    // The value being changed from the Body tab, if any.
    var editingBody by rememberSaveable { mutableStateOf<BodyEdit?>(null) }
    LaunchedEffect(Unit) { viewModel.load() }

    val opened = openExercise?.let { name -> state.exercises.firstOrNull { it.name.equals(name, ignoreCase = true) } }
    if (opened != null) {
        BackHandler { openExercise = null }
        ExerciseAnalyticsScreen(
            history = opened,
            advice = state.advice.firstOrNull { it.exercise.equals(opened.name, ignoreCase = true) },
            acceptLabel = state::acceptLabel,
            onAccept = viewModel::acceptAdvice,
            onBack = { openExercise = null },
        )
        return
    }
    val open = { name: String -> openExercise = name }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = { ScreenTopBar(title = "Analytics", onBack = null) },
    ) { padding ->
        val report = state.report
        val body = state.body
        val nutrition = state.nutrition
        val consistency = state.consistency
        if (state.isLoading || report == null || body == null || nutrition == null || consistency == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Accent)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SegmentedControl(
                    options = AnalyticsTab.entries.map { it.label },
                    selectedIndex = tab.ordinal,
                    onSelect = { tab = AnalyticsTab.entries[it] },
                )
            }
            when (tab) {
                AnalyticsTab.Overview -> {
                    if (state.advice.isNotEmpty()) {
                        item {
                            Column {
                                AccentTitle("Coach")
                                Spacer(Modifier.height(12.dp))
                                AdviceCard(state.advice, acceptLabel = state::acceptLabel, onAccept = viewModel::acceptAdvice)
                            }
                        }
                    }
                    item { RangeSelector(state.range, viewModel::setRange) }
                    if (report.isEmpty) {
                        item { EmptyAnalytics(state.range) }
                    } else {
                        item { Overview(report) }
                        item { StatGrid(report) }
                        item { ConsistencySection(consistency) }
                        item { ActivityChart(report) }
                        item { WeeklyVolumeSection(body.weeks) }
                        item { TrainingPattern(consistency) }
                        item { TopExercises(report, open) }
                        item { PlansTrained(report) }
                        item { Timeline(report, onOpenSession) }
                    }
                }

                AnalyticsTab.Exercises -> exercisesTab(state.exercises, state.records, open)
                AnalyticsTab.Body -> bodyTab(body) { editingBody = it }
                AnalyticsTab.Nutrition -> {
                    item { RangeSelector(state.range, viewModel::setRange) }
                    nutritionTab(report, body, nutrition, state.expenditure, state.energy, onOpenFood, viewModel::addActivity, viewModel::deleteActivity)
                }
            }
        }
        editingBody?.let { edit ->
            BodyEditDialog(
                edit = edit,
                body = body,
                onDismiss = { editingBody = null },
                onWeight = { viewModel.updateWeight(it); editingBody = null },
                onHeight = { viewModel.updateHeight(it); editingBody = null },
            )
        }
    }
}

// --- Range ---

/** "Today / Last 7 days / Last 30 days / Last 90 days" as small chips under the tabs. */
@Composable
private fun RangeSelector(selected: AnalyticsRange, onSelect: (AnalyticsRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AnalyticsRange.entries.forEach { range ->
            Chip(range.chip, selected = range == selected) { onSelect(range) }
        }
    }
}

@Composable
private fun EmptyAnalytics(range: AnalyticsRange) {
    Card {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(icon = Icons.Rounded.BarChart, padding = 16.dp, iconSize = 32.dp)
            Spacer(Modifier.height(16.dp))
            Text("No workouts ${if (range == AnalyticsRange.Today) "today yet" else "in ${range.phrase}"}", style = AppType.Headline, color = AppColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Finish a workout and your analytics will show up here.",
                style = AppType.Caption,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// --- Pieces ---

// --- Formatting ---

/** 1:35 for 95 seconds. */
internal fun formatSeconds(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

internal fun dayHeadline(day: DayStat): String {
    val name = day.date.dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.getDefault())
    val label = if (day.date == LocalDate.now()) "Today" else name
    return "$label, ${day.date.dayOfMonth} ${day.date.month.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())}"
}

internal fun dayReadout(day: DayStat, metric: ChartMetric): String {
    if (day.isRestDay) return "Rest day"
    return "${day.planNames.joinToString(" + ")} · ${metric.format(metric.value(day))}"
}
