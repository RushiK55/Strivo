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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material3.Icon
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
import com.example.strivo.feature.body.BodyUiState

/**
 * "Your body today": which muscles today's plan works, in words, with a way into the 3D body where they glow. It says what is
 * known (the plan's muscles come from the exercise library) and stays honest when a day has nothing or nothing known.
 */
@Composable
internal fun TodayBodyCard(state: BodyUiState, onOpen: () -> Unit) {
    val main = state.mainLabels()
    val helper = state.helperLabels()
    AppCard(modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).clickable(onClick = onOpen), padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(AppColors.AccentSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Accessibility, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("YOUR BODY TODAY", style = AppType.Overline, color = AppColors.Accent)
                Text(
                    text = when {
                        state.dayExercises.isEmpty() -> "Rest day"
                        main.isNotEmpty() -> main.take(3).joinToString(" · ") + if (main.size > 3) " +${main.size - 3}" else ""
                        helper.isNotEmpty() -> helper.take(3).joinToString(" · ")
                        else -> "Today's training"
                    },
                    style = AppType.BodyStrong,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (state.trained.isEmpty) "Open your 3D body" else "See it glow on your 3D body",
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = AppColors.TextTertiary, modifier = Modifier.size(24.dp))
        }
    }
}
