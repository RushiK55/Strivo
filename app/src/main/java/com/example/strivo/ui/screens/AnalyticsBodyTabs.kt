package com.example.strivo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.data.analytics.AnalyticsReport
import com.example.strivo.data.analytics.BmiCategory
import com.example.strivo.data.analytics.BmiResult
import com.example.strivo.data.analytics.BodyReport
import com.example.strivo.data.analytics.ExtraActivity
import com.example.strivo.data.analytics.NutritionReport
import com.example.strivo.data.analytics.WeightEntry
import com.example.strivo.ui.components.AccentButton
import com.example.strivo.ui.components.AccentTitle
import com.example.strivo.ui.components.StrivoAlertDialog
import com.example.strivo.ui.theme.AppColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

/* Body, progress and calories pages of the analytics screen. Each states what it needs when data is missing. */

private fun fixed1(value: Double) = "%.1f".format(Locale.getDefault(), value)
private fun kcal(value: Double) = "%,.0f kcal".format(Locale.getDefault(), value)
private fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@Composable
private fun MissingData(message: String) {
    Card {
        Text(message, color = AppColors.TextSecondary, fontSize = 14.sp)
    }
}

// --- Body: BMI ---

internal fun LazyListScope.bodyTab(body: BodyReport) {
    item {
        Column {
            AccentTitle("BODY MASS INDEX")
            Spacer(Modifier.height(16.dp))
            val bmi = body.bmi
            if (bmi == null) {
                MissingData("Add your height and weight in Profile to see your BMI.")
            } else {
                BmiCard(bmi, body.profile.weightKg ?: 0.0)
            }
        }
    }
    item {
        Column {
            AccentTitle("YOUR DETAILS")
            Spacer(Modifier.height(16.dp))
            val p = body.profile
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Height", p.heightCm?.let { "${fixed1(it)} cm" } ?: "--", Modifier.weight(1f))
                    StatTile("Weight", p.weightKg?.let { "${fixed1(it)} kg" } ?: "--", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Age", p.age?.let { "$it yrs" } ?: "--", Modifier.weight(1f))
                    StatTile("Gender", p.gender ?: "--", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BmiCard(bmi: BmiResult, weightKg: Double) {
    Card {
        Text(fixed1(bmi.bmi), color = AppColors.TextPrimary, fontSize = 48.sp, fontWeight = FontWeight.Black)
        Text(bmi.category.label, color = AppColors.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        BmiGauge(bmi)
        Spacer(Modifier.height(20.dp))
        Text(
            "Healthy weight for your height: ${bmi.healthyMinKg.toInt()}-${bmi.healthyMaxKg.toInt()} kg",
            color = AppColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        val delta = bmi.kgToHealthyRange
        Text(
            when {
                delta == 0.0 -> "At ${fixed1(weightKg)} kg you are inside that range."
                delta > 0 -> "At ${fixed1(weightKg)} kg you are ${fixed1(delta)} kg below it."
                else -> "At ${fixed1(weightKg)} kg you are ${fixed1(abs(delta))} kg above it."
            },
            color = AppColors.TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "BMI does not tell muscle from fat, so it can read high for people with a lot of muscle.",
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
        )
    }
}

/** A bar split into the four WHO categories with a marker where the user's BMI falls. */
@Composable
private fun BmiGauge(bmi: BmiResult) {
    val scaleMin = 15.0
    val scaleMax = 40.0
    // Category edges on the BMI scale.
    val edges = listOf(scaleMin, 18.5, 25.0, 30.0, scaleMax)
    val categories = BmiCategory.entries
    val shortLabels = listOf("Under", "Normal", "Over", "Obese")
    val position = ((bmi.bmi.coerceIn(scaleMin, scaleMax) - scaleMin) / (scaleMax - scaleMin)).toFloat()

    Column(Modifier.semantics { contentDescription = "BMI ${fixed1(bmi.bmi)}, ${bmi.category.label}" }) {
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val barTop = 8.dp.toPx()
            val barHeight = 8.dp.toPx()
            val gap = 2.dp.toPx()
            categories.forEachIndexed { index, category ->
                val start = ((edges[index] - scaleMin) / (scaleMax - scaleMin)).toFloat() * size.width
                val end = ((edges[index + 1] - scaleMin) / (scaleMax - scaleMin)).toFloat() * size.width
                drawRoundRect(
                    color = if (category == bmi.category) AppColors.Accent else AppColors.Field,
                    topLeft = Offset(start + if (index == 0) 0f else gap / 2, barTop),
                    size = androidx.compose.ui.geometry.Size(end - start - (if (index == 0 || index == categories.lastIndex) gap / 2 else gap), barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                )
            }
            // Marker: a white line with a dot on top, ringed in the card colour.
            val x = position * size.width
            drawLine(AppColors.TextPrimary, Offset(x, 6.dp.toPx()), Offset(x, 18.dp.toPx()), strokeWidth = 2.dp.toPx())
            drawCircle(AppColors.Surface, radius = 5.dp.toPx(), center = Offset(x, 4.dp.toPx()))
            drawCircle(AppColors.TextPrimary, radius = 3.dp.toPx(), center = Offset(x, 4.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth()) {
            categories.indices.forEach { index ->
                Text(
                    shortLabels[index],
                    modifier = Modifier.weight((edges[index + 1] - edges[index]).toFloat()),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    fontWeight = if (categories[index] == bmi.category) FontWeight.Bold else FontWeight.Medium,
                    color = if (categories[index] == bmi.category) AppColors.TextPrimary else AppColors.TextSecondary,
                )
            }
        }
    }
}

// --- Progress ---

internal fun LazyListScope.progressTab(body: BodyReport) {
    item { WeightSection(body) }
    item { WeeklyVolumeSection(body) }
    item { StrengthSection(body) }
}

@Composable
private fun WeightSection(body: BodyReport) {
    Column {
        AccentTitle("WEIGHT")
        Spacer(Modifier.height(16.dp))
        val progress = body.weight
        if (progress == null) {
            MissingData("Add your weight in Profile and it will be tracked here every time you update it.")
            return
        }
        Card {
            Text("${fixed1(progress.latest.kg)} kg", color = AppColors.TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.Black)
            Text("Weighed in on ${shortDate(progress.latest.date)}", color = AppColors.TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            if (progress.hasTrend) {
                val change = progress.changeKg
                val icon = when {
                    change > 0.05 -> Icons.Rounded.TrendingUp
                    change < -0.05 -> Icons.Rounded.TrendingDown
                    else -> Icons.Rounded.TrendingFlat
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = AppColors.TextPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${if (change > 0) "+" else ""}${fixed1(change)} kg since ${shortDate(progress.first.date)}",
                        color = AppColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(16.dp))
                WeightLineChart(progress.entries)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Highest", "${fixed1(progress.highKg)} kg", Modifier.weight(1f))
                    StatTile("Lowest", "${fixed1(progress.lowKg)} kg", Modifier.weight(1f))
                }
            } else {
                Text(
                    "This is your first weigh-in. Update your weight each week and the trend will appear here.",
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/** Weight over time: a thin line with a dot per weigh-in; tap a dot to read it. */
@Composable
private fun WeightLineChart(entries: List<WeightEntry>) {
    var selected by remember(entries) { mutableIntStateOf(entries.lastIndex) }
    val first = entries.first().date
    val spanDays = maxOf(1L, entries.last().date.toEpochDay() - first.toEpochDay()).toFloat()
    val lowest = entries.minOf { it.kg }
    val highest = entries.maxOf { it.kg }
    // Pad the scale so the line does not touch the top and bottom, and a flat history still draws mid-height.
    val pad = maxOf(0.5, (highest - lowest) * 0.15)
    val scaleMin = lowest - pad
    val scaleMax = highest + pad
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = AppColors.TextSecondary, fontSize = 10.sp)
    val summary = entries.joinToString(" ") { "${shortDate(it.date)}: ${fixed1(it.kg)} kg." }

    Text(
        "${shortDate(entries[selected].date)} · ${fixed1(entries[selected].kg)} kg",
        color = AppColors.TextSecondary,
        fontSize = 13.sp,
    )
    Spacer(Modifier.height(8.dp))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .semantics { contentDescription = "Weight over time. $summary" }
            .pointerInput(entries) {
                detectTapGestures { tap ->
                    val inset = 8.dp.toPx()
                    val width = size.width - 2 * inset
                    selected = entries.indices.minByOrNull { index ->
                        abs(inset + (entries[index].date.toEpochDay() - first.toEpochDay()) / spanDays * width - tap.x)
                    } ?: selected
                }
            },
    ) {
        val inset = 8.dp.toPx()
        val width = size.width - 2 * inset
        val labelRoom = 14.dp.toPx()
        val chartTop = labelRoom
        val chartHeight = size.height - labelRoom
        fun x(entry: WeightEntry) = inset + (entry.date.toEpochDay() - first.toEpochDay()) / spanDays * width
        fun y(kg: Double) = chartTop + chartHeight * (1 - ((kg - scaleMin) / (scaleMax - scaleMin)).toFloat())

        listOf(highest, lowest).distinct().forEach { kg ->
            drawLine(AppColors.Field, Offset(0f, y(kg)), Offset(size.width, y(kg)), strokeWidth = 1.dp.toPx())
        }
        drawText(measurer, "${fixed1(highest)} kg", Offset(0f, 0f), style = labelStyle)

        for (i in 0 until entries.lastIndex) {
            drawLine(AppColors.Accent, Offset(x(entries[i]), y(entries[i].kg)), Offset(x(entries[i + 1]), y(entries[i + 1].kg)), strokeWidth = 2.dp.toPx())
        }
        entries.forEachIndexed { index, entry ->
            val centre = Offset(x(entry), y(entry.kg))
            val isSelected = index == selected
            // A ring in the card colour keeps neighbouring dots apart.
            drawCircle(AppColors.Surface, radius = (if (isSelected) 7 else 5).dp.toPx(), center = centre)
            drawCircle(AppColors.Accent, radius = (if (isSelected) 5 else 4).dp.toPx(), center = centre)
        }
    }
}

@Composable
private fun WeeklyVolumeSection(body: BodyReport) {
    Column {
        AccentTitle("WEEKLY VOLUME")
        Spacer(Modifier.height(16.dp))
        val weeks = body.weeks
        if (weeks.all { it.volumeKg == 0.0 && it.workouts == 0 }) {
            MissingData("Finish workouts and your volume for each week shows up here.")
            return
        }
        var selected by remember(weeks) { mutableIntStateOf(weeks.indexOfLast { it.workouts > 0 }.takeIf { it >= 0 } ?: weeks.lastIndex) }
        Card {
            val week = weeks[selected]
            Text("Week of ${shortDate(week.weekStart)}", color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "${formatKg(week.volumeKg)} · ${if (week.workouts == 1) "1 workout" else "${week.workouts} workouts"}",
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            BarChart(
                values = weeks.map { it.volumeKg },
                descriptions = weeks.map { "Week of ${shortDate(it.weekStart)}" },
                format = ::formatKg,
                selected = selected,
                onSelect = { selected = it },
            )
            Spacer(Modifier.height(8.dp))
            AxisLabels(weeks.map { it.weekStart.dayOfMonth.toString() }, selected)
            Spacer(Modifier.height(4.dp))
            Text("Number shown is the Monday each week starts on.", color = AppColors.TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StrengthSection(body: BodyReport) {
    Column {
        AccentTitle("STRENGTH PROGRESS")
        Spacer(Modifier.height(16.dp))
        val rows = body.strength.take(6)
        if (rows.isEmpty()) {
            MissingData("Do the same weighted exercise in two workouts and your heaviest weight is compared here.")
            return
        }
        Card {
            rows.forEachIndexed { index, item ->
                if (index > 0) Spacer(Modifier.height(16.dp))
                val change = item.changeKg
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            buildString {
                                append("${fixed1(item.firstBestKg)} → ${fixed1(item.latestBestKg)} kg · ${item.sessions} workouts")
                                item.latestEstimatedMaxKg?.let { append(" · est. max ${fixed1(it)} kg") }
                            },
                            color = AppColors.TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Icon(
                        when {
                            change > 0 -> Icons.Rounded.TrendingUp
                            change < 0 -> Icons.Rounded.TrendingDown
                            else -> Icons.Rounded.TrendingFlat
                        },
                        contentDescription = when {
                            change > 0 -> "Up"
                            change < 0 -> "Down"
                            else -> "Unchanged"
                        },
                        tint = when {
                            change > 0 -> AppColors.Success
                            change < 0 -> AppColors.Danger
                            else -> AppColors.TextSecondary
                        },
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${if (change > 0) "+" else ""}${fixed1(change)} kg",
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Heaviest weight in the first workout compared with the latest. The estimated max is the best set of the latest workout by the Epley formula.",
                color = AppColors.TextSecondary,
                fontSize = 11.sp,
            )
        }
    }
}

// --- Calories ---

internal fun LazyListScope.caloriesTab(
    report: AnalyticsReport,
    body: BodyReport,
    nutrition: NutritionReport,
    onOpenFood: () -> Unit,
    onAddActivity: (ExtraActivity) -> Unit,
    onDeleteActivity: (Long) -> Unit,
) {
    val burned = report.caloriesBurned
    item {
        Column {
            AccentTitle("CALORIES BURNED")
            Spacer(Modifier.height(16.dp))
            if (burned == null) {
                MissingData("Add your weight in Profile and Strivo will estimate the calories your workouts burn. You can also add other activity below.")
            } else if (burned <= 0.0) {
                MissingData("No workouts or other activity in the last ${report.range.label}, so there is nothing burned to show.")
            } else {
                CaloriesCard(report, burned)
            }
        }
    }
    item { OtherActivity(report, onAddActivity, onDeleteActivity) }
    item {
        Column {
            AccentTitle("CALORIES EATEN")
            Spacer(Modifier.height(16.dp))
            if (nutrition.isEmpty) {
                MissingData("Nothing logged in the last ${nutrition.range.label}. Log what you eat and your intake shows up here.")
            } else {
                EatenCard(nutrition)
            }
            Spacer(Modifier.height(12.dp))
            AccentButton(
                text = "LOG FOOD",
                onClick = onOpenFood,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Add,
            )
        }
    }
    item {
        Column {
            AccentTitle("DAILY NEEDS")
            Spacer(Modifier.height(16.dp))
            val bmr = body.bmrKcal
            val maintenance = body.maintenanceKcal
            if (bmr == null || maintenance == null) {
                MissingData("Add your gender, age, height and weight in Profile to estimate your daily calories.")
            } else {
                Card {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("At rest (BMR)", kcal(bmr), Modifier.weight(1f))
                        StatTile("Maintenance", kcal(maintenance), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Activity level: ${body.activity.label}", color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Chosen from how many days a week you trained over the last four weeks. " +
                            "Maintenance is roughly what you eat to stay at your current weight.",
                        color = AppColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
    item {
        Text(
            "These are estimates, not measurements. BMR uses the Mifflin-St Jeor equation, and workout calories are " +
                "5 MET × your weight × workout time. Calories eaten are only what you log, and days with nothing logged are left out of the averages.",
            color = AppColors.TextSecondary,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun CaloriesCard(report: AnalyticsReport, burned: Double) {
    var selected by remember(report) {
        mutableIntStateOf(report.days.indexOfLast { it.burnedKcal > 0 }.takeIf { it >= 0 } ?: report.days.lastIndex)
    }
    Card {
        Text(kcal(burned), color = AppColors.TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Black)
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
        AxisLabels(
            report.days.mapIndexed { index, day ->
                when {
                    report.days.size <= 7 -> day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())
                    index % 5 == 0 || index == report.days.lastIndex -> day.date.dayOfMonth.toString()
                    else -> ""
                }
            },
            selected,
        )
    }
}

// --- Other activity: calories the user adds by hand ---

@Composable
private fun OtherActivity(report: AnalyticsReport, onAdd: (ExtraActivity) -> Unit, onDelete: (Long) -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ExtraActivity?>(null) }
    val entries = report.days.asReversed().flatMap { it.extras.asReversed() }

    Column {
        AccentTitle("OTHER ACTIVITY")
        Spacer(Modifier.height(8.dp))
        Text(
            "Burned calories outside your tracked workouts: a run, a walk, a match. Enter the number from your watch or machine.",
            color = AppColors.TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        if (entries.isNotEmpty()) {
            Card {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                listOfNotNull(dayLabel(entry.date), entry.minutes?.let { "$it min" }).joinToString(" · "),
                                color = AppColors.TextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                        Text(kcal(entry.calories.toDouble()), color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IconButton(onClick = { toDelete = entry }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ${entry.name}", tint = AppColors.Danger)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        AccentButton(
            text = "ADD ACTIVITY",
            onClick = { showSheet = true },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Add,
        )
    }

    if (showSheet) {
        ActivitySheet(onDismiss = { showSheet = false }, onSave = { showSheet = false; onAdd(it) })
    }
    toDelete?.let { entry ->
        StrivoAlertDialog(
            title = "Delete activity?",
            onDismiss = { toDelete = null },
            confirmText = "Delete",
            confirmColor = AppColors.Danger,
            onConfirm = {
                toDelete = null
                entry.id?.let(onDelete)
            },
        ) {
            Text("Remove '${entry.name}' (${entry.calories} kcal)?", color = AppColors.TextSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivitySheet(onDismiss: () -> Unit, onSave: (ExtraActivity) -> Unit) {
    val today = LocalDate.now()
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today) }
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
            Text("Add activity", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            // Names only, to save typing; no calorie values are suggested.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Walking", "Running", "Cycling", "Swimming", "Sports", "Yoga").forEach { option ->
                    Chip(option, selected = name == option) { name = option; error = null }
                }
            }
            FoodField("Activity", name, KeyboardType.Text) { name = it; error = null }
            FoodField("Calories burned (kcal)", calories, KeyboardType.Number) {
                calories = it.filter(Char::isDigit).take(5)
                error = null
            }
            FoodField("Minutes (optional)", minutes, KeyboardType.Number) {
                minutes = it.filter(Char::isDigit).take(4)
                error = null
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { date = date.minusDays(1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Earlier day", tint = AppColors.TextPrimary)
                }
                Text(
                    dayLabel(date),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { date = date.plusDays(1) }, enabled = date < today) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Later day",
                        tint = if (date < today) AppColors.TextPrimary else AppColors.TextSecondary.copy(alpha = 0.3f),
                    )
                }
            }
            error?.let { Text(it, color = AppColors.Danger, fontSize = 13.sp) }
            AccentButton(
                text = "SAVE",
                onClick = {
                    val kcal = calories.toIntOrNull()
                    when {
                        name.isBlank() -> error = "Enter what you did."
                        kcal == null || kcal !in 1..20000 -> error = "Enter the calories burned."
                        else -> onSave(ExtraActivity(date = date, name = name.trim(), calories = kcal, minutes = minutes.toIntOrNull()?.takeIf { it > 0 }))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                height = 56.dp,
            )
        }
    }
}

@Composable
private fun EatenCard(nutrition: NutritionReport) {
    var selected by remember(nutrition) {
        mutableIntStateOf(nutrition.days.indexOfLast { it.isLogged }.takeIf { it >= 0 } ?: nutrition.days.lastIndex)
    }
    Card {
        val average = nutrition.avgCaloriesPerLoggedDay ?: 0.0
        Text("${kcal(average)} a day", color = AppColors.TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Black)
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
        AxisLabels(
            nutrition.days.mapIndexed { index, d ->
                when {
                    nutrition.days.size <= 7 -> d.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())
                    index % 5 == 0 || index == nutrition.days.lastIndex -> d.date.dayOfMonth.toString()
                    else -> ""
                }
            },
            selected,
        )
        val macros = nutrition.avgMacros
        if (macros != null) {
            Spacer(Modifier.height(20.dp))
            Text("AVERAGE MACROS PER DAY", color = AppColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Protein", "${macros.proteinG.toInt()} g · ${macros.proteinPercent.toInt()}%", Modifier.weight(1f))
                StatTile("Carbs", "${macros.carbsG.toInt()} g · ${macros.carbsPercent.toInt()}%", Modifier.weight(1f))
                StatTile("Fat", "${macros.fatG.toInt()} g · ${macros.fatPercent.toInt()}%", Modifier.weight(1f))
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

private fun dayLabel(date: LocalDate): String =
    if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
