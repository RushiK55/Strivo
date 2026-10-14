package com.example.strivo.feature.body

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.strivo.core.ui.components.AppCard
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.body.BodyKey
import com.example.strivo.domain.body.DrawState
import com.example.strivo.domain.body.MuscleLayer
import com.example.strivo.domain.body.MuscleRole
import com.example.strivo.domain.body.TrainedMusclesFinder
import com.example.strivo.domain.model.Exercise
import com.example.strivo.feature.body.gl.BodyGlView

/**
 * "Your body": the 3D body of the account (female or male, as the profile says), with the muscles that the chosen day's training
 * works glowing on it. Pick a day to see what it trains, turn and zoom the body, tap a muscle to read about it and see which of your
 * exercises work it.
 */
@Composable
fun BodyScreen(onBack: () -> Unit, viewModel: BodyViewModel = viewModel(key = "body_screen")) {
    LaunchedEffect(Unit) { viewModel.load(withModel = true) }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    BodyContent(ui, onBack, onSelectDay = viewModel::selectDay, onSelect = viewModel::select, onToggleSkin = viewModel::toggleSkin)
}

/** The screen itself, from a state and what the user does; apart from [BodyScreen] so a preview can show it with made-up data. */
@Composable
fun BodyContent(ui: BodyUiState, onBack: () -> Unit, onSelectDay: (String) -> Unit, onSelect: (String?) -> Unit, onToggleSkin: () -> Unit) {
    var creditsOpen by remember { mutableStateOf(false) }
    var glView by remember { mutableStateOf<BodyGlView?>(null) }
    var front by remember { mutableStateOf(true) }
    val catalogue = ui.catalogue
    val model = ui.model

    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        if (catalogue != null && model != null) {
            // The body is drawn in the space between the days at the top and the card at the bottom (which is taller for a muscle), so
            // neither hides it; the camera frames it in that space.
            BodyGl(
                ui = ui,
                onPicked = onSelect,
                onView = { glView = it },
                modifier = Modifier.padding(top = 132.dp, bottom = if (ui.selected != null) 310.dp else 168.dp),
            )
        } else {
            Text(
                text = ui.error ?: "Loading your body…",
                style = AppType.Body,
                color = AppColors.TextSecondary,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }

        // Top: back, who the body is, and the days
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 4.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                com.example.strivo.core.ui.components.PlainBackButton(onClick = onBack)
                Column(Modifier.weight(1f)) {
                    Text("Your body", style = AppType.Headline, color = AppColors.TextPrimary)
                    Text(
                        text = bodyCaption(ui),
                        style = AppType.Caption,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { creditsOpen = true }) {
                    Icon(Icons.Rounded.Info, contentDescription = "Credits for the 3D models", tint = AppColors.TextSecondary)
                }
            }
            DayChips(selected = ui.day, today = ui.today, onSelect = onSelectDay)
        }

        // Right: skin, flip, reset
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoundButton(Icons.Rounded.Person, if (ui.skinOn) "Hide the skin" else "Show the skin", active = ui.skinOn, onClick = onToggleSkin)
            RoundButton(Icons.Rounded.FlipCameraAndroid, "Turn the body round", active = false) {
                front = !front
                glView?.showWhole(front)
                onSelect(null)
            }
            RoundButton(Icons.Rounded.Replay, "Show the whole body", active = false) {
                front = true
                glView?.showWhole(true)
                onSelect(null)
            }
        }

        // Bottom: the muscle picked, or what the day trains
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            val selected = ui.selected?.let { id -> catalogue?.group(id) }
            AnimatedVisibility(
                visible = selected != null,
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = slideOutVertically { it / 2 } + fadeOut(),
            ) {
                if (selected != null && catalogue != null) MuscleCard(ui, selected.id, onClose = { onSelect(null) })
            }
            AnimatedVisibility(visible = selected == null && catalogue != null, enter = fadeIn(), exit = fadeOut()) {
                TrainingSummary(ui)
            }
        }
    }

    if (creditsOpen) CreditsDialog(onClose = { creditsOpen = false })
}

