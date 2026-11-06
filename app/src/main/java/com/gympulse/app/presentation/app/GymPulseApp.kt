package com.gympulse.app.presentation.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.gympulse.app.core.designsystem.components.LocalHapticsEnabled
import com.gympulse.app.core.designsystem.components.LocalSnackbarHostState
import com.gympulse.app.core.designsystem.components.LocalWeightUnit
import com.gympulse.app.core.designsystem.components.rememberTicker
import com.gympulse.app.core.designsystem.theme.TabularNumbers
import com.gympulse.app.core.navigation.CREATED_EXERCISE_ID_KEY
import com.gympulse.app.core.navigation.CompareWorkoutsRoute
import com.gympulse.app.core.navigation.EquipmentRoute
import com.gympulse.app.core.navigation.ExerciseDetailRoute
import com.gympulse.app.core.navigation.ExerciseEditRoute
import com.gympulse.app.core.navigation.ExerciseLibraryRoute
import com.gympulse.app.core.navigation.HistoryDetailRoute
import com.gympulse.app.core.navigation.HomeRoute
import com.gympulse.app.core.navigation.LocationEditRoute
import com.gympulse.app.core.navigation.LocationsRoute
import com.gympulse.app.core.navigation.OnboardingRoute
import com.gympulse.app.core.navigation.ProfileRoute
import com.gympulse.app.core.navigation.ProgramDetailRoute
import com.gympulse.app.core.navigation.ProgramEditRoute
import com.gympulse.app.core.navigation.ProgramsRoute
import com.gympulse.app.core.navigation.ProgressRoute
import com.gympulse.app.core.navigation.SessionDetailRoute
import com.gympulse.app.core.navigation.SessionEditRoute
import com.gympulse.app.core.navigation.SessionsRoute
import com.gympulse.app.core.navigation.SettingsRoute
import com.gympulse.app.core.navigation.WorkoutRoute
import com.gympulse.app.core.navigation.WorkoutSummaryRoute
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.presentation.equipment.EquipmentScreen
import com.gympulse.app.presentation.exercises.ExerciseEditScreen
import com.gympulse.app.presentation.exercises.ExerciseLibraryScreen
import com.gympulse.app.presentation.history.CompareWorkoutsScreen
import com.gympulse.app.presentation.history.HistoryDetailScreen
import com.gympulse.app.presentation.home.HomeScreen
import com.gympulse.app.presentation.locations.LocationEditScreen
import com.gympulse.app.presentation.locations.LocationsScreen
import com.gympulse.app.presentation.login.LoginScreen
import com.gympulse.app.presentation.onboarding.OnboardingScreen
import com.gympulse.app.presentation.profile.ProfileScreen
import com.gympulse.app.presentation.programs.ProgramDetailScreen
import com.gympulse.app.presentation.programs.ProgramEditScreen
import com.gympulse.app.presentation.programs.ProgramsScreen
import com.gympulse.app.presentation.progress.ExerciseDetailScreen
import com.gympulse.app.presentation.progress.ProgressScreen
import com.gympulse.app.presentation.sessions.SessionDetailScreen
import com.gympulse.app.presentation.sessions.SessionEditScreen
import com.gympulse.app.presentation.sessions.SessionsScreen
import com.gympulse.app.presentation.settings.SettingsScreen
import com.gympulse.app.presentation.workout.WorkoutScreen
import com.gympulse.app.presentation.workout.WorkoutSummaryScreen
import kotlin.reflect.KClass

enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    HOME(HomeRoute, HomeRoute::class, "Accueil", Icons.Rounded.Home, Icons.Outlined.Home),
    PROGRAMS(ProgramsRoute, ProgramsRoute::class, "Programmes", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    SESSIONS(SessionsRoute, SessionsRoute::class, "Séances", Icons.Rounded.FitnessCenter, Icons.Outlined.FitnessCenter),
    PROGRESS(ProgressRoute, ProgressRoute::class, "Progression", Icons.Rounded.Insights, Icons.Outlined.Insights),
    PROFILE(ProfileRoute, ProfileRoute::class, "Profil", Icons.Rounded.Person, Icons.Outlined.Person),
}

private fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun GymPulseApp(
    appViewModel: AppViewModel,
    openWorkoutRequested: Boolean,
    onOpenWorkoutConsumed: () -> Unit,
) {
    val state by appViewModel.uiState.collectAsStateWithLifecycle()
    val signedIn by appViewModel.signedIn.collectAsStateWithLifecycle()
    if (state.loading) return
    if (!signedIn) {
        LoginScreen()
        return
    }

    val navController = rememberNavController()
    val snackbarHostState = LocalSnackbarHostState.current
    val context = LocalContext.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { top -> destination?.hierarchy?.any { it.hasRoute(top.routeClass) } == true }
    val onWorkoutScreens = destination?.hasRoute(WorkoutRoute::class) == true || destination?.hasRoute(WorkoutSummaryRoute::class) == true
    val startDestination: Any = remember { if (state.preferences.onboardingCompleted) HomeRoute else OnboardingRoute }

    var alreadyActive by remember { mutableStateOf<AppEvent.WorkoutAlreadyActive?>(null) }
    var pendingStartId by remember { mutableLongStateOf(0L) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (pendingStartId != 0L) appViewModel.startWorkout(pendingStartId)
        pendingStartId = 0L
    }
    val startWorkout: (Long) -> Unit = { sessionId ->
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingStartId = sessionId
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            appViewModel.startWorkout(sessionId)
        }
    }

    LaunchedEffect(Unit) {
        appViewModel.eventFlow.collect { event ->
            when (event) {
                AppEvent.OpenWorkout -> navController.navigate(WorkoutRoute) { launchSingleTop = true }
                is AppEvent.WorkoutAlreadyActive -> alreadyActive = event
                is AppEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }
    LaunchedEffect(openWorkoutRequested, state.activeWorkout != null) {
        if (openWorkoutRequested) {
            if (state.activeWorkout != null) appViewModel.resumeWorkout()
            onOpenWorkoutConsumed()
        }
    }

    CompositionLocalProvider(
        LocalWeightUnit provides state.preferences.weightUnit,
        LocalHapticsEnabled provides state.preferences.vibrationEnabled,
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                AnimatedVisibility(
                    visible = currentTopLevel != null,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    Column {
                        val active = state.activeWorkout
                        if (active != null && !onWorkoutScreens) {
                            ActiveWorkoutBar(active, now = appViewModel::now, onClick = { appViewModel.resumeWorkout() })
                        }
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                            TopLevelDestination.entries.forEach { top ->
                                val selected = currentTopLevel == top
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navController.navigateTopLevel(top) },
                                    icon = { Icon(if (selected) top.selectedIcon else top.icon, contentDescription = null) },
                                    label = { Text(top.label, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        indicatorColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .background(MaterialTheme.colorScheme.background),
                enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 12 } },
            ) {
                composable<OnboardingRoute> {
                    OnboardingScreen(onFinished = {
                        navController.navigate(HomeRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
                    })
                }
                composable<HomeRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
                    HomeScreen(
                        onStartWorkout = startWorkout,
                        onResumeWorkout = { appViewModel.resumeWorkout() },
                        onOpenSession = { navController.navigate(SessionDetailRoute(it)) },
                        onOpenSessions = { navController.navigateTopLevel(TopLevelDestination.SESSIONS) },
                        onOpenHistoryDetail = { navController.navigate(HistoryDetailRoute(it)) },
                        onOpenProgress = { navController.navigateTopLevel(TopLevelDestination.PROGRESS) },
                        onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
                        onManageLocations = { navController.navigate(LocationsRoute) },
                        onCreateSession = { navController.navigate(SessionEditRoute()) },
                    )
                }
                composable<ProgramsRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
                    ProgramsScreen(
                        onOpenProgram = { navController.navigate(ProgramDetailRoute(it)) },
                        onCreateProgram = { navController.navigate(ProgramEditRoute()) },
                        onStartWorkout = startWorkout,
                    )
                }
                composable<SessionsRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
                    SessionsScreen(
                        onOpenSession = { navController.navigate(SessionDetailRoute(it)) },
                        onCreateSession = { navController.navigate(SessionEditRoute()) },
                        onStartWorkout = startWorkout,
                        onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
                        onCreateExercise = { navController.navigate(ExerciseEditRoute()) },
                        onManageLocations = { navController.navigate(LocationsRoute) },
                    )
                }
                composable<ProgressRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
                    ProgressScreen(
                        onOpenWorkout = { navController.navigate(HistoryDetailRoute(it)) },
                        onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
                        onCompare = { first, second -> navController.navigate(CompareWorkoutsRoute(first, second)) },
                    )
                }
                composable<ProfileRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
                    ProfileScreen(
                        onOpenExerciseLibrary = { navController.navigate(ExerciseLibraryRoute) },
                        onOpenLocations = { navController.navigate(LocationsRoute) },
                        onOpenEquipment = { navController.navigate(EquipmentRoute) },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenProgress = { navController.navigateTopLevel(TopLevelDestination.PROGRESS) },
                    )
                }
                composable<ProgramDetailRoute> {
                    ProgramDetailScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(ProgramEditRoute(it)) },
                        onOpenSession = { navController.navigate(SessionDetailRoute(it)) },
                        onStartWorkout = startWorkout,
                    )
                }
                composable<ProgramEditRoute> {
                    ProgramEditScreen(
                        onBack = { navController.popBackStack() },
                        onCreateSession = { navController.navigate(SessionEditRoute()) },
                    )
                }
                composable<SessionDetailRoute> {
                    SessionDetailScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(SessionEditRoute(sessionId = it)) },
                        onDuplicate = { navController.navigate(SessionEditRoute(duplicateFromId = it)) },
                        onStartWorkout = startWorkout,
                        onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
                    )
                }
                composable<SessionEditRoute> {
                    SessionEditScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { id ->
                            navController.popBackStack()
                            if (navController.currentDestination?.hasRoute(SessionDetailRoute::class) != true) {
                                navController.navigate(SessionDetailRoute(id))
                            }
                        },
                        onCreateExercise = { navController.navigate(ExerciseEditRoute(returnResult = true)) },
                    )
                }
                composable<ExerciseLibraryRoute> {
                    ExerciseLibraryScreen(
                        onBack = { navController.popBackStack() },
                        onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
                        onCreateExercise = { navController.navigate(ExerciseEditRoute()) },
                    )
                }
                composable<ExerciseEditRoute> { entry ->
                    val route = entry.toRoute<ExerciseEditRoute>()
                    ExerciseEditScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { id ->
                            if (route.returnResult) navController.previousBackStackEntry?.savedStateHandle?.set(CREATED_EXERCISE_ID_KEY, id)
                            navController.popBackStack()
                        },
                        onDeleted = {
                            navController.popBackStack()
                            if (navController.currentDestination?.hasRoute(ExerciseDetailRoute::class) == true) navController.popBackStack()
                        },
                    )
                }
                composable<ExerciseDetailRoute> {
                    ExerciseDetailScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(ExerciseEditRoute(exerciseId = it)) },
                    )
                }
                composable<WorkoutRoute>(
                    enterTransition = { fadeIn(tween(250)) + scaleIn(tween(300), initialScale = 0.96f) },
                ) {
                    WorkoutScreen(
                        onBack = { navController.popBackStack() },
                        onFinish = { id -> navController.navigate(WorkoutSummaryRoute(id)) },
                        onClosed = {
                            if (!navController.popBackStack(WorkoutRoute, inclusive = true)) navController.navigate(HomeRoute)
                        },
                    )
                }
                composable<WorkoutSummaryRoute> {
                    WorkoutSummaryScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { id ->
                            navController.navigate(HistoryDetailRoute(id)) {
                                popUpTo(navController.graph.findStartDestination().id)
                            }
                        },
                    )
                }
                composable<HistoryDetailRoute> {
                    HistoryDetailScreen(
                        onBack = { navController.popBackStack() },
                        onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
                        onCompare = { first, second -> navController.navigate(CompareWorkoutsRoute(first, second)) },
                    )
                }
                composable<CompareWorkoutsRoute> {
                    CompareWorkoutsScreen(onBack = { navController.popBackStack() })
                }
                composable<LocationsRoute> {
                    LocationsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenLocation = { navController.navigate(LocationEditRoute(it)) },
                        onCreateLocation = { navController.navigate(LocationEditRoute()) },
                    )
                }
                composable<LocationEditRoute> {
                    LocationEditScreen(onBack = { navController.popBackStack() })
                }
                composable<EquipmentRoute> {
                    EquipmentScreen(
                        onBack = { navController.popBackStack() },
                        onOpenLocation = { navController.navigate(LocationEditRoute(it)) },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenLocations = { navController.navigate(LocationsRoute) },
                    )
                }
            }
        }
    }

    if (state.showResumePrompt && !onWorkoutScreens) {
        val active = state.activeWorkout!!
        AlertDialog(
            onDismissRequest = appViewModel::dismissResumePrompt,
            title = { Text("Séance interrompue") },
            text = {
                Text(
                    "« ${active.sessionName} », commencée ${Formatters.relativeDay(active.startedAt).lowercase()} à ${Formatters.time(active.startedAt)}, " +
                        "n'est pas terminée (${active.completedSetCount}/${active.totalSets} séries enregistrées). Voulez-vous la reprendre ?",
                )
            },
            confirmButton = { TextButton(onClick = appViewModel::resumeWorkout) { Text("Reprendre") } },
            dismissButton = {
                Row {
                    TextButton(onClick = appViewModel::abandonInterruptedWorkout) {
                        Text("Abandonner", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = appViewModel::dismissResumePrompt) { Text("Plus tard") }
                }
            },
        )
    }

    alreadyActive?.let { event ->
        AlertDialog(
            onDismissRequest = { alreadyActive = null },
            title = { Text("Une séance est déjà en cours") },
            text = { Text("« ${event.activeName} » n'est pas terminée. Reprenez-la, ou abandonnez-la pour démarrer la nouvelle séance.") },
            confirmButton = {
                TextButton(onClick = {
                    alreadyActive = null
                    appViewModel.resumeWorkout()
                }) { Text("Reprendre") }
            },
            dismissButton = {
                TextButton(onClick = {
                    alreadyActive = null
                    appViewModel.replaceActiveWorkout(event.requestedSessionId)
                }) { Text("Abandonner et démarrer", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

/** Persistent reminder of the running workout, shown above the bottom navigation. */
@Composable
private fun ActiveWorkoutBar(execution: WorkoutExecution, now: () -> Long, onClick: () -> Unit) {
    val time by rememberTicker(now, intervalMs = 500L)
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse-alpha")
    val rest = execution.rest
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .alpha(if (execution.isPaused) 1f else alpha)
                        .clip(CircleShape)
                        .background(if (execution.isPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(execution.sessionName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val status = when {
                        execution.isPaused -> "En pause"
                        rest != null && !rest.isFinished(time) -> "Repos ${Formatters.countdown(rest.remainingMs(time))}"
                        rest != null -> "Série suivante prête"
                        else -> execution.currentExercise?.name ?: "En cours"
                    }
                    Text(status, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    Formatters.clock(execution.elapsedMs(time)),
                    style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                    fontWeight = FontWeight.Bold,
                )
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Ouvrir la séance")
            }
            LinearProgressIndicator(
                progress = { execution.progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            )
        }
    }
}
