package com.gympulse.app.presentation.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gympulse.app.core.designsystem.components.BigActionButton
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.EvoTopBar
import com.gympulse.app.core.designsystem.components.GradientCard
import com.gympulse.app.core.designsystem.components.LoadingState
import com.gympulse.app.core.designsystem.components.LocalSnackbarHostState
import com.gympulse.app.core.designsystem.components.LocalWeightUnit
import com.gympulse.app.core.designsystem.components.SectionHeader
import com.gympulse.app.core.designsystem.components.StatTile
import com.gympulse.app.core.designsystem.components.TrendLabel
import com.gympulse.app.core.designsystem.components.rememberTicker
import com.gympulse.app.core.designsystem.theme.GymPulseTheme
import com.gympulse.app.core.navigation.WorkoutSummaryRoute
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.core.util.label
import com.gympulse.app.domain.model.PersonalRecord
import com.gympulse.app.domain.model.RecordType
import com.gympulse.app.domain.model.WeightUnit
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.repository.ProgressRepository
import com.gympulse.app.domain.repository.WorkoutRepository
import com.gympulse.app.domain.stats.TrainingMath
import com.gympulse.app.domain.workout.ActiveWorkoutManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SummaryUiState(
    val loading: Boolean = true,
    val execution: WorkoutExecution? = null,
    val isActive: Boolean = false,
    val previous: WorkoutExecution? = null,
    val records: List<PersonalRecord> = emptyList(),
)

@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val manager: ActiveWorkoutManager,
    workoutRepository: WorkoutRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {
    private val executionId = savedStateHandle.toRoute<WorkoutSummaryRoute>().executionId

    val notes = MutableStateFlow<String?>(null)
    val saving = MutableStateFlow(false)

    val state: StateFlow<SummaryUiState> = combine(
        manager.active,
        workoutRepository.observeExecution(executionId),
        workoutRepository.observeHistory(),
    ) { active, stored, history -> Triple(active?.takeIf { it.id == executionId } ?: stored, active?.id == executionId, history) }
        .distinctUntilChanged()
        .mapLatest { (execution, isActive, history) ->
            if (execution == null) return@mapLatest SummaryUiState(loading = false)
            if (notes.value == null) notes.value = execution.notes
            val previous = history
                .filter { it.id != execution.id && it.sessionId == execution.sessionId && it.startedAt < execution.startedAt }
                .maxByOrNull { it.startedAt }
            SummaryUiState(
                loading = false,
                execution = execution,
                isActive = isActive,
                previous = previous,
                records = progressRepository.detectRecords(execution),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState())

    fun now(): Long = manager.now()

    fun save(onSaved: (Long) -> Unit) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            val id = manager.finish(notes.value?.trim())
            saving.value = false
            onSaved(id ?: executionId)
        }
    }
}

@Composable
fun WorkoutSummaryScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: WorkoutSummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val unit = LocalWeightUnit.current
    val time by rememberTicker(viewModel::now, intervalMs = 1_000L, enabled = state.isActive)

    Scaffold(
        topBar = { EvoTopBar(title = "Récapitulatif", onBack = onBack) },
        bottomBar = {
            val execution = state.execution
            if (execution != null) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        if (state.isActive) {
                            BigActionButton(
                                text = if (saving) "Enregistrement…" else "Terminer et enregistrer",
                                icon = Icons.Rounded.Save,
                                enabled = !saving,
                                onClick = {
                                    viewModel.save { id ->
                                        onSaved(id)
                                    }
                                },
                            )
                        } else {
                            BigActionButton(text = "Voir le détail", onClick = { onSaved(execution.id) })
                        }
                    }
                }
            }
        },
    ) { padding ->
        val execution = state.execution
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            execution == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Séance introuvable", style = MaterialTheme.typography.titleMedium)
            }
            else -> SummaryContent(
                state = state,
                execution = execution,
                durationMs = if (state.isActive) execution.elapsedMs(time) else execution.durationMs,
                notes = notes.orEmpty(),
                onNotesChange = { viewModel.notes.value = it },
                unit = unit,
                modifier = Modifier.padding(padding),
            )
        }
    }
    LaunchedEffect(Unit) { snackbar.currentSnackbarData?.dismiss() }
}

