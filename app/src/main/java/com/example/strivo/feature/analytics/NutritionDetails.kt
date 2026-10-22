package com.example.strivo.feature.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.components.AccentTitle
import com.example.strivo.core.ui.components.Card
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.analytics.BodyReport
import com.example.strivo.domain.analytics.ExpenditureMinLoggedDays
import com.example.strivo.domain.analytics.ExpenditureMinWeighInDays
import com.example.strivo.domain.analytics.ExpenditureStatus
import com.example.strivo.domain.analytics.MacroSplit
import com.example.strivo.domain.analytics.NutritionReport
import com.example.strivo.domain.analytics.AnalyticsRange
import com.example.strivo.domain.analytics.proteinTargetG
import com.example.strivo.domain.coach.calorieTargets
import java.util.Locale
import kotlin.math.roundToInt

// --- What you eat: how often it is logged and what it is made of ---

@Composable
internal fun EatingSection(nutrition: NutritionReport, weightKg: Double?, onOpenFood: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentTitle("What you eat", modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenFood) { Text("Log food", style = AppType.BodyStrong, color = AppColors.Accent) }
        }
        Spacer(Modifier.height(8.dp))
        if (nutrition.isEmpty) {
            MissingData("Nothing logged ${if (nutrition.range == AnalyticsRange.Today) "today yet" else "in ${nutrition.range.phrase}"}. Log what you eat and your macros show up here.")
            return@Column
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Logged", style = AppType.Body, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
                Text("${nutrition.loggedDays} of ${nutrition.range.days} days", style = AppType.BodyStrong, color = AppColors.TextPrimary)
            }
            if (nutrition.days.size > 1) {
                Spacer(Modifier.height(8.dp))
                LoggedStrip(nutrition.days.map { it.isLogged })
            }
            val macros = nutrition.avgMacros
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(thickness = 1.dp, color = AppColors.Divider)
            Spacer(Modifier.height(18.dp))
            if (macros == null) {
                Text("Add protein, carbs and fat to the foods you log to see how your day splits.", style = AppType.Caption, color = AppColors.TextSecondary)
            } else {
                MacroSplitBar(macros)
                val aim = proteinTargetG(weightKg)
                if (aim != null) {
                    Spacer(Modifier.height(18.dp))
                    ProteinAim(macros.proteinG, aim, weightKg)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Averages per logged day, from ${nutrition.foodsWithMacros} of ${nutrition.foodsLogged} foods that have macros entered.",
                    style = AppType.Caption,
                    color = AppColors.TextTertiary,
                )
            }
        }
    }
}

/** One thin block per day of the range: filled where food was logged. Shows at a glance how steady the logging was. */
@Composable
private fun LoggedStrip(logged: List<Boolean>) {
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val gap = (if (logged.size > 31) 1.dp else 3.dp).toPx()
        val width = (size.width - gap * (logged.size - 1)) / logged.size
        logged.forEachIndexed { index, isLogged ->
            drawRoundRect(
                color = if (isLogged) AppColors.Accent else AppColors.Field,
                topLeft = Offset(index * (width + gap), 0f),
                size = Size(width, size.height),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}

private val ProteinColor get() = AppColors.Accent
private val CarbsColor get() = AppColors.SetWarmUp
private val FatColor get() = AppColors.SetDrop

/** Protein, carbs and fat as one bar split by share of calories, with the grams under each. */
@Composable
private fun MacroSplitBar(macros: MacroSplit) {
    val parts = listOf(
        Triple("Protein", macros.proteinPercent to macros.proteinG, ProteinColor),
        Triple("Carbs", macros.carbsPercent to macros.carbsG, CarbsColor),
        Triple("Fat", macros.fatPercent to macros.fatG, FatColor),
    )
    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(AppColors.SurfaceHigh), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        parts.forEach { (_, share, color) ->
            if (share.first > 0) Box(Modifier.weight(share.first.toFloat()).fillMaxHeight().background(color))
        }
    }
    Spacer(Modifier.height(12.dp))
    Row {
        parts.forEach { (label, share, color) ->
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = AppType.Caption, color = AppColors.TextSecondary)
                }
                Text("${share.second.roundToInt()} g", style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary)
                Text("${share.first.roundToInt()}%", style = AppType.Caption, color = AppColors.TextTertiary)
            }
        }
    }
}

