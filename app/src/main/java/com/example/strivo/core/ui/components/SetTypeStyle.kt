package com.example.strivo.core.ui.components

import androidx.compose.ui.graphics.Color
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.domain.model.SetType

/** The colour a set type is shown in; a normal set uses [normal]. */
fun SetType.color(normal: Color = AppColors.TextSecondary): Color = when (this) {
    SetType.Normal -> normal
    SetType.WarmUp -> AppColors.SetWarmUp
    SetType.Drop -> AppColors.SetDrop
    SetType.Failure -> AppColors.Danger
}
