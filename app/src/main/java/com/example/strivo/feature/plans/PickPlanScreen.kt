package com.example.strivo.feature.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.IconBadge
import com.example.strivo.core.ui.components.ScreenTopBar
import com.example.strivo.core.ui.components.SectionLabel
import com.example.strivo.core.ui.components.StrivoAlertDialog
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.model.Plan
import com.example.strivo.domain.plan.LibraryPlan
import kotlinx.coroutines.launch

/**
 * Where a plan for [day] comes from. The user's own plans come first: the ones they made, the ones a trainer sent and accepted, and
 * the ones they took from the plan library. Under them is the plan library (what an admin publishes, or the starter plans), which
 * works with no trainer at all. Tapping a plan puts a copy on the day and goes back; the copy stays linked to the user's plan, so what
 * they change on the day (heavier weights, fewer reps on the last set, an exercise more or less) is kept in the plan. A plan taken from
 * the library becomes one of the user's own at that moment.
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
    val library by planViewModel.libraryPlans.collectAsStateWithLifecycle()
    val counts by planViewModel.exerciseCounts.collectAsStateWithLifecycle()
    val own = remember(plans) { plans.filter { it.isPrebuilt } }
    // A library plan the user already took is one of their own: it is in the first list, with their changes, not in this one.
    val fromLibrary = remember(library, own) {
        val taken = own.map { it.origin }.toSet()
        library.filter { it.origin !in taken }
    }
    var planToDelete by remember { mutableStateOf<Plan?>(null) }

    LaunchedEffect(Unit) { planViewModel.refreshPlans() }

    fun put(action: suspend () -> Unit) {
        scope.launch {
            action()
            exerciseViewModel.loadExercisesByDay(day)
            onBack()
        }
    }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = { ScreenTopBar(title = "Add to $day", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            item {
                Text(
                    "Tap a plan to put it on $day. What you change on the day (weights, reps, exercises) is kept in your plan.",
                    style = AppType.Body,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 18.dp),
                )
            }

            item { SectionLabel("Your plans") }
            if (own.isEmpty()) {
                item {
                    Text(
                        "Nothing yet. Take a plan from the library below, or make your own.",
                        style = AppType.Caption,
                        color = AppColors.TextTertiary,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }
            items(own, key = { "own-" + (it.planId ?: it.hashCode().toLong()) }) { plan ->
                PlanRow(
                    title = plan.planName,
                    detail = listOf(countText(counts[plan.planId] ?: 0), originText(plan)).filter { it.isNotEmpty() }.joinToString(" · "),
                    onClick = { put { planViewModel.addPlanToDay(plan, day) } },
                ) {
                    IconButton(onClick = { onEditPlan(plan) }) {
                        Icon(Icons.Rounded.EditNote, contentDescription = "Edit exercises", tint = AppColors.TextSecondary)
                    }
                    IconButton(onClick = { planToDelete = plan }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete plan", tint = AppColors.TextTertiary)
                    }
                }
            }

            if (fromLibrary.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionLabel("Plan library") }
                items(fromLibrary, key = { "library-" + it.id }) { plan ->
                    PlanRow(
                        title = plan.name,
                        detail = listOf(countText(plan.exercises.size), plan.level).filter { it.isNotEmpty() }.joinToString(" · "),
                        extra = plan.exercises.joinToString(", ") { it.name },
                        onClick = { put { planViewModel.addLibraryPlanToDay(plan, day) } },
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                AccentButton(
                    text = "CREATE NEW PLAN",
                    onClick = onCreatePlan,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Add,
                )
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
                    planToDelete = null
                }
            },
        ) {
            Text(
                "This deletes '${plan.planName}' from your plans. The days it is already on keep their copy, but it is no longer kept up to date.",
                color = AppColors.TextSecondary,
            )
        }
    }
}

private fun countText(count: Int): String = if (count == 1) "1 exercise" else "$count exercises"

/** Where one of the user's plans came from, in words. */
private fun originText(plan: Plan): String = when {
    plan.fromTrainer -> "From your trainer"
    plan.fromLibrary -> "From the library"
    else -> "Made by you"
}

/** One plan in a list: a card that adds it to the day when tapped, with room at the end for buttons. */
@Composable
private fun PlanRow(
    title: String,
    detail: String,
    onClick: () -> Unit,
    extra: String = "",
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .clip(AppShapes.Card)
            .background(AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = Icons.Rounded.FitnessCenter)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.Headline, color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(detail, style = AppType.Caption, color = AppColors.TextSecondary)
            if (extra.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(extra, style = AppType.Caption, color = AppColors.TextTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}
