package com.example.strivo.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.core.util.formatKg
import com.example.strivo.core.util.formatMinutes
import com.example.strivo.domain.analytics.parseDurationSeconds
import com.example.strivo.domain.model.Plan
import com.example.strivo.domain.model.WorkoutSession
import com.example.strivo.domain.model.isWork

/**
 * An earlier day, as it really was. A day that has passed is not a plan to start: it shows the workouts that were done (each one opens
 * with every set, weight and rep) and, under them, the plans scheduled for that weekday that were not done. Starting a workout is only
 * for today and the days to come.
 */
@Composable
internal fun PastDayRows(
    weekday: String,
    sessions: List<WorkoutSession>,
    plans: List<Plan>,
    onOpenSession: (WorkoutSession) -> Unit,
    onOpenPlan: (Plan) -> Unit,
) {
    val done = sessions.sortedBy { it.date }
    val missed = plans.filter { plan -> done.none { it.planName == plan.planName } }
    if (done.isEmpty() && missed.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nothing was planned or done", style = AppType.BodyStrong, color = AppColors.TextPrimary)
            Text("There is no workout to show for this $weekday.", style = AppType.Caption, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
        }
        return
    }
    done.forEachIndexed { index, session ->
        if (index > 0) PastDivider()
        DoneRow(session) { onOpenSession(session) }
    }
    missed.forEachIndexed { index, plan ->
        if (done.isNotEmpty() || index > 0) PastDivider()
        MissedRow(plan) { onOpenPlan(plan) }
    }
}

@Composable
private fun PastDivider() = HorizontalDivider(Modifier.padding(start = 70.dp), thickness = 1.dp, color = AppColors.Divider)

/** A workout that was done: its name, what it covered, and a way into every set. */
@Composable
private fun DoneRow(session: WorkoutSession, onClick: () -> Unit) {
    val volume = session.exercises.sumOf { exercise -> exercise.sets.filter { it.isWork }.sumOf { it.weight * it.reps } }
    val count = session.exercises.size
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(AppColors.Accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, contentDescription = "Done", tint = Color.Black, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(session.planName, style = AppType.Headline, color = AppColors.TextPrimary, maxLines = 1)
            Text(
                "${if (count == 1) "1 exercise" else "$count exercises"} · ${formatMinutes(parseDurationSeconds(session.totalTime).toDouble())} · ${formatKg(volume)}",
                style = AppType.Caption,
                color = AppColors.TextSecondary,
                maxLines = 1,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = AppColors.TextTertiary)
    }
}

/** A plan scheduled for that day that was not done: shown as it is now, with no way to start it. */
@Composable
private fun MissedRow(plan: Plan, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(AppColors.SurfaceHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Remove, contentDescription = "Not done", tint = AppColors.TextTertiary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(plan.planName, style = AppType.Headline, color = AppColors.TextSecondary, maxLines = 1)
            Text("Not done", style = AppType.Caption, color = AppColors.TextTertiary)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = AppColors.TextTertiary)
    }
}
