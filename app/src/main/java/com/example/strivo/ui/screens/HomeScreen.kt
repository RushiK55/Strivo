package com.example.strivo.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.StrivoApp
import com.example.strivo.data.analytics.currentStreak
import com.example.strivo.data.model.EXTRA_PLAN_NAME
import com.example.strivo.data.model.Exercise
import com.example.strivo.data.model.Plan
import com.example.strivo.ui.components.AccentButton
import com.example.strivo.ui.components.AccentTitle
import com.example.strivo.ui.components.ChevronBadge
import com.example.strivo.ui.components.DecimalWheelPicker
import com.example.strivo.ui.components.IconBadge
import com.example.strivo.ui.components.StrivoAlertDialog
import com.example.strivo.ui.theme.AppColors
import com.example.strivo.util.dayAbbr
import com.example.strivo.util.dayName
import com.example.strivo.util.weekOf
import com.example.strivo.viewmodel.AuthViewModel
import com.example.strivo.viewmodel.ExerciseViewModel
import com.example.strivo.viewmodel.PlanViewModel
import com.example.strivo.viewmodel.ProfileViewModel
import com.example.strivo.viewmodel.groupedByDay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DayItemWidth = 87.dp // 75 (card) + 12 (margins)
private val DayListPadding = 16.dp

