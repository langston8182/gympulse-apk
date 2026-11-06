package com.gympulse.app.presentation.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.ExerciseImage
import com.gympulse.app.core.designsystem.components.NumberInputDialog
import com.gympulse.app.core.designsystem.components.NumberStepper
import com.gympulse.app.core.designsystem.components.ProgressRing
import com.gympulse.app.core.designsystem.components.dragHandle
import com.gympulse.app.core.designsystem.components.rememberEvoHaptics
import com.gympulse.app.core.designsystem.components.rememberReorderableListState
import com.gympulse.app.core.designsystem.components.reorderableItem
import com.gympulse.app.core.designsystem.theme.GymPulseTheme
import com.gympulse.app.core.designsystem.theme.TabularNumbers
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.domain.model.Equipment
import com.gympulse.app.domain.model.ExecutedExercise
import com.gympulse.app.domain.model.ExecutedSet
import com.gympulse.app.domain.model.ExercisePerformance
import com.gympulse.app.domain.model.ExerciseStatus
import com.gympulse.app.domain.model.LoadMode
import com.gympulse.app.domain.model.RestTimer
import com.gympulse.app.domain.model.WeightUnit
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.presentation.common.SetFormat

internal fun nextEffortLabel(execution: WorkoutExecution, unit: WeightUnit): String {
    val exercise = execution.currentExercise ?: return "Fin de séance"
    val set = execution.currentSet ?: return exercise.name
    return "${exercise.name} · Série ${execution.currentSetIndex + 1}/${exercise.sets.size} · ${SetFormat.executedTarget(set, exercise.type, unit)}"
}

