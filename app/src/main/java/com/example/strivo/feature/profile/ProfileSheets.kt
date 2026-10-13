package com.example.strivo.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.RestDayPicker
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GenderSheet(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface,
        shape = AppShapes.Sheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text("Gender", style = AppType.Title, color = AppColors.TextPrimary)
            Spacer(Modifier.height(16.dp))
            com.example.strivo.core.ui.components.SegmentedControl(
                options = listOf("Male", "Female"),
                selectedIndex = if (selected == "Female") 1 else 0,
                onSelect = { selected = if (it == 1) "Female" else "Male" },
                trackColor = AppColors.SurfaceHigh,
                selectedColor = AppColors.SetBorder,

            )
            Spacer(Modifier.height(24.dp))
            AccentButton(text = "Save", onClick = { onSave(selected) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RestDaysSheet(initial: Set<DayOfWeek>, onDismiss: () -> Unit, onSave: (Set<DayOfWeek>) -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface,
        shape = AppShapes.Sheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text("Rest days", style = AppType.Title, color = AppColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "A rest day without a workout won't break your streak. Pick any days, or none.",
                style = AppType.Caption,
                color = AppColors.TextSecondary,
            )
            Spacer(Modifier.height(20.dp))
            RestDayPicker(selected = selected, onChange = { selected = it })
            Spacer(Modifier.height(24.dp))
            AccentButton(text = "Save", onClick = { onSave(selected) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