@Composable
fun HomeScreen(
    authViewModel: AuthViewModel,
    planViewModel: PlanViewModel,
    exerciseViewModel: ExerciseViewModel,
    profileViewModel: ProfileViewModel,
    onAddPlan: (day: String) -> Unit,
    onOpenPlan: (Plan) -> Unit,
    onStartWorkout: (Plan) -> Unit,
    onAddExercise: (planId: Long) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val currentWeek = remember { weekOf(today) }
    var selectedDay by rememberSaveable { mutableStateOf(dayName(today.dayOfWeek)) }

    val auth by authViewModel.state.collectAsStateWithLifecycle()
    val plans by planViewModel.plans.collectAsStateWithLifecycle()
    val groupedPlans = remember(plans) { plans.groupedByDay() }
    val plansForDay = groupedPlans[selectedDay].orEmpty()

    // A plan counts as completed when its workout was finished that day, or (today) every exercise in it is ticked off.
    val sessions by exerciseViewModel.history.collectAsStateWithLifecycle()
    val profileState by profileViewModel.state.collectAsStateWithLifecycle()
    val dayExercises by exerciseViewModel.todayExercises.collectAsStateWithLifecycle()
    val selectedDate = remember(selectedDay) { currentWeek.first { dayName(it.dayOfWeek) == selectedDay } }
    fun exerciseCount(plan: Plan): Int = dayExercises.count { it.planId == plan.planId }
    fun isCompleted(plan: Plan): Boolean {
        if (sessions.any { it.planName == plan.planName && it.date.toLocalDate() == selectedDate }) return true
        val exercises = dayExercises.filter { it.planId == plan.planId }
        return selectedDate == today && exercises.isNotEmpty() && exercises.all { it.isCheck }
    }

    var showWeightDialog by remember { mutableStateOf(false) }
    var planToDelete by remember { mutableStateOf<Plan?>(null) }
    var showExtraSheet by remember { mutableStateOf(false) }
    var knownExercises by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        profileViewModel.load() // the rest days the streak skips
        planViewModel.removeEmptyExtraPlans()
        planViewModel.refreshPlans()
        exerciseViewModel.fetchHistory()
        exerciseViewModel.loadExercisesByDay(selectedDay)
        if (profileViewModel.shouldAskWeight()) showWeightDialog = true
    }

    // Data restored from the cloud (a new phone, a change from elsewhere) shows up without leaving the screen.
    val cloudVersion by (LocalContext.current.applicationContext as StrivoApp).cloudDataVersion.collectAsStateWithLifecycle()
    LaunchedEffect(cloudVersion) {
        if (cloudVersion > 0) {
            planViewModel.refreshPlans()
            exerciseViewModel.fetchHistory()
            exerciseViewModel.loadExercisesByDay(selectedDay)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .systemBarsPadding(),
    ) {
        // Greeting, and a snapshot of how the week is going
        item {
            val weekStart = currentWeek.first()
            val weekEnd = currentWeek.last()
            val todaysPlans = groupedPlans[dayName(today.dayOfWeek)].orEmpty()
            val doneToday = todaysPlans.count { plan ->
                sessions.any { it.planName == plan.planName && it.date.toLocalDate() == today }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Hello, ${auth.userName?.split(' ')?.first() ?: "User"}",
                            color = AppColors.TextPrimary,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                        )
                        Text(
                            text = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                            color = AppColors.TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    IconBadge(
                        icon = Icons.Filled.FitnessCenter,
                        background = AppColors.Accent.copy(alpha = 0.2f),
                        padding = 10.dp,
                        iconSize = 26.dp,
                        shape = RoundedCornerShape(14.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeaderStat(
                        icon = Icons.Rounded.LocalFireDepartment,
                        value = currentStreak(sessions, today, profileState.profile.restDays).toString(),
                        label = "Day streak",
                        modifier = Modifier.weight(1f),
                    )
                    HeaderStat(
                        icon = Icons.Filled.FitnessCenter,
                        value = sessions.count { it.date.toLocalDate() in weekStart..weekEnd }.toString(),
                        label = "This week",
                        modifier = Modifier.weight(1f),
                    )
                    HeaderStat(
                        icon = Icons.Rounded.Check,
                        value = if (todaysPlans.isEmpty()) "Rest" else "$doneToday / ${todaysPlans.size}",
                        label = "Today",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Weekly schedule header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 40.dp, end = 20.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccentTitle(
                    text = "Weekly Schedule",
                    fontSize = 24.sp,
                    letterSpacing = 0.sp,
                    barWidth = 60.dp,
                )
                IconBadge(
                    icon = Icons.Rounded.Add,
                    tint = Color.Black,
                    background = AppColors.Accent,
                    padding = 12.dp,
                    modifier = Modifier.clickable { onAddPlan(selectedDay) },
                )
            }
        }

        // Weekly schedule list
        item {
            WeekSelector(
                week = currentWeek,
                today = today,
                selectedDay = selectedDay,
                onSelect = { day ->
                    selectedDay = day
                    exerciseViewModel.loadExercisesByDay(day)
                },
            )
        }

        // Plans header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccentTitle(
                    text = if (selectedDay == dayName(today.dayOfWeek)) "Today's Plans" else "$selectedDay's Plans",
                    fontSize = 24.sp,
                    letterSpacing = 0.sp,
                    barWidth = 60.dp,
                )
                val done = plansForDay.count { isCompleted(it) }
                if (plansForDay.isNotEmpty()) {
                    Text(
                        text = "$done / ${plansForDay.size} done",
                        color = if (done == plansForDay.size) AppColors.Accent else AppColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (plansForDay.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppColors.Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.EventNote,
                            contentDescription = null,
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(30.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("No plans for $selectedDay", color = AppColors.TextSecondary)
                    }
                }
            }
        } else {
            items(plansForDay, key = { it.planId ?: it.hashCode().toLong() }) { plan ->
                PlanCard(
                    plan = plan,
                    completed = isCompleted(plan),
                    canStart = !isCompleted(plan) && exerciseCount(plan) > 0,
                    onClick = { onOpenPlan(plan) },
                    onStart = { onStartWorkout(plan) },
                    onDelete = { planToDelete = plan },
                )
            }
        }

        // Exercises done on top of (or instead of) the plans: added straight to the day, no plan needed.
        item {
            val shape = RoundedCornerShape(20.dp)
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .border(BorderStroke(1.dp, AppColors.Accent.copy(alpha = 0.35f)), shape)
                    .clickable {
                        scope.launch {
                            knownExercises = exerciseViewModel.knownExercises()
                            showExtraSheet = true
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(
                    icon = Icons.Rounded.Add,
                    tint = Color.Black,
                    background = AppColors.Accent,
                    padding = 8.dp,
                    iconSize = 20.dp,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Add extra exercise", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Did something that's not in a plan? Add it to $selectedDay.", color = AppColors.TextSecondary, fontSize = 12.sp)
                }
            }
        }

        item { Spacer(Modifier.height(100.dp)) }
    }

    planToDelete?.let { plan ->
        StrivoAlertDialog(
            title = "Delete Plan?",
            onDismiss = { planToDelete = null },
            confirmText = "Delete",
            confirmColor = AppColors.Danger,
            onConfirm = {
                planToDelete = null
                plan.planId?.let { id ->
                    scope.launch {
                        planViewModel.deletePlan(id)
                        exerciseViewModel.loadExercisesByDay(selectedDay)
                    }
                }
            },
        ) {
            Text(
                "This will permanently delete the '${plan.planName}' plan and all its exercises from $selectedDay.",
                color = AppColors.TextSecondary,
            )
        }
    }

    if (showExtraSheet) {
        // What this day's extra plan already holds, so those rows show as added.
        val extraPlanId = plansForDay.firstOrNull { it.planName == EXTRA_PLAN_NAME }?.planId
        val addedNames = dayExercises.filter { it.planId == extraPlanId }.map { it.name.trim().lowercase() }.toSet()
        ExtraExerciseSheet(
            day = selectedDay,
            exercises = knownExercises,
            addedNames = addedNames,
            onAdd = { exercise ->
                scope.launch {
                    val planId = planViewModel.extraPlanFor(selectedDay)
                    // Extras are one-offs by default; a copy keeps the sets, reps, weight and notes.
                    exerciseViewModel.copyExerciseToPlan(exercise.copy(vanishEndOfDay = true), planId)
                }
            },
            onNew = {
                showExtraSheet = false
                scope.launch { onAddExercise(planViewModel.extraPlanFor(selectedDay)) }
            },
            onDismiss = { showExtraSheet = false },
        )
    }

    if (showWeightDialog) {
        WeightUpdateDialog(
            onUpdate = { weight ->
                profileViewModel.updateWeight(weight)
                showWeightDialog = false
            },
        )
    }
}

@Composable
private fun WeekSelector(
    week: List<LocalDate>,
    today: LocalDate,
    selectedDay: String,
    onSelect: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxWidth().height(140.dp)) {
        val density = LocalDensity.current
        val viewportPx = with(density) { maxWidth.toPx() }
        val itemPx = with(density) { DayItemWidth.toPx() }
        val padPx = with(density) { DayListPadding.toPx() }

        // Centre today's card in the row.
        LaunchedEffect(viewportPx) {
            val todayIndex = today.dayOfWeek.value - 1
            val shift = (viewportPx / 2 - itemPx / 2) - padPx
            listState.animateScrollToItem(todayIndex, scrollOffset = (-shift).toInt())
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = DayListPadding),
        ) {
            items(week) { date ->
                val name = dayName(date.dayOfWeek)
                DayCard(
                    date = date,
                    selected = name == selectedDay,
                    onClick = { onSelect(name) },
                )
            }
        }
    }
}

