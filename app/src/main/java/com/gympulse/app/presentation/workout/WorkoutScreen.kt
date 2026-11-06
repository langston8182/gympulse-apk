package com.gympulse.app.presentation.workout

import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympulse.app.core.designsystem.components.BigActionButton
import com.gympulse.app.core.designsystem.components.ConfirmDialog
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.ExerciseImage
import com.gympulse.app.core.designsystem.components.LoadingState
import com.gympulse.app.core.designsystem.components.LocalWeightUnit
import com.gympulse.app.core.designsystem.components.Pill
import com.gympulse.app.core.designsystem.components.rememberEvoHaptics
import com.gympulse.app.core.designsystem.components.rememberTicker
import com.gympulse.app.core.designsystem.theme.GymPulseTheme
import com.gympulse.app.core.designsystem.theme.TabularNumbers
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.core.util.label
import com.gympulse.app.domain.model.ExerciseStatus
import com.gympulse.app.domain.model.LoadMode
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.workout.WorkoutEvent
import com.gympulse.app.presentation.common.SetFormat

@Composable
fun WorkoutScreen(
    onBack: () -> Unit,
    onFinish: (Long) -> Unit,
    onClosed: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val execution by viewModel.execution.collectAsStateWithLifecycle()
    val restored by viewModel.restored.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.ensureServiceRunning() }
    LaunchedEffect(restored, execution == null) {
        if (restored && execution == null) onClosed()
    }
    KeepScreenOn(preferences.keepScreenOn)

    val current = execution
    if (current == null) {
        LoadingState()
        return
    }
    WorkoutContent(current, viewModel, onBack, onFinish, onClosed)
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view: View = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun WorkoutContent(
    execution: WorkoutExecution,
    viewModel: WorkoutViewModel,
    onBack: () -> Unit,
    onFinish: (Long) -> Unit,
    onClosed: () -> Unit,
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val previous by viewModel.previous.collectAsStateWithLifecycle()
    val time = rememberTicker(viewModel::now, intervalMs = 200L)
    val haptics = rememberEvoHaptics()
    val unit = LocalWeightUnit.current

    var showPlan by remember { mutableStateOf(false) }
    var showFinishConfirm by remember { mutableStateOf(false) }
    var showAbandonConfirm by remember { mutableStateOf(false) }
    var editSet by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var restJustFinished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event == WorkoutEvent.RestFinished) restJustFinished = true
        }
    }
    LaunchedEffect(execution.rest) { if (execution.rest == null) restJustFinished = false }

    val exercise = execution.currentExercise
    val set = execution.currentSet
    val allDone = execution.isAllDone
    val requestFinish = {
        if (allDone) onFinish(execution.id) else showFinishConfirm = true
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                WorkoutTopBar(
                    execution = execution,
                    time = time,
                    onMinimize = onBack,
                    onTogglePause = { if (execution.isPaused) viewModel.resume() else viewModel.pause() },
                    onOpenPlan = { showPlan = true },
                    onAddSet = { viewModel.addSet(execution.currentExerciseIndex) },
                    onSkipExercise = viewModel::skipExercise,
                    onFinish = requestFinish,
                    onAbandon = { showAbandonConfirm = true },
                )
            },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        when {
                            allDone && set?.isCompleted != false -> BigActionButton(
                                text = "Voir le récapitulatif",
                                icon = Icons.Rounded.Flag,
                                onClick = { onFinish(execution.id) },
                            )
                            set?.isCompleted == true -> BigActionButton(
                                text = "Mettre à jour la série",
                                icon = Icons.Rounded.Update,
                                onClick = { viewModel.completeSet() },
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                            )
                            else -> BigActionButton(
                                text = "Terminer la série",
                                icon = Icons.Rounded.Check,
                                enabled = !execution.isPaused && exercise?.status != ExerciseStatus.SKIPPED,
                                onClick = { viewModel.completeSet() },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ProgressHeader(execution)
                if (exercise != null) {
                    ExerciseHeader(
                        execution = execution,
                        onResumeSkipped = { viewModel.goToExercise(execution.currentExerciseIndex) },
                    )
                    SetStrip(
                        exercise = exercise,
                        currentSetIndex = execution.currentSetIndex,
                        unit = unit,
                        onSelect = { index -> viewModel.goTo(execution.currentExerciseIndex, index) },
                        onEdit = { index -> editSet = execution.currentExerciseIndex to index },
                        onAddSet = { viewModel.addSet(execution.currentExerciseIndex) },
                    )
                }
                AnimatedVisibility(
                    visible = execution.rest != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    execution.rest?.let { rest ->
                        RestPanel(
                            rest = rest,
                            time = time,
                            justFinished = restJustFinished,
                            nextLabel = nextEffortLabel(execution, unit),
                            onSkip = {
                                haptics.tick()
                                viewModel.skipRest()
                            },
                            onExtend = { seconds ->
                                haptics.tick()
                                viewModel.extendRest(seconds)
                            },
                        )
                    }
                }
                AnimatedContent(
                    targetState = allDone && set?.isCompleted != false,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "workout-main",
                ) { done ->
                    if (done) {
                        CompletionCard(execution, time)
                    } else if (exercise != null && set != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            TargetCard(
                                exercise = exercise,
                                set = set,
                                setIndex = execution.currentSetIndex,
                                previous = previous[exercise.key],
                                unit = unit,
                            )
                            if (exercise.isTimed) {
                                DurationPanel(
                                    execution = execution,
                                    draft = draft,
                                    time = time,
                                    onStart = viewModel::startTimer,
                                    onPause = viewModel::pauseTimer,
                                    onReset = viewModel::resetTimer,
                                    onAdjust = viewModel::adjustDuration,
                                    onSetDuration = viewModel::setDuration,
                                )
                            } else {
                                RepetitionInputs(
                                    exercise = exercise,
                                    draft = draft,
                                    unit = unit,
                                    weightStep = viewModel.weightStep(exercise, unit),
                                    onAdjustReps = viewModel::adjustRepetitions,
                                    onSetReps = viewModel::setRepetitions,
                                    onAdjustWeight = { viewModel.adjustWeight(it, unit) },
                                    onSetWeight = { viewModel.setWeight(it, unit) },
                                )
                            }
                        }
                    }
                }
                UpNext(execution, onOpen = { viewModel.goToExercise(it) }, onOpenPlan = { showPlan = true })
                Spacer(Modifier.height(8.dp))
            }
        }

        if (execution.isPaused) {
            PausedOverlay(
                execution = execution,
                time = time,
                onResume = viewModel::resume,
                onFinish = requestFinish,
            )
        }
    }

    if (showPlan) {
        WorkoutPlanSheet(
            execution = execution,
            remaining = viewModel.remainingIndices(execution),
            unit = unit,
            onDismiss = { showPlan = false },
            onOpenExercise = { index ->
                viewModel.goToExercise(index)
                showPlan = false
            },
            onReorder = viewModel::reorderRemaining,
        )
    }

    editSet?.let { (exerciseIndex, setIndex) ->
        val target = execution.exercises.getOrNull(exerciseIndex)
        val targetSet = target?.sets?.getOrNull(setIndex)
        if (target != null && targetSet != null) {
            EditSetDialog(
                exercise = target,
                set = targetSet,
                setNumber = setIndex + 1,
                unit = unit,
                onDismiss = { editSet = null },
                onSave = { reps, weightKg, duration ->
                    viewModel.correctSet(exerciseIndex, setIndex, reps, weightKg, duration)
                    editSet = null
                },
            )
        } else {
            editSet = null
        }
    }

    if (showFinishConfirm) {
        val remainingSets = execution.totalSets - execution.completedSetCount
        ConfirmDialog(
            title = "Terminer la séance ?",
            message = "Il reste $remainingSets série(s) non réalisée(s). Elles seront marquées comme passées ; tout ce qui a été fait est conservé.",
            confirmLabel = "Terminer",
            icon = Icons.Rounded.Flag,
            onConfirm = {
                showFinishConfirm = false
                onFinish(execution.id)
            },
            onDismiss = { showFinishConfirm = false },
        )
    }
    if (showAbandonConfirm) {
        ConfirmDialog(
            title = "Abandonner la séance ?",
            message = "Les ${execution.completedSetCount} série(s) enregistrée(s) ne seront pas ajoutées à votre historique. Cette action est définitive.",
            confirmLabel = "Abandonner",
            destructive = true,
            icon = Icons.Rounded.DeleteForever,
            onConfirm = {
                showAbandonConfirm = false
                viewModel.abandon(onClosed)
            },
            onDismiss = { showAbandonConfirm = false },
        )
    }
}

