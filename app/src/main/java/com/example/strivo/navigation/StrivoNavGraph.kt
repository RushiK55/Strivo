package com.example.strivo.navigation

import android.app.Application
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.domain.model.Plan
import com.example.strivo.feature.analytics.AnalyticsScreen
import com.example.strivo.feature.analytics.AnalyticsViewModel
import com.example.strivo.feature.auth.AuthViewModel
import com.example.strivo.feature.body.BodyScreen
import com.example.strivo.feature.food.FoodScreen
import com.example.strivo.feature.food.FoodViewModel
import com.example.strivo.feature.history.HistoryDetailsScreen
import com.example.strivo.feature.history.HistoryScreen
import com.example.strivo.feature.home.HomeScreen
import com.example.strivo.feature.library.LibraryScreen
import com.example.strivo.feature.library.LibraryViewModel
import com.example.strivo.feature.plans.DayPlansScreen
import com.example.strivo.feature.plans.ExerciseDetailsScreen
import com.example.strivo.feature.plans.ExerciseViewModel
import com.example.strivo.feature.plans.PickPlanScreen
import com.example.strivo.feature.plans.PlanDetailsScreen
import com.example.strivo.feature.plans.PlanViewModel
import com.example.strivo.feature.plans.SaveExerciseScreen
import com.example.strivo.feature.plans.SavePlanScreen
import com.example.strivo.feature.profile.ProfileScreen
import com.example.strivo.feature.profile.ProfileViewModel
import com.example.strivo.feature.workout.WorkoutScreen
import com.example.strivo.feature.workout.WorkoutViewModel

private const val NavAnimationMillis = 300

@Composable
internal fun StrivoNavGraph(
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
            // The coach reads the same data as Analytics; an instance of its own keeps Home independent of that tab.
            val coachViewModel: AnalyticsViewModel = viewModel(key = "home_coach")
            val coach by coachViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { coachViewModel.load() }
            HomeScreen(
                authViewModel = authViewModel,
                planViewModel = planViewModel,
                exerciseViewModel = exerciseViewModel,
                profileViewModel = profileViewModel,
                onAddPlan = { day -> navController.navigate(Routes.pickPlan(day)) },
                onOpenPlan = { plan -> plan.planId?.let { navController.navigate(Routes.planDetails(it)) } },
                onViewPlan = { plan -> plan.planId?.let { navController.navigate(Routes.planDetails(it, viewOnly = true)) } },
                onOpenSession = { session ->
                    exerciseViewModel.fetchHistory() // the details screen reads the shared history list
                    session.id?.let { navController.navigate(Routes.historyDetails(it)) }
                },
                onStartWorkout = { plan -> plan.planId?.let { navController.navigate(Routes.workout(it)) } },
                onAddExercise = { planId -> navController.navigate(Routes.saveExercise(planId)) },
                onOpenLibrary = { navController.navigate(Routes.library()) },
                onOpenBody = { navController.navigate(Routes.BODY) },
                advice = coach.advice,
                onOpenCoach = { navController.openTab(Routes.ANALYTICS) },
                acceptLabel = coach::acceptLabel,
                onAcceptAdvice = coachViewModel::acceptAdvice,
            )
        }

        composable(Routes.BODY) {
            BodyScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                authViewModel = authViewModel,
                profileViewModel = profileViewModel,
                onOpenBody = { navController.navigate(Routes.BODY) },
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
                    navController.openTab(Routes.FOOD)
                },
            )
        }

        composable(
            Routes.LIBRARY,
            arguments = listOf(navArgument("planId") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val planId = entry.arguments?.getLong("planId") ?: -1L
            val libraryViewModel: LibraryViewModel = viewModel(
                key = "library_$planId",
                factory = viewModelFactory { initializer { LibraryViewModel(application, planId) } },
            )
            LibraryScreen(viewModel = libraryViewModel, onBack = { navController.popBackStack() })
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
            arguments = listOf(
                navArgument("planId") { type = NavType.LongType },
                navArgument("viewOnly") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val plan = planById(entry.arguments?.getLong("planId"))
            if (plan?.planId != null) {
                PlanDetailsScreen(
                    plan = plan,
                    canStart = entry.arguments?.getBoolean("viewOnly") != true,
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
                        // Leave the workout and any plan screens, then open History exactly as its tab does, so Home stays
                        // one tap away.
                        navController.popBackStack(Routes.HOME, inclusive = false)
                        navController.openTab(Routes.HISTORY)
                    },
                )
            } else {
                BlankScreen()
            }
        }
    }
}

/** Switches to a bottom-bar tab: Home stays at the bottom of the stack and each tab keeps its own state. */
internal fun NavController.openTab(route: String) = navigate(route) {
    popUpTo(Routes.HOME) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun BlankScreen() {
    Box(Modifier.fillMaxSize().background(AppColors.Background))
}
