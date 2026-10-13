package com.example.strivo.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType

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
        shape = RoundedCornerShape(22.dp),
        title = { Text(title, style = AppType.Title.copy(fontSize = 20.sp), color = AppColors.TextPrimary) },
        text = content,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = confirmColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        },
        dismissButton = dismissText?.let {
            { TextButton(onClick = onDismiss) { Text(it, color = dismissColor, fontSize = 16.sp) } }
        },
    )
}

/** A dialog around a wheel picker: the title, the picker in the middle, Save and Cancel. */
@Composable
fun WheelDialog(title: String, onDismiss: () -> Unit, onSave: () -> Unit, content: @Composable () -> Unit) {
    StrivoAlertDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = "Save",
        onConfirm = onSave,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
    }
}
