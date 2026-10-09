package com.example.strivo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.ui.theme.AppColors

/** A section label with the short accent bar under it. */
@Composable
fun AccentTitle(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    letterSpacing: TextUnit = 1.2.sp,
    barWidth: Dp = 40.dp,
    gap: Dp = 4.dp,
) {
    Column(modifier) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = AppColors.TextPrimary,
            letterSpacing = letterSpacing,
        )
        Spacer(Modifier.height(gap))
        Box(
            Modifier
                .width(barWidth)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AppColors.Accent)
        )
    }
}

/** Upper-cased section label used on the form / detail screens. */
@Composable
fun SectionLabel(title: String, modifier: Modifier = Modifier, bottomSpace: Dp = 16.dp) {
    Column(modifier) {
        AccentTitle(text = title.uppercase())
        Spacer(Modifier.height(bottomSpace))
    }
}

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
                .size(32.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "Back",
                tint = AppColors.TextPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Plain back arrow (no circle) used by the form screens. */
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
            imageVector = Icons.Rounded.ArrowBackIosNew,
            contentDescription = "Back",
            tint = AppColors.TextPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Header bar: back button, centred title, balanced on the right. */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    circleBack: Boolean = true,
    fontSize: TextUnit = 22.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    Box(
        modifier = modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            fontSize = fontSize,
            fontWeight = fontWeight,
            letterSpacing = letterSpacing,
            color = AppColors.TextPrimary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 64.dp),
        )
        if (onBack != null) Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
            if (circleBack) CircleBackButton(onBack) else PlainBackButton(onBack)
        }
    }
}

@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 55.dp,
    shape: Shape = RoundedCornerShape(15.dp),
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    icon: ImageVector? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = Color.Black),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = contentPadding,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.Black)
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, fontSize = fontSize, fontWeight = fontWeight, letterSpacing = letterSpacing)
    }
}

/** Round icon badge, e.g. the leading icon of a list card. */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = AppColors.Accent,
    background: Color = AppColors.Accent.copy(alpha = 0.1f),
    padding: Dp = 12.dp,
    iconSize: Dp = 24.dp,
    border: BorderStroke? = null,
    shape: Shape = CircleShape,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** The little round chevron at the end of tappable cards. */
@Composable
fun ChevronBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(AppColors.Field)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = null,
            tint = AppColors.TextPrimary,
            modifier = Modifier.size(12.dp),
        )
    }
}

// --- Dialog helpers ---

@Composable
fun StrivoAlertDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = "Cancel",
    confirmColor: Color = AppColors.Accent,
    dismissColor: Color = AppColors.TextSecondary,
    dismissOnOutside: Boolean = true,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (dismissOnOutside) onDismiss() },
        containerColor = AppColors.Surface,
        shape = RoundedCornerShape(25.dp),
        title = { Text(title, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = content,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = confirmColor, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = dismissText?.let {
            { TextButton(onClick = onDismiss) { Text(it, color = dismissColor) } }
        },
    )
}

// --- Snackbar with a custom colour ---

class ColoredSnackbarVisuals(
    override val message: String,
    val color: Color?,
) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration = androidx.compose.material3.SnackbarDuration.Short
}

suspend fun SnackbarHostState.showColored(message: String, color: Color? = null) {
    showSnackbar(ColoredSnackbarVisuals(message, color))
}

@Composable
fun StrivoSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data: SnackbarData ->
        val custom = (data.visuals as? ColoredSnackbarVisuals)?.color
        Snackbar(
            snackbarData = data,
            containerColor = custom ?: MaterialTheme.colorScheme.inverseSurface,
            contentColor = if (custom != null) Color.White else MaterialTheme.colorScheme.inverseOnSurface,
            shape = RoundedCornerShape(12.dp),
        )
    }
}
