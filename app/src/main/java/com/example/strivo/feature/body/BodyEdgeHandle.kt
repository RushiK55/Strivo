package com.example.strivo.feature.body

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.theme.AppColors
import kotlinx.coroutines.delay

/**
 * A way into the 3D body that stays out of the way: a small arrow tab on the edge of the screen. Tapping it slides out a round button
 * with the body on it (it tucks itself away again after a few seconds); tapping that opens the body screen.
 */
@Composable
fun BodyEdgeHandle(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            delay(TUCK_AWAY_MILLIS)
            open = false
        }
    }
    Box(modifier, contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            visible = !open,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
        ) {
            Box(
                Modifier
                    .width(22.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(AppColors.Surface.copy(alpha = 0.92f))
                    .border(BorderStroke(1.dp, AppColors.Divider), RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .clickable { open = true }
                    .semantics { contentDescription = "Show the button for your 3D body" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
        ) {
            Box(
                Modifier
                    .padding(end = 12.dp)
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(AppColors.Accent)
                    .clickable {
                        open = false
                        onOpen()
                    }
                    .semantics { contentDescription = "Open your 3D body" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Accessibility, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Black, modifier = Modifier.size(30.dp))
            }
        }
    }
}

private const val TUCK_AWAY_MILLIS = 4000L
