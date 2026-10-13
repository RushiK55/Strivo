package com.example.strivo.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType

/** "BODY MASS INDEX" -> "Body mass index"; text that is not all capitals is left alone. */
fun String.sentenceCase(): String =
    if (any { it.isLetter() } && this == uppercase()) lowercase().replaceFirstChar { it.titlecase() } else this

/** A section heading: sentence case, quiet weight, no decoration. */
@Composable
fun AccentTitle(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 17.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
) {
    Text(
        text = text.sentenceCase(),
        modifier = modifier,
        style = AppType.Headline.copy(fontSize = fontSize, fontWeight = fontWeight),
        color = AppColors.TextPrimary,
    )
}

/** A small label above a group of fields on the form and detail screens. */
@Composable
fun SectionLabel(title: String, modifier: Modifier = Modifier, bottomSpace: Dp = 10.dp) {
    Column(modifier) {
        Text(title.sentenceCase(), style = AppType.BodyStrong, color = AppColors.TextSecondary)
        Spacer(Modifier.height(bottomSpace))
    }
}
