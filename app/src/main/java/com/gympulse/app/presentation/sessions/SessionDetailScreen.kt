package com.gympulse.app.presentation.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gympulse.app.core.designsystem.components.BigActionButton
import com.gympulse.app.core.designsystem.components.ConfirmDialog
import com.gympulse.app.core.designsystem.components.DemoBadge
import com.gympulse.app.core.designsystem.components.EmptyState
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.EvoTopBar
import com.gympulse.app.core.designsystem.components.ExerciseImage
import com.gympulse.app.core.designsystem.components.GoalBadge
import com.gympulse.app.core.designsystem.components.GradientCard
import com.gympulse.app.core.designsystem.components.LoadingState
import com.gympulse.app.core.designsystem.components.LocalWeightUnit
import com.gympulse.app.core.designsystem.components.Pill
import com.gympulse.app.core.designsystem.components.SectionHeader
import com.gympulse.app.core.designsystem.theme.GymPulseTheme
import com.gympulse.app.core.navigation.SessionDetailRoute
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.core.util.label
import com.gympulse.app.domain.location.SessionCompatibility
import com.gympulse.app.domain.location.SessionRecommender
import com.gympulse.app.domain.model.ExercisePerformance
import com.gympulse.app.domain.model.TrainingLocation
import com.gympulse.app.domain.model.TrainingProgram
import com.gympulse.app.domain.model.WorkoutSession
import com.gympulse.app.domain.repository.LocationRepository
import com.gympulse.app.domain.repository.ProgramRepository
import com.gympulse.app.domain.repository.ProgressRepository
import com.gympulse.app.domain.repository.SessionRepository
import com.gympulse.app.domain.repository.WorkoutRepository
import com.gympulse.app.presentation.common.CompatibilityPill
import com.gympulse.app.presentation.common.SetFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionDetailState(
    val loading: Boolean = true,
    val session: WorkoutSession? = null,
    val lastPerformances: Map<Long, ExercisePerformance> = emptyMap(),
    val lastPerformedAt: Long? = null,
    val timesPerformed: Int = 0,
    val programs: List<TrainingProgram> = emptyList(),
    val locations: List<TrainingLocation> = emptyList(),
    val compatibility: SessionCompatibility? = null,
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    progressRepository: ProgressRepository,
    workoutRepository: WorkoutRepository,
    programRepository: ProgramRepository,
    locationRepository: LocationRepository,
) : ViewModel() {
    private val sessionId = savedStateHandle.toRoute<SessionDetailRoute>().sessionId

    val state: StateFlow<SessionDetailState> = combine(
        sessionRepository.observeSession(sessionId),
        progressRepository.observeLastPerformances().onStart { emit(emptyMap()) },
        workoutRepository.observeHistory().onStart { emit(emptyList()) },
        combine(programRepository.observePrograms(), locationRepository.observeLocations(), ::Pair),
        locationRepository.context,
    ) { session, last, history, (programs, locations), context ->
        val performed = history.filter { it.sessionId == sessionId }
        SessionDetailState(
            loading = false,
            session = session,
            lastPerformances = last,
            lastPerformedAt = performed.maxOfOrNull { it.startedAt },
            timesPerformed = performed.size,
            programs = programs.filter { program -> program.sessions.any { it.id == sessionId } },
            locations = locations.filter { session != null && it.id in session.compatibleLocationIds },
            compatibility = session?.let { s -> context.location?.let { SessionRecommender.compatibility(s, it) } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailState())

    fun toggleFavorite() {
        val session = state.value.session ?: return
        viewModelScope.launch { sessionRepository.setFavorite(session.id, !session.isFavorite) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            sessionRepository.deleteSession(sessionId)
            onDeleted()
        }
    }
}

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onStartWorkout: (Long) -> Unit,
    onOpenExercise: (Long) -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val session = state.session

    Scaffold(
        topBar = {
            EvoTopBar(
                title = "Séance",
                onBack = onBack,
                actions = {
                    if (session != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                if (session.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = if (session.isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                                tint = if (session.isFavorite) GymPulseTheme.extendedColors.record else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { onEdit(session.id) }) { Icon(Icons.Rounded.Edit, contentDescription = "Modifier la séance") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Plus d'actions") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Dupliquer") },
                                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                    onClick = { menu = false; onDuplicate(session.id) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menu = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (session != null && session.exercises.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        BigActionButton("Démarrer la séance", onClick = { onStartWorkout(session.id) }, icon = Icons.Rounded.PlayArrow)
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            session == null -> EmptyState(
                icon = Icons.AutoMirrored.Rounded.EventNote,
                title = "Séance introuvable",
                message = "Elle a peut-être été supprimée.",
                actionLabel = "Retour",
                onAction = onBack,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") { SessionHeader(state, session) }
                item(key = "exercises-header") {
                    SectionHeader("Exercices", subtitle = "${session.exercises.size} exercices · ${session.totalSets} séries", modifier = Modifier.padding(top = 8.dp))
                }
                if (session.exercises.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Rounded.Edit,
                            title = "Séance vide",
                            message = "Ajoutez des exercices pour pouvoir la démarrer.",
                            actionLabel = "Ajouter des exercices",
                            onAction = { onEdit(session.id) },
                        )
                    }
                }
                itemsIndexed(session.exercises, key = { _, item -> item.id.takeIf { it != 0L } ?: item.order }) { index, item ->
                    val last = state.lastPerformances[item.exerciseId]
                    EvoCard(onClick = { onOpenExercise(item.exerciseId) }, contentPadding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(24.dp),
                            )
                            ExerciseImage(item.exercise.imageUri, size = 52.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.exercise.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    SetFormat.targetsSummary(item.sets, item.exercise.type, unit) + SetFormat.loadSuffix(item.exercise.loadMode).takeIf { item.sets.any { it.targetWeightKg != null } }.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Timer, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Repos ${Formatters.seconds(item.restDurationSeconds)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (item.notes.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(item.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (last != null) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.History, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Dernière fois (${Formatters.relativeDay(last.date).lowercase()}) : ${SetFormat.performedSummary(last.sets, last.type, unit)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && session != null) {
        ConfirmDialog(
            title = "Supprimer « ${session.name} » ?",
            message = "La séance sera retirée de vos programmes. Les entraînements déjà réalisés restent dans votre historique.",
            confirmLabel = "Supprimer",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SessionHeader(state: SessionDetailState, session: WorkoutSession) {
    GradientCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GoalBadge(session.trainingGoal)
            if (session.isDemo) DemoBadge()
        }
        Spacer(Modifier.height(12.dp))
        Text(session.name, style = MaterialTheme.typography.headlineMedium)
        if (session.description.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(session.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            HeaderStat("${session.exercises.size}", "exercices")
            HeaderStat("${session.totalSets}", "séries")
            HeaderStat("~${Formatters.duration(session.estimatedDurationSeconds * 1000L)}", "durée")
            HeaderStat("${state.timesPerformed}", "réalisée(s)")
        }
        if (state.lastPerformedAt != null || state.locations.isNotEmpty() || state.compatibility != null || state.programs.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.compatibility?.let { CompatibilityPill(it.compatibility) }
                state.lastPerformedAt?.let { Pill("Dernière : ${Formatters.relativeDay(it).lowercase()}", icon = Icons.Rounded.History) }
                state.locations.forEach { Pill(it.name, icon = Icons.Rounded.Place) }
                state.programs.forEach { Pill(it.name, icon = Icons.AutoMirrored.Rounded.EventNote) }
            }
        }
        state.compatibility?.missingEquipment?.takeIf { it.isNotEmpty() }?.let { missing ->
            Spacer(Modifier.height(8.dp))
            Text(
                "Matériel manquant ici : ${missing.joinToString { it.label }}",
                style = MaterialTheme.typography.bodySmall,
                color = GymPulseTheme.extendedColors.warning,
            )
        }
    }
}

@Composable
private fun HeaderStat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
