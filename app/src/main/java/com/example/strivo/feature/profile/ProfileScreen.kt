package com.example.strivo.feature.profile

import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.core.ui.components.AppCard
import com.example.strivo.core.ui.components.ChevronBadge
import com.example.strivo.core.ui.components.DecimalWheelPicker
import com.example.strivo.core.ui.components.ScreenTopBar
import com.example.strivo.core.ui.components.StrivoAlertDialog
import com.example.strivo.core.ui.components.WheelDialog
import com.example.strivo.core.ui.components.WheelPicker
import com.example.strivo.core.ui.components.restDaysSummary
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import com.example.strivo.core.util.fixed1
import com.example.strivo.data.demo.DemoWeightTrend
import com.example.strivo.feature.auth.AuthViewModel
import kotlinx.coroutines.launch

private enum class ProfileEdit { Gender, Age, Height, Weight, RestDays }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTopBar(title = "Profile", onBack = null)
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 32.dp)
                .navigationBarsPadding(),
        ) {
            ProfileHeader(name = auth.userName ?: "User", email = auth.userEmail ?: "")

            SectionHeader("Weight")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.Card)
                    .background(AppColors.Surface)
                    .padding(vertical = 16.dp),
            ) {
                WeightStat("Current", profile.weight, Modifier.weight(1f))
                StatSeparator()
                WeightStat("Highest", profile.highWeight, Modifier.weight(1f))
                StatSeparator()
                WeightStat("Lowest", profile.lowWeight, Modifier.weight(1f))
            }

            SectionHeader("Personal records")
            AppCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                SettingsRow(Icons.Rounded.FitnessCenter, "Heaviest lift", value = "${records.maxWeight.fixed1()} kg")
                RowDivider()
                SettingsRow(Icons.Rounded.Repeat, "Most reps in a set", value = "${records.maxReps.toInt()} reps")
                RowDivider()
                SettingsRow(Icons.Rounded.Equalizer, "Best set volume", value = "${records.maxVolume.fixed1()} kg")
            }

            SectionHeader("Details")
            AppCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                SettingsRow(Icons.Rounded.Wc, "Gender", value = profile.gender ?: "Not set") { editing = ProfileEdit.Gender }
                RowDivider()
                SettingsRow(Icons.Rounded.CalendarToday, "Age", value = profile.age?.let { "$it yrs" } ?: "Not set") { editing = ProfileEdit.Age }
                RowDivider()
                SettingsRow(Icons.Rounded.Height, "Height", value = profile.height?.let { "${it.fixed1()} cm" } ?: "Not set") { editing = ProfileEdit.Height }
                RowDivider()
                SettingsRow(Icons.Rounded.MonitorWeight, "Weight", value = profile.weight?.let { "${it.fixed1()} kg" } ?: "Not set") { editing = ProfileEdit.Weight }
                RowDivider()
                SettingsRow(Icons.Rounded.EventBusy, "Rest days", value = restDaysSummary(profile.restDays)) { editing = ProfileEdit.RestDays }
            }

            SectionHeader("Data")
            AppCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                SyncRow(syncStatus, onClick = profileViewModel::syncNow)
                if (isDebugBuild) {
                    RowDivider()
                    SettingsRow(Icons.Rounded.Science, "Load demo data (4 weeks)") {
                        scope.launch {
                            val added = profileViewModel.loadDemoData()
                            Toast.makeText(context, "Added $added demo workouts", Toast.LENGTH_SHORT).show()
                        }
                    }
                    // The same demo with weigh-ins that move fast, so the coach's weight advice can be seen.
                    listOf(DemoWeightTrend.FastLoss, DemoWeightTrend.FastGain).forEach { trend ->
                        RowDivider()
                        SettingsRow(Icons.Rounded.Science, "Load demo: ${trend.label}") {
                            scope.launch {
                                val added = profileViewModel.loadDemoData(trend)
                                Toast.makeText(context, "Added $added demo workouts, ${trend.label}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    RowDivider()
                    SettingsRow(Icons.Rounded.DeleteSweep, "Remove demo data", tint = AppColors.Danger) { showRemoveDemo = true }
                    RowDivider()
                    SettingsRow(Icons.Rounded.DeleteSweep, "Clear history (keep demo)", tint = AppColors.Danger) { showClearHistory = true }
                }
            }

            // Said plainly, and precisely: the trainer sees training and body progress and daily calories and protein, but
            // never what was eaten (the food log is the user's own backup; the trainer's dashboard cannot read it).
            Text(
                "Your trainer can see your plans, workouts, body weight and your daily calories and protein, to coach you. They cannot see what you eat. Your data is backed up to your account.",
                style = AppType.Caption,
                color = AppColors.TextTertiary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            )

            Spacer(Modifier.height(20.dp))
            AppCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                SettingsRow(Icons.AutoMirrored.Rounded.Logout, "Log out", tint = AppColors.Danger, showChevron = false) { showLogout = true }
            }
        }
    }

    // --- Edit dialogs ---

    when (editing) {
        ProfileEdit.RestDays -> RestDaysSheet(
            initial = profile.restDays,
            onDismiss = { editing = null },
            onSave = { days ->
                editing = null
                save { profileViewModel.saveRestDays(days) }
            },
        )

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
                title = "Age",
                onDismiss = { editing = null },
                onSave = {
                    editing = null
                    save { profileViewModel.saveProfile(profile.gender ?: "Male", age, profile.height ?: 170.0, profile.weight ?: 70.0) }
                },
            ) {
                WheelPicker(label = "years", minValue = 10, maxValue = 100, initialValue = age, onChanged = { age = it })
            }
        }

        ProfileEdit.Height -> {
            var height by remember { mutableStateOf(profile.height ?: 170.0) }
            WheelDialog(
                title = "Height",
                onDismiss = { editing = null },
                onSave = {
                    editing = null
                    save { profileViewModel.saveProfile(profile.gender ?: "Male", profile.age ?: 25, height, profile.weight ?: 70.0) }
                },
            ) {
                DecimalWheelPicker(label = "cm", minValue = 100, maxValue = 250, value = height, onValueChange = { height = it })
            }
        }

        ProfileEdit.Weight -> {
            var weight by remember { mutableStateOf(profile.weight ?: 70.0) }
            WheelDialog(
                title = "Weight",
                onDismiss = { editing = null },
                onSave = {
                    editing = null
                    save { profileViewModel.updateWeightOnly(weight) }
                },
            ) {
                DecimalWheelPicker(label = "kg", minValue = 30, maxValue = 250, value = weight, onValueChange = { weight = it })
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
            title = "Log out?",
            onDismiss = { showLogout = false },
            confirmText = "Log out",
            confirmColor = AppColors.Danger,
            onConfirm = {
                showLogout = false
                authViewModel.logout()
            },
        ) {
            Text("You can log back in any time. Your data stays backed up.", color = AppColors.TextSecondary)
        }
    }
}

