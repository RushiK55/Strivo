package com.example.strivo.feature.coach

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.components.AppCard
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.core.util.formatWeight
import com.example.strivo.domain.coach.Advice
import com.example.strivo.domain.coach.AdviceKind

/** How each kind of advice is marked: rising for heavier, falling for a lighter week, and so on. */
private fun AdviceKind.icon(): ImageVector = when (this) {
    AdviceKind.IncreaseWeight -> Icons.AutoMirrored.Rounded.TrendingUp
    AdviceKind.Deload -> Icons.AutoMirrored.Rounded.TrendingDown
    AdviceKind.FastLoss, AdviceKind.FastGain -> Icons.Rounded.Speed
    AdviceKind.Protein -> Icons.Rounded.Restaurant
    AdviceKind.WeighIn -> Icons.Rounded.MonitorWeight
    AdviceKind.Inactive -> Icons.Rounded.FitnessCenter
}

private fun AdviceKind.tint(): Color = when (this) {
    AdviceKind.IncreaseWeight -> AppColors.Accent
    AdviceKind.Deload -> AppColors.SetWarmUp
    AdviceKind.FastLoss, AdviceKind.FastGain -> AppColors.Warning
    AdviceKind.Protein -> AppColors.Success
    AdviceKind.WeighIn, AdviceKind.Inactive -> AppColors.TextSecondary
}

/**
 * The coach's suggestions in one card. Only the first [limit] are shown; with [onSeeAll], a last row leads to the rest.
 * Advice with an [acceptLabel] (the plan it would change) gets an Accept button, which hands it to [onAccept] to put its
 * weight into the plans.
 */
@Composable
internal fun AdviceCard(
    advice: List<Advice>,
    modifier: Modifier = Modifier,
    limit: Int = advice.size,
    onSeeAll: (() -> Unit)? = null,
    acceptLabel: (Advice) -> String? = { null },
    onAccept: (Advice) -> Unit = {},
    /** Closes a tip for today: it is hidden until tomorrow, and is back then if it is still true. */
    onDismiss: ((Advice) -> Unit)? = null,
) {
    val context = LocalContext.current
    val shown = advice.take(limit)
    AppCard(modifier = modifier, padding = PaddingValues(vertical = 4.dp)) {
        shown.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 66.dp), thickness = 1.dp, color = AppColors.Divider)
            val where = acceptLabel(item)
            AdviceRow(
                advice = item,
                acceptedIn = where,
                onAccept = where?.let {
                    {
                        onAccept(item)
                        Toast.makeText(context, "${item.exercise} is now ${formatWeight(item.suggestedKg ?: 0.0)} kg in $where", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = onDismiss?.let { dismiss -> { dismiss(item) } },
            )
        }
        if (onSeeAll != null) {
            HorizontalDivider(Modifier.padding(start = 66.dp), thickness = 1.dp, color = AppColors.Divider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSeeAll)
                    .padding(start = 66.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val more = advice.size - shown.size
                Text(
                    if (more > 0) "See all ${advice.size} in Analytics" else "More in Analytics",
                    style = AppType.BodyStrong,
                    color = AppColors.Accent,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = AppColors.Accent)
            }
        }
    }
}

@Composable
internal fun AdviceRow(
    advice: Advice,
    modifier: Modifier = Modifier,
    acceptedIn: String? = null,
    onAccept: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Row(modifier.padding(start = 14.dp, end = if (onDismiss != null) 6.dp else 14.dp, top = 12.dp, bottom = 12.dp)) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(advice.kind.tint().copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(advice.kind.icon(), contentDescription = null, tint = advice.kind.tint(), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(advice.title, style = AppType.BodyStrong, color = AppColors.TextPrimary)
            Text(advice.detail, style = AppType.Caption, color = AppColors.TextSecondary)
            if (onAccept != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .clip(AppShapes.Chip)
                            .background(advice.kind.tint().copy(alpha = 0.16f))
                            .clickable(onClick = onAccept)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = advice.kind.tint(), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Accept · ${formatWeight(advice.suggestedKg ?: 0.0)} kg", style = AppType.BodyStrong, color = advice.kind.tint())
                    }
                    if (acceptedIn != null) {
                        Spacer(Modifier.width(10.dp))
                        Text("in $acceptedIn", style = AppType.Caption, color = AppColors.TextTertiary, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (onDismiss != null) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close for today, it comes back tomorrow", tint = AppColors.TextTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
