package com.example.strivo.ui

import android.app.Application
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.strivo.data.model.Plan
import com.example.strivo.ui.screens.AuthScreen
import com.example.strivo.ui.screens.DayPlansScreen
import com.example.strivo.ui.screens.ExerciseDetailsScreen
import com.example.strivo.ui.screens.HistoryDetailsScreen
import com.example.strivo.ui.screens.HistoryScreen
import com.example.strivo.ui.screens.HomeScreen
import com.example.strivo.ui.screens.OnboardingScreen
import com.example.strivo.ui.screens.PlanDetailsScreen
import com.example.strivo.ui.screens.ProfileScreen
import com.example.strivo.ui.screens.SaveExerciseScreen
import com.example.strivo.ui.screens.AnalyticsScreen
import com.example.strivo.ui.screens.FoodScreen
import com.example.strivo.ui.screens.PickPlanScreen
import com.example.strivo.ui.screens.SavePlanScreen
import com.example.strivo.ui.screens.WorkoutScreen
import com.example.strivo.ui.theme.AppColors
import com.example.strivo.viewmodel.AuthViewModel
import com.example.strivo.viewmodel.AnalyticsViewModel
import com.example.strivo.viewmodel.FoodViewModel
import com.example.strivo.viewmodel.ExerciseViewModel
import com.example.strivo.viewmodel.PlanViewModel
import com.example.strivo.viewmodel.ProfileViewModel
import com.example.strivo.viewmodel.WorkoutViewModel

private object Routes {
    const val HOME = "home"
    const val PROFILE = "profile"
    const val HISTORY = "history"
    const val ANALYTICS = "analytics"
    const val FOOD = "food"
    const val HISTORY_DETAILS = "historyDetails/{sessionId}"
    const val SAVE_PLAN = "savePlan"
    const val PICK_PLAN = "pickPlan/{day}"
    const val DAY_PLANS = "dayPlans/{day}"
    const val PLAN_DETAILS = "planDetails/{planId}"
    const val SAVE_EXERCISE = "saveExercise/{planId}?exerciseId={exerciseId}&isExtra={isExtra}"
    const val EXERCISE_DETAILS = "exerciseDetails/{planId}/{exerciseId}"
    const val WORKOUT = "workout/{planId}"

    fun historyDetails(sessionId: Long) = "historyDetails/$sessionId"
    fun pickPlan(day: String) = "pickPlan/$day"
    fun dayPlans(day: String) = "dayPlans/$day"
    fun planDetails(planId: Long) = "planDetails/$planId"
    fun saveExercise(planId: Long, exerciseId: Long? = null, isExtra: Boolean = false) =
        "saveExercise/$planId?exerciseId=${exerciseId ?: -1L}&isExtra=$isExtra"

    fun exerciseDetails(planId: Long, exerciseId: Long) = "exerciseDetails/$planId/$exerciseId"
    fun workout(planId: Long) = "workout/$planId"
}

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

private const val NavAnimationMillis = 300

/** The five top-level screens, shown as tabs in the bottom bar. Every other screen is opened on top of them. */
private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Rounded.Home),
    Tab(Routes.FOOD, "Food", Icons.Rounded.Restaurant),
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
                NavigationBar(containerColor = AppColors.Surface, tonalElevation = 0.dp) {
                    Tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = tab.route == currentRoute,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 11.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = AppColors.Accent,
                                indicatorColor = AppColors.Accent,
                                unselectedIconColor = AppColors.TextSecondary,
                                unselectedTextColor = AppColors.TextSecondary,
                            ),
                        )
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

