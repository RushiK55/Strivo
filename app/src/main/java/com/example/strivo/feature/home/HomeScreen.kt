package com.example.strivo.feature.home

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.StrivoApp
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.AccentTitle
import com.example.strivo.core.ui.components.AppCard
import com.example.strivo.core.ui.components.DecimalWheelPicker
import com.example.strivo.core.ui.components.StrivoAlertDialog
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.core.util.dayName
import com.example.strivo.core.util.weekOf
import com.example.strivo.domain.analytics.currentStreak
import com.example.strivo.domain.coach.Advice
import com.example.strivo.domain.model.EXTRA_PLAN_NAME
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.Plan
import com.example.strivo.domain.plan.isPlanDoneToday
import com.example.strivo.feature.auth.AuthViewModel
import com.example.strivo.feature.body.BodyEdgeHandle
import com.example.strivo.feature.coach.AdviceCard
import com.example.strivo.feature.plans.ExerciseViewModel
import com.example.strivo.feature.plans.ExtraExerciseSheet
import com.example.strivo.feature.plans.PlanViewModel
import com.example.strivo.feature.plans.groupedByDay
import com.example.strivo.feature.profile.ProfileViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle

@Composable
fun HomeScreen(
    authViewModel: AuthViewModel,
    planViewModel: PlanViewModel,
    exerciseViewModel: ExerciseViewModel,
    profileViewModel: ProfileViewModel,
    onAddPlan: (day: String) -> Unit,
    onOpenPlan: (Plan) -> Unit,
    /** Opens a workout that was done (every set of it), for an earlier day. */
    onOpenSession: (com.example.strivo.domain.model.WorkoutSession) -> Unit = {},
    /** Opens a plan to look at, with no way to start it (a day that has passed). */
    onViewPlan: (Plan) -> Unit = {},
    onStartWorkout: (Plan) -> Unit,
    onAddExercise: (planId: Long) -> Unit,
    onOpenLibrary: () -> Unit = {},
    onOpenBody: () -> Unit = {},
    advice: List<Advice> = emptyList(),
    onOpenCoach: () -> Unit = {},
    acceptLabel: (Advice) -> String? = { null },
    onAcceptAdvice: (Advice) -> Unit = {},
) {
    val today = remember { LocalDate.now() }
    val currentWeek = remember { weekOf(today) }
    var selectedDay by rememberSaveable { mutableStateOf(dayName(today.dayOfWeek)) }

    val auth by authViewModel.state.collectAsStateWithLifecycle()
    val plans by planViewModel.plans.collectAsStateWithLifecycle()
    val offers by planViewModel.offers.collectAsStateWithLifecycle()
    val notice by planViewModel.notice.collectAsStateWithLifecycle()
    val snoozed by planViewModel.snoozed.collectAsStateWithLifecycle()
    // What the user closed today stays hidden until tomorrow, when it comes back if it is still true.
    val shownOffers = offers.filter { "offer:" + it.ids.joinToString(",") !in snoozed }
    val shownAdvice = advice.filter { "advice:${it.kind}:${it.exercise.orEmpty()}" !in snoozed }
    val context = LocalContext.current
    AskToAllowNotifications()
    // What accepting did ("Added 7 plans to your week"), said once.
    LaunchedEffect(notice) {
        notice?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); planViewModel.clearNotice() }
    }
    val groupedPlans = remember(plans) { plans.groupedByDay() }
    val plansForDay = groupedPlans[selectedDay].orEmpty()

    // A plan counts as completed when its workout was finished that day, or (today) every exercise in it is ticked off.
    val sessions by exerciseViewModel.history.collectAsStateWithLifecycle()
    val profileState by profileViewModel.state.collectAsStateWithLifecycle()
    val dayExercises by exerciseViewModel.todayExercises.collectAsStateWithLifecycle()
    val selectedDate = remember(selectedDay) { currentWeek.first { dayName(it.dayOfWeek) == selectedDay } }
    // A day that has passed is looked at, not started: it shows what was done.
    val isPast = selectedDate.isBefore(today)
    fun exerciseCount(plan: Plan): Int = dayExercises.count { it.planId == plan.planId }
    val finishedToday by exerciseViewModel.finishedToday.collectAsStateWithLifecycle()
    // The ticks come from the exercises loaded for the selected day, so they only apply to that day.
    fun isCompletedOn(plan: Plan, date: LocalDate): Boolean {
        val finished = sessions.any { it.planName == plan.planName && it.date.toLocalDate() == date }
        if (date != today || date != selectedDate) return finished
        return isPlanDoneToday(
            exercises = dayExercises.filter { it.planId == plan.planId },
            workoutFinished = finished,
            presentAtFinish = plan.planId?.let { finishedToday[it] },
            isExtraPlan = plan.planName == EXTRA_PLAN_NAME,
        )
    }
    fun isCompleted(plan: Plan): Boolean = isCompletedOn(plan, selectedDate)

    var showWeightDialog by remember { mutableStateOf(false) }
    var planToDelete by remember { mutableStateOf<Plan?>(null) }
    var showExtraSheet by remember { mutableStateOf(false) }
    var knownExercises by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    var planChoices by remember { mutableStateOf<List<com.example.strivo.feature.plans.PlanChoice>>(emptyList()) }
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

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        ) {
            // Greeting
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(),
                            style = AppType.Overline,
                            color = AppColors.TextSecondary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Hello, ${auth.userName?.split(' ')?.first() ?: "there"}",
                            style = AppType.LargeTitle,
                            color = AppColors.TextPrimary,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = onOpenLibrary) {
                        Icon(Icons.Rounded.MenuBook, contentDescription = "Exercise library", tint = AppColors.TextSecondary)
                    }
                    Avatar(name = auth.userName)
                }
            }

            // The week at a glance
            item {
                val weekStart = currentWeek.first()
                val weekEnd = currentWeek.last()
                val todaysPlans = groupedPlans[dayName(today.dayOfWeek)].orEmpty()
                val doneToday = todaysPlans.count { isCompletedOn(it, today) }
                Spacer(Modifier.height(20.dp))
                AppCard(padding = PaddingValues(vertical = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SummaryStat(
                            value = currentStreak(sessions, today, profileState.profile.restDays).toString(),
                            label = "Day streak",
                            accentIcon = true,
                            modifier = Modifier.weight(1f),
                        )
                        StatDivider()
                        SummaryStat(
                            value = sessions.count { it.date.toLocalDate() in weekStart..weekEnd }.toString(),
                            label = "Workouts this week",
                            modifier = Modifier.weight(1f),
                        )
                        StatDivider()
                        SummaryStat(
                            value = if (todaysPlans.isEmpty()) "Rest" else "$doneToday/${todaysPlans.size}",
                            label = if (todaysPlans.isEmpty()) "Today" else "Done today",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Plans the trainer sent that are waiting for an answer: one card for a week's worth, with Accept and Decline
            if (shownOffers.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    TrainerOffersCard(
                        offers = shownOffers,
                        onAccept = planViewModel::acceptOffer,
                        onDecline = planViewModel::declineOffer,
                        onClose = { planViewModel.snooze("offer:" + it.ids.joinToString(",")) },
                    )
                }
            }

            // Week strip
            item {
                Spacer(Modifier.height(16.dp))
                WeekStrip(
                    week = currentWeek,
                    today = today,
                    selectedDay = selectedDay,
                    dayStatus = { date -> dayStatus(date, groupedPlans[dayName(date.dayOfWeek)].orEmpty(), sessions) },
                    onSelect = { day ->
                        selectedDay = day
                        exerciseViewModel.loadExercisesByDay(day)
                    },
                )
            }

            // The selected day's plans
            item {
                val done = plansForDay.count { isCompleted(it) }
                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        AccentTitle(if (selectedDate == today) "Today" else selectedDay, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        if (plansForDay.isNotEmpty()) {
                            Text(
                                "$done of ${plansForDay.size} done",
                                style = AppType.Caption,
                                color = if (done == plansForDay.size) AppColors.Accent else AppColors.TextSecondary,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .clip(AppShapes.Chip)
                            .clickable { onAddPlan(selectedDay) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add plan", style = AppType.BodyStrong, color = AppColors.Accent)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            item {
                AppCard(padding = PaddingValues(0.dp)) {
                    if (isPast) {
                        PastDayRows(
                            weekday = selectedDay,
                            sessions = sessions.filter { it.date.toLocalDate() == selectedDate },
                            plans = plansForDay.filter { it.planName != EXTRA_PLAN_NAME },
                            onOpenSession = onOpenSession,
                            onOpenPlan = onViewPlan,
                        )
                    } else {
                    if (plansForDay.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("Nothing planned", style = AppType.BodyStrong, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Add a plan for $selectedDay, or log an extra exercise below.",
                                style = AppType.Caption,
                                color = AppColors.TextSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        plansForDay.forEachIndexed { index, plan ->
                            if (index > 0) RowDivider()
                            val completed = isCompleted(plan)
                            PlanRow(
                                plan = plan,
                                exercises = exerciseCount(plan),
                                completed = completed,
                                canStart = !completed && exerciseCount(plan) > 0,
                                onClick = { onOpenPlan(plan) },
                                onStart = { onStartWorkout(plan) },
                                onDelete = { planToDelete = plan },
                            )
                        }
                    }
                    RowDivider()
                    // Exercises done on top of (or instead of) the plans: added straight to the day, no plan needed.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    knownExercises = exerciseViewModel.knownExercises()
                                    planChoices = planViewModel.planChoices()
                                    showExtraSheet = true
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(AppColors.SurfaceHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Add extra exercise", style = AppType.BodyStrong, color = AppColors.TextPrimary)
                            Text("Something that's not in a plan", style = AppType.Caption, color = AppColors.TextSecondary)
                        }
                    }
                    }
                }
            }

            // The coach: the few things most worth doing next, from the user's own logs.
            if (shownAdvice.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(28.dp))
                    AccentTitle("For you", modifier = Modifier.padding(start = 4.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    AdviceCard(
                        shownAdvice,
                        limit = 3,
                        onSeeAll = onOpenCoach,
                        acceptLabel = acceptLabel,
                        onAccept = onAcceptAdvice,
                        onDismiss = { planViewModel.snooze("advice:${it.kind}:${it.exercise.orEmpty()}") },
                    )
                }
            }
        }
        BodyEdgeHandle(onOpen = onOpenBody, modifier = Modifier.align(Alignment.CenterEnd))
    }

    planToDelete?.let { plan ->
        StrivoAlertDialog(
            title = "Delete plan?",
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
                "'${plan.planName}' and its exercises will be removed from $selectedDay.",
                style = AppType.Body,
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
            plans = planChoices,
            addedNames = addedNames,
            addedPlanNames = plansForDay.map { it.planName.trim().lowercase() }.toSet(),
            onAdd = { exercise ->
                scope.launch {
                    val planId = planViewModel.extraPlanFor(selectedDay)
                    // Extras are one-offs by default; a copy keeps the sets, reps, weight and notes.
                    exerciseViewModel.copyExerciseToPlan(exercise.copy(vanishEndOfDay = true), planId)
                }
            },
            onAddPlan = { choice ->
                scope.launch {
                    // A real plan on the day for today only, like the extra plan: it counts, can be started, and goes tomorrow.
                    planViewModel.addOneOffPlan(choice, selectedDay)
                    exerciseViewModel.loadExercisesByDay(selectedDay)
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

// --- Header ---

/** A round avatar with the first letter of the name. */
@Composable
private fun Avatar(name: String?) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(AppColors.AccentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name?.trim()?.firstOrNull()?.uppercase() ?: "S",
            style = AppType.Headline,
            color = AppColors.Accent,
        )
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier, accentIcon: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (accentIcon) {
                Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(2.dp))
            }
            Text(value, style = AppType.Metric.copy(fontSize = 24.sp), color = AppColors.TextPrimary, maxLines = 1)
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = AppType.Caption, color = AppColors.TextSecondary, maxLines = 1)
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(36.dp).background(AppColors.Divider))
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 70.dp), thickness = 1.dp, color = AppColors.Divider)
}

// --- Weekly weigh-in ---

@Composable
private fun WeightUpdateDialog(onUpdate: (Double) -> Unit) {
    var weight by remember { mutableStateOf(70.0) }
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.Surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Weekly weigh-in", style = AppType.Title, color = AppColors.TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text("Keeps your trends and calorie estimates accurate.", style = AppType.Caption, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            DecimalWheelPicker(label = "KG", minValue = 30, maxValue = 250, value = weight, onValueChange = { weight = it })
            Spacer(Modifier.height(24.dp))
            AccentButton(text = "Save weight", onClick = { onUpdate(weight) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
