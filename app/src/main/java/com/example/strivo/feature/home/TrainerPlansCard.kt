package com.example.strivo.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.components.AppCard
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.model.PREBUILT_DAY
import com.example.strivo.domain.plan.ReceivedPlan

/** "From your trainer": plans the trainer sent that were just added. Tap one to open it; the cross hides it. */
@Composable
internal fun TrainerPlansCard(plans: List<ReceivedPlan>, onOpen: (ReceivedPlan) -> Unit, onDismiss: (ReceivedPlan) -> Unit) {
    AppCard(padding = PaddingValues(0.dp)) {
        Text(
            "FROM YOUR TRAINER",
            style = AppType.Overline,
            color = AppColors.Accent,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp),
        )
        plans.forEachIndexed { index, plan ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 70.dp), thickness = 1.dp, color = AppColors.Divider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(plan) }
                    .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(AppColors.AccentSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.FitnessCenter, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(plan.planName, style = AppType.BodyStrong, color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val where = if (plan.day == PREBUILT_DAY) "Added to your plan library" else "Added to ${plan.day}"
                    val who = plan.trainerName.takeIf { it.isNotBlank() }?.let { " by $it" }.orEmpty()
                    Text("$where$who", style = AppType.Caption, color = AppColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (plan.note.isNotBlank()) {
                        Text("“${plan.note}”", style = AppType.Caption, color = AppColors.TextPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = { onDismiss(plan) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Hide", tint = AppColors.TextTertiary, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.size(4.dp))
    }
}
