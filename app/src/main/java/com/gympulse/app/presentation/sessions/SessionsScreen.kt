package com.gympulse.app.presentation.sessions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.gympulse.app.core.designsystem.components.dragHandle
import com.gympulse.app.core.designsystem.components.rememberReorderableListState
import com.gympulse.app.core.designsystem.components.reorderableItem
import com.gympulse.app.domain.model.WorkoutSession
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.GoalBadge
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympulse.app.core.designsystem.components.EmptyState
import com.gympulse.app.core.designsystem.components.LoadingState
import com.gympulse.app.core.designsystem.components.PillSelector
import com.gympulse.app.core.designsystem.components.SectionHeader
import com.gympulse.app.domain.location.Compatibility
import com.gympulse.app.presentation.common.LocationSelectorChip
import com.gympulse.app.presentation.common.SessionCard
import com.gympulse.app.presentation.exercises.ExerciseLibraryContent
import com.gympulse.app.presentation.exercises.ExerciseLibraryViewModel

@Composable
fun SessionsScreen(
    onOpenSession: (Long) -> Unit,
    onCreateSession: () -> Unit,
    onStartWorkout: (Long) -> Unit,
    onOpenExercise: (Long) -> Unit,
    onCreateExercise: () -> Unit,
    onManageLocations: () -> Unit,
    viewModel: SessionsViewModel = hiltViewModel(),
    libraryViewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "fab") { current ->
                ExtendedFloatingActionButton(
                    onClick = if (current == 0) onCreateSession else onCreateExercise,
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text(if (current == 0) "Nouvelle séance" else "Nouvel exercice") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Séances", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    LocationSelectorChip(onManageLocations = onManageLocations)
                }
            }
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background, modifier = Modifier.padding(top = 8.dp)) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Mes séances (${state.totalCount})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Exercices (${libraryState.totalCount})") })
            }
            Spacer(Modifier.height(12.dp))
            if (tab == 0) {
                SessionsList(
                    state = state,
                    onFilter = viewModel::setFilter,
                    onOpenSession = onOpenSession,
                    onStartWorkout = onStartWorkout,
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onCreateSession = onCreateSession,
                    onReorder = viewModel::setReordering,
                    onSaveOrder = viewModel::saveOrder,
                )
            } else {
                ExerciseLibraryContent(
                    state = libraryState,
                    viewModel = libraryViewModel,
                    onExerciseClick = { onOpenExercise(it.id) },
                    onCreateExercise = onCreateExercise,
                )
            }
        }
    }
}

@Composable
private fun SessionsList(
    state: SessionsState,
    onFilter: (SessionFilter) -> Unit,
    onOpenSession: (Long) -> Unit,
    onStartWorkout: (Long) -> Unit,
    onToggleFavorite: (com.gympulse.app.domain.model.WorkoutSession) -> Unit,
    onCreateSession: () -> Unit,
    onReorder: (Boolean) -> Unit,
    onSaveOrder: (List<Long>) -> Unit,
) {
    when {
        state.loading -> LoadingState()
        state.reordering -> ReorderList(state, onDone = { onReorder(false) }, onSaveOrder = onSaveOrder)
        state.totalCount == 0 -> EmptyState(
            icon = Icons.AutoMirrored.Rounded.EventNote,
            title = "Aucune séance",
            message = "Composez votre première séance : exercices, séries, charges et temps de repos.",
            actionLabel = "Créer une séance",
            onAction = onCreateSession,
        )
        else -> LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "filters") {
                PillSelector(options = SessionFilter.entries, selected = state.filter, onSelect = onFilter, label = { it.label })
            }
            if (state.totalCount > 1) {
                item(key = "reorder") {
                    TextButton(onClick = { onReorder(true) }) {
                        Icon(Icons.Rounded.SwapVert, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Réorganiser mes séances")
                    }
                }
            }
            if (state.recommended.isEmpty() && state.others.isEmpty()) {
                item(key = "empty-filter") {
                    EmptyState(
                        icon = Icons.Rounded.FilterAltOff,
                        title = "Aucune séance pour ce filtre",
                        message = "Essayez un autre filtre.",
                        actionLabel = "Toutes les séances",
                        onAction = { onFilter(SessionFilter.ALL) },
                    )
                }
            }
            if (state.recommended.isNotEmpty()) {
                item(key = "recommended-header") {
                    SectionHeader(
                        "Recommandées ici",
                        subtitle = "Réalisables avec le matériel de « ${state.location.location?.name} »",
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(state.recommended, key = { "r-${it.session.id}" }) { item ->
                    SessionCard(
                        session = item.session,
                        onClick = { onOpenSession(item.session.id) },
                        compatibility = item.compatibility.compatibility,
                        lastPerformedAt = item.lastPerformedAt,
                        onStart = { onStartWorkout(item.session.id) },
                        onToggleFavorite = { onToggleFavorite(item.session) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (state.others.isNotEmpty()) {
                item(key = "others-header") {
                    SectionHeader(
                        if (state.recommended.isEmpty()) "Toutes les séances" else "Autres séances",
                        subtitle = if (state.location.location != null) "Du matériel peut manquer à cet endroit" else null,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(state.others, key = { "o-${it.session.id}" }) { item ->
                    SessionCard(
                        session = item.session,
                        onClick = { onOpenSession(item.session.id) },
                        compatibility = item.compatibility.compatibility.takeIf { it == Compatibility.INCOMPATIBLE },
                        lastPerformedAt = item.lastPerformedAt,
                        onStart = { onStartWorkout(item.session.id) },
                        onToggleFavorite = { onToggleFavorite(item.session) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            item(key = "spacer") { Spacer(Modifier.fillMaxWidth().height(8.dp)) }
        }
    }
}

/**
 * Flat list of every session in the user's order. Drag the handle to move a session; the new
 * order is saved when the finger is released. Screen readers get "move up / down" actions.
 */
@Composable
private fun ReorderList(state: SessionsState, onDone: () -> Unit, onSaveOrder: (List<Long>) -> Unit) {
    val order = remember(state.ordered) { mutableStateListOf<WorkoutSession>().apply { addAll(state.ordered) } }
    val listState = rememberLazyListState()
    fun move(from: Int, to: Int) {
        if (from in order.indices && to in order.indices) order.add(to, order.removeAt(from))
    }
    // Index 0 of the lazy list is the header.
    val reorder = rememberReorderableListState(
        listState,
        onMove = { from, to -> move(from - 1, to - 1) },
        canMoveOver = { key -> key != HEADER_KEY },
        onDragFinished = { onSaveOrder(order.map { it.id }) },
    )
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = HEADER_KEY) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ordre des séances", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Maintenez la poignée et glissez. Cet ordre est repris sur l'accueil.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onDone) { Text("Terminé") }
            }
        }
        itemsIndexed(order, key = { _, session -> session.id }) { index, session ->
            EvoCard(
                modifier = Modifier
                    .reorderableItem(reorder, session.id)
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("Monter") { move(index, index - 1).also { onSaveOrder(order.map { s -> s.id }) }; index > 0 },
                            CustomAccessibilityAction("Descendre") { move(index, index + 1).also { onSaveOrder(order.map { s -> s.id }) }; index < order.lastIndex },
                        )
                    },
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(session.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        GoalBadge(session.trainingGoal)
                    }
                    Icon(
                        Icons.Rounded.DragHandle,
                        contentDescription = "Déplacer ${session.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp).padding(12.dp).dragHandle(reorder, session.id),
                    )
                }
            }
        }
    }
}

private const val HEADER_KEY = "reorder-header"
