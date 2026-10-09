package com.example.strivo.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.data.model.PREBUILT_DAY
import com.example.strivo.data.model.Plan
import com.example.strivo.ui.components.AccentButton
import com.example.strivo.ui.components.IconBadge
import com.example.strivo.ui.components.PasteSharedDialog
import com.example.strivo.ui.components.ScreenTopBar
import com.example.strivo.ui.components.StrivoAlertDialog
import com.example.strivo.ui.theme.AppColors
import com.example.strivo.viewmodel.ExerciseViewModel
import com.example.strivo.viewmodel.PlanViewModel
import kotlinx.coroutines.launch

/**
 * Lists the prebuilt plans (empty until the user creates one). Tapping a plan copies it, with its
 * exercises, onto [day] and goes back.
 */
@Composable
fun PickPlanScreen(
    day: String,
    planViewModel: PlanViewModel,
    exerciseViewModel: ExerciseViewModel,
    onBack: () -> Unit,
    onCreatePlan: () -> Unit,
    onEditPlan: (Plan) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val plans by planViewModel.plans.collectAsStateWithLifecycle()
    val prebuilt = remember(plans) { plans.filter { it.isPrebuilt } }
    var planToDelete by remember { mutableStateOf<Plan?>(null) }
    var showPaste by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        containerColor = AppColors.Background,
        topBar = { ScreenTopBar(title = "Add to $day", onBack = onBack) },
    ) { padding ->
        if (prebuilt.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                IconBadge(
                    icon = Icons.Rounded.FitnessCenter,
                    tint = AppColors.Accent.copy(alpha = 0.5f),
                    background = AppColors.Surface,
                    padding = 30.dp,
                    iconSize = 60.dp,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "No prebuilt plans yet",
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Create a plan once, then add it to any day.",
                    color = AppColors.TextSecondary,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(40.dp))
                AccentButton(
                    text = "CREATE PLAN",
                    onClick = onCreatePlan,
                    modifier = Modifier.width(200.dp),
                    letterSpacing = 1.2.sp,
                    icon = Icons.Rounded.Add,
                )
                Spacer(Modifier.height(12.dp))
                PastePlanButton { showPaste = true }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            ) {
                item {
                    Text(
                        "Tap a plan to add it to $day.",
                        color = AppColors.TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
                items(prebuilt, key = { it.planId ?: it.hashCode().toLong() }) { plan ->
                    Row(
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(25.dp))
                            .background(AppColors.Surface)
                            .border(BorderStroke(1.dp, AppColors.Field), RoundedCornerShape(25.dp))
                            .clickable {
                                scope.launch {
                                    planViewModel.addPlanToDay(plan, day)
                                    exerciseViewModel.loadExercisesByDay(day)
                                    onBack()
                                }
                            }
                            .padding(start = 20.dp, top = 15.dp, bottom = 15.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(icon = Icons.Rounded.FitnessCenter)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(plan.planName, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text("Tap to add to $day", color = AppColors.TextSecondary, fontSize = 13.sp)
                        }
                        IconButton(onClick = { onEditPlan(plan) }) {
                            Icon(Icons.Rounded.EditNote, contentDescription = "Edit exercises", tint = AppColors.Accent)
                        }
                        IconButton(onClick = { planToDelete = plan }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete plan", tint = AppColors.Danger)
                        }
                    }
                }
                item {
                    AccentButton(
                        text = "CREATE NEW PLAN",
                        onClick = onCreatePlan,
                        modifier = Modifier.fillMaxWidth(),
                        letterSpacing = 1.2.sp,
                        icon = Icons.Rounded.Add,
                    )
                    Spacer(Modifier.height(8.dp))
                    PastePlanButton { showPaste = true }
                }
            }
        }
    }

    if (showPaste) {
        PasteSharedDialog(
            title = "Paste plan",
            onDismiss = { showPaste = false },
            onImport = { shared ->
                showPaste = false
                scope.launch {
                    val name = shared.planName ?: "Shared plan"
                    val id = planViewModel.addPlan(Plan(planName = name, planDay = PREBUILT_DAY))
                    exerciseViewModel.addExercises(shared.exercises.map { it.copy(planId = id) })
                    Toast.makeText(context, "Added '$name' to your prebuilt plans", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    planToDelete?.let { plan ->
        StrivoAlertDialog(
            title = "Delete Plan?",
            onDismiss = { planToDelete = null },
            confirmText = "Delete",
            confirmColor = AppColors.Danger,
            onConfirm = {
                scope.launch {
                    plan.planId?.let { planViewModel.deletePlan(it) }
                    planToDelete = null
                }
            },
        ) {
            Text(
                "This deletes '${plan.planName}' from your prebuilt plans. Copies already on a day are kept.",
                color = AppColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun PastePlanButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("PASTE A SHARED PLAN", color = AppColors.Accent, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}