@Composable
private fun StrivoNavGraph(
    navController: NavHostController,
    modifier: Modifier,
    authViewModel: AuthViewModel,
    planViewModel: PlanViewModel,
    exerciseViewModel: ExerciseViewModel,
    profileViewModel: ProfileViewModel,
) {
    val application = LocalContext.current.applicationContext as Application
    val plans by planViewModel.plans.collectAsStateWithLifecycle()
    val exercises by exerciseViewModel.exercises.collectAsStateWithLifecycle()
    val history by exerciseViewModel.history.collectAsStateWithLifecycle()

    fun planById(id: Long?): Plan? = plans.firstOrNull { it.planId == id }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier.background(AppColors.Background),
        enterTransition = {
            slideInHorizontally(tween(NavAnimationMillis)) { it / 3 } + fadeIn(tween(NavAnimationMillis))
        },
        exitTransition = {
            slideOutHorizontally(tween(NavAnimationMillis)) { -it / 6 } + fadeOut(tween(NavAnimationMillis))
        },
        popEnterTransition = {
            slideInHorizontally(tween(NavAnimationMillis)) { -it / 6 } + fadeIn(tween(NavAnimationMillis))
        },
        popExitTransition = {
            slideOutHorizontally(tween(NavAnimationMillis)) { it / 3 } + fadeOut(tween(NavAnimationMillis))
        },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                authViewModel = authViewModel,
                planViewModel = planViewModel,
                exerciseViewModel = exerciseViewModel,
                profileViewModel = profileViewModel,
                onAddPlan = { day -> navController.navigate(Routes.pickPlan(day)) },
                onOpenPlan = { plan -> plan.planId?.let { navController.navigate(Routes.planDetails(it)) } },
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                authViewModel = authViewModel,
                profileViewModel = profileViewModel,
            )
        }

        composable(Routes.ANALYTICS) {
            val analyticsViewModel: AnalyticsViewModel = viewModel()
            AnalyticsScreen(
                viewModel = analyticsViewModel,
                onOpenSession = { session ->
                    exerciseViewModel.fetchHistory() // the details screen reads the shared history list
                    session.id?.let { navController.navigate(Routes.historyDetails(it)) }
                },
                onOpenFood = {
                    navController.navigate(Routes.FOOD) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }

        composable(Routes.FOOD) {
            val foodViewModel: FoodViewModel = viewModel()
            FoodScreen(viewModel = foodViewModel)
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                exerciseViewModel = exerciseViewModel,
                onOpenSession = { session -> session.id?.let { navController.navigate(Routes.historyDetails(it)) } },
            )
        }

        composable(
            Routes.HISTORY_DETAILS,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) { entry ->
            val session = history.firstOrNull { it.id == entry.arguments?.getLong("sessionId") }
            if (session != null) {
                HistoryDetailsScreen(session = session, onBack = { navController.popBackStack() })
            } else {
                BlankScreen()
            }
        }

        composable(
            Routes.PICK_PLAN,
            arguments = listOf(navArgument("day") { type = NavType.StringType }),
        ) { entry ->
            PickPlanScreen(
                day = entry.arguments?.getString("day").orEmpty(),
                planViewModel = planViewModel,
                exerciseViewModel = exerciseViewModel,
                onBack = { navController.popBackStack() },
                onCreatePlan = { navController.navigate(Routes.SAVE_PLAN) },
                onEditPlan = { plan -> plan.planId?.let { navController.navigate(Routes.planDetails(it)) } },
            )
        }

        composable(Routes.SAVE_PLAN) {
            SavePlanScreen(
                planViewModel = planViewModel,
                onBack = { navController.popBackStack() },
                onSaved = { plan ->
                    navController.navigate(Routes.planDetails(plan.planId ?: -1L)) {
                        popUpTo(Routes.SAVE_PLAN) { inclusive = true }
                    }
                },
            )
        }

        composable(
            Routes.DAY_PLANS,
            arguments = listOf(navArgument("day") { type = NavType.StringType }),
        ) { entry ->
            DayPlansScreen(
                day = entry.arguments?.getString("day").orEmpty(),
                planViewModel = planViewModel,
                exerciseViewModel = exerciseViewModel,
                onBack = { navController.popBackStack() },
                onAddPlan = { day -> navController.navigate(Routes.pickPlan(day)) },
                onOpenPlan = { plan -> plan.planId?.let { navController.navigate(Routes.planDetails(it)) } },
            )
        }

        composable(
            Routes.PLAN_DETAILS,
            arguments = listOf(navArgument("planId") { type = NavType.LongType }),
        ) { entry ->
            val plan = planById(entry.arguments?.getLong("planId"))
            if (plan?.planId != null) {
                PlanDetailsScreen(
                    plan = plan,
                    otherPlans = plans.filter { it.planId != plan.planId },
                    exerciseViewModel = exerciseViewModel,
                    onBack = { navController.popBackStack() },
                    onAddExercise = { navController.navigate(Routes.saveExercise(plan.planId)) },
                    onEditExercise = { exercise ->
                        navController.navigate(Routes.saveExercise(plan.planId, exercise.id))
                    },
                    onOpenExercise = { exercise ->
                        exercise.id?.let { navController.navigate(Routes.exerciseDetails(plan.planId, it)) }
                    },
                    onStartWorkout = { navController.navigate(Routes.workout(plan.planId)) },
                )
            } else {
                BlankScreen()
            }
        }

        composable(
            Routes.SAVE_EXERCISE,
            arguments = listOf(
                navArgument("planId") { type = NavType.LongType },
                navArgument("exerciseId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("isExtra") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val args = entry.arguments
            val exerciseId = args?.getLong("exerciseId") ?: -1L
            val exercise = if (exerciseId >= 0) exercises.firstOrNull { it.id == exerciseId } else null
            SaveExerciseScreen(
                plan = planById(args?.getLong("planId")),
                exercise = exercise,
                isExtra = args?.getBoolean("isExtra") ?: false,
                exerciseViewModel = exerciseViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Routes.EXERCISE_DETAILS,
            arguments = listOf(
                navArgument("planId") { type = NavType.LongType },
                navArgument("exerciseId") { type = NavType.LongType },
            ),
        ) { entry ->
            val plan = planById(entry.arguments?.getLong("planId"))
            val exercise = exercises.firstOrNull { it.id == entry.arguments?.getLong("exerciseId") }
            if (plan?.planId != null && exercise != null) {
                ExerciseDetailsScreen(
                    exercise = exercise,
                    plan = plan,
                    onBack = { navController.popBackStack() },
                    onStartWorkout = { navController.navigate(Routes.workout(plan.planId)) },
                )
            } else {
                BlankScreen()
            }
        }

        composable(
            Routes.WORKOUT,
            arguments = listOf(navArgument("planId") { type = NavType.LongType }),
        ) { entry ->
            val plan = planById(entry.arguments?.getLong("planId"))
            if (plan != null) {
                val workoutViewModel: WorkoutViewModel = viewModel(
                    key = "workout_${plan.planId}",
                    factory = viewModelFactory { initializer { WorkoutViewModel(application, plan) } },
                )
                WorkoutScreen(
                    plan = plan,
                    viewModel = workoutViewModel,
                    onBack = { navController.popBackStack() },
                    onFinishWorkout = {
                        // Back to home, then show the history list on top of it.
                        navController.navigate(Routes.HISTORY) {
                            popUpTo(Routes.HOME) { inclusive = false }
                        }
                    },
                )
            } else {
                BlankScreen()
            }
        }
    }
}

@Composable
private fun BlankScreen() {
    Box(Modifier.fillMaxSize().background(AppColors.Background))
}
