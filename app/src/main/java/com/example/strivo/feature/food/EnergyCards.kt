package com.example.strivo.feature.food

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.Card
import com.example.strivo.core.ui.components.Chip
import com.example.strivo.core.ui.components.SoftButton
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.analytics.CalorieGoal
import com.example.strivo.domain.analytics.EnergyDay
import com.example.strivo.domain.analytics.EnergySummary
import com.example.strivo.domain.analytics.ExtraActivity
import com.example.strivo.domain.analytics.FoodEntry
import com.example.strivo.domain.analytics.proteinTargetG
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private fun kcalText(value: Double): String = "%,d".format(Locale.getDefault(), value.roundToInt())

private fun kcalText(value: Int): String = "%,d".format(Locale.getDefault(), value)

private fun signed(value: Double): String = (if (value < 0) "−" else "+") + kcalText(abs(value))

// --- Today at a glance ---

/** Calories eaten against what the day allows, with the burn that makes up the allowance, the goal and the two ways to log. */
@Composable
internal fun EnergyCard(state: FoodState, onAddFood: () -> Unit, onAddActivity: () -> Unit, onGoal: (CalorieGoal) -> Unit) {
    val day = state.day ?: return
    val target = state.targetKcal
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SideStat("Eaten", kcalText(day.eaten), AppColors.Accent, Modifier.weight(1f))
            EnergyRing(eaten = day.eaten, target = target, modifier = Modifier.size(156.dp))
            SideStat("Active burn", kcalText(day.activeKcal), AppColors.Success, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        val base = day.baseKcal
        if (base != null) {
            Text(
                "Base ${kcalText(base)} + active ${kcalText(day.activeKcal)} = ${kcalText(base + day.activeKcal)} burned",
                style = AppType.BodyStrong,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.goal != CalorieGoal.Maintain && target != null) {
                Text(
                    "Goal ${signed(state.goal.offsetKcal)} → eat about ${kcalText(target)}",
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                when (state.baseSource) {
                    BaseSource.Learned -> "Base learned from your weight and food log, without the exercise you log."
                    else -> "Base from the formula for your age, height and weight. It sharpens as you log food and weigh in."
                },
                style = AppType.Caption,
                color = AppColors.TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        } else {
            Text(
                "Add your gender, age, height and weight in Profile to see what you burn and what is left for the day.",
                style = AppType.Caption,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalorieGoal.entries.forEach { goal -> Chip(goal.label, selected = goal == state.goal) { onGoal(goal) } }
            Text(
                when (state.goal) {
                    CalorieGoal.Lose -> "about 0.5 kg a week"
                    CalorieGoal.Maintain -> "stay at your weight"
                    CalorieGoal.Gain -> "about 0.25 kg a week"
                },
                style = AppType.Caption,
                color = AppColors.TextSecondary,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentButton("Add food", onAddFood, Modifier.weight(1f), height = 46.dp, icon = Icons.Rounded.Add, fontSize = 15.sp)
            SoftButton("Add activity", onAddActivity, Modifier.weight(1f), height = 46.dp, icon = Icons.Rounded.DirectionsRun, contentColor = AppColors.Accent)
        }
    }
}

@Composable
private fun SideStat(label: String, value: String, dot: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(5.dp))
            Text(label, style = AppType.Caption, color = AppColors.TextSecondary, maxLines = 1)
        }
        Text(value, style = AppType.Metric.copy(fontSize = 22.sp), color = AppColors.TextPrimary, maxLines = 1)
        Text("kcal", style = AppType.Caption, color = AppColors.TextTertiary)
    }
}

/** A ring that fills as the day's calories are eaten; past the target it goes round again in a warning colour. */
@Composable
private fun EnergyRing(eaten: Int, target: Double?, modifier: Modifier = Modifier) {
    val progress = if (target != null && target > 0) (eaten / target).toFloat() else 0f
    val animated by animateFloatAsState(progress, tween(700), label = "energyRing")
    val left = target?.minus(eaten)
    Box(
        modifier = modifier.semantics {
            contentDescription = if (left == null) "$eaten kcal eaten" else if (left >= 0) "${left.roundToInt()} kcal left" else "${abs(left).roundToInt()} kcal over"
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 15.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(AppColors.SurfaceHigh, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            if (animated > 0f) {
                drawArc(AppColors.Accent, -90f, animated.coerceAtMost(1f) * 360f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (animated > 1f) {
                drawArc(AppColors.Warning, -90f, (animated - 1f).coerceAtMost(1f) * 360f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                kcalText(if (left == null) eaten.toDouble() else abs(left)),
                style = AppType.MetricLarge.copy(fontSize = 30.sp, lineHeight = 34.sp),
                color = if (left != null && left < 0) AppColors.Warning else AppColors.TextPrimary,
                maxLines = 1,
            )
            Text(
                when {
                    left == null -> "kcal eaten"
                    left >= 0 -> "kcal left"
                    else -> "kcal over"
                },
                style = AppType.Caption,
                color = AppColors.TextSecondary,
            )
        }
    }
}

// --- Burned ---

/** Everything that burned calories on the day, beyond the base: tracked workouts and activity added by hand. */
@Composable
internal fun BurnedCard(day: EnergyDay, onAdd: () -> Unit, onDelete: (ExtraActivity) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 0.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Burned by activity", style = AppType.Headline, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
            Text("${kcalText(day.activeKcal)} kcal", style = AppType.Caption, color = AppColors.TextSecondary)
        }
        Card(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            if (day.workouts.isEmpty() && day.extras.isEmpty()) {
                Text(
                    "Nothing yet. A walk, a match, a run? Add it so your calories add up.",
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
            day.workouts.forEachIndexed { index, workout ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 1.dp, color = AppColors.Divider)
                BurnRow(
                    icon = Icons.Rounded.FitnessCenter,
                    title = workout.planName,
                    subtitle = listOfNotNull(
                        "Workout",
                        workout.minutes?.let { "$it min" + if (workout.lengthEstimated) " (estimated)" else "" },
                    ).joinToString(" · "),
                    kcal = workout.kcal?.let(::kcalText) ?: "–",
                )
            }
            day.extras.forEachIndexed { index, activity ->
                if (index > 0 || day.workouts.isNotEmpty()) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 1.dp, color = AppColors.Divider)
                BurnRow(
                    icon = Icons.Rounded.DirectionsRun,
                    title = activity.name,
                    subtitle = listOfNotNull("Activity", activity.minutes?.let { "$it min" }).joinToString(" · "),
                    kcal = kcalText(activity.calories),
                    onDelete = { onDelete(activity) },
                )
            }
            if (day.workoutNeedsWeight) {
                Text(
                    "Add your weight in Profile to count your workouts.",
                    style = AppType.Caption,
                    color = AppColors.Warning,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        SoftButton("Add activity", onAdd, Modifier.fillMaxWidth(), height = 46.dp, icon = Icons.Rounded.Add, contentColor = AppColors.Accent)
    }
}

@Composable
private fun BurnRow(icon: ImageVector, title: String, subtitle: String, kcal: String, onDelete: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = if (onDelete == null) 16.dp else 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.BodyStrong, color = AppColors.TextPrimary, maxLines = 1)
            Text(subtitle, style = AppType.Caption, color = AppColors.TextSecondary, maxLines = 1)
        }
        Text(kcal, style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary)
        Text(" kcal", style = AppType.Caption, color = AppColors.TextSecondary)
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete $title", tint = AppColors.TextTertiary)
            }
        }
    }
}

// --- Macros ---

/** Protein against an aim for the user's weight, and how the rest of the day's macro calories split between carbs and fat. */
@Composable
internal fun MacrosCard(entries: List<FoodEntry>, weightKg: Double?) {
    if (entries.isEmpty()) return
    val withMacros = entries.filter { it.hasMacros }
    Card {
        Text("Macros", style = AppType.Headline, color = AppColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        if (withMacros.isEmpty()) {
            Text("Add protein, carbs and fat to a food to see them here.", style = AppType.Caption, color = AppColors.TextSecondary)
            return@Card
        }
        val protein = withMacros.sumOf { it.proteinG!! }
        val carbs = withMacros.sumOf { it.carbsG!! }
        val fat = withMacros.sumOf { it.fatG!! }
        val kcal = protein * 4 + carbs * 4 + fat * 9
        val aim = proteinTargetG(weightKg)
        MacroBar("Protein", if (aim != null) "${protein.roundToInt()} / $aim g" else "${protein.roundToInt()} g", if (aim != null) (protein / aim).toFloat() else (protein * 4 / kcal).toFloat(), AppColors.Accent)
        Spacer(Modifier.height(12.dp))
        MacroBar("Carbs", "${carbs.roundToInt()} g · ${(carbs * 4 / kcal * 100).roundToInt()}%", (carbs * 4 / kcal).toFloat(), AppColors.SetWarmUp)
        Spacer(Modifier.height(12.dp))
        MacroBar("Fat", "${fat.roundToInt()} g · ${(fat * 9 / kcal * 100).roundToInt()}%", (fat * 9 / kcal).toFloat(), AppColors.SetDrop)
        Spacer(Modifier.height(10.dp))
        Text(
            buildString {
                if (aim != null) append("Protein aim is 1.8 g per kg of your weight. ")
                append("From ${withMacros.size} of ${entries.size} foods with macros entered.")
            },
            style = AppType.Caption,
            color = AppColors.TextTertiary,
        )
    }
}

@Composable
private fun MacroBar(label: String, value: String, fraction: Float, color: Color) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "macro")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppType.Body, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        Text(value, style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary)
    }
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(AppColors.SurfaceHigh)) {
        Box(Modifier.fillMaxWidth(animated.coerceAtLeast(0.02f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

// --- The week ---

/** Seven bars of calories eaten, each with a tick for what the day allowed; tap one to open that day. */
@Composable
internal fun WeekCard(summary: EnergySummary, goal: CalorieGoal, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val days = summary.days
    val allowed = days.map { day -> day.burnedKcal?.plus(goal.offsetKcal) }
    val top = max(1.0, days.indices.maxOf { max(days[it].eaten.toDouble(), allowed[it] ?: 0.0) }) * 1.12
    Column {
        Text("Last 7 days", style = AppType.Headline, color = AppColors.TextPrimary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Card {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .pointerInput(days) {
                        detectTapGestures { offset -> onSelect(days[(offset.x / (size.width / days.size)).toInt().coerceIn(0, days.lastIndex)].date) }
                    },
            ) {
                val slot = size.width / days.size
                val barWidth = slot * 0.46f
                days.forEachIndexed { i, day ->
                    val left = slot * i + (slot - barWidth) / 2
                    if (day.date == selected) {
                        drawRoundRect(AppColors.SurfaceHigh, Offset(slot * i + 2f, 0f), Size(slot - 4f, size.height), CornerRadius(12f))
                    }
                    val barHeight = if (day.isLogged) (day.eaten / top * size.height).toFloat().coerceAtLeast(6f) else 4f
                    val over = allowed[i]?.let { day.eaten > it } == true
                    drawRoundRect(
                        color = when {
                            !day.isLogged -> AppColors.Divider
                            over -> AppColors.Warning
                            else -> AppColors.Accent
                        },
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(8f),
                    )
                    allowed[i]?.let { limit ->
                        val y = size.height - (limit / top * size.height).toFloat()
                        drawLine(AppColors.TextPrimary.copy(alpha = 0.75f), Offset(left - 4f, y), Offset(left + barWidth + 4f, y), strokeWidth = 3f)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                days.forEach { day ->
                    Text(
                        if (day.date == LocalDate.now()) "Today" else day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3),
                        style = AppType.Caption.copy(fontSize = 11.sp),
                        color = if (day.date == selected) AppColors.TextPrimary else AppColors.TextTertiary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val balance = summary.avgBalance
            val perWeek = summary.kgPerWeek
            Text(
                if (balance == null || perWeek == null || summary.countedDays < 2) {
                    "Log food on a few days and your week adds up here. The line on each bar is what that day allowed."
                } else {
                    "You averaged ${kcalText(summary.avgEaten ?: 0.0)} eaten against ${kcalText(summary.avgBurned ?: 0.0)} burned: " +
                        "a ${if (balance <= 0) "deficit" else "surplus"} of ${kcalText(abs(balance))} a day, about " +
                        "${"%.2f".format(Locale.getDefault(), abs(perWeek))} kg a week ${if (balance <= 0) "lost" else "gained"} if it carries on. An estimate."
                },
                style = AppType.Caption,
                color = AppColors.TextSecondary,
            )
        }
    }
}
