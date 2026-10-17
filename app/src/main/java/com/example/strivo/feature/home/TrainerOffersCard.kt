package com.example.strivo.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.clickable as clickableBox
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
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.util.formatWeight
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.plan.PlanAssignment
import com.example.strivo.domain.plan.PlanOffer
import com.example.strivo.domain.model.PREBUILT_DAY

/**
 * "From your trainer": what the trainer sent that the user has not answered. Plans sent together (a week's worth) are ONE offer,
 * so seven plans are one short card, not seven. Tapping an offer opens it: every plan with its day and exercises (sets, reps, weight) and the
 * trainer's note, so the user knows what they are saying yes to. Accept puts each plan on the day the trainer chose; Decline adds nothing.
 */
@Composable
internal fun TrainerOffersCard(
    offers: List<PlanOffer>,
    onAccept: (PlanOffer) -> Unit,
    onDecline: (PlanOffer) -> Unit,
    /** Closes an offer for today without answering it: it comes back tomorrow. */
    onClose: (PlanOffer) -> Unit = {},
) {
    var opened by remember { mutableStateOf<PlanOffer?>(null) }
    // An offer that was answered (or withdrawn) while its sheet is open closes it.
    val shown = opened?.takeIf { open -> offers.any { it.ids == open.ids } }
    shown?.let { offer ->
        OfferSheet(offer, onAccept = { onAccept(offer); opened = null }, onDecline = { onDecline(offer); opened = null }, onDismiss = { opened = null })
    }
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(AppShapes.Control).clickable { opened = offer }) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(AppColors.AccentSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.FitnessCenter, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(offer.title, style = AppType.BodyStrong, color = AppColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(offer.where, style = AppType.Caption, color = AppColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "See the plan", tint = AppColors.TextTertiary)
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { onClose(offer) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close for today, it comes back tomorrow", tint = AppColors.TextTertiary, modifier = Modifier.size(20.dp))
                    }
                }
                Text(
                    offer.names(),
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp).clickable { opened = offer },
                )
                Text(
                    "See the exercises",
                    style = AppType.Caption,
                    color = AppColors.Accent,
                    modifier = Modifier.padding(top = 4.dp).clickable { opened = offer },
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

/** An offer in full: for each plan its day and exercises, then the answer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfferSheet(offer: PlanOffer, onAccept: () -> Unit, onDecline: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface,
        shape = AppShapes.Sheet,
    ) {
        LazyColumn(
            modifier = Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            item {
                Text(offer.title, style = AppType.Title, color = AppColors.TextPrimary)
                Text(offer.where, style = AppType.Caption, color = AppColors.TextSecondary)
                if (offer.note.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text("“${offer.note}”", style = AppType.Body, color = AppColors.TextPrimary)
                }
            }
            items(offer.plans, key = { it.id }) { plan -> PlanBlock(plan) }
            item {
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Decline", onClick = onDecline, modifier = Modifier.weight(1f), height = 48.dp)
                    AccentButton("Accept", onClick = onAccept, modifier = Modifier.weight(1f), height = 48.dp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Accepting adds it to your plans" + if (offer.days.any { it != PREBUILT_DAY }) " and puts it on the day above." else ".",
                    style = AppType.Caption,
                    color = AppColors.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun PlanBlock(plan: PlanAssignment) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(plan.planName, style = AppType.Headline, color = AppColors.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (plan.day == PREBUILT_DAY) "Your plans" else plan.day, style = AppType.Caption, color = AppColors.Accent)
        }
        Spacer(Modifier.height(4.dp))
        plan.exercises.forEach { exercise ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(exercise.name, style = AppType.BodyStrong, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
                    Text(planLine(exercise), style = AppType.Caption, color = AppColors.TextSecondary)
                }
                if (exercise.notes.isNotBlank()) {
                    Text(exercise.notes, style = AppType.Caption, color = AppColors.TextTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(thickness = 1.dp, color = AppColors.Divider)
        }
    }
}

/** "4 × 8 · 60 kg", "3 × 15 · bodyweight", with "+ 1 drop" when there are drop sets. */
private fun planLine(exercise: Exercise): String {
    val weight = exercise.weight.toDoubleOrNull() ?: 0.0
    val drops = if (exercise.dropSets > 0) " + ${exercise.dropSets} drop" else ""
    return "${exercise.sets} × ${exercise.reps}$drops · " + if (weight > 0) "${formatWeight(weight)} kg" else "bodyweight"
}
