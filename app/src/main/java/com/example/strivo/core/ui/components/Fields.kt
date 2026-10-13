package com.example.strivo.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType

/** A single-line text field on the raised surface colour, used in sheets and forms. */
@Composable
fun SurfaceTextField(label: String, value: String, keyboard: KeyboardType, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = AppShapes.Control,
        textStyle = AppType.Body,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppColors.TextPrimary,
            unfocusedTextColor = AppColors.TextPrimary,
            focusedContainerColor = AppColors.SurfaceHigh,
            unfocusedContainerColor = AppColors.SurfaceHigh,
            focusedBorderColor = AppColors.Accent,
            unfocusedBorderColor = Color.Transparent,
            focusedLabelColor = AppColors.TextSecondary,
            unfocusedLabelColor = AppColors.TextSecondary,
            cursorColor = AppColors.Accent,
        ),
    )
}