/** The account: initial in a tinted circle, name and email. */
@Composable
private fun ProfileHeader(name: String, email: String) {
    AppCard(padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AppColors.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(name.trim().take(1).uppercase().ifEmpty { "?" }, style = AppType.Title, color = AppColors.Accent)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = AppType.Headline, color = AppColors.TextPrimary, maxLines = 1)
                if (email.isNotEmpty()) Text(email, style = AppType.Caption, color = AppColors.TextSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = AppType.BodyStrong,
        color = AppColors.TextSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun WeightStat(label: String, kg: Double?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(kg?.fixed1() ?: "–", style = AppType.Title.copy(fontFeatureSettings = "tnum"), color = AppColors.TextPrimary)
            if (kg != null) {
                Spacer(Modifier.width(3.dp))
                Text("kg", style = AppType.Caption, color = AppColors.TextSecondary, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        Text(label, style = AppType.Caption, color = AppColors.TextSecondary)
    }
}

@Composable
private fun StatSeparator() {
    Box(
        Modifier
            .padding(vertical = 6.dp)
            .width(1.dp)
            .height(36.dp)
            .background(AppColors.Divider)
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 62.dp), thickness = 1.dp, color = AppColors.Divider)
}

/**
 * A row of a grouped list: icon tile, label, an optional value on the right, and a chevron when it opens something.
 * Destructive rows pass a red [tint] for both the icon and the label.
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    tint: Color? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background((tint ?: AppColors.Accent).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint ?: AppColors.Accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = AppType.Body, color = tint ?: AppColors.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1)
        if (value != null) {
            Text(value, style = AppType.BodyStrong, color = if (onClick != null) AppColors.TextSecondary else AppColors.TextPrimary, maxLines = 1)
        }
        if (onClick != null && showChevron) {
            Spacer(Modifier.width(2.dp))
            ChevronBadge()
        } else {
            Spacer(Modifier.width(6.dp))
        }
    }
}
