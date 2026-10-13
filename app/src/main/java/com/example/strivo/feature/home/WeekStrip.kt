package com.example.strivo.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.core.util.dayName
import com.example.strivo.domain.model.Plan
import com.example.strivo.domain.model.WorkoutSession
import java.time.LocalDate
import java.util.Locale
import java.time.format.TextStyle as DateTextStyle

// --- Week strip ---

/** How a day looks in the strip: nothing planned, planned, or every planned workout done. */
internal enum class DayStatus { Rest, Planned, Done }

internal fun dayStatus(date: LocalDate, plans: List<Plan>, sessions: List<WorkoutSession>): DayStatus {
    if (plans.isEmpty()) {
        // A workout done on a day with nothing scheduled (an extra session) still counts as done.
        return if (sessions.any { it.date.toLocalDate() == date }) DayStatus.Done else DayStatus.Rest
    }
    val allDone = plans.all { plan -> sessions.any { it.planName == plan.planName && it.date.toLocalDate() == date } }
    return if (allDone) DayStatus.Done else DayStatus.Planned
}

private val DayCardWidth = 64.dp
private val DayCardGap = 10.dp
private val WeekPadding = 16.dp

/** Lets the week row ignore the list's side padding, so the cards scroll from edge to edge. */
private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val side = horizontal.roundToPx()
    val width = constraints.maxWidth + side * 2
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-side, 0) }
}

/**
 * This week as a row of tall day cards that scrolls sideways, opened with today in the middle.
 * Tap a day to see its plans.
 */
@Composable
internal fun WeekStrip(
    week: List<LocalDate>,
    today: LocalDate,
    selectedDay: String,
    dayStatus: (LocalDate) -> DayStatus,
    onSelect: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    BoxWithConstraints(Modifier.bleed(WeekPadding).fillMaxWidth()) {
        val density = LocalDensity.current
        val viewportPx = with(density) { maxWidth.toPx() }
        val itemPx = with(density) { DayCardWidth.toPx() }
        val padPx = with(density) { WeekPadding.toPx() }

        // Centre today's card in the row.
        LaunchedEffect(viewportPx) {
            val todayIndex = today.dayOfWeek.value - 1
            val shift = (viewportPx / 2 - itemPx / 2) - padPx
            listState.animateScrollToItem(todayIndex, scrollOffset = (-shift).toInt())
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = WeekPadding),
            horizontalArrangement = Arrangement.spacedBy(DayCardGap),
        ) {
            items(week) { date ->
                val name = dayName(date.dayOfWeek)
                DayCard(
                    date = date,
                    isToday = date == today,
                    selected = name == selectedDay,
                    status = dayStatus(date),
                    onClick = { onSelect(name) },
                )
            }
        }
    }
}

@Composable
private fun DayCard(date: LocalDate, isToday: Boolean, selected: Boolean, status: DayStatus, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) AppColors.AccentSoft else AppColors.Surface, tween(200), label = "day-bg")
    val border by animateColorAsState(if (selected) AppColors.Accent else Color.Transparent, tween(200), label = "day-border")
    val pill by animateColorAsState(if (selected) AppColors.Accent else Color.Transparent, tween(200), label = "day-pill")
    Column(
        modifier = Modifier
            .width(DayCardWidth)
            .height(112.dp)
            .clip(AppShapes.Card)
            .background(background)
            .border(BorderStroke(1.5.dp, border), AppShapes.Card)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.getDefault()),
            style = AppType.Caption.copy(fontWeight = FontWeight.SemiBold),
            color = when {
                selected -> AppColors.Accent
                isToday -> AppColors.Accent
                else -> AppColors.TextSecondary
            },
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(pill),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = AppType.Headline.copy(fontFeatureSettings = "tnum"),
                color = if (selected) Color.Black else AppColors.TextPrimary,
            )
        }
        Spacer(Modifier.height(8.dp))
        // A dot under days with something planned; filled accent once it is all done.
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(
                    when (status) {
                        DayStatus.Done -> AppColors.Accent
                        DayStatus.Planned -> AppColors.TextTertiary
                        DayStatus.Rest -> Color.Transparent
                    }
                ),
        )
    }
}
