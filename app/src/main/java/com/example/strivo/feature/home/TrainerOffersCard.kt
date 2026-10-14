package com.example.strivo.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.AppCard
import com.example.strivo.core.ui.components.SoftButton
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.plan.PlanOffer

/**
 * "From your trainer": what the trainer sent that the user has not answered. Plans sent together (a week's worth) are ONE offer,
 * so seven plans are one short card, not seven. Accept puts each plan on the day the trainer chose; Decline adds nothing.
 */
@Composable
internal fun TrainerOffersCard(offers: List<PlanOffer>, onAccept: (PlanOffer) -> Unit, onDecline: (PlanOffer) -> Unit) {
    AppCard(padding = PaddingValues(0.dp)) {
        Text(
            "FROM YOUR TRAINER",
            style = AppType.Overline,
            color = AppColors.Accent,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp),
        )
        offers.forEachIndexed { index, offer ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 1.dp, color = AppColors.Divider)
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(AppColors.AccentSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.FitnessCenter, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(offer.title, style = AppType.BodyStrong, color = AppColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(offer.where, style = AppType.Caption, color = AppColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(
                    offer.names(),
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (offer.note.isNotBlank()) {
                    Text("“${offer.note}”", style = AppType.Caption, color = AppColors.TextPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Decline", onClick = { onDecline(offer) }, modifier = Modifier.weight(1f), height = 44.dp)
                    AccentButton("Accept", onClick = { onAccept(offer) }, modifier = Modifier.weight(1f), height = 44.dp)
                }
            }
        }
        Spacer(Modifier.height(0.dp))
    }
}