@Composable
private fun SummaryContent(
    state: SummaryUiState,
    execution: WorkoutExecution,
    durationMs: Long,
    notes: String,
    onNotesChange: (String) -> Unit,
    unit: WeightUnit,
    modifier: Modifier,
) {
    val volume = remember(execution) { TrainingMath.workoutVolumeKg(execution) }
    val sets = remember(execution) { TrainingMath.completedSets(execution) }
    val reps = remember(execution) { TrainingMath.completedRepetitions(execution) }
    val exercisesDone = execution.exercises.count { exercise -> exercise.sets.any { it.isCompleted } }
    val trophyScale = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { trophyScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    val record = GymPulseTheme.extendedColors.record

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GradientCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .scale(trophyScale.value)
                        .size(84.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(record.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(Icons.Rounded.EmojiEvents, null, tint = record, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    if (state.isActive) "Bravo, séance accomplie !" else "Séance enregistrée",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "${execution.sessionName} · ${Formatters.fullDate(execution.startedAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Durée", Formatters.duration(durationMs), Modifier.weight(1f), icon = Icons.Rounded.Schedule)
            StatTile("Volume", Formatters.volume(volume, unit), Modifier.weight(1f), icon = Icons.Rounded.FitnessCenter, accent = MaterialTheme.colorScheme.secondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Exercices", "$exercisesDone/${execution.exercises.size}", Modifier.weight(1f), icon = Icons.Rounded.Layers, accent = MaterialTheme.colorScheme.tertiary)
            StatTile("Séries", "$sets", Modifier.weight(1f), icon = Icons.Rounded.Repeat, accent = GymPulseTheme.extendedColors.chart[3], supporting = "$reps répétitions")
        }

        state.previous?.let { previous ->
            ComparisonCard(previous, volume, durationMs, sets, unit)
        }

        if (state.records.isNotEmpty()) {
            SectionHeader("Nouveaux records", subtitle = "${state.records.size} record(s) battu(s) aujourd'hui")
            EvoCard(contentPadding = PaddingValues(16.dp)) {
                state.records.forEachIndexed { index, item ->
                    if (index > 0) Spacer(Modifier.height(12.dp))
                    RecordRow(item, unit)
                }
            }
        }

        SectionHeader("Répartition des exercices")
        EvoCard(contentPadding = PaddingValues(16.dp)) {
            val totalSets = sets.coerceAtLeast(1)
            execution.exercises.filter { exercise -> exercise.sets.any { it.isCompleted } }.forEachIndexed { index, exercise ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                val done = exercise.completedSets.size
                val exerciseVolume = TrainingMath.exerciseVolumeKg(exercise)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(exercise.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "$done séries" + if (exerciseVolume > 0) " · ${Formatters.volume(exerciseVolume, unit)}" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { done.toFloat() / totalSets },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = GymPulseTheme.extendedColors.chart[index % GymPulseTheme.extendedColors.chart.size],
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    drawStopIndicator = {},
                )
            }
        }

        SectionHeader("Notes")
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            enabled = state.isActive,
            placeholder = { Text("Sensations, douleurs, objectifs pour la prochaine fois…") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            shape = RoundedCornerShape(18.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ComparisonCard(previous: WorkoutExecution, volume: Double, durationMs: Long, sets: Int, unit: WeightUnit) {
    val previousVolume = TrainingMath.workoutVolumeKg(previous)
    val previousSets = TrainingMath.completedSets(previous)
    EvoCard(contentPadding = PaddingValues(16.dp)) {
        Text("Par rapport à la dernière fois", style = MaterialTheme.typography.titleMedium)
        Text(Formatters.fullDate(previous.startedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        CompareRow("Volume", Formatters.volume(previousVolume, unit), Formatters.volume(volume, unit), if (previousVolume > 0) (volume - previousVolume) / previousVolume else null)
        Spacer(Modifier.height(8.dp))
        CompareRow("Durée", Formatters.duration(previous.durationMs), Formatters.duration(durationMs), null)
        Spacer(Modifier.height(8.dp))
        CompareRow("Séries", "$previousSets", "$sets", if (previousSets > 0) (sets - previousSets).toDouble() / previousSets else null)
    }
}

@Composable
private fun CompareRow(label: String, before: String, after: String, ratio: Double?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Text(before, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("→", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(after, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (ratio != null) TrendLabel(ratio)
    }
}

@Composable
internal fun RecordRow(record: PersonalRecord, unit: WeightUnit) {
    val gold = GymPulseTheme.extendedColors.record
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(gold.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { androidx.compose.material3.Icon(Icons.Rounded.MilitaryTech, null, tint = gold) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(record.exerciseName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(record.type.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatRecordValue(record, unit), style = MaterialTheme.typography.titleSmall, color = gold, fontWeight = FontWeight.Bold)
            record.previousValue?.let {
                Text(
                    "avant : ${formatRecordValue(record.copy(value = it, repetitions = null), unit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun formatRecordValue(record: PersonalRecord, unit: WeightUnit): String = when (record.type) {
    RecordType.MAX_WEIGHT -> Formatters.weight(record.value, unit) + (record.repetitions?.let { " × $it" } ?: "")
    RecordType.ESTIMATED_ONE_REP_MAX -> "~" + Formatters.weight(record.value, unit)
    RecordType.BEST_SET_VOLUME, RecordType.SESSION_VOLUME -> Formatters.volume(record.value, unit)
    RecordType.MAX_REPETITIONS -> "${record.value.toInt()} reps"
    RecordType.MAX_DURATION -> Formatters.seconds(record.value.toInt())
}
