package com.example.strivo.ui.screens

import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.data.prefs.UserProfile
import com.example.strivo.ui.components.AccentButton
import com.example.strivo.ui.components.AccentTitle
import com.example.strivo.ui.components.CircleBackButton
import com.example.strivo.ui.components.DecimalWheelPicker
import com.example.strivo.data.sync.SyncStatus
import com.example.strivo.ui.components.StrivoAlertDialog
import com.example.strivo.ui.components.WheelPicker
import com.example.strivo.ui.theme.AppColors
import com.example.strivo.util.fixed1
import com.example.strivo.viewmodel.AuthViewModel
import com.example.strivo.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

private enum class ProfileEdit { Gender, Age, Height, Weight }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    profileViewModel: ProfileViewModel,
) {
    val auth by authViewModel.state.collectAsStateWithLifecycle()
    val state by profileViewModel.state.collectAsStateWithLifecycle()
    val syncStatus by profileViewModel.syncStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<ProfileEdit?>(null) }
    var showLogout by remember { mutableStateOf(false) }
    var showClearHistory by remember { mutableStateOf(false) }
    var showRemoveDemo by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // The demo-data rows exist only in debug builds.
    val isDebugBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    LaunchedEffect(Unit) { profileViewModel.load() }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize().background(AppColors.Background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppColors.Accent)
        }
        return
    }

    val profile = state.profile
    val records = state.records

    fun save(block: suspend () -> Unit) {
        scope.launch {
            block()
            profileViewModel.load()
        }
    }

    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            ProfileHeader(name = auth.userName ?: "User", email = auth.userEmail ?: "")
            Column(Modifier.padding(24.dp).navigationBarsPadding()) {
                ProfileSection("Weight Journey")
                Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    WeightStatCard("HIGHEST", "${profile.highWeight?.fixed1() ?: "--"} kg", AppColors.Danger)
                    WeightStatCard("LOWEST", "${profile.lowWeight?.fixed1() ?: "--"} kg", AppColors.Success)
                }
                Spacer(Modifier.height(40.dp))

                ProfileSection("Peak Performance")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(25.dp))
                        .background(AppColors.Surface)
                        .padding(20.dp),
                ) {
                    RecordRow("Highest Lift", "${records.maxWeight.fixed1()} kg", Icons.Rounded.FitnessCenter)
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = AppColors.Field)
                    RecordRow("Most Reps", "${records.maxReps.toInt()} reps", Icons.Rounded.Repeat)
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = AppColors.Field)
                    RecordRow("Best Volume", "${records.maxVolume.fixed1()} kg", Icons.Rounded.Equalizer)
                }
                Spacer(Modifier.height(40.dp))

                ProfileSection("Personal Details")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(25.dp))
                        .background(AppColors.Surface),
                ) {
                    DetailRow("Gender", profile.gender ?: "--", Icons.Rounded.Wc) { editing = ProfileEdit.Gender }
                    HorizontalDivider(Modifier.padding(start = 60.dp), color = AppColors.Field)
                    DetailRow("Age", "${profile.age ?: "--"} yrs", Icons.Rounded.CalendarToday) { editing = ProfileEdit.Age }
                    HorizontalDivider(Modifier.padding(start = 60.dp), color = AppColors.Field)
                    DetailRow("Height", "${profile.height?.fixed1() ?: "--"} cm", Icons.Rounded.Height) { editing = ProfileEdit.Height }
                    HorizontalDivider(Modifier.padding(start = 60.dp), color = AppColors.Field)
                    DetailRow("Weight", "${profile.weight?.fixed1() ?: "--"} kg", Icons.Rounded.MonitorWeight) { editing = ProfileEdit.Weight }
                }
                Spacer(Modifier.height(40.dp))

                ProfileSection("More")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(30.dp))
                        .background(AppColors.Surface),
                ) {
                    SyncRow(syncStatus, onClick = profileViewModel::syncNow)
                    HorizontalDivider(Modifier.padding(start = 60.dp), color = AppColors.Field)
                    if (isDebugBuild) {
                        MoreRow(Icons.Rounded.Science, "Load demo data (7 days)", AppColors.TextPrimary, AppColors.Accent) {
                            scope.launch {
                                val added = profileViewModel.loadDemoData()
                                Toast.makeText(context, "Added $added demo workouts", Toast.LENGTH_SHORT).show()
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 60.dp), color = AppColors.Field)
                        MoreRow(Icons.Rounded.DeleteSweep, "Remove demo data", AppColors.Danger, AppColors.Danger) {
                            showRemoveDemo = true
                        }
                        HorizontalDivider(Modifier.padding(start = 60.dp), color = AppColors.Field)
                        MoreRow(Icons.Rounded.DeleteSweep, "Clear history (keep demo)", AppColors.Danger, AppColors.Danger) {
                            showClearHistory = true
                        }
                        HorizontalDivider(Modifier.padding(start = 60.dp), color = AppColors.Field)
                    }
                    MoreRow(Icons.AutoMirrored.Rounded.Logout, "Logout Account", AppColors.Danger, AppColors.Danger) { showLogout = true }
                }
                Spacer(Modifier.height(60.dp))
            }
        }

        // Transparent app bar floating over the header
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Profile", fontWeight = FontWeight.Black, color = AppColors.TextPrimary, letterSpacing = 1.sp, fontSize = 20.sp)
        }
    }

    // --- Edit dialogs ---

    when (editing) {
        ProfileEdit.Gender -> GenderSheet(
            initial = profile.gender ?: "Male",
            onDismiss = { editing = null },
            onSave = { gender ->
                editing = null
                save { profileViewModel.saveProfile(gender, profile.age ?: 25, profile.height ?: 170.0, profile.weight ?: 70.0) }
            },
        )

        ProfileEdit.Age -> {
            var age by remember { mutableStateOf(profile.age ?: 25) }
            WheelDialog(
                title = "Edit Age",
                onDismiss = { editing = null },
                onSave = {
                    editing = null
                    save { profileViewModel.saveProfile(profile.gender ?: "Male", age, profile.height ?: 170.0, profile.weight ?: 70.0) }
                },
            ) {
                WheelPicker(label = "YEARS", minValue = 10, maxValue = 100, initialValue = age, onChanged = { age = it })
            }
        }

        ProfileEdit.Height -> {
            var height by remember { mutableStateOf(profile.height ?: 170.0) }
            WheelDialog(
                title = "Edit Height",
                onDismiss = { editing = null },
                onSave = {
                    editing = null
                    save { profileViewModel.saveProfile(profile.gender ?: "Male", profile.age ?: 25, height, profile.weight ?: 70.0) }
                },
            ) {
                DecimalWheelPicker(label = "CM", minValue = 100, maxValue = 250, value = height, onValueChange = { height = it })
            }
        }

        ProfileEdit.Weight -> {
            var weight by remember { mutableStateOf(profile.weight ?: 70.0) }
            WheelDialog(
                title = "Edit Weight",
                onDismiss = { editing = null },
                onSave = {
                    editing = null
                    save { profileViewModel.updateWeightOnly(weight) }
                },
            ) {
                DecimalWheelPicker(label = "KG", minValue = 30, maxValue = 250, value = weight, onValueChange = { weight = it })
            }
        }

        null -> Unit
    }

    if (showRemoveDemo) {
        StrivoAlertDialog(
            title = "Remove demo data?",
            onDismiss = { showRemoveDemo = false },
            confirmText = "Remove",
            confirmColor = AppColors.Danger,
            onConfirm = {
                showRemoveDemo = false
                scope.launch {
                    val removed = profileViewModel.removeDemoData()
                    Toast.makeText(context, "Removed $removed demo workouts", Toast.LENGTH_SHORT).show()
                }
            },
        ) {
            Text(
                "This deletes only the demo workouts. Your own workouts, plans, exercises and account are kept.",
                color = AppColors.TextSecondary,
            )
        }
    }

    if (showClearHistory) {
        StrivoAlertDialog(
            title = "Clear history?",
            onDismiss = { showClearHistory = false },
            confirmText = "Clear",
            confirmColor = AppColors.Danger,
            onConfirm = {
                showClearHistory = false
                scope.launch {
                    val removed = profileViewModel.clearHistoryKeepingDemo()
                    Toast.makeText(context, "Cleared $removed workouts", Toast.LENGTH_SHORT).show()
                }
            },
        ) {
            Text(
                "This permanently deletes your saved workouts and the exercise log behind your records. " +
                    "The demo workouts, your plans, exercises and account are kept.",
                color = AppColors.TextSecondary,
            )
        }
    }

    if (showLogout) {
        StrivoAlertDialog(
            title = "Logout",
            onDismiss = { showLogout = false },
            confirmText = "Logout",
            confirmColor = AppColors.Danger,
            onConfirm = {
                showLogout = false
                authViewModel.logout()
            },
        ) {
            Text("Are you sure you want to logout?", color = AppColors.TextSecondary)
        }
    }
}