/** "Female body · Tuesday" and, if the profile has no gender, what to do about it. */
private fun bodyCaption(ui: BodyUiState): String {
    val who = if (ui.body == BodyKey.Female) "Female body" else "Male body"
    val known = ui.gender.equals("female", ignoreCase = true) || ui.gender.equals("male", ignoreCase = true)
    return if (!known) "$who · set your gender in Profile to match yours" else "$who · ${ui.day}"
}

// ---- The 3D body ----

@Composable
private fun BodyGl(ui: BodyUiState, onPicked: (String?) -> Unit, onView: (BodyGlView?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val catalogue = ui.catalogue ?: return
    val model = ui.model ?: return
    val picked by rememberUpdatedState(onPicked)
    val view = remember(catalogue) {
        BodyGlView(
            context,
            catalogue,
            object : BodyGlView.Listener {
                override fun onPicked(group: String?) = picked(group)
                override fun onReady() = Unit
                override fun onFailure(message: String) = Unit
            },
        )
    }
    DisposableEffect(view) {
        onView(view)
        onDispose { onView(null) }
    }
    // The graphics rest when the screen is not in front.
    DisposableEffect(lifecycle, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
        onDispose { lifecycle.removeObserver(observer); view.onPause() }
    }
    LaunchedEffect(view, model, ui.body) { view.setModel(model, ui.body) }
    // A deep muscle lies under the surface ones, so picking one fades them.
    val deep = ui.selected?.let { catalogue.group(it)?.layer == MuscleLayer.Deep } == true
    LaunchedEffect(view, ui.selected, ui.trained, ui.skinOn, deep) { view.setScene(DrawState(ui.selected, deep, ui.trained), ui.skinOn) }
    LaunchedEffect(view, ui.selected, model) { view.focus(ui.selected) }
    AndroidView(
        factory = { view },
        modifier = modifier.fillMaxSize().semantics { contentDescription = "3D model of your body. Drag to turn it, pinch to zoom, tap a muscle." },
    )
}

// ---- Controls ----

@Composable
private fun DayChips(selected: String, today: String, onSelect: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(BodyViewModel.Week) { day ->
            val isSelected = day == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) AppColors.Accent else AppColors.Surface.copy(alpha = 0.85f))
                    .then(if (!isSelected && day == today) Modifier.border(BorderStroke(1.dp, AppColors.Accent), RoundedCornerShape(50)) else Modifier)
                    .clickable { onSelect(day) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.take(3),
                    style = AppType.BodyStrong,
                    color = if (isSelected) Color.Black else AppColors.TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (active) AppColors.AccentSoft else AppColors.Surface.copy(alpha = 0.85f))
            .border(BorderStroke(1.dp, if (active) AppColors.Accent else AppColors.Divider), CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) AppColors.Accent else AppColors.TextPrimary, modifier = Modifier.size(22.dp))
    }
}

// ---- What the day trains ----

@Composable
private fun TrainingSummary(ui: BodyUiState) {
    AppCard {
        val main = ui.mainLabels()
        val helper = ui.helperLabels()
        Text(
            if (ui.day == ui.today) "TRAINING TODAY" else "TRAINING ${ui.day.uppercase()}",
            style = AppType.Overline,
            color = AppColors.Accent,
        )
        Spacer(Modifier.height(6.dp))
        when {
            ui.dayExercises.isEmpty() -> Text("Nothing is planned for ${ui.day}. Tap any muscle to read about it.", style = AppType.Body, color = AppColors.TextSecondary)
            ui.trained.isEmpty -> Text(
                "${ui.dayExercises.size} exercise${if (ui.dayExercises.size == 1) "" else "s"} planned, but their muscles are not known yet. Tap a muscle to read about it.",
                style = AppType.Body,
                color = AppColors.TextSecondary,
            )
            else -> {
                if (main.isNotEmpty()) Text(main.joinToString(" · "), style = AppType.Headline, color = AppColors.TextPrimary)
                if (helper.isNotEmpty()) {
                    Text("Also working: ${helper.take(5).joinToString(", ")}${if (helper.size > 5) " +${helper.size - 5}" else ""}", style = AppType.Caption, color = AppColors.TextSecondary)
                }
                Spacer(Modifier.height(4.dp))
                Text("They glow on your body. Tap a muscle for your exercises for it.", style = AppType.Caption, color = AppColors.TextSecondary)
            }
        }
    }
}

