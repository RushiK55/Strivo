package com.example.strivo.feature.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.core.ui.components.AccentTitle
import com.example.strivo.core.ui.components.Card
import com.example.strivo.core.ui.components.DecimalWheelPicker
import com.example.strivo.core.ui.components.WheelDialog
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.analytics.BmiCategory
import com.example.strivo.domain.analytics.BmiResult
import com.example.strivo.domain.analytics.BodyProfile
import com.example.strivo.domain.analytics.BodyReport
import kotlin.math.abs

// --- Body ---

/** A value in the Body tab the user can change right there. */
enum class BodyEdit { Weight, Height }

internal fun LazyListScope.bodyTab(body: BodyReport, onEdit: (BodyEdit) -> Unit) {
    item { WeightSection(body, onUpdate = { onEdit(BodyEdit.Weight) }) }
    item {
        Column {
            AccentTitle("Body mass index")
            Spacer(Modifier.height(12.dp))
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
            AccentTitle("Your details")
            Spacer(Modifier.height(12.dp))
            DetailsCard(body.profile, onEditHeight = { onEdit(BodyEdit.Height) }, onEditWeight = { onEdit(BodyEdit.Weight) })
        }
    }
}

@Composable
private fun BmiCard(bmi: BmiResult, weightKg: Double) {
    Card {
        Text(fixed1(bmi.bmi), color = AppColors.TextPrimary, style = AppType.MetricLarge)
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

// --- Editing height and weight ---

/** Height and weight can be changed right here; age and gender are in Profile. */
@Composable
private fun DetailsCard(profile: BodyProfile, onEditHeight: () -> Unit, onEditWeight: () -> Unit) {
    Card(padding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
        DetailRow("Height", profile.heightCm?.let { "${fixed1(it)} cm" } ?: "Add", onEditHeight)
        HorizontalDivider(Modifier.padding(horizontal = 14.dp), thickness = 1.dp, color = AppColors.Divider)
        DetailRow("Weight", profile.weightKg?.let { "${fixed1(it)} kg" } ?: "Add", onEditWeight)
        HorizontalDivider(Modifier.padding(horizontal = 14.dp), thickness = 1.dp, color = AppColors.Divider)
        DetailRow("Age", profile.age?.let { "$it yrs" } ?: "–", onClick = null)
        HorizontalDivider(Modifier.padding(horizontal = 14.dp), thickness = 1.dp, color = AppColors.Divider)
        DetailRow("Gender", profile.gender ?: "–", onClick = null)
    }
}

@Composable
private fun DetailRow(label: String, value: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Control)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AppType.Body, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = AppType.BodyStrong.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary)
        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Edit, contentDescription = "Change $label", tint = AppColors.Accent, modifier = Modifier.size(16.dp))
        }
    }
}

/** The wheel picker for a value being changed from the Body tab. */
@Composable
internal fun BodyEditDialog(edit: BodyEdit, body: BodyReport, onDismiss: () -> Unit, onWeight: (Double) -> Unit, onHeight: (Double) -> Unit) {
    when (edit) {
        BodyEdit.Weight -> {
            var weight by remember { mutableStateOf(body.profile.weightKg ?: body.weight?.latest?.kg ?: 70.0) }
            WheelDialog(title = "Weight", onDismiss = onDismiss, onSave = { onWeight(weight) }) {
                DecimalWheelPicker(label = "kg", minValue = 30, maxValue = 250, value = weight, onValueChange = { weight = it })
            }
        }

        BodyEdit.Height -> {
            var height by remember { mutableStateOf(body.profile.heightCm ?: 170.0) }
            WheelDialog(title = "Height", onDismiss = onDismiss, onSave = { onHeight(height) }) {
                DecimalWheelPicker(label = "cm", minValue = 100, maxValue = 250, value = height, onValueChange = { height = it })
            }
        }
    }
}
