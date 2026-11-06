package com.gympulse.app.presentation.programs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympulse.app.core.designsystem.components.ConfirmDialog
import com.gympulse.app.core.designsystem.components.EvoTopBar
import com.gympulse.app.core.designsystem.components.GoalBadge
import com.gympulse.app.core.designsystem.components.ListRow
import com.gympulse.app.core.designsystem.components.LoadingState
import com.gympulse.app.core.designsystem.components.LocalSnackbarHostState
import com.gympulse.app.core.designsystem.components.PillSelector
import com.gympulse.app.core.designsystem.components.SectionHeader
import com.gympulse.app.core.designsystem.components.dragHandle
import com.gympulse.app.core.designsystem.components.rememberReorderableListState
import com.gympulse.app.core.designsystem.components.reorderableItem
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.core.util.label
import com.gympulse.app.domain.model.TrainingGoal
import kotlinx.coroutines.launch

/** Number of LazyColumn items placed before the list of sessions. */
private const val HEADER_ITEMS = 4

@Composable
fun ProgramEditScreen(
    onBack: () -> Unit,
    onCreateSession: () -> Unit,
    viewModel: ProgramEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var confirmDiscard by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val requestBack = { if (state.hasChanges) confirmDiscard = true else onBack() }
    BackHandler(enabled = state.hasChanges) { confirmDiscard = true }

    val listState = rememberLazyListState()
    val sessionKeys = state.form.sessions.map { "s-${it.id}" }.toSet()
    val reorder = rememberReorderableListState(
        listState = listState,
        onMove = { from, to -> viewModel.moveSession(from - HEADER_ITEMS, to - HEADER_ITEMS) },
        canMoveOver = { key -> key in sessionKeys },
    )

    Scaffold(
        topBar = {
            EvoTopBar(
                title = if (state.isNew) "Nouveau programme" else "Modifier le programme",
                onBack = requestBack,
                actions = {
                    TextButton(enabled = !state.saving && !state.loading, onClick = {
                        viewModel.save {
                            scope.launch { snackbar.showSnackbar("Programme enregistré") }
                            onBack()
                        }
                    }) { Text("Enregistrer") }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.notFound -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Ce programme n'existe plus.") }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "fields") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.form.name,
                            onValueChange = viewModel::setName,
                            label = { Text("Nom du programme") },
                            placeholder = { Text("Ex. Programme Hypertrophie") },
                            isError = state.nameError,
                            supportingText = if (state.nameError) ({ Text("Le nom est obligatoire") }) else null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.form.description,
                            onValueChange = viewModel::setDescription,
                            label = { Text("Description (facultatif)") },
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item(key = "goal") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader("Objectif")
                        PillSelector(TrainingGoal.entries, state.form.goal, viewModel::setGoal, label = { it.label })
                    }
                }
                item(key = "locations") {
                    if (state.locations.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader("Lieux compatibles")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.locations.forEach { location ->
                                    FilterChip(
                                        selected = location.id in state.form.locationIds,
                                        onClick = { viewModel.toggleLocation(location.id) },
                                        label = { Text(location.name) },
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "sessions-header") {
                    SectionHeader(
                        "Séances (${state.form.sessions.size})",
                        subtitle = if (state.form.sessions.size > 1) "Faites glisser la poignée pour changer l'ordre du cycle" else null,
                    )
                }
                itemsIndexed(state.form.sessions, key = { _, session -> "s-${session.id}" }) { index, session ->
                    val key = "s-${session.id}"
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().reorderableItem(reorder, key),
                    ) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
                            Column(Modifier.weight(1f)) {
                                Text(session.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${session.exercises.size} exercices · ~${Formatters.duration(session.estimatedDurationSeconds * 1000L)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.moveSession(index, index - 1) }, enabled = index > 0) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Monter ${session.name}")
                            }
                            IconButton(onClick = { viewModel.moveSession(index, index + 1) }, enabled = index < state.form.sessions.lastIndex) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Descendre ${session.name}")
                            }
                            IconButton(onClick = { viewModel.removeSession(session.id) }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Retirer ${session.name}")
                            }
                            Icon(
                                Icons.Rounded.DragIndicator,
                                contentDescription = "Faire glisser ${session.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(44.dp).padding(10.dp).dragHandle(reorder, key),
                            )
                        }
                    }
                }
                item(key = "add") {
                    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ajouter des séances")
                    }
                }
            }
        }
    }

    if (showPicker) {
        var selected by remember { mutableStateOf(state.form.sessions.map { it.id }.toSet()) }
        ModalBottomSheet(onDismissRequest = { showPicker = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxHeight(0.85f)) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Séances du programme", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        showPicker = false
                        onCreateSession()
                    }) { Text("Nouvelle séance") }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(state.allSessions, key = { it.id }) { session ->
                        val checked = session.id in selected
                        ListRow(
                            title = session.name,
                            subtitle = "${session.exercises.size} exercices · ${session.totalSets} séries",
                            leading = { Checkbox(checked = checked, onCheckedChange = null) },
                            trailing = { GoalBadge(session.trainingGoal) },
                            onClick = { selected = if (checked) selected - session.id else selected + session.id },
                        )
                    }
                }
                Box(Modifier.navigationBarsPadding().padding(16.dp)) {
                    Button(
                        onClick = {
                            viewModel.setSelectedSessions(selected)
                            showPicker = false
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Valider (${selected.size})") }
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Abandonner les modifications ?",
            message = "Les changements non enregistrés seront perdus.",
            confirmLabel = "Abandonner",
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                onBack()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}
