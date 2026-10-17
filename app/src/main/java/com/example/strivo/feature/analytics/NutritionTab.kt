package com.example.strivo.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.AccentTitle
import com.example.strivo.core.ui.components.Card
import com.example.strivo.core.ui.components.StatTile
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.analytics.AnalyticsRange
import com.example.strivo.domain.analytics.AnalyticsReport
import com.example.strivo.domain.analytics.BodyReport
import com.example.strivo.domain.analytics.ExpenditureMinLoggedDays
import com.example.strivo.domain.analytics.ExpenditureMinWeighInDays
import com.example.strivo.domain.analytics.ExpenditureStatus
import com.example.strivo.domain.analytics.ExtraActivity
import com.example.strivo.domain.analytics.NutritionReport
import com.example.strivo.domain.coach.CalorieTargets
import com.example.strivo.domain.coach.calorieTargets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

// --- Nutrition ---

internal fun LazyListScope.nutritionTab(
    report: AnalyticsReport,
    body: BodyReport,
    nutrition: NutritionReport,
    expenditure: ExpenditureStatus?,
    onOpenFood: () -> Unit,
    onAddActivity: (ExtraActivity) -> Unit,
    onDeleteActivity: (Long) -> Unit,
) {
    item {
        Column {
            AccentTitle("Energy expenditure")
            Spacer(Modifier.height(12.dp))
            ExpenditureCard(expenditure, body.maintenanceKcal)
        }
    }
    calorieTargets(expenditure, body.maintenanceKcal)?.let { targets -> item { CalorieTargetsSection(targets) } }
    item {
        Column {
            AccentTitle("Calories eaten")
            Spacer(Modifier.height(12.dp))
            if (nutrition.isEmpty) {
                MissingData("Nothing logged ${nutrition.range.let { if (it == AnalyticsRange.Today) "today yet" else "in ${it.phrase}" }}. Log what you eat and your intake shows up here.")
            } else {
                EatenCard(nutrition, body.weight?.latest?.kg ?: body.profile.weightKg)
            }
            Spacer(Modifier.height(12.dp))
            AccentButton(
                text = "Log food",
                onClick = onOpenFood,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Add,
            )
        }
    }
    val burned = report.caloriesBurned
    item {
        Column {
            AccentTitle("Calories burned")
            Spacer(Modifier.height(12.dp))
            if (burned == null) {
                MissingData("Add your weight in Profile and Strivo will estimate the calories your workouts burn. You can also add other activity below.")
            } else if (burned <= 0.0) {
                MissingData("No workouts or other activity ${report.range.let { if (it == AnalyticsRange.Today) "today yet" else "in ${it.phrase}" }}, so there is nothing burned to show.")
            } else {
                CaloriesCard(report, burned)
            }
        }
    }
    item { OtherActivity(report, onAddActivity, onDeleteActivity) }
    item {
        Column {
            AccentTitle("Daily needs")
            Spacer(Modifier.height(12.dp))
            val bmr = body.bmrKcal
            val maintenance = body.maintenanceKcal
            if (bmr == null || maintenance == null) {
                MissingData("Add your gender, age, height and weight in Profile to estimate your daily calories.")
            } else {
                Card {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("At rest (BMR)", kcal(bmr), Modifier.weight(1f), background = AppColors.SurfaceHigh)
                        StatTile("Maintenance", kcal(maintenance), Modifier.weight(1f), background = AppColors.SurfaceHigh)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Activity level: ${body.activity.label}", style = AppType.BodyStrong, color = AppColors.TextPrimary)
                    Text(
                        "Chosen from how many days a week you trained over the last four weeks. " +
                            "Maintenance is roughly what you eat to stay at your current weight.",
                        style = AppType.Caption,
                        color = AppColors.TextSecondary,
                    )
                }
            }
        }
    }
    item {
        Text(
            "These are estimates, not measurements. Expenditure comes from your food log and trend weight " +
                "(7,700 kcal per kg). BMR uses the Mifflin-St Jeor equation, and workout calories are 5 MET × your weight × workout time. " +
                "Calories eaten are only what you log, and days with nothing logged are left out of the averages.",
            style = AppType.Caption,
            color = AppColors.TextTertiary,
        )
    }
}

