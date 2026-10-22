package com.example.strivo.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.feature.auth.AuthScreen
import com.example.strivo.feature.auth.AuthViewModel
import com.example.strivo.feature.plans.ExerciseViewModel
import com.example.strivo.feature.plans.PlanViewModel
import com.example.strivo.feature.profile.OnboardingScreen
import com.example.strivo.feature.profile.ProfileViewModel

/** Signed out → auth, signed in without a profile → onboarding, otherwise the app. */
@Composable
fun StrivoRoot() {
    val authViewModel: AuthViewModel = viewModel()
    val auth by authViewModel.state.collectAsStateWithLifecycle()

    val uid = auth.uid
    if (uid == null) {
        AuthScreen(authViewModel)
        return
    }

    // Everything below belongs to this one account. The view models hold that account's data in memory, so they
    // are keyed by the account: signing in as someone else gets fresh ones instead of the previous person's.
    key(uid) {
        val planViewModel: PlanViewModel = viewModel(key = "plans-$uid")
        val exerciseViewModel: ExerciseViewModel = viewModel(key = "exercises-$uid")
        val profileViewModel: ProfileViewModel = viewModel(key = "profile-$uid")

        if (!auth.isProfileComplete) {
            OnboardingScreen(authViewModel, profileViewModel)
        } else {
            StrivoNavHost(authViewModel, planViewModel, exerciseViewModel, profileViewModel)
        }
    }
}

/** The five top-level screens, shown as tabs in the bottom bar. Every other screen is opened on top of them. */
private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Rounded.Home),
    Tab(Routes.FOOD, "Calories", Icons.Rounded.LocalFireDepartment),
    Tab(Routes.ANALYTICS, "Analytics", Icons.Rounded.BarChart),
    Tab(Routes.HISTORY, "History", Icons.Rounded.History),
    Tab(Routes.PROFILE, "Profile", Icons.Rounded.Person),
)

@Composable
private fun StrivoNavHost(
    authViewModel: AuthViewModel,
    planViewModel: PlanViewModel,
    exerciseViewModel: ExerciseViewModel,
    profileViewModel: ProfileViewModel,
) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showBar = Tabs.any { it.route == currentRoute }

    Scaffold(
        containerColor = AppColors.Background,
        // Each screen handles the status bar itself; the bar below handles the navigation bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                Column {
                HorizontalDivider(thickness = 1.dp, color = AppColors.Divider)
                NavigationBar(containerColor = AppColors.Background, tonalElevation = 0.dp) {
                    Tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = tab.route == currentRoute,
                            onClick = { navController.openTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AppColors.Accent,
                                selectedTextColor = AppColors.Accent,
                                indicatorColor = AppColors.AccentSoft,
                                unselectedIconColor = AppColors.TextTertiary,
                                unselectedTextColor = AppColors.TextTertiary,
                            ),
                        )
                    }
                }
                }
            }
        },
    ) { inner ->
        StrivoNavGraph(
            navController = navController,
            modifier = Modifier.padding(inner).consumeWindowInsets(inner),
            authViewModel = authViewModel,
            planViewModel = planViewModel,
            exerciseViewModel = exerciseViewModel,
            profileViewModel = profileViewModel,
        )
    }
}