@Composable
internal fun RestPanel(
    rest: RestTimer,
    time: State<Long>,
    justFinished: Boolean,
    nextLabel: String,
    onSkip: () -> Unit,
    onExtend: (Int) -> Unit,
) {
    val restColor = GymPulseTheme.extendedColors.rest
    val ready = rest.isFinished(time.value) || justFinished
    val haptics = rememberEvoHaptics()
    LaunchedEffect(ready) { if (ready) haptics.confirm() }
    val border by animateColorAsState(if (ready) MaterialTheme.colorScheme.primary else restColor.copy(alpha = 0.4f), label = "rest-border")
    EvoCard(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.5.dp, border),
        contentPadding = PaddingValues(20.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(ready, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "rest-state") { isReady ->
                if (isReady) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ProgressRing(progress = 1f, size = 168.dp, strokeWidth = 12.dp, color = MaterialTheme.colorScheme.primary) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Série suivante prête !", style = MaterialTheme.typography.titleLarge)
                    }
                } else {
                    val remaining = rest.remainingMs(time.value)
                    val total = (rest.totalSeconds * 1000L).coerceAtLeast(1)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ProgressRing(
                            progress = remaining.toFloat() / total,
                            size = 200.dp,
                            strokeWidth = 14.dp,
                            color = restColor,
                            animate = false,
                            modifier = Modifier.semantics { contentDescription = "Repos, ${Formatters.countdown(remaining)} restantes" },
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("REPOS", style = MaterialTheme.typography.labelLarge, color = restColor)
                                Text(
                                    Formatters.countdown(remaining),
                                    style = MaterialTheme.typography.displayMedium.merge(TabularNumbers),
                                    fontWeight = FontWeight.Black,
                                )
                                if (rest.isPaused) Text("En pause", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onExtend(15) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("+15 s") }
                OutlinedButton(onClick = { onExtend(30) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("+30 s") }
                Button(
                    onClick = onSkip,
                    modifier = Modifier.weight(1.4f).heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (ready) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(if (ready) Icons.Rounded.Check else Icons.Rounded.SkipNext, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (ready) "C'est parti" else "Passer")
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Prochain effort", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(nextLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun SetStrip(
    exercise: ExecutedExercise,
    currentSetIndex: Int,
    unit: WeightUnit,
    onSelect: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onAddSet: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentSetIndex, exercise.key) { listState.animateScrollToItem((currentSetIndex - 1).coerceAtLeast(0)) }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(exercise.sets, key = { index, _ -> "${exercise.key}-$index" }) { index, set ->
            SetChip(
                index = index,
                set = set,
                exercise = exercise,
                current = index == currentSetIndex,
                unit = unit,
                onClick = { onSelect(index) },
                onLongClick = { if (set.isCompleted) onEdit(index) },
            )
        }
        item(key = "add") {
            Surface(
                onClick = onAddSet,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(width = 56.dp, height = 64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, contentDescription = "Ajouter une série") }
            }
        }
    }
}

@Composable
private fun SetChip(
    index: Int,
    set: ExecutedSet,
    exercise: ExecutedExercise,
    current: Boolean,
    unit: WeightUnit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val success = GymPulseTheme.extendedColors.success
    val container = when {
        current -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        set.isCompleted -> success.copy(alpha = 0.10f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderColor = if (current) MaterialTheme.colorScheme.primary else Color.Transparent
    val label = when {
        set.isCompleted -> SetFormat.executedActual(set, exercise.type, unit)
        set.status == com.gympulse.app.domain.model.SetStatus.SKIPPED -> "Passée"
        else -> SetFormat.executedTarget(set, exercise.type, unit)
    }
    Column(
        Modifier
            .widthIn(min = 84.dp)
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = "Aller à la série ${index + 1}",
                onLongClickLabel = if (set.isCompleted) "Corriger la série ${index + 1}" else null,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "S${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (set.isCompleted) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.Check, null, tint = success, modifier = Modifier.size(14.dp))
            }
            if (set.isExtra) {
                Spacer(Modifier.width(4.dp))
                Text("+", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        Text(label, style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers), fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
internal fun TargetCard(
    exercise: ExecutedExercise,
    set: ExecutedSet,
    setIndex: Int,
    previous: ExercisePerformance?,
    unit: WeightUnit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        InfoBlock(
            title = "Objectif",
            value = SetFormat.executedTarget(set, exercise.type, unit) + if (exercise.loadMode == LoadMode.PER_IMPLEMENT && set.targetWeightKg != null) " / haltère" else "",
            modifier = Modifier.weight(1f),
            accent = MaterialTheme.colorScheme.primary,
        )
        val previousSet = previous?.sets?.getOrNull(setIndex) ?: previous?.sets?.lastOrNull()
        InfoBlock(
            title = if (previous != null) "Dernière fois · ${Formatters.relativeDay(previous.date)}" else "Dernière fois",
            value = previousSet?.let { SetFormat.performed(it, exercise.type, unit) } ?: "Première fois",
            modifier = Modifier.weight(1f),
            accent = MaterialTheme.colorScheme.secondary,
            supporting = previous?.let { SetFormat.performedSummary(it.sets, exercise.type, unit) },
        )
    }
}

@Composable
private fun InfoBlock(title: String, value: String, modifier: Modifier, accent: Color, supporting: String? = null) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium.merge(TabularNumbers), fontWeight = FontWeight.Bold, maxLines = 2)
        if (supporting != null) {
            Spacer(Modifier.height(2.dp))
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun RepetitionInputs(
    exercise: ExecutedExercise,
    draft: SetDraft,
    unit: WeightUnit,
    weightStep: Double,
    onAdjustReps: (Int) -> Unit,
    onSetReps: (Int) -> Unit,
    onAdjustWeight: (Int) -> Unit,
    onSetWeight: (Double?) -> Unit,
) {
    var editReps by remember { mutableStateOf(false) }
    var editWeight by remember { mutableStateOf(false) }
    val isBodyweightBased = exercise.equipment.any { it == Equipment.PULL_UP_BAR || it == Equipment.DIP_STATION || it == Equipment.BODYWEIGHT }
    val weightLabel = if (isBodyweightBased) "Lest" else "Charge"
    val weightText = draft.weightKg?.let { Formatters.weightValue(it, unit) } ?: "0"
    EvoCard(contentPadding = PaddingValues(16.dp)) {
        NumberStepper(
            label = "Répétitions",
            value = draft.repetitions.toString(),
            onDecrement = { onAdjustReps(-1) },
            onIncrement = { onAdjustReps(1) },
            onValueClick = { editReps = true },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        NumberStepper(
            label = weightLabel,
            value = weightText,
            unit = unit.symbol + SetFormat.loadSuffix(exercise.loadMode) + " · pas de ${Formatters.number(weightStep)}",
            onDecrement = { onAdjustWeight(-1) },
            onIncrement = { onAdjustWeight(1) },
            onValueClick = { editWeight = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (editReps) {
        NumberInputDialog(
            title = "Répétitions réalisées",
            initialValue = draft.repetitions.toString(),
            decimal = false,
            validate = { it.toIntOrNull()?.let { value -> value in 0..999 } == true },
            onDismiss = { editReps = false },
            onConfirm = {
                it.toIntOrNull()?.let(onSetReps)
                editReps = false
            },
        )
    }
    if (editWeight) {
        NumberInputDialog(
            title = "$weightLabel utilisée",
            initialValue = draft.weightKg?.let { Formatters.weightValue(it, unit).replace('\u202F', ' ').replace(" ", "") } ?: "",
            suffix = unit.symbol,
            supportingText = if (exercise.loadMode == LoadMode.PER_IMPLEMENT) "Poids d'un haltère" else null,
            validate = { it.isBlank() || Formatters.parseDecimal(it)?.let { value -> value in 0.0..2000.0 } == true },
            onDismiss = { editWeight = false },
            onConfirm = {
                onSetWeight(Formatters.parseDecimal(it))
                editWeight = false
            },
        )
    }
}

@Composable
internal fun DurationPanel(
    execution: WorkoutExecution,
    draft: SetDraft,
    time: State<Long>,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onAdjust: (Int) -> Unit,
    onSetDuration: (Int) -> Unit,
) {
    val set = execution.currentSet ?: return
    val timer = execution.setTimer
    val haptics = rememberEvoHaptics()
    val targetMs = ((set.targetDurationSeconds ?: draft.durationSeconds).coerceAtLeast(1)) * 1000L
    val elapsed = timer?.elapsedMs(time.value) ?: 0L
    val reached = elapsed >= targetMs
    val success = GymPulseTheme.extendedColors.success
    var editDuration by remember { mutableStateOf(false) }
    LaunchedEffect(reached, set.order) { if (reached && timer != null) haptics.confirm() }

    EvoCard(contentPadding = PaddingValues(20.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ProgressRing(
                progress = (elapsed.toFloat() / targetMs).coerceAtMost(1f),
                size = 200.dp,
                color = if (reached) success else MaterialTheme.colorScheme.primary,
                animate = false,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Timelapse, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Text(
                        if (reached) "+" + Formatters.clock(elapsed - targetMs) else Formatters.countdown(targetMs - elapsed),
                        style = MaterialTheme.typography.displayMedium.merge(TabularNumbers),
                        fontWeight = FontWeight.Black,
                        color = if (reached) success else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (reached) "Objectif atteint" else "Objectif ${Formatters.seconds((targetMs / 1000).toInt())}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (timer?.isRunning == true) {
                    FilledTonalButton(onClick = onPause, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Icon(Icons.Rounded.Pause, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Pause")
                    }
                } else {
                    Button(onClick = onStart, modifier = Modifier.weight(1f).heightIn(min = 56.dp), enabled = !execution.isPaused) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (elapsed > 0) "Reprendre" else "Démarrer")
                    }
                }
                if (elapsed > 0 && timer?.isRunning != true) {
                    OutlinedButton(onClick = onReset, modifier = Modifier.heightIn(min = 56.dp)) {
                        Icon(Icons.Rounded.Replay, contentDescription = "Réinitialiser le chronomètre")
                    }
                }
            }
            if (elapsed == 0L) {
                Spacer(Modifier.height(16.dp))
                NumberStepper(
                    label = "Durée enregistrée sans chronomètre",
                    value = Formatters.seconds(draft.durationSeconds),
                    onDecrement = { onAdjust(-5) },
                    onIncrement = { onAdjust(5) },
                    onValueClick = { editDuration = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (editDuration) {
        NumberInputDialog(
            title = "Durée réalisée",
            initialValue = draft.durationSeconds.toString(),
            decimal = false,
            suffix = "s",
            validate = { it.toIntOrNull()?.let { value -> value in 0..3600 } == true },
            onDismiss = { editDuration = false },
            onConfirm = {
                it.toIntOrNull()?.let(onSetDuration)
                editDuration = false
            },
        )
    }
}

@Composable
internal fun UpNext(execution: WorkoutExecution, onOpen: (Int) -> Unit, onOpenPlan: () -> Unit) {
    val index = execution.currentExerciseIndex
    val previousIndex = (index - 1).takeIf { it >= 0 }
    val nextIndex = (index + 1 until execution.exercises.size).firstOrNull {
        execution.exercises[it].status != ExerciseStatus.SKIPPED && execution.exercises[it].hasPendingSets
    } ?: (index + 1).takeIf { it < execution.exercises.size }
    if (previousIndex == null && nextIndex == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        NeighbourCard("Précédent", previousIndex?.let { execution.exercises[it] }, Modifier.weight(1f)) { previousIndex?.let(onOpen) }
        NeighbourCard("Suivant", nextIndex?.let { execution.exercises[it] }, Modifier.weight(1f)) { nextIndex?.let(onOpen) ?: onOpenPlan() }
    }
}

@Composable
private fun NeighbourCard(title: String, exercise: ExecutedExercise?, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = exercise != null,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (exercise != null) {
                ExerciseImage(exercise.imageUri, size = 36.dp, shape = RoundedCornerShape(10.dp))
                Spacer(Modifier.width(10.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    exercise?.name ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (exercise != null) {
                    Text(
                        "${exercise.completedSets.size}/${exercise.sets.size} séries" + if (exercise.status == ExerciseStatus.SKIPPED) " · passé" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Overview of the workout: jump to any exercise, reorder the remaining ones by drag and drop. */
@Composable
internal fun WorkoutPlanSheet(
    execution: WorkoutExecution,
    remaining: List<Int>,
    unit: WeightUnit,
    onDismiss: () -> Unit,
    onOpenExercise: (Int) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val remainingKeys = remember(remaining, execution.exercises) { remaining.map { execution.exercises[it].key }.toSet() }
    var items by remember(execution.exercises) { mutableStateOf(execution.exercises) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableListState(
        listState = listState,
        onMove = { from, to ->
            items = items.toMutableList().apply { add(to, removeAt(from)) }
        },
        canMoveOver = { key -> key in remainingKeys },
        onDragFinished = { onReorder(items.filter { it.key in remainingKeys }.map { it.key }) },
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding()) {
            Text("Plan de la séance", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                "Touchez un exercice pour y aller. Faites glisser les exercices restants pour changer l'ordre.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            LazyColumn(state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(items, key = { _, item -> item.key }) { _, item ->
                    val index = execution.exercises.indexOfFirst { it.key == item.key }
                    val isCurrent = index == execution.currentExerciseIndex
                    val draggable = item.key in remainingKeys
                    Surface(
                        onClick = { onOpenExercise(index) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .reorderableItem(reorder, item.key),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val (icon, tint) = when (item.status) {
                                ExerciseStatus.COMPLETED -> Icons.Rounded.CheckCircle to GymPulseTheme.extendedColors.success
                                ExerciseStatus.SKIPPED -> Icons.Rounded.SkipNext to MaterialTheme.colorScheme.onSurfaceVariant
                                ExerciseStatus.IN_PROGRESS -> Icons.Rounded.PlayArrow to MaterialTheme.colorScheme.primary
                                ExerciseStatus.PENDING -> Icons.Rounded.RadioButtonUnchecked to MaterialTheme.colorScheme.outline
                            }
                            Icon(icon, contentDescription = null, tint = tint)
                            Spacer(Modifier.width(12.dp))
                            ExerciseImage(item.imageUri, size = 40.dp, shape = RoundedCornerShape(12.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${item.completedSets.size}/${item.sets.size} séries · " +
                                        SetFormat.executedTarget(item.sets.first(), item.type, unit),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (draggable) {
                                Icon(
                                    Icons.Rounded.DragIndicator,
                                    contentDescription = "Déplacer ${item.name}",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(8.dp)
                                        .dragHandle(reorder, item.key),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EditSetDialog(
    exercise: ExecutedExercise,
    set: ExecutedSet,
    setNumber: Int,
    unit: WeightUnit,
    onDismiss: () -> Unit,
    onSave: (Int?, Double?, Int?) -> Unit,
) {
    var reps by remember { mutableStateOf(set.actualRepetitions?.toString().orEmpty()) }
    var weight by remember { mutableStateOf(set.actualWeightKg?.let { Formatters.weightValue(it, unit).replace("\u202F", "") }.orEmpty()) }
    var duration by remember { mutableStateOf(set.actualDurationSeconds?.toString().orEmpty()) }
    val repsValid = exercise.isTimed || reps.toIntOrNull()?.let { it in 0..999 } == true
    val weightValid = weight.isBlank() || Formatters.parseDecimal(weight)?.let { it >= 0 } == true
    val durationValid = !exercise.isTimed || duration.toIntOrNull()?.let { it in 0..3600 } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Corriger la série $setNumber") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(exercise.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (exercise.isTimed) {
                    OutlinedTextField(
                        value = duration,
                        onValueChange = { duration = it.filter(Char::isDigit) },
                        label = { Text("Durée (secondes)") },
                        isError = !durationValid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                } else {
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { reps = it.filter(Char::isDigit) },
                        label = { Text("Répétitions") },
                        isError = !repsValid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Charge (${unit.symbol}${SetFormat.loadSuffix(exercise.loadMode)})") },
                    isError = !weightValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = repsValid && weightValid && durationValid,
                onClick = {
                    onSave(
                        reps.toIntOrNull(),
                        Formatters.parseDecimal(weight)?.takeIf { it > 0 }?.let { unit.toKg(it) },
                        duration.toIntOrNull(),
                    )
                },
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
