package com.example.strivo.feature.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.core.ui.components.color
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.core.util.formatWeight
import com.example.strivo.domain.model.SetType
import com.example.strivo.domain.plan.nextSet

// --- Focus mode ---

internal data class FocusInfo(
    val exerciseIndex: Int,
    val setIndex: Int,
    val exerciseName: String,
    val setCount: Int,
    val weight: Double,
    val reps: Int,
    val type: SetType,
    val isRunning: Boolean,
)

/** The set the focus screen is about: the running one, otherwise the next set still to do. */
internal fun focusTarget(state: WorkoutState): FocusInfo? {
    val activeExercise = state.activeSetExerciseIndex
    val activeSet = state.activeSetIndex
    if (activeExercise != null && activeSet != null) {
        val exercise = state.exercises.getOrNull(activeExercise)
        val set = exercise?.setsList?.getOrNull(activeSet)
        if (exercise != null && set != null) {
            return FocusInfo(activeExercise, activeSet, exercise.name, exercise.setsList.size, set.weight, set.reps, set.type, isRunning = true)
        }
    }
    // The exercise being worked on is finished first; then the earliest one still to do (see nextSet).
    val next = nextSet(state.exercises, state.currentExerciseIndex) ?: return null
    val exercise = state.exercises[next.exerciseIndex]
    val set = exercise.setsList[next.setIndex]
    return FocusInfo(next.exerciseIndex, next.setIndex, exercise.name, exercise.setsList.size, set.weight, set.reps, set.type, isRunning = false)
}

/**
 * Whether the phone can blur what is behind a view (Android 12 and later). On Android 11 and older `Modifier.blur` does nothing, so the
 * list would stay sharp behind a thin dark layer and look see-through; there the dark layer is made nearly solid instead.
 */
internal val CanBlur: Boolean = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

/** How dark the layer over the workout list is: light over a blur, nearly solid where there is no blur. */
internal val FocusScrimAlpha: Float = if (CanBlur) 0.55f else 0.94f

/**
 * Full-screen focus view over the blurred workout list. One big round button: START for the next set
 * (accent), STOP while a set runs (orange). The list only shows again when the user closes this view.
 */