@Composable
private fun ProfileHeader(name: String, email: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 50.dp, bottomEnd = 50.dp))
            .background(AppColors.Surface)
            .padding(top = 140.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppColors.Accent)
                    .padding(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(AppColors.Field),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = AppColors.TextPrimary, modifier = Modifier.size(70.dp))
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(AppColors.Accent)
                    .padding(6.dp),
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(name, color = AppColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(email, color = AppColors.TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ProfileSection(title: String) {
    AccentTitle(text = title.uppercase(), fontSize = 13.sp)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun RowScope.WeightStatCard(label: String, value: String, tint: Color) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(tint.copy(alpha = 0.1f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tint, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = tint)
    }
}

@Composable
private fun RecordRow(label: String, value: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.Field)
                .padding(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(15.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.Accent)
    }
}

@Composable
private fun DetailRow(label: String, value: String, icon: ImageVector, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 14.sp, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun MoreRow(icon: ImageVector, title: String, textColor: Color, iconColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconColor)
        Spacer(Modifier.width(16.dp))
        Text(title, color = textColor, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun WheelDialog(title: String, onDismiss: () -> Unit, onSave: () -> Unit, content: @Composable () -> Unit) {
    StrivoAlertDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = "Save",
        onConfirm = onSave,
        dismissColor = AppColors.Danger,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderSheet(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Edit Gender", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                listOf("Male", "Female").forEach { value ->
                    val isSelected = value == selected
                    Text(
                        text = value,
                        color = if (isSelected) Color.Black else AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (isSelected) AppColors.Accent else AppColors.Field)
                            .clickable { selected = value }
                            .padding(horizontal = 30.dp, vertical = 15.dp),
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
            AccentButton(
                text = "SAVE CHANGES",
                onClick = { onSave(selected) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The cloud backup state: what is waiting to upload, whether the phone is offline, or what went wrong. */
@Composable
private fun SyncRow(status: SyncStatus, onClick: () -> Unit) {
    val (icon, title, detail) = when {
        status.isSyncing -> Triple(Icons.Rounded.CloudSync, "Syncing…", "Backing up to the cloud")
        status.error != null -> Triple(Icons.Rounded.CloudOff, "Cloud sync problem", status.error)
        status.pendingChanges > 0 && !status.isOnline -> Triple(
            Icons.Rounded.CloudOff,
            "Saved on this phone",
            "${status.pendingChanges} changes will upload when you are online",
        )
        status.pendingChanges > 0 -> Triple(Icons.Rounded.CloudSync, "${status.pendingChanges} changes waiting", "Tap to sync now")
        else -> Triple(
            Icons.Rounded.CloudDone,
            "Backed up to the cloud",
            status.lastSyncMillis?.let { "Last synced " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(it) } ?: "Tap to sync now",
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (status.error != null) AppColors.Danger else AppColors.Accent)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AppColors.TextPrimary, fontWeight = FontWeight.Medium)
            Text(detail, color = AppColors.TextSecondary, fontSize = 12.sp)
        }
    }
}
