package com.example.strivo.feature.plans

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.AccentTitle
import com.example.strivo.core.ui.components.ScreenTopBar
import com.example.strivo.core.ui.components.StrivoAlertDialog
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.domain.model.PREBUILT_DAY
import com.example.strivo.domain.model.Plan
import kotlinx.coroutines.launch

/** Creates one of the user's own plans. It is put on a weekday later, from that day's "add plan" screen. */
@Composable
fun SavePlanScreen(
    planViewModel: PlanViewModel,
    onBack: () -> Unit,
    onSaved: (Plan) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var planName by rememberSaveable { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var savedPlan by remember { mutableStateOf<Plan?>(null) }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = { ScreenTopBar(title = "New plan", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            AccentTitle("PLAN DETAILS")
            Spacer(Modifier.height(30.dp))
            OutlinedTextField(
                value = planName,
                onValueChange = { planName = it; nameError = null },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Plan Name") },
                placeholder = { Text("e.g. Chest & Triceps") },
                leadingIcon = { Icon(Icons.Rounded.FitnessCenter, contentDescription = null, tint = AppColors.Accent) },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it, color = AppColors.Danger) } },
                shape = AppShapes.Control,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppColors.TextPrimary,
                    unfocusedTextColor = AppColors.TextPrimary,
                    errorTextColor = AppColors.TextPrimary,
                    focusedContainerColor = AppColors.Surface,
                    unfocusedContainerColor = AppColors.Surface,
                    errorContainerColor = AppColors.Surface,
                    focusedBorderColor = AppColors.Accent,
                    unfocusedBorderColor = AppColors.Surface,
                    errorBorderColor = AppColors.Danger,
                    focusedLabelColor = AppColors.TextSecondary,
                    unfocusedLabelColor = AppColors.TextSecondary,
                    errorLabelColor = AppColors.TextSecondary,
                    focusedPlaceholderColor = AppColors.Placeholder,
                    unfocusedPlaceholderColor = AppColors.Placeholder,
                    cursorColor = AppColors.Accent,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "The plan is saved to your plans. Add its exercises next, then put it on any day from that day's \"Add plan\" screen.",
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
            )

            Spacer(Modifier.height(60.dp))
            AccentButton(
                text = "SAVE PLAN",
                onClick = {
                    val name = planName.trim()
                    if (name.isEmpty()) {
                        nameError = "Please enter a plan name"
                    } else {
                        scope.launch {
                            val id = planViewModel.addPlan(Plan(planName = name, planDay = PREBUILT_DAY))
                            savedPlan = Plan(planId = id, planName = name, planDay = PREBUILT_DAY)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    savedPlan?.let { plan ->
        StrivoAlertDialog(
            title = "Plan saved",
            onDismiss = {},
            dismissOnOutside = false,
            dismissText = null,
            confirmText = "OK",
            onConfirm = { onSaved(plan) },
        ) {
            Text("Plan '${plan.planName}' has been added to your plans.", color = AppColors.TextSecondary)
        }
    }
}