// ---- One muscle ----

@Composable
private fun MuscleCard(ui: BodyUiState, id: String, onClose: () -> Unit) {
    val catalogue = ui.catalogue ?: return
    val group = catalogue.group(id) ?: return
    val note = ui.notes[id]
    val role = ui.trained.roleOf(id)
    val uses = remember(id, ui.dayExercises, ui.library) { TrainedMusclesFinder.usesOf(id, ui.dayExercises, ui.library, catalogue) }
    val usedNames = uses.map { TrainedMusclesFinder.normalise(it.exercise.name) }.toSet()
    val more = remember(id, ui.library, usedNames) {
        ui.library.filter { id in it.primaryMuscles && TrainedMusclesFinder.normalise(it.name) !in usedNames }.take(4).map { it.name }
    }
    AppCard(modifier = Modifier.heightIn(max = 330.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(group.label, style = AppType.Title, color = AppColors.TextPrimary)
                    note?.subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, style = AppType.Caption, color = AppColors.TextSecondary) }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(
                    text = when (role) {
                        MuscleRole.Main -> "Main muscle · ${ui.day}"
                        MuscleRole.Helper -> "Helping · ${ui.day}"
                        null -> if (ui.trained.isEmpty) group.libraryGroup else "Not worked ${ui.day}"
                    },
                    highlighted = role != null,
                )
                if (group.layer == MuscleLayer.Deep) Badge(text = "Deep muscle", highlighted = false)
            }
            Spacer(Modifier.height(10.dp))
            Text(note?.about?.takeIf { it.isNotBlank() } ?: group.about, style = AppType.Body, color = AppColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
            Text("YOUR EXERCISES · ${ui.day.uppercase()}", style = AppType.Overline, color = AppColors.Accent)
            Spacer(Modifier.height(4.dp))
            if (uses.isEmpty()) {
                Text("Nothing in ${ui.day}'s plan works this muscle.", style = AppType.Caption, color = AppColors.TextSecondary)
            } else {
                for (use in uses) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(use.exercise.name, style = AppType.BodyStrong, color = AppColors.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${describeSets(use.exercise)}${if (use.role == MuscleRole.Helper) " · helps" else ""}",
                            style = AppType.Caption,
                            color = AppColors.TextSecondary,
                        )
                    }
                }
            }
            if (more.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("MORE FOR ${group.label.uppercase()}", style = AppType.Overline, color = AppColors.Accent)
                Spacer(Modifier.height(4.dp))
                Text(more.joinToString(", "), style = AppType.Caption, color = AppColors.TextSecondary)
            }
        }
    }
}

/** "3 × 10 · 40 kg", or "3 × 10" for bodyweight. */
private fun describeSets(exercise: Exercise): String {
    val weight = exercise.weight.toDoubleOrNull() ?: 0.0
    val kg = if (weight > 0) " · ${if (weight % 1.0 == 0.0) weight.toInt() else weight} kg" else ""
    return "${exercise.sets} × ${exercise.reps}$kg"
}

@Composable
private fun Badge(text: String, highlighted: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (highlighted) AppColors.AccentSoft else AppColors.Field)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = AppType.Caption, color = if (highlighted) AppColors.Accent else AppColors.TextSecondary, maxLines = 1)
    }
}

// ---- Credits ----

@Composable
private fun CreditsDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val text = remember {
        runCatching { context.assets.open("ATTRIBUTION.md").bufferedReader().readText() }.getOrDefault("").replace("**", "").replace("`", "")
            .ifBlank { "The 3D body models are adapted from BodyParts3D, Z-Anatomy (CC BY-SA 4.0) and the Human Reference Atlas (CC BY 4.0). Educational use only." }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        containerColor = AppColors.Surface,
        title = { Text("3D model credits", style = AppType.Headline, color = AppColors.TextPrimary) },
        text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) { Text(text, style = AppType.Caption, color = AppColors.TextSecondary) } },
        confirmButton = { TextButton(onClick = onClose) { Text("Close", color = AppColors.Accent) } },
    )
}
