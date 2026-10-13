package com.example.strivo.core.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