@Composable
private fun WorkoutTopBar(
    execution: WorkoutExecution,
    time: State<Long>,
    onMinimize: () -> Unit,
    onTogglePause: () -> Unit,
    onOpenPlan: () -> Unit,
    onAddSet: () -> Unit,
    onSkipExercise: () -> Unit,
    onFinish: () -> Unit,
    onAbandon: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMinimize) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Réduire la séance") }
        Column(Modifier.weight(1f)) {
            Text(
                execution.sessionName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                Formatters.clock(execution.elapsedMs(time.value)),
                style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers),
                fontWeight = FontWeight.ExtraBold,
            )
        }
        IconButton(onClick = onTogglePause) {
            Icon(
                if (execution.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                contentDescription = if (execution.isPaused) "Reprendre la séance" else "Mettre en pause",
            )
        }
        IconButton(onClick = onOpenPlan) { Icon(Icons.AutoMirrored.Rounded.ListAlt, contentDescription = "Plan de la séance") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Plus d'actions") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Ajouter une série") },
                    leadingIcon = { Icon(Icons.Rounded.Add, null) },
                    onClick = { menu = false; onAddSet() },
                )
                DropdownMenuItem(
                    text = { Text("Passer l'exercice") },
                    leadingIcon = { Icon(Icons.Rounded.SkipNext, null) },
                    onClick = { menu = false; onSkipExercise() },
                )
                DropdownMenuItem(
                    text = { Text("Réorganiser / plan") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ListAlt, null) },
                    onClick = { menu = false; onOpenPlan() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Terminer la séance") },
                    leadingIcon = { Icon(Icons.Rounded.Flag, null) },
                    onClick = { menu = false; onFinish() },
                )
                DropdownMenuItem(
                    text = { Text("Abandonner la séance", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onAbandon() },
                )
            }
        }
    }
}

