package com.example.strivo.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.model.Plan
import java.time.format.TextStyle as DateTextStyle

// --- Plan rows ---

@Composable
internal fun PlanRow(
    plan: Plan,
    exercises: Int,
    completed: Boolean,
    canStart: Boolean,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (completed) AppColors.Accent else AppColors.AccentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (completed) Icons.Rounded.Check else Icons.Rounded.FitnessCenter,
                contentDescription = null,
                tint = if (completed) Color.Black else AppColors.Accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = plan.planName,
                style = AppType.Headline,
                color = if (completed) AppColors.TextSecondary else AppColors.TextPrimary,
                textDecoration = if (completed) TextDecoration.LineThrough else null,
                maxLines = 1,
            )
            Text(
                text = when {
                    completed -> "Completed"
                    exercises == 0 -> "No exercises yet"
                    else -> (if (exercises == 1) "1 exercise" else "$exercises exercises") + if (plan.isOneOff) " · today only" else ""
                },
                style = AppType.Caption,
                color = if (completed) AppColors.Accent else AppColors.TextSecondary,
            )
        }
        if (canStart) {
            // One tap to begin: no need to open the plan first.
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(AppColors.Accent)
                    .clickable(onClick = onStart),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Start ${plan.planName}", tint = Color.Black, modifier = Modifier.size(22.dp))
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = AppColors.TextTertiary)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(14.dp),
                containerColor = AppColors.SurfaceHigh,
            ) {
                DropdownMenuItem(text = { Text("Open plan", color = AppColors.TextPrimary) }, onClick = { menuOpen = false; onClick() })
                DropdownMenuItem(text = { Text("Remove from this day", color = AppColors.Danger) }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}
