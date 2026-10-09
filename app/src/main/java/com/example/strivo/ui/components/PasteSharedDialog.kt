package com.example.strivo.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.data.ExerciseShare
import com.example.strivo.data.SharedContent
import com.example.strivo.ui.theme.AppColors

/** Asks for a message shared from Strivo, checks it, and hands the decoded content to [onImport]. */
@Composable
fun PasteSharedDialog(title: String, onDismiss: () -> Unit, onImport: (SharedContent) -> Unit) {
    val context = LocalContext.current
    var pasted by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    StrivoAlertDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = "Add",
        onConfirm = {
            val shared = ExerciseShare.decode(pasted)
            if (shared == null) error = "That doesn't look like a message shared from Strivo." else onImport(shared)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Paste a message you received from Strivo, or tap the button to paste from your clipboard.",
                color = AppColors.TextSecondary,
                fontSize = 14.sp,
            )
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                isError = error != null,
                supportingText = error?.let { { Text(it, color = AppColors.Danger) } },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppColors.TextPrimary,
                    unfocusedTextColor = AppColors.TextPrimary,
                    errorTextColor = AppColors.TextPrimary,
                    focusedContainerColor = AppColors.Field,
                    unfocusedContainerColor = AppColors.Field,
                    errorContainerColor = AppColors.Field,
                    focusedBorderColor = AppColors.Accent,
                    unfocusedBorderColor = AppColors.Field,
                    errorBorderColor = AppColors.Danger,
                    cursorColor = AppColors.Accent,
                ),
            )
            TextButton(onClick = { pasted = readClipboardText(context); error = null }) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("PASTE FROM CLIPBOARD", color = AppColors.Accent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun readClipboardText(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
}
