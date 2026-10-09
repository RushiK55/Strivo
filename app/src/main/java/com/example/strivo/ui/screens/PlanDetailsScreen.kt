package com.example.strivo.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.data.ExerciseShare
import com.example.strivo.data.model.Exercise
import com.example.strivo.data.model.Plan
import com.example.strivo.ui.components.AccentTitle
import com.example.strivo.ui.components.CollapsingListScreen
import com.example.strivo.ui.components.IconBadge
import com.example.strivo.ui.components.PasteSharedDialog
import com.example.strivo.ui.components.StrivoAlertDialog
import com.example.strivo.ui.theme.AppColors
import com.example.strivo.viewmodel.ExerciseViewModel
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
        expandedHeight = 240.dp,
        onBack = onBack,
        listState = listState,
        backButtonBackground = AppColors.Surface.copy(alpha = 0.5f),
        header = { PlanHeader(plan, exercises.size) },
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
                    .padding(start = 24.dp, top = 30.dp, end = 24.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccentTitle("WORKOUT SEQUENCE", letterSpacing = 1.5.sp, gap = 6.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (items.isNotEmpty()) {
                        IconBadge(
                            icon = Icons.Rounded.Share,
                            tint = AppColors.Accent,
                            background = AppColors.Surface,
                            padding = 10.dp,
                            iconSize = 22.dp,
                            modifier = Modifier.clickable {
                                shareText(context, ExerciseShare.encodePlan(plan.planName, items), "Share plan")
                            },
                        )
                    }
                    IconBadge(
                        icon = Icons.Rounded.ContentPaste,
                        tint = AppColors.Accent,
                        background = AppColors.Surface,
                        padding = 10.dp,
                        iconSize = 22.dp,
                        modifier = Modifier.clickable { showPaste = true },
                    )
                    IconBadge(
                        icon = Icons.Rounded.Add,
                        tint = Color.Black,
                        background = AppColors.Accent,
                        padding = 10.dp,
                        iconSize = 22.dp,
                        modifier = Modifier.clickable(onClick = onAddExercise),
                    )
                }
            }
        }

        if (items.isEmpty()) {
            item { EmptyExercises() }
        } else {
            itemsIndexed(items, key = { _, e -> exerciseKey(e) }) { index, exercise ->
                val key = exerciseKey(exercise)
                val isDragging = draggingKey == key
                ExerciseCard(
                    exercise = exercise,
                    index = index,
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
private fun PlanHeader(plan: Plan, exerciseCount: Int) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = 80.dp)
                .size(200.dp)
                .clip(CircleShape)
                .background(AppColors.Accent.copy(alpha = 0.03f))
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBadge(
                icon = Icons.Rounded.FitnessCenter,
                background = AppColors.Accent.copy(alpha = 0.1f),
                border = BorderStroke(2.dp, AppColors.Accent.copy(alpha = 0.2f)),
                padding = 24.dp,
                iconSize = 60.dp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "${if (plan.isPrebuilt) "PREBUILT" else plan.planDay} • $exerciseCount EXERCISES",
                color = AppColors.Accent,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppColors.Surface)
                    .border(BorderStroke(1.dp, AppColors.Accent.copy(alpha = 0.3f)), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun EmptyExercises() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(
            icon = Icons.AutoMirrored.Rounded.ListAlt,
            tint = AppColors.Accent.copy(alpha = 0.3f),
            background = AppColors.Surface,
            padding = 30.dp,
            iconSize = 60.dp,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "No exercises in this plan",
            color = AppColors.TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ExerciseCard(
    exercise: Exercise,
    index: Int,
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
    val shape = RoundedCornerShape(30.dp)

    Column(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Surface)
            .border(BorderStroke(1.dp, if (isDragging) AppColors.Accent else AppColors.Field), shape)
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = dragHandle.size(width = 28.dp, height = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.DragIndicator,
                    contentDescription = "Drag to reorder",
                    tint = if (isDragging) AppColors.Accent else AppColors.TextSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (exercise.isCheck) AppColors.Accent.copy(alpha = 0.2f) else AppColors.Field),
                contentAlignment = Alignment.Center,
            ) {
                if (exercise.isCheck) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(24.dp))
                } else {
                    Text("${index + 1}", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textDecoration = if (exercise.isCheck) TextDecoration.LineThrough else null,
                    color = if (exercise.isCheck) AppColors.TextSecondary else AppColors.TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${exercise.sets} SETS • ${exercise.reps} REPS • ${exercise.weight} KG",
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = AppColors.TextSecondary)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = AppColors.Surface,
                ) {
                    DropdownMenuItem(
                        text = { Text("View Details", color = AppColors.TextPrimary) },
                        onClick = { menuOpen = false; onOpen() },
                    )
                    DropdownMenuItem(
                        text = { Text("Edit", color = AppColors.TextPrimary) },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy to another plan", color = AppColors.TextPrimary) },
                        onClick = { menuOpen = false; onCopy() },
                    )
                    DropdownMenuItem(
                        text = { Text("Move to another plan", color = AppColors.TextPrimary) },
                        onClick = { menuOpen = false; onMove() },
                    )
                    DropdownMenuItem(
                        text = { Text("Share", color = AppColors.TextPrimary) },
                        onClick = { menuOpen = false; onShare() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = AppColors.Danger) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
        if (exercise.setsList.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(start = 100.dp, end = 20.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                exercise.setsList.take(5).forEach { set ->
                    Text(
                        text = "${set.weight}kg × ${set.reps}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.Field)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StartWorkoutButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Accent)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "START WORKOUT",
            color = Color.Black,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            letterSpacing = 1.2.sp,
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
