package com.example.strivo.feature.body

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.domain.body.MuscleCatalogue
import com.example.strivo.domain.body.MuscleGroup
import com.example.strivo.domain.body.MuscleNote
import com.example.strivo.domain.body.MuscleRole
import com.example.strivo.domain.body.TrainedMuscles
import com.example.strivo.domain.model.LibraryMuscleGroups

/**
 * Every muscle of the body as a list, grouped (Chest, Back, Shoulders ...), for picking one without having to hit it on the body: a tap
 * lights it up on the body, flies the camera to it and opens its card with the details and the exercises for it. The muscles the shown
 * day trains are marked, and a search finds a muscle by its name (or the admin's subtitle for it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleListSheet(
    catalogue: MuscleCatalogue,
    body: com.example.strivo.domain.body.BodyKey,
    trained: TrainedMuscles,
    notes: Map<String, MuscleNote>,
    selected: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val sections = remember(catalogue, body, query, notes) {
        val text = query.trim()
        catalogue.groups
            .filter { it.placeOn(body) != null }
            .filter { g -> text.isEmpty() || g.label.contains(text, ignoreCase = true) || notes[g.id]?.subtitle.orEmpty().contains(text, ignoreCase = true) || g.libraryGroup.contains(text, ignoreCase = true) }
            .groupBy { it.libraryGroup }
            .toList()
            .sortedBy { (group, _) -> LibraryMuscleGroups.indexOf(group).let { if (it < 0) LibraryMuscleGroups.size else it } }
            .map { (group, muscles) -> group to muscles.sortedBy { it.label.lowercase() } }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface,
        shape = AppShapes.Sheet,
    ) {
        Column(Modifier.fillMaxHeight(0.92f).navigationBarsPadding()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Muscles", style = AppType.Title, color = AppColors.TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Pick one to light it up on the body and see what it does and which exercises work it." +
                        if (!trained.isEmpty) " A dot marks what this day trains." else "",
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                singleLine = true,
                placeholder = { Text("Search muscles") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = AppColors.Accent) },
                shape = AppShapes.Control,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppColors.TextPrimary,
                    unfocusedTextColor = AppColors.TextPrimary,
                    focusedContainerColor = AppColors.SurfaceHigh,
                    unfocusedContainerColor = AppColors.SurfaceHigh,
                    focusedBorderColor = AppColors.Accent,
                    unfocusedBorderColor = AppColors.SurfaceHigh,
                    focusedPlaceholderColor = AppColors.Placeholder,
                    unfocusedPlaceholderColor = AppColors.Placeholder,
                    cursorColor = AppColors.Accent,
                ),
            )
            if (sections.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Nothing matches.", style = AppType.Body, color = AppColors.TextSecondary)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                    sections.forEach { (group, muscles) ->
                        item(key = "group-$group") {
                            Text(group.uppercase(), style = AppType.Overline, color = AppColors.Accent, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                        }
                        items(muscles, key = { it.id }) { muscle ->
                            MuscleRow(muscle, notes[muscle.id]?.subtitle.orEmpty(), trained.roleOf(muscle.id), muscle.id == selected) { onPick(muscle.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MuscleRow(muscle: MuscleGroup, subtitle: String, role: MuscleRole?, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Control)
            .background(if (isSelected) AppColors.AccentSoft else AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(start = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when (role) {
                        MuscleRole.Main -> AppColors.Accent
                        MuscleRole.Helper -> AppColors.Accent.copy(alpha = 0.45f)
                        null -> androidx.compose.ui.graphics.Color.Transparent
                    },
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(muscle.label, style = AppType.BodyStrong, color = if (isSelected) AppColors.Accent else AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, style = AppType.Caption, color = AppColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = AppColors.TextTertiary)
    }
}