@Composable
private fun ProgressHeader(execution: WorkoutExecution) {
    val progress by animateFloatAsState(execution.progress, label = "workout-progress")
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Exercice ${execution.currentExerciseIndex + 1}/${execution.exercises.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${execution.completedSetCount}/${execution.totalSets} séries",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun ExerciseHeader(execution: WorkoutExecution, onResumeSkipped: () -> Unit) {
    val exercise = execution.currentExercise ?: return
    EvoCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExerciseImage(exercise.imageUri, size = 84.dp, shape = RoundedCornerShape(20.dp), contentDescription = exercise.name)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                AnimatedContent(exercise.name, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "exercise-name") { name ->
                    Text(name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Série ${execution.currentSetIndex + 1}/${exercise.sets.size}" +
                        (exercise.muscleGroups.takeIf { it.isNotEmpty() }?.let { " · " + it.take(2).joinToString { m -> m.label } } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (exercise.loadMode == LoadMode.PER_IMPLEMENT) {
                    Spacer(Modifier.height(6.dp))
                    Pill("Charge par haltère")
                }
            }
        }
        if (exercise.notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(exercise.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (exercise.status == ExerciseStatus.SKIPPED) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onResumeSkipped, modifier = Modifier.fillMaxWidth()) { Text("Exercice passé · le reprendre") }
        }
    }
}

@Composable
private fun CompletionCard(execution: WorkoutExecution, time: State<Long>) {
    val record = GymPulseTheme.extendedColors.record
    EvoCard(contentPadding = PaddingValues(24.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(record.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.EmojiEvents, null, tint = record, modifier = Modifier.size(40.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("Toutes les séries sont terminées !", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "${execution.completedSetCount} séries en ${Formatters.duration(execution.elapsedMs(time.value))}. " +
                    "Ajoutez une série si besoin, ou consultez le récapitulatif.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PausedOverlay(execution: WorkoutExecution, time: State<Long>, onResume: () -> Unit, onFinish: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(32.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.Pause, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text("Séance en pause", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                Formatters.clock(execution.elapsedMs(time.value)),
                style = MaterialTheme.typography.displayMedium.merge(TabularNumbers),
            )
            Text(
                "Le chronomètre et le repos sont figés.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            BigActionButton("Reprendre", onClick = onResume, icon = Icons.Rounded.PlayArrow)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Terminer la séance") }
        }
    }
}