/** The average protein against the usual aim for the user's weight. */
@Composable
private fun ProteinAim(proteinG: Double, aimG: Int, weightKg: Double?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Protein aim", style = AppType.Body, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        Text("${proteinG.roundToInt()} / $aimG g", style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary)
    }
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(AppColors.SurfaceHigh)) {
        Box(Modifier.fillMaxWidth((proteinG / aimG).toFloat().coerceIn(0.02f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(ProteinColor))
    }
    Spacer(Modifier.height(4.dp))
    Text(
        buildString {
            if (weightKg != null && weightKg > 0) append("%.1f g per kg of your weight. ".format(Locale.getDefault(), proteinG / weightKg))
            append("1.8 g per kg is the aim; 1.6 to 2.2 is the range usually advised for building muscle.")
        },
        style = AppType.Caption,
        color = AppColors.TextSecondary,
    )
}

// --- What you burn: the daily figure and what to eat for each goal ---

@Composable
internal fun BurnSection(expenditure: ExpenditureStatus?, body: BodyReport) {
    Column {
        AccentTitle("What you burn")
        Spacer(Modifier.height(12.dp))
        val formula = body.maintenanceKcal
        val learned = expenditure?.estimate
        if (learned == null && formula == null && expenditure == null) {
            MissingData("Add your gender, age, height and weight in Profile to estimate your daily calories.")
            return@Column
        }
        Card {
            val daily = learned?.kcal ?: formula
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("A normal day", style = AppType.Caption, color = AppColors.TextSecondary)
                    if (daily != null) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("%,.0f".format(Locale.getDefault(), daily), style = AppType.MetricLarge, color = AppColors.TextPrimary)
                            Text(" kcal", style = AppType.Body, color = AppColors.TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                }
                Text(
                    if (learned != null) "From your data" else "From the formula",
                    style = AppType.Caption,
                    color = if (learned != null) AppColors.Success else AppColors.TextSecondary,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(AppColors.SurfaceHigh).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            if (learned != null) {
                Text(
                    "You ate about ${kcal(learned.avgIntakeKcal)} a day on ${learned.loggedDays} logged days while your trend weight " +
                        "moved ${signed(learned.trendChangeKg)} kg over ${learned.weighInDays} days." +
                        (formula?.let { " The formula says ${kcal(it)}." } ?: ""),
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                )
            } else {
                Text(
                    "Log your food and weigh in regularly, and Strivo works out what you really burn from what you eat and how your weight moves.",
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                )
                Spacer(Modifier.height(14.dp))
                ProgressLine("Days with food logged", expenditure?.loggedDays ?: 0, ExpenditureMinLoggedDays, "in the last ${expenditure?.windowDays ?: 28} days")
                Spacer(Modifier.height(10.dp))
                ProgressLine("Days between weigh-ins", expenditure?.weighInSpanDays ?: 0, ExpenditureMinWeighInDays, "first to last")
            }
            calorieTargets(expenditure, formula)?.let { targets ->
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(thickness = 1.dp, color = AppColors.Divider)
                Text("To eat for", style = AppType.Caption, color = AppColors.TextSecondary, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                TargetRow("Lose 0.5 kg a week", targets.loseKcal)
                TargetRow("Stay at your weight", targets.maintainKcal)
                TargetRow("Gain 0.25 kg a week", targets.gainKcal)
            }
            val bmr = body.bmrKcal
            if (bmr != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "At rest you use ${kcal(bmr)}. Activity level: ${body.activity.label.lowercase()}, from how many days a week you trained over the last four weeks.",
                    style = AppType.Caption,
                    color = AppColors.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun TargetRow(label: String, kcalPerDay: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppType.Body, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        Text("${kcal(kcalPerDay)} a day", style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary)
    }
}

@Composable
private fun ProgressLine(label: String, value: Int, needed: Int, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppType.Body, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        Text("${minOf(value, needed)} / $needed", style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary)
    }
    Text(detail, style = AppType.Caption, color = AppColors.TextTertiary)
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(AppColors.SurfaceHigh)) {
        Box(Modifier.fillMaxWidth((value.toFloat() / needed).coerceIn(0.02f, 1f)).height(5.dp).clip(RoundedCornerShape(3.dp)).background(AppColors.Accent))
    }
}