@Composable
private fun DayCard(date: LocalDate, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) Color.Transparent else AppColors.Surface, tween(200), label = "day-bg")
    val borderColor by animateColorAsState(if (selected) AppColors.Accent else Color.Transparent, tween(200), label = "day-border")
    Column(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 10.dp)
            .width(75.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(background)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, borderColor), RoundedCornerShape(25.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = dayAbbr(date.dayOfWeek),
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(15.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (selected) AppColors.Accent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                color = if (selected) Color.Black else AppColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun PlanCard(
    plan: Plan,
    completed: Boolean,
    canStart: Boolean,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Surface)
            // The same accent outline a finished exercise gets, so "done" looks the same everywhere.
            .then(if (completed) Modifier.border(BorderStroke(1.dp, AppColors.Accent.copy(alpha = 0.3f)), shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, top = 15.dp, end = 8.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            icon = if (completed) Icons.Rounded.Check else Icons.Rounded.FitnessCenter,
            background = AppColors.Accent.copy(alpha = if (completed) 0.2f else 0.1f),
            border = BorderStroke(1.dp, AppColors.Accent.copy(alpha = 0.2f)),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = plan.planName,
                color = if (completed) AppColors.TextSecondary else AppColors.TextPrimary,
                textDecoration = if (completed) TextDecoration.LineThrough else null,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (completed) "Completed" else "Tap to view exercises",
                color = if (completed) AppColors.Accent else AppColors.TextSecondary,
                fontWeight = if (completed) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete plan", tint = AppColors.Danger)
        }
        if (canStart) {
            // One tap to begin: no need to open the plan first.
            IconBadge(
                icon = Icons.Rounded.PlayArrow,
                tint = Color.Black,
                background = AppColors.Accent,
                padding = 8.dp,
                iconSize = 22.dp,
                modifier = Modifier.clickable(onClick = onStart),
            )
        } else {
            ChevronBadge()
        }
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun WeightUpdateDialog(onUpdate: (Double) -> Unit) {
    var weight by remember { mutableStateOf(70.0) }
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(AppColors.Surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Weight Update", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("Keep your progress on track!", fontSize = 14.sp, color = AppColors.TextSecondary)
            Spacer(Modifier.height(30.dp))
            DecimalWheelPicker(
                label = "KG",
                minValue = 30,
                maxValue = 250,
                value = weight,
                onValueChange = { weight = it },
            )
            Spacer(Modifier.height(30.dp))
            Button(
                onClick = { onUpdate(weight) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = Color.Black),
            ) {
                Text("UPDATE", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** One number from the week with a small icon and a label, in the same card style as the rest of the app. */
@Composable
private fun HeaderStat(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Surface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(8.dp))
        Text(value, color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(label, color = AppColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
