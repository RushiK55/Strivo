package com.example.strivo.feature.plans

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.AccentTitle
import com.example.strivo.core.ui.components.AppCard
import com.example.strivo.core.ui.components.CollapsingListScreen
import com.example.strivo.core.ui.components.StrivoAlertDialog
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.data.share.ExerciseShare
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.Plan
import com.example.strivo.domain.model.dropSetsSuffix
import kotlinx.coroutines.launch

private data class Transfer(val exercise: Exercise, val move: Boolean)

private const val DragScrollZone = 160f
private const val DragScrollStep = 30f

private fun exerciseKey(exercise: Exercise): Long = exercise.id ?: exercise.hashCode().toLong()

@Composable
fun PlanDetailsScreen(
    plan: Plan,
    otherPlans: List<Plan>,
    exerciseViewModel: ExerciseViewModel,
    onBack: () -> Unit,
    onAddExercise: () -> Unit,
    onOpenLibrary: () -> Unit = {},
    onEditExercise: (Exercise) -> Unit,
    onOpenExercise: (Exercise) -> Unit,
    onStartWorkout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val exercises by exerciseViewModel.exercises.collectAsStateWithLifecycle()
    var exerciseToDelete by remember { mutableStateOf<Exercise?>(null) }
    var transfer by remember { mutableStateOf<Transfer?>(null) }
    var showPaste by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // The list shown while dragging; it is saved once the user lets go.
    var items by remember { mutableStateOf(exercises) }
    var draggingKey by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(plan.planId) {
        plan.planId?.let { exerciseViewModel.loadExercisesByPlan(it) }
    }
    LaunchedEffect(exercises) {
        if (draggingKey == null) items = exercises
    }

    fun onDrag(deltaY: Float) {
        val key = draggingKey ?: return
        dragOffset += deltaY
        val visible = listState.layoutInfo.visibleItemsInfo
        val current = visible.firstOrNull { it.key == key } ?: return

        // Swap places once the dragged card's centre crosses another card.
        val centre = current.offset + dragOffset + current.size / 2f
        val target = visible.firstOrNull {
            it.key is Long && it.key != key && centre > it.offset && centre < it.offset + it.size
        }
        if (target != null) {
            val from = items.indexOfFirst { exerciseKey(it) == key }
            val to = items.indexOfFirst { exerciseKey(it) == target.key }
            if (from >= 0 && to >= 0) {
                items = items.toMutableList().apply { add(to, removeAt(from)) }
                // Keep the card under the finger after its slot changes.
                val newOffset = if (to > from) current.offset + target.size else target.offset
                dragOffset += current.offset - newOffset
            }
        }

        // Scroll the list when the card is dragged near the top or bottom edge.
        val top = current.offset + dragOffset
        val bottom = top + current.size
        val scrollStep = when {
            top < DragScrollZone -> -DragScrollStep
            bottom > listState.layoutInfo.viewportEndOffset - DragScrollZone -> DragScrollStep
            else -> 0f
        }
        if (scrollStep != 0f) {
            scope.launch { dragOffset += listState.scrollBy(scrollStep) }
        }
    }

    fun onDragEnd() {
        draggingKey = null
        dragOffset = 0f
        val planId = plan.planId ?: return
        val ids = items.mapNotNull { it.id }
        if (ids != exercises.mapNotNull { it.id }) {
            scope.launch { exerciseViewModel.reorderExercises(planId, ids) }
        }
    }

    CollapsingListScreen(
        title = plan.planName,
        subtitle = listOf(
            if (plan.isPrebuilt) "Prebuilt plan" else plan.planDay,
            if (exercises.size == 1) "1 exercise" else "${exercises.size} exercises",
        ).joinToString(" · "),
        expandedHeight = 132.dp,
        onBack = onBack,
        listState = listState,
        backButtonBackground = AppColors.Surface,
        header = {},
        overlay = {
            if (exercises.isNotEmpty() && !plan.isPrebuilt) {
                StartWorkoutButton(
                    onClick = onStartWorkout,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        },
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 12.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccentTitle("Exercises", modifier = Modifier.weight(1f))
                if (items.isNotEmpty()) {
                    IconButton(onClick = { shareText(context, ExerciseShare.encodePlan(plan.planName, items), "Share plan") }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share plan", tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
                    }
                }
                IconButton(onClick = onOpenLibrary) {
                    Icon(Icons.Rounded.MenuBook, contentDescription = "Add from the exercise library", tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { showPaste = true }) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste exercises", tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
                }
                Row(
                    modifier = Modifier
                        .clip(AppShapes.Chip)
                        .clickable(onClick = onAddExercise)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", style = AppType.BodyStrong, color = AppColors.Accent)
                }
            }
        }

        if (items.isEmpty()) {
            item { EmptyExercises(onAddExercise) }
        } else {
            itemsIndexed(items, key = { _, e -> exerciseKey(e) }) { index, exercise ->
                val key = exerciseKey(exercise)
                val isDragging = draggingKey == key
                ExerciseCard(
                    exercise = exercise,
                    index = index,
                    isFirst = index == 0,
                    isLast = index == items.lastIndex,
                    isDragging = isDragging,
                    dragHandle = Modifier.pointerInput(key) {
                        detectDragGestures(
                            onDragStart = { draggingKey = key; dragOffset = 0f },
                            onDragEnd = ::onDragEnd,
                            onDragCancel = ::onDragEnd,
                            onDrag = { change, amount ->
                                change.consume()
                                onDrag(amount.y)
                            },
                        )
                    },
                    modifier = if (isDragging) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer { translationY = dragOffset }
                    } else {
                        Modifier.animateItem()
                    },
                    onOpen = { onOpenExercise(exercise) },
                    onEdit = { onEditExercise(exercise) },
                    onCopy = { transfer = Transfer(exercise, move = false) },
                    onMove = { transfer = Transfer(exercise, move = true) },
                    onShare = { shareText(context, ExerciseShare.encodeExercise(exercise), "Share exercise") },
                    onDelete = { exerciseToDelete = exercise },
                )
            }
            item {
                Text(
                    "Drag the handle to reorder. Tap an exercise for its details.",
                    style = AppType.Caption,
                    color = AppColors.TextTertiary,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp),
                )
            }
        }

        item { Spacer(Modifier.height(120.dp)) }
    }

    if (showPaste) {
        PasteSharedDialog(
            title = "Paste exercise",
            onDismiss = { showPaste = false },
            onImport = { shared ->
                showPaste = false
                val planId = plan.planId
                if (planId != null) {
                    scope.launch {
                        exerciseViewModel.addExercises(shared.exercises.map { it.copy(planId = planId) })
                        Toast.makeText(context, "Added ${shared.exercises.size} exercise(s)", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

    transfer?.let { (exercise, move) ->
        StrivoAlertDialog(
            title = "${if (move) "Move" else "Copy"} '${exercise.name}' to…",
            onDismiss = { transfer = null },
            confirmText = "Cancel",
            dismissText = null,
            onConfirm = { transfer = null },
        ) {
            if (otherPlans.isEmpty()) {
                Text("You have no other plans yet.", color = AppColors.TextSecondary)
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    otherPlans.forEach { target ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(AppColors.Field)
                                .clickable {
                                    val targetId = target.planId
                                    val fromId = plan.planId
                                    transfer = null
                                    if (targetId != null && fromId != null) {
                                        scope.launch {
                                            if (move) {
                                                exerciseViewModel.moveExerciseToPlan(exercise, fromId, targetId)
                                            } else {
                                                exerciseViewModel.copyExerciseToPlan(exercise, targetId)
                                            }
                                            Toast.makeText(
                                                context,
                                                "${if (move) "Moved" else "Copied"} to ${target.planName}",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(target.planName, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
                            Text(
                                if (target.isPrebuilt) "Prebuilt plan" else target.planDay,
                                color = AppColors.TextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    exerciseToDelete?.let { exercise ->
        StrivoAlertDialog(
            title = "Delete Exercise?",
            onDismiss = { exerciseToDelete = null },
            confirmText = "Delete",
            confirmColor = AppColors.Danger,
            onConfirm = {
                val id = exercise.id
                val planId = plan.planId
                exerciseToDelete = null
                if (id != null && planId != null) {
                    scope.launch { exerciseViewModel.deleteExercise(id, planId) }
                }
            },
        ) {
            Text("Are you sure you want to remove '${exercise.name}'?", color = AppColors.TextSecondary)
        }
    }
}

@Composable
private fun EmptyExercises(onAdd: () -> Unit) {
    AppCard(modifier = Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 20.dp, vertical = 28.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No exercises yet", style = AppType.Headline, color = AppColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Add the exercises for this plan, in the order you do them.",
                style = AppType.Caption,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            AccentButton(text = "Add exercise", onClick = onAdd, icon = Icons.Rounded.Add, height = 46.dp)
        }
    }
}

/** "3 × 10 · 40 kg" (or "bodyweight"), with "+ 1 drop" when the plan has drop sets. */
private fun planSummary(exercise: Exercise): String {
    val weight = exercise.weight.toDoubleOrNull() ?: 0.0
    val weightText = if (weight <= 0.0) "bodyweight" else "${if (weight % 1.0 == 0.0) weight.toInt() else weight} kg"
    return "${exercise.sets} × ${exercise.reps}${exercise.dropSetsSuffix()} · $weightText"
}

/**
 * One exercise as a row of a grouped list: the rows together look like a single card, with the first and last
 * rounded and thin dividers between. The row being dragged lifts out as its own rounded card.
 */
@Composable
private fun ExerciseCard(
    exercise: Exercise,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    isDragging: Boolean,
    dragHandle: Modifier,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val radius = 18.dp
    val shape = when {
        isDragging -> RoundedCornerShape(radius)
        isFirst && isLast -> RoundedCornerShape(radius)
        isFirst -> RoundedCornerShape(topStart = radius, topEnd = radius)
        isLast -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
        else -> RoundedCornerShape(0.dp)
    }
    val completedSets = exercise.setsList.filter { it.isCompleted }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(if (isDragging) AppColors.SurfaceHigh else AppColors.Surface)
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = dragHandle.size(width = 32.dp, height = 44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.DragIndicator,
                    contentDescription = "Drag to reorder",
                    tint = if (isDragging) AppColors.Accent else AppColors.TextTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (exercise.isCheck) AppColors.Accent else AppColors.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (exercise.isCheck) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                } else {
                    Text("${index + 1}", style = AppType.Caption.copy(fontWeight = FontWeight.SemiBold), color = AppColors.TextSecondary)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    style = AppType.Headline.copy(fontSize = 16.sp),
                    textDecoration = if (exercise.isCheck) TextDecoration.LineThrough else null,
                    color = if (exercise.isCheck) AppColors.TextSecondary else AppColors.TextPrimary,
                    maxLines = 1,
                )
                Text(text = planSummary(exercise), style = AppType.Caption, color = AppColors.TextSecondary, maxLines = 1)
                if (completedSets.isNotEmpty()) {
                    Text(
                        text = "Today: " + completedSets.joinToString(", ") { (it.type.badge?.let { b -> "$b " } ?: "") + "${if (it.weight % 1.0 == 0.0) it.weight.toInt() else it.weight}×${it.reps}" },
                        style = AppType.Caption,
                        color = AppColors.Accent,
                        maxLines = 1,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = AppColors.TextTertiary)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(14.dp),
                    containerColor = AppColors.SurfaceHigh,
                ) {
                    DropdownMenuItem(text = { Text("View details", color = AppColors.TextPrimary) }, onClick = { menuOpen = false; onOpen() })
                    DropdownMenuItem(text = { Text("Edit", color = AppColors.TextPrimary) }, onClick = { menuOpen = false; onEdit() })
                    DropdownMenuItem(text = { Text("Copy to another plan", color = AppColors.TextPrimary) }, onClick = { menuOpen = false; onCopy() })
                    DropdownMenuItem(text = { Text("Move to another plan", color = AppColors.TextPrimary) }, onClick = { menuOpen = false; onMove() })
                    DropdownMenuItem(text = { Text("Share", color = AppColors.TextPrimary) }, onClick = { menuOpen = false; onShare() })
                    DropdownMenuItem(text = { Text("Delete", color = AppColors.Danger) }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
        if (!isLast && !isDragging) {
            HorizontalDivider(Modifier.padding(start = 82.dp), thickness = 1.dp, color = AppColors.Divider)
        }
    }
}

/** The main action, docked at the bottom over a fade so the list scrolls cleanly underneath. */
@Composable
private fun StartWorkoutButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, AppColors.Background, AppColors.Background)))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
    ) {
        AccentButton(
            text = "Start workout",
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            height = 56.dp,
            icon = Icons.Rounded.PlayArrow,
        )
    }
}

/** Opens the system share sheet with [text], a message another Strivo user can paste into the app. */
private fun shareText(context: Context, text: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}
