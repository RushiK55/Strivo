package com.example.strivo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarToday
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.data.model.WorkoutSession
import com.example.strivo.ui.components.ChevronBadge
import com.example.strivo.ui.components.IconBadge
import com.example.strivo.ui.components.ScreenTopBar
import com.example.strivo.ui.theme.AppColors
import com.example.strivo.viewmodel.ExerciseViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TimeFormat get() = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val DayFormat get() = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())
private val FullFormat get() = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy • h:mm a", Locale.getDefault())

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
        topBar = { ScreenTopBar(title = "Workout History", onBack = null, fontSize = 20.sp) },
    ) { padding ->
        if (history.isEmpty()) {
            EmptyHistory(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                grouped.forEach { (date, sessions) ->
                    item(key = "header_$date") {
                        Row(
                            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formattedDate(date),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.TextSecondary,
                                letterSpacing = 1.sp,
                            )
                            Spacer(Modifier.width(8.dp))
                            HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = AppColors.Surface)
                        }
                    }
                    items(sessions.size, key = { "session_${sessions[it].id}" }) { i ->
                        HistoryCard(sessions[i], onClick = { onOpenSession(sessions[i]) })
                    }
                    item(key = "gap_$date") { Spacer(Modifier.height(10.dp)) }
                }
            }
        }
    }
}

private fun formattedDate(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "TODAY"
        today.minusDays(1) -> "YESTERDAY"
        else -> DayFormat.format(date).uppercase()
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconBadge(
            icon = Icons.Rounded.History,
            tint = AppColors.Accent.copy(alpha = 0.5f),
            background = AppColors.Surface,
            padding = 30.dp,
            iconSize = 60.dp,
        )
        Spacer(Modifier.height(30.dp))
        Text(
            "No history found",
            fontSize = 22.sp,
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text("Your workout legacy starts here.", color = AppColors.TextSecondary, fontSize = 15.sp)
    }
}

@Composable
private fun HistoryCard(session: WorkoutSession, onClick: () -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier = Modifier
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Surface)
            .border(BorderStroke(1.dp, AppColors.Field), shape)
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = Icons.Rounded.FitnessCenter, padding = 14.dp)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                session.planName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = AppColors.TextPrimary,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(session.totalTime, color = AppColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "${session.exercises.size} EXERCISES",
                    color = AppColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                TimeFormat.format(session.date),
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            ChevronBadge()
        }
    }
}

@Composable
fun HistoryDetailsScreen(session: WorkoutSession, onBack: () -> Unit) {
    Scaffold(
        containerColor = AppColors.Background,
        topBar = { ScreenTopBar(title = "Session Recap", onBack = onBack, fontSize = 20.sp) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            SessionHeader(session)
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryCard("Exercises", session.exercises.size.toString(), Icons.Rounded.FitnessCenter)
                SummaryCard("Total Time", session.totalTime, Icons.Outlined.Timer)
            }
            Spacer(Modifier.height(40.dp))
            Text(
                "WORKOUT BREAKDOWN",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .width(40.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppColors.Accent)
            )
            Spacer(Modifier.height(24.dp))
            session.exercises.forEach { exercise -> PerformedExerciseCard(exercise) }
            Spacer(Modifier.height(50.dp))
        }
    }
}

@Composable
private fun SessionHeader(session: WorkoutSession) {
    val shape = RoundedCornerShape(30.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Surface)
            .border(BorderStroke(1.dp, AppColors.Field), shape)
            .padding(24.dp),
    ) {
        Text(
            session.planName,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = AppColors.TextPrimary,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = Icons.Rounded.CalendarToday, padding = 6.dp, iconSize = 14.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                FullFormat.format(session.date),
                color = AppColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun RowScope.SummaryCard(label: String, value: String, icon: ImageVector) {
    val shape = RoundedCornerShape(25.dp)
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .background(AppColors.Surface)
            .border(BorderStroke(1.dp, AppColors.Field), shape)
            .padding(20.dp),
    ) {
        IconBadge(icon = icon, padding = 8.dp, iconSize = 20.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            label.uppercase(),
            color = AppColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = AppColors.TextPrimary)
    }
}

@Composable
private fun PerformedExerciseCard(exercise: com.example.strivo.data.model.PerformedExercise) {
    val shape = RoundedCornerShape(30.dp)
    Column(
        modifier = Modifier
            .padding(bottom = 20.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Surface)
            .border(BorderStroke(1.dp, AppColors.Field), shape)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = Icons.Outlined.CheckCircleOutline,
                padding = 10.dp,
                iconSize = 22.dp,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                exercise.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 20.dp),
            thickness = 1.5.dp,
            color = AppColors.Field,
        )
        exercise.sets.forEachIndexed { index, set ->
            Row(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "SET ${index + 1}",
                    color = AppColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.Field)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${set.weight} KG × ${set.reps} REPS",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = AppColors.TextPrimary,
                        letterSpacing = 0.5.sp,
                    )
                    if (set.setDuration.isNotEmpty() && set.setDuration != "00:00:00") {
                        Text(
                            "Time: ${set.setDuration}",
                            color = AppColors.TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