/** What to eat a day to lose, hold or gain, from the expenditure above. */
@Composable
private fun CalorieTargetsSection(targets: CalorieTargets) {
    Column {
        AccentTitle("Calorie targets")
        Spacer(Modifier.height(12.dp))
        Card {
            TargetRow("Lose 0.5 kg a week", targets.loseKcal)
            HorizontalDivider(thickness = 1.dp, color = AppColors.Divider)
            TargetRow("Stay at your weight", targets.maintainKcal)
            HorizontalDivider(thickness = 1.dp, color = AppColors.Divider)
            TargetRow("Gain 0.25 kg a week", targets.gainKcal)
            Spacer(Modifier.height(8.dp))
            Text(
                if (targets.fromYourData) {
                    "From what you really burn, so they follow your own data. Lose or gain faster than this and you risk muscle or fat."
                } else {
                    "From the formula for now. Once Strivo has learned what you burn, these follow your own data."
                },
                style = AppType.Caption,
                color = AppColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun TargetRow(label: String, kcalPerDay: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppType.Body, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
        Text("${kcal(kcalPerDay)} a day", style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary)
    }
}

/**
 * What the user really burns, from their own intake and weight trend; until there is enough data, how far along
 * they are, with the formula estimate meanwhile.
 */
@Composable
private fun ExpenditureCard(status: ExpenditureStatus?, formulaKcal: Double?) {
    val estimate = status?.estimate
    Card {
        if (estimate != null) {
            Text("Estimated from your data", style = AppType.Caption, color = AppColors.TextSecondary)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%,.0f".format(Locale.getDefault(), estimate.kcal), style = AppType.MetricLarge, color = AppColors.TextPrimary)
                Spacer(Modifier.width(6.dp))
                Text("kcal a day", style = AppType.Body, color = AppColors.TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(12.dp))
            val balance = estimate.avgIntakeKcal - estimate.kcal
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Avg eaten", kcal(estimate.avgIntakeKcal), Modifier.weight(1f), background = AppColors.SurfaceHigh)
                StatTile("Trend", "${signed(estimate.trendChangeKg)} kg", Modifier.weight(1f), background = AppColors.SurfaceHigh)
                StatTile(if (balance <= 0) "Deficit" else "Surplus", kcal(abs(balance)), Modifier.weight(1f), background = AppColors.SurfaceHigh)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "You ate about ${kcal(estimate.avgIntakeKcal)} a day on ${estimate.loggedDays} logged days while your trend weight " +
                    "moved ${signed(estimate.trendChangeKg)} kg over ${estimate.weighInDays} days." +
                    (formulaKcal?.let { " The formula estimate is ${kcal(it)}." } ?: ""),
                style = AppType.Caption,
                color = AppColors.TextSecondary,
            )
        } else {
            Text("Learning what you burn", style = AppType.Headline, color = AppColors.TextPrimary)
            Text(
                "Log your food and weigh in regularly. Once there is enough, Strivo works out what you really burn from what you eat and how your weight moves.",
                style = AppType.Caption,
                color = AppColors.TextSecondary,
            )
            Spacer(Modifier.height(14.dp))
            val logged = status?.loggedDays ?: 0
            val span = status?.weighInSpanDays ?: 0
            ProgressLine("Days with food logged", logged, ExpenditureMinLoggedDays, "in the last ${status?.windowDays ?: 28} days")
            Spacer(Modifier.height(10.dp))
            ProgressLine("Days between weigh-ins", span, ExpenditureMinWeighInDays, "first to last")
            if (formulaKcal != null) {
                Spacer(Modifier.height(12.dp))
                Text("Meanwhile the formula estimate is ${kcal(formulaKcal)} a day.", style = AppType.Caption, color = AppColors.TextSecondary)
            }
        }
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
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(AppColors.SurfaceHigh)
    ) {
        Box(
            Modifier
                .fillMaxWidth((value.toFloat() / needed).coerceIn(0.02f, 1f))
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AppColors.Accent)
        )
    }
}

@Composable
private fun CaloriesCard(report: AnalyticsReport, burned: Double) {
    var selected by remember(report) {
        mutableIntStateOf(report.days.indexOfLast { it.burnedKcal > 0 }.takeIf { it >= 0 } ?: report.days.lastIndex)
    }
    Card {
        Text(kcal(burned), color = AppColors.TextPrimary, style = AppType.MetricLarge)
        val workoutCalories = report.workoutCalories
        Text(
            buildString {
                if (report.workouts > 0 && workoutCalories != null) {
                    append("Workouts ${kcal(workoutCalories)} (estimate, about ${kcal(workoutCalories / report.workouts)} each)")
                }
                if (report.extraCalories > 0) {
                    if (isNotEmpty()) append(" + ")
                    append("other activity ${kcal(report.extraCalories)}")
                }
            },
            color = AppColors.TextSecondary,
            fontSize = 13.sp,
        )
        if (report.workouts > 0 && workoutCalories == null) {
            Spacer(Modifier.height(4.dp))
            Text("Add your weight in Profile to include workout calories.", color = AppColors.TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        val day = report.days.getOrNull(selected)
        Text(day?.let { dayHeadline(it) }.orEmpty(), color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            day?.let { d ->
                val parts = buildList {
                    if (d.workouts > 0 && d.kcal > 0) add("${d.planNames.joinToString(" + ")} ${kcal(d.kcal)}")
                    d.extras.forEach { add("${it.name} ${kcal(it.calories.toDouble())}") }
                }
                if (parts.isEmpty()) "Nothing burned logged" else parts.joinToString(" · ")
            }.orEmpty(),
            color = AppColors.TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        BarChart(
            values = report.days.map { it.burnedKcal },
            descriptions = report.days.map { dayHeadline(it) },
            format = ::kcal,
            selected = selected,
            onSelect = { selected = it },
        )
        Spacer(Modifier.height(8.dp))
        AxisLabels(rangeAxisLabels(report.days.map { it.date }), selected)
    }
}

@Composable
private fun EatenCard(nutrition: NutritionReport, weightKg: Double?) {
    var selected by remember(nutrition) {
        mutableIntStateOf(nutrition.days.indexOfLast { it.isLogged }.takeIf { it >= 0 } ?: nutrition.days.lastIndex)
    }
    Card {
        val average = nutrition.avgCaloriesPerLoggedDay ?: 0.0
        Text("${kcal(average)} a day", color = AppColors.TextPrimary, style = AppType.Metric)
        Text(
            "Average over ${nutrition.loggedDays} of ${nutrition.range.days} days with food logged",
            color = AppColors.TextSecondary,
            fontSize = 13.sp,
        )
        val difference = nutrition.avgDifferenceFromMaintenance
        val maintenance = nutrition.maintenanceKcal
        if (difference != null && maintenance != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${kcal(abs(difference))} ${if (difference <= 0) "under" else "over"} your maintenance of about ${kcal(maintenance)}",
                color = AppColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(16.dp))
        val day = nutrition.days.getOrNull(selected)
        Text(day?.let { dayLabel(it.date) }.orEmpty(), color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            day?.let { if (it.isLogged) "${kcal(it.calories.toDouble())} · ${it.entries.size} foods" else "Nothing logged" }.orEmpty(),
            color = AppColors.TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        BarChart(
            values = nutrition.days.map { it.calories.toDouble() },
            descriptions = nutrition.days.map { dayLabel(it.date) },
            format = ::kcal,
            selected = selected,
            onSelect = { selected = it },
        )
        Spacer(Modifier.height(8.dp))
        AxisLabels(rangeAxisLabels(nutrition.days.map { it.date }), selected)
        val macros = nutrition.avgMacros
        if (macros != null) {
            Spacer(Modifier.height(20.dp))
            Text("Average macros per day", style = AppType.Caption, color = AppColors.TextSecondary)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Protein", "${macros.proteinG.toInt()} g · ${macros.proteinPercent.toInt()}%", Modifier.weight(1f), background = AppColors.SurfaceHigh)
                StatTile("Carbs", "${macros.carbsG.toInt()} g · ${macros.carbsPercent.toInt()}%", Modifier.weight(1f), background = AppColors.SurfaceHigh)
                StatTile("Fat", "${macros.fatG.toInt()} g · ${macros.fatPercent.toInt()}%", Modifier.weight(1f), background = AppColors.SurfaceHigh)
            }
            if (weightKg != null && weightKg > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "%.1f g of protein per kg of body weight".format(Locale.getDefault(), macros.proteinG / weightKg),
                    style = AppType.BodyStrong,
                    color = AppColors.TextPrimary,
                )
                Text("1.6 to 2.2 g per kg is the range usually advised for building muscle.", style = AppType.Caption, color = AppColors.TextSecondary)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "From ${nutrition.foodsWithMacros} of ${nutrition.foodsLogged} logged foods that have macros entered.",
                color = AppColors.TextSecondary,
                fontSize = 11.sp,
            )
        }
    }
}

internal fun dayLabel(date: LocalDate): String =
    if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