@Composable
internal fun FocusOverlay(visible: Boolean, info: FocusInfo?, viewModel: WorkoutViewModel, onClose: () -> Unit, onInfo: (Int) -> Unit = {}) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(300)), exit = fadeOut(tween(250))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = FocusScrimAlpha))
                // Swallow touches so nothing behind the overlay can be tapped.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        )
    }
    AnimatedVisibility(
        visible = visible && info != null,
        modifier = Modifier.fillMaxSize(),
        enter = scaleIn(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            initialScale = 0.15f,
        ) + fadeIn(tween(200)),
        exit = scaleOut(tween(250), targetScale = 0.15f) + fadeOut(tween(200)),
    ) {
        if (info == null) return@AnimatedVisibility
        val setTime by viewModel.setTime.collectAsStateWithLifecycle()
        val restTime by viewModel.restTime.collectAsStateWithLifecycle()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val canStart = info.isRunning || info.reps > 0
        val isResting = state.showRestTimer && !info.isRunning
        val paused = state.isPaused
        val restingBetweenExercises = isResting && state.isExerciseRest
        var editing by remember { mutableStateOf<MetricEdit?>(null) }

        // The button's colour is the phase: yellow = ready for a set, orange = a set is running, red = resting.
        val buttonColor by animateColorAsState(
            when {
                paused -> AppColors.Accent
                info.isRunning -> AppColors.WarningAccent
                isResting -> AppColors.Rest
                canStart -> AppColors.Accent
                else -> AppColors.Field
            },
            tween(300),
            label = "focusColor",
        )

        // The button pops (shrinks, then springs back) every time it changes from START to STOP or to the next set.
        val pop = remember { Animatable(1f) }
        LaunchedEffect(info.isRunning, info.exerciseIndex, info.setIndex) {
            pop.snapTo(0.8f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
        val pulse by rememberInfiniteTransition(label = "setPulse").animateFloat(
            initialValue = 1f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
            label = "setPulseScale",
        )

        // The same wheel pickers as in the exercise list, opened by tapping the weight or reps.
        when (editing) {
            MetricEdit.Weight -> WeightPickerDialog(
                initial = info.weight,
                onDismiss = { editing = null },
                onSelected = { viewModel.setWeight(info.exerciseIndex, info.setIndex, it); editing = null },
            )

            MetricEdit.Reps -> RepsPickerDialog(
                initial = if (info.reps == 0) 10 else info.reps,
                onDismiss = { editing = null },
                onSelected = { viewModel.setReps(info.exerciseIndex, info.setIndex, it); editing = null },
            )

            null -> Unit
        }

        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(AppColors.Surface.copy(alpha = 0.85f)),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Show exercise list", tint = AppColors.TextPrimary)
            }
            // How this exercise is done, its notes and tip, without leaving the set.
            IconButton(
                onClick = { onInfo(info.exerciseIndex) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(AppColors.Surface.copy(alpha = 0.85f)),
            ) {
                Icon(androidx.compose.material.icons.Icons.Outlined.Info, contentDescription = "About this exercise: notes and how to do it", tint = AppColors.TextPrimary)
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "${if (restingBetweenExercises) "Up next · " else ""}${if (info.type == SetType.Normal) "Set" else info.type.label} ${info.setIndex + 1} of ${info.setCount}",
                    style = AppType.BodyStrong,
                    color = AppColors.Accent,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    info.exerciseName,
                    style = AppType.LargeTitle,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                Spacer(Modifier.height(24.dp))
                // The weight and reps of this set. The set can differ from the plan: tap a value to pick it on the wheel,
                // or nudge it with the round buttons. Every change is saved at once, and stopping the set saves it.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricStepper(
                        label = "kg",
                        onEdit = { editing = MetricEdit.Weight },
                        value = if (info.weight == 0.0) "BW" else formatWeight(info.weight),
                        onMinus = { viewModel.setWeight(info.exerciseIndex, info.setIndex, (info.weight - WeightStep).coerceAtLeast(0.0)) },
                        onPlus = { viewModel.setWeight(info.exerciseIndex, info.setIndex, info.weight + WeightStep) },
                        modifier = Modifier.weight(1f),
                    )
                    MetricStepper(
                        label = "Reps",
                        onEdit = { editing = MetricEdit.Reps },
                        value = if (info.reps > 0) info.reps.toString() else "–",
                        onMinus = { viewModel.setReps(info.exerciseIndex, info.setIndex, (info.reps - 1).coerceAtLeast(0)) },
                        onPlus = { viewModel.setReps(info.exerciseIndex, info.setIndex, info.reps + 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
                val saved = state.lastSavedSet
                if (!info.isRunning && saved != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = AppColors.Success, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Saved $saved", style = AppType.Caption, color = AppColors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(28.dp))
                // What the clock below is counting: rest between sets or between exercises, or a paused workout.
                val badge = when {
                    paused -> "Paused"
                    restingBetweenExercises -> "Rest before next exercise"
                    isResting -> "Rest before next set"
                    info.isRunning -> "Set time"
                    else -> "Ready when you are"
                }
                val badgeColor = when {
                    paused -> AppColors.Warning
                    isResting -> AppColors.Rest
                    info.isRunning -> AppColors.WarningAccent
                    else -> AppColors.TextSecondary
                }
                Text(
                    text = badge,
                    style = AppType.Caption.copy(fontWeight = FontWeight.SemiBold),
                    color = badgeColor,
                    modifier = Modifier
                        .clip(AppShapes.Chip)
                        .background(badgeColor.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        info.isRunning -> setTime
                        isResting -> restTime
                        else -> ZeroTimeMinSec
                    },
                    color = if (info.isRunning || isResting) AppColors.TextPrimary else AppColors.TextTertiary,
                    style = AppType.MetricLarge.copy(fontSize = 60.sp, lineHeight = 66.sp),
                )
                Spacer(Modifier.height(28.dp))
                Box(contentAlignment = Alignment.Center) {
                    if (info.isRunning && !paused) {
                        // Soft ring that keeps growing outwards and fading while the set runs.
                        Box(
                            Modifier
                                .size(184.dp)
                                .scale(pulse)
                                .alpha((1.35f - pulse) / 0.35f * 0.4f)
                                .border(BorderStroke(3.dp, AppColors.WarningAccent), CircleShape),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(184.dp)
                            .scale(pop.value)
                            .shadow(
                                elevation = if (canStart) 24.dp else 0.dp,
                                shape = CircleShape,
                                ambientColor = buttonColor,
                                spotColor = buttonColor,
                            )
                            .clip(CircleShape)
                            .background(buttonColor)
                            .clickable {
                                // A set without reps cannot start: send the user back to the list to fill it in.
                                when {
                                    paused -> viewModel.startStopWorkout() // resume everything that was frozen
                                    canStart -> viewModel.onSetButton(info.exerciseIndex, info.setIndex)
                                    else -> onClose()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (canStart || paused) {
                            Icon(
                                imageVector = if (info.isRunning && !paused) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                contentDescription = when {
                                    paused -> "Resume"
                                    info.isRunning -> "Finish set"
                                    else -> "Start set"
                                },
                                tint = Color.Black,
                                modifier = Modifier.size(96.dp),
                            )
                        } else {
                            Text("Set reps", style = AppType.Title, color = AppColors.TextPrimary)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = when {
                        paused -> "Tap to resume"
                        info.isRunning -> "Tap to finish the set"
                        canStart -> "Tap to start the set"
                        else -> "Tap to open the list"
                    },
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                )
                // The same pause as the main button on the list: it freezes the workout, set and rest clocks together.
                if (state.isWorkoutRunning) {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(AppColors.Surface.copy(alpha = 0.9f))
                            .clickable { viewModel.startStopWorkout() }
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Pause, contentDescription = null, tint = AppColors.TextPrimary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pause workout", style = AppType.BodyStrong, color = AppColors.TextPrimary)
                    }
                }
            }
        }
    }
}

private const val WeightStep = 2.5

private enum class MetricEdit { Weight, Reps }

/**
 * The weight or reps of the current set, big: tap the value to pick it on the same wheel as in the exercise list,
 * or nudge it with the round buttons (accent "+" like the add buttons elsewhere).
 */
@Composable
private fun MetricStepper(
    label: String,
    value: String,
    onEdit: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(AppShapes.Card)
            .background(AppColors.Surface)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = AppType.Caption, color = AppColors.TextSecondary)
        Text(
            value,
            style = AppType.Metric.copy(fontSize = 34.sp, lineHeight = 40.sp),
            color = if (value == "–") AppColors.TextTertiary else AppColors.TextPrimary,
            maxLines = 1,
            modifier = Modifier
                .clip(AppShapes.Chip)
                .clickable(onClick = onEdit)
                .padding(horizontal = 12.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepperButton(Icons.Rounded.Remove, "Decrease $label", onMinus, accent = false)
            StepperButton(Icons.Rounded.Add, "Increase $label", onPlus, accent = true)
        }
    }
}

/** Round buttons like the ones used across the app: the "+" is accent-filled, the "−" is the soft grey. */
@Composable
private fun StepperButton(icon: ImageVector, description: String, onClick: () -> Unit, accent: Boolean) {
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(if (accent) AppColors.Accent else AppColors.SurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (accent) Color.Black else AppColors.TextPrimary, modifier = Modifier.size(20.dp))
    }
}
