package com.example.strivo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.data.model.Plan
import com.example.strivo.ui.components.AccentButton
import com.example.strivo.ui.components.IconBadge
import com.example.strivo.ui.components.ScreenTopBar
import com.example.strivo.ui.components.StrivoAlertDialog
import com.example.strivo.ui.theme.AppColors
import com.example.strivo.viewmodel.ExerciseViewModel
import com.example.strivo.viewmodel.PlanViewModel
import com.example.strivo.viewmodel.groupedByDay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.launch

@Composable
fun DayPlansScreen(
    day: String,
    planViewModel: PlanViewModel,
    exerciseViewModel: ExerciseViewModel,
    onBack: () -> Unit,
    onAddPlan: (String) -> Unit,
    onOpenPlan: (Plan) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val plans by planViewModel.plans.collectAsStateWithLifecycle()
    val plansForDay = remember(plans) { plans.groupedByDay()[day].orEmpty() }
    var planToDelete by remember { mutableStateOf<Plan?>(null) }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = { ScreenTopBar(title = "$day Plans", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddPlan(day) },
                containerColor = AppColors.Accent,
                contentColor = Color.Black,
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add plan", modifier = Modifier.size(32.dp))
            }
        },
    ) { padding ->
        if (plansForDay.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                IconBadge(
                    icon = Icons.Rounded.CalendarToday,
                    tint = AppColors.Accent.copy(alpha = 0.5f),
                    background = AppColors.Surface,
                    padding = 30.dp,
                    iconSize = 60.dp,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "No plans scheduled for $day",
                    color = AppColors.TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(40.dp))
                AccentButton(
                    text = "ADD PLAN",
                    onClick = { onAddPlan(day) },
                    modifier = Modifier.width(200.dp),
                    height = 55.dp,
                    letterSpacing = 1.2.sp,
                    icon = Icons.Rounded.Add,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            ) {
                items(plansForDay, key = { it.planId ?: it.hashCode().toLong() }) { plan ->
                    Row(
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(25.dp))
                            .background(AppColors.Surface)
                            .border(BorderStroke(1.dp, AppColors.Field), RoundedCornerShape(25.dp))
                            .clickable { onOpenPlan(plan) }
                            .padding(start = 20.dp, top = 15.dp, bottom = 15.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(icon = Icons.Rounded.FitnessCenter)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(plan.planName, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text("Manage exercises", color = AppColors.TextSecondary)
                        }
                        IconButton(onClick = { planToDelete = plan }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete plan", tint = AppColors.Danger)
                        }
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForwardIos,
                            contentDescription = null,
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
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
                    exerciseViewModel.loadTodayExercises()
                    planToDelete = null
                }
            },
        ) {
            Text(
                "This will permanently delete the '${plan.planName}' plan and all its exercises.",
                color = AppColors.TextSecondary,
            )
        }
    }
}
