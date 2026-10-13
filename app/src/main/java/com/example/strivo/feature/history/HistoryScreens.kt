package com.example.strivo.feature.history

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.core.ui.components.AppCard
import com.example.strivo.core.ui.components.ChevronBadge
import com.example.strivo.core.ui.components.IconBadge
import com.example.strivo.core.ui.components.ScreenTopBar
import com.example.strivo.core.ui.components.color
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.core.util.formatKg
import com.example.strivo.domain.model.ExerciseSet
import com.example.strivo.domain.model.PerformedExercise
import com.example.strivo.domain.model.WorkoutSession
import com.example.strivo.domain.model.countsForRecords
import com.example.strivo.domain.model.isWork
import com.example.strivo.domain.model.setLabels
import com.example.strivo.feature.plans.ExerciseViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TimeFormat get() = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val DayFormat get() = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())
private val FullFormat get() = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy · h:mm a", Locale.getDefault())

private fun ExerciseSet.volume(): Double = weight * reps
// Warm-ups are not counted as work: they would only inflate the totals.
private fun WorkoutSession.setCount(): Int = exercises.sumOf { ex -> ex.sets.count { it.isWork } }
private fun WorkoutSession.volume(): Double = exercises.sumOf { ex -> ex.sets.filter { it.isWork }.sumOf { it.volume() } }
private fun weightText(kg: Double): String = if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString()

@Composable
fun HistoryScreen(
    exerciseViewModel: ExerciseViewModel,
    onOpenSession: (WorkoutSession) -> Unit,
) {
    val history by exerciseViewModel.history.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { exerciseViewModel.fetchHistory() }

    // Group sessions by date for display (the list is already newest-first).
    val grouped = remember(history) { history.groupBy { it.date.toLocalDate() } }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            ScreenTopBar(
                title = "History",
                onBack = null,
                subtitle = if (history.isEmpty()) null else if (history.size == 1) "1 workout" else "${history.size} workouts",
            )
        },
    ) { padding ->
        if (history.isEmpty()) {
            EmptyHistory(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            ) {
                grouped.forEach { (date, sessions) ->
                    item(key = "header_$date") {
                        Text(
                            text = formattedDate(date),
                            style = AppType.BodyStrong,
                            color = AppColors.TextSecondary,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
                        )
                    }
                    item(key = "day_$date") {
                        // The sessions of one day as one grouped card.
                        AppCard(padding = PaddingValues(vertical = 4.dp)) {
                            sessions.forEachIndexed { index, session ->
                                if (index > 0) HorizontalDivider(Modifier.padding(start = 70.dp), thickness = 1.dp, color = AppColors.Divider)
                                HistoryRow(session, onClick = { onOpenSession(session) })
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formattedDate(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DayFormat.format(date)
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconBadge(icon = Icons.Rounded.History, padding = 18.dp, iconSize = 34.dp)
        Spacer(Modifier.height(18.dp))
        Text("No workouts yet", style = AppType.Title, color = AppColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Finish a workout and it will be saved here, set by set.",
            style = AppType.Body,
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HistoryRow(session: WorkoutSession, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = Icons.Rounded.FitnessCenter, padding = 9.dp, iconSize = 22.dp, shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(session.planName, style = AppType.BodyStrong, color = AppColors.TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                Text(TimeFormat.format(session.date), style = AppType.Caption, color = AppColors.TextTertiary)
            }
            val volume = session.volume()
            Text(
                listOfNotNull(
                    session.totalTime,
                    if (session.exercises.size == 1) "1 exercise" else "${session.exercises.size} exercises",
                    if (volume > 0) formatKg(volume) else null,
                ).joinToString(" · "),
                style = AppType.Caption,
                color = AppColors.TextSecondary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(4.dp))
        ChevronBadge()
    }
}

// --- One session ---

@Composable
fun HistoryDetailsScreen(session: WorkoutSession, onBack: () -> Unit) {
    Scaffold(
        containerColor = AppColors.Background,
        topBar = { ScreenTopBar(title = "Workout", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                FullFormat.format(session.date),
                style = AppType.Caption,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text(
                session.planName,
                style = AppType.LargeTitle,
                color = AppColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            SessionStats(session)
            Spacer(Modifier.height(24.dp))
            Text("Exercises", style = AppType.Headline, color = AppColors.TextPrimary, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
            session.exercises.forEach { exercise -> PerformedExerciseCard(exercise) }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Duration, exercises, sets and volume in one row. */
@Composable
private fun SessionStats(session: WorkoutSession) {
    val volume = session.volume()
    val stats = listOfNotNull(
        "Duration" to session.totalTime,
        "Exercises" to session.exercises.size.toString(),
        "Sets" to session.setCount().toString(),
        if (volume > 0) "Volume" to formatKg(volume) else null,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Card)
            .background(AppColors.Surface)
            .padding(vertical = 16.dp),
    ) {
        stats.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .width(1.dp)
                        .height(36.dp)
                        .background(AppColors.Divider)
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = AppType.Headline.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary, maxLines = 1)
                Text(label, style = AppType.Caption, color = AppColors.TextSecondary)
            }
        }
    }
}

/** One exercise: its name, best set, and a small table of what was lifted. */
@Composable
private fun PerformedExerciseCard(exercise: PerformedExercise) {
    val best = (exercise.sets.filter { it.countsForRecords }.ifEmpty { exercise.sets }).maxByOrNull { it.weight * 1000 + it.reps }
    val workSets = exercise.sets.count { it.isWork }
    val warmUps = exercise.sets.size - workSets
    val labels = exercise.sets.setLabels()
    AppCard(modifier = Modifier.padding(bottom = 12.dp), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
        Text(exercise.name, style = AppType.Headline, color = AppColors.TextPrimary)
        Text(
            listOfNotNull(
                if (workSets == 1) "1 set" else "$workSets sets",
                if (warmUps > 0) "$warmUps warm-up" + (if (warmUps > 1) "s" else "") else null,
                best?.takeIf { it.weight > 0 }?.let { "best ${weightText(it.weight)} kg × ${it.reps}" },
            ).joinToString(" · "),
            style = AppType.Caption,
            color = AppColors.TextSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(bottom = 2.dp)) {
            Text("Set", style = AppType.Overline, color = AppColors.TextTertiary, modifier = Modifier.width(44.dp))
            Text("Weight × reps", style = AppType.Overline, color = AppColors.TextTertiary, modifier = Modifier.weight(1f))
            Text("Time", style = AppType.Overline, color = AppColors.TextTertiary)
        }
        exercise.sets.forEachIndexed { index, set ->
            HorizontalDivider(thickness = 1.dp, color = AppColors.Divider)
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(labels[index], style = AppType.BodyStrong, color = set.type.color(), modifier = Modifier.width(44.dp))
                Text(
                    if (set.weight > 0) "${weightText(set.weight)} kg × ${set.reps}" else "${set.reps} reps",
                    style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"),
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                val duration = set.setDuration.takeIf { it.isNotEmpty() && it != "00:00:00" && it != "00:00" }
                Text(duration ?: "–", style = AppType.Caption, color = AppColors.TextSecondary)
            }
        }
    }
}
