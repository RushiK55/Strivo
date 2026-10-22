package com.example.strivo.feature.analytics

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.analytics.AnalyticsReport
import com.example.strivo.domain.analytics.BodyReport
import com.example.strivo.domain.analytics.EnergySummary
import com.example.strivo.domain.analytics.ExpenditureStatus
import com.example.strivo.domain.analytics.ExtraActivity
import com.example.strivo.domain.analytics.NutritionReport
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// --- Nutrition ---

/**
 * Four things, each its own shape: the balance of eaten against burned, what the eating is made of, what the body
 * burns and what to eat for a goal, and the activity added by hand.
 */
internal fun LazyListScope.nutritionTab(
    report: AnalyticsReport,
    body: BodyReport,
    nutrition: NutritionReport,
    expenditure: ExpenditureStatus?,
    energy: EnergySummary?,
    onOpenFood: () -> Unit,
    onAddActivity: (ExtraActivity) -> Unit,
    onDeleteActivity: (Long) -> Unit,
) {
    val weightKg = body.weight?.latest?.kg ?: body.profile.weightKg
    item { EnergySection(energy, report.range, body.weeklyRateKg) }
    item { EatingSection(nutrition, weightKg, onOpenFood) }
    item { BurnSection(expenditure, body) }
    item { OtherActivity(report, weightKg, onAddActivity, onDeleteActivity) }
    item {
        Text(
            "These are estimates, not measurements. What you burn comes from your food log and trend weight (7,700 kcal per kg), " +
                "or from the Mifflin-St Jeor formula until there is enough. Workout calories are (5 − 1) MET × your weight × workout time, " +
                "the part above resting. Days with nothing logged are left out of the averages.",
            style = AppType.Caption,
            color = AppColors.TextTertiary,
        )
    }
}

internal fun dayLabel(date: LocalDate): String =
    if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
