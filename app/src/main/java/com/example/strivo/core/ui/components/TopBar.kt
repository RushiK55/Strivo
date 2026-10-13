package com.example.strivo.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType

@Composable
fun CircleBackButton(onClick: () -> Unit, modifier: Modifier = Modifier, background: Color = AppColors.Surface) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = AppColors.TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Plain back arrow used in the top bars. */
@Composable
fun PlainBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = AppColors.TextPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Top bar. With [onBack] it is a pushed screen: back arrow and a centred title. Without it is a tab screen:
 * a large title on the left, like the top of a list.
 */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (onBack == null) {
        Row(
            modifier = modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = AppType.LargeTitle, color = AppColors.TextPrimary, maxLines = 1)
                if (subtitle != null) Text(subtitle, style = AppType.Caption, color = AppColors.TextSecondary)
            }
            actions()
        }
        return
    }
    Box(
        modifier = modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = AppType.Headline,
            color = AppColors.TextPrimary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 64.dp),
        )
        Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) { PlainBackButton(onBack) }
        Row(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}
