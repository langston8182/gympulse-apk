package com.gympulse.app.presentation.history

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympulse.app.core.designsystem.components.ConfirmDialog
import com.gympulse.app.core.designsystem.components.DemoBadge
import com.gympulse.app.core.designsystem.components.EmptyState
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.EvoTopBar
import com.gympulse.app.core.designsystem.components.ExerciseImage
import com.gympulse.app.core.designsystem.components.GoalBadge
import com.gympulse.app.core.designsystem.components.GradientCard
import com.gympulse.app.core.designsystem.components.ListRow
import com.gympulse.app.core.designsystem.components.LoadingState
import com.gympulse.app.core.designsystem.components.LocalWeightUnit
import com.gympulse.app.core.designsystem.components.Pill
import com.gympulse.app.core.designsystem.components.SectionHeader
import com.gympulse.app.core.designsystem.components.StatTile
import com.gympulse.app.core.designsystem.theme.GymPulseTheme
import com.gympulse.app.core.designsystem.theme.TabularNumbers
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.domain.model.ExecutedExercise
import com.gympulse.app.domain.model.SetStatus
import com.gympulse.app.domain.model.WeightUnit
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.stats.TrainingMath
import com.gympulse.app.presentation.common.SetFormat
import com.gympulse.app.presentation.workout.RecordRow

@Composable
fun HistoryDetailScreen(
    onBack: () -> Unit,
    onOpenExercise: (Long) -> Unit,
    onCompare: (Long, Long) -> Unit,
    viewModel: HistoryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    var confirmDelete by remember { mutableStateOf(false) }
    var showCompare by remember { mutableStateOf(false) }
    val execution = state.execution

    Scaffold(
        topBar = {
            EvoTopBar(
                title = "Séance réalisée",
                onBack = onBack,
                actions = {
                    if (execution != null) {
                        if (state.candidates.isNotEmpty()) {
                            IconButton(onClick = { showCompare = true }) {
                                Icon(Icons.AutoMirrored.Rounded.CompareArrows, contentDescription = "Comparer avec une autre séance")
                            }
                        }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Supprimer cette séance") }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            execution == null -> EmptyState(
                icon = Icons.Rounded.History,
                title = "Séance introuvable",
                message = "Elle a peut-être été supprimée.",
                actionLabel = "Retour",
                onAction = onBack,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "header") { Header(execution) }
                item(key = "totals") { Totals(execution, unit) }
                if (state.records.isNotEmpty()) {
                    item(key = "records") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader("Records battus", subtitle = "${state.records.size} record(s) lors de cette séance")
                            EvoCard(contentPadding = PaddingValues(16.dp)) {
                                state.records.forEachIndexed { index, record ->
                                    if (index > 0) Spacer(Modifier.height(12.dp))
                                    RecordRow(record, unit)
                                }
                            }
                        }
                    }
                }
                item(key = "exercises-header") { SectionHeader("Exercices") }
                items(execution.exercises, key = { it.key }) { exercise ->
                    ExerciseCard(exercise, unit, onOpen = exercise.exerciseId?.let { id -> { onOpenExercise(id) } })
                }
                if (execution.notes.isNotBlank()) {
                    item(key = "notes") {
                        EvoCard(contentPadding = PaddingValues(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Rounded.Notes, null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(8.dp))
                                Text("Notes", style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(execution.notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && execution != null) {
        ConfirmDialog(
            title = "Supprimer cette séance ?",
            message = "« ${execution.sessionName} » du ${Formatters.dayMonthYear(execution.startedAt)} sera retirée de l'historique et des statistiques. Cette action est définitive.",
            confirmLabel = "Supprimer",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (showCompare && execution != null) {
        ModalBottomSheet(onDismissRequest = { showCompare = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Text("Comparer avec…", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                items(state.candidates.take(60), key = { it.id }) { other ->
                    ListRow(
                        title = other.sessionName,
                        subtitle = "${Formatters.fullDate(other.startedAt)} · ${Formatters.volume(TrainingMath.workoutVolumeKg(other), unit)}",
                        trailing = {
                            if (other.sessionId == execution.sessionId) Pill("Même séance")
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                        },
                        onClick = {
                            showCompare = false
                            val (older, newer) = listOf(other, execution).sortedBy { it.startedAt }
                            onCompare(older.id, newer.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(execution: WorkoutExecution) {
    GradientCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GoalBadge(execution.trainingGoal)
            if (execution.isDemo) DemoBadge()
        }
        Spacer(Modifier.height(10.dp))
        Text(execution.sessionName, style = MaterialTheme.typography.headlineMedium)
        Text(
            Formatters.fullDate(execution.startedAt) + " · " + Formatters.time(execution.startedAt) +
                (execution.endedAt?.let { " – " + Formatters.time(it) } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        execution.locationName?.let {
            Spacer(Modifier.height(8.dp))
            Pill(it, icon = Icons.Rounded.Place)
        }
    }
}

@Composable
private fun Totals(execution: WorkoutExecution, unit: WeightUnit) {
    val exercisesDone = execution.exercises.count { exercise -> exercise.sets.any { it.isCompleted } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Durée", Formatters.duration(execution.durationMs), Modifier.weight(1f), icon = Icons.Rounded.Schedule)
            StatTile("Volume", Formatters.volume(TrainingMath.workoutVolumeKg(execution), unit), Modifier.weight(1f), icon = Icons.Rounded.FitnessCenter, accent = MaterialTheme.colorScheme.secondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Séries", "${TrainingMath.completedSets(execution)}", Modifier.weight(1f), icon = Icons.Rounded.Layers, accent = MaterialTheme.colorScheme.tertiary, supporting = "$exercisesDone exercices")
            StatTile("Répétitions", "${TrainingMath.completedRepetitions(execution)}", Modifier.weight(1f), icon = Icons.Rounded.Repeat, accent = GymPulseTheme.extendedColors.chart[3])
        }
    }
}

@Composable
private fun ExerciseCard(exercise: ExecutedExercise, unit: WeightUnit, onOpen: (() -> Unit)?) {
    val success = GymPulseTheme.extendedColors.success
    EvoCard(onClick = onOpen, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExerciseImage(exercise.imageUri, size = 44.dp, shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val volume = TrainingMath.exerciseVolumeKg(exercise)
                Text(
                    "${exercise.completedSets.size}/${exercise.sets.size} séries" + if (volume > 0) " · ${Formatters.volume(volume, unit)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onOpen != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        exercise.sets.forEachIndexed { index, set ->
            val skipped = set.status == SetStatus.SKIPPED
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(
                        if (skipped) MaterialTheme.colorScheme.surfaceContainerHighest else success.copy(alpha = 0.16f)
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = if (skipped) MaterialTheme.colorScheme.onSurfaceVariant else success)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (skipped) "Passée" else SetFormat.executedActual(set, exercise.type, unit),
                    style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                    fontWeight = if (skipped) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (skipped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "obj. " + SetFormat.executedTarget(set, exercise.type, unit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                set.actualRestSeconds?.let {
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Rounded.Timer, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.seconds(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (exercise.notes.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(exercise.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
