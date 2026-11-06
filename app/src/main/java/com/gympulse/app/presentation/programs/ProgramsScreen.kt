package com.gympulse.app.presentation.programs

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympulse.app.core.designsystem.components.BigActionButton
import com.gympulse.app.core.designsystem.components.ConfirmDialog
import com.gympulse.app.core.designsystem.components.DemoBadge
import com.gympulse.app.core.designsystem.components.EmptyState
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.EvoTopBar
import com.gympulse.app.core.designsystem.components.GoalBadge
import com.gympulse.app.core.designsystem.components.GradientCard
import com.gympulse.app.core.designsystem.components.LoadingState
import com.gympulse.app.core.designsystem.components.Pill
import com.gympulse.app.core.designsystem.components.SectionHeader
import com.gympulse.app.core.designsystem.components.goalColor
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.presentation.common.SessionCard

@Composable
fun ProgramsScreen(
    onOpenProgram: (Long) -> Unit,
    onCreateProgram: () -> Unit,
    onStartWorkout: (Long) -> Unit,
    viewModel: ProgramsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreateProgram, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Nouveau programme") })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                Text("Programmes", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Enchaînez vos séances selon un cycle : GymPulse propose toujours la suivante.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                state.loading -> LoadingState()
                state.items.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.CalendarMonth,
                    title = "Aucun programme",
                    message = "Regroupez plusieurs séances (par exemple Push, Pull, Legs) dans un programme.",
                    actionLabel = "Créer un programme",
                    onAction = onCreateProgram,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.items, key = { it.program.id }) { item ->
                        ProgramCard(item, onOpen = { onOpenProgram(item.program.id) }, onStart = onStartWorkout, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramCard(item: ProgramItem, onOpen: () -> Unit, onStart: (Long) -> Unit, modifier: Modifier) {
    val program = item.program
    val accent = goalColor(program.goal)
    GradientCard(modifier = modifier.fillMaxWidth(), onClick = onOpen, colors = listOf(accent.copy(alpha = 0.22f), MaterialTheme.colorScheme.surfaceContainer)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GoalBadge(program.goal)
            if (program.isDemo) DemoBadge()
        }
        Spacer(Modifier.height(10.dp))
        Text(program.name, style = MaterialTheme.typography.headlineSmall)
        if (program.description.isNotBlank()) {
            Text(program.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            program.sessions.forEachIndexed { index, session -> Pill("${index + 1}. ${session.name}") }
            item.locationNames.forEach { Pill(it, icon = Icons.Rounded.Place) }
        }
        item.nextSession?.let { next ->
            Spacer(Modifier.height(14.dp))
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Prochaine séance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(next.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        item.lastPerformedAt?.let {
                            Text("Dernière séance ${Formatters.relativeDay(it).lowercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FilledIconButton(
                        onClick = { onStart(next.id) },
                        enabled = next.exercises.isNotEmpty(),
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Démarrer ${next.name}") }
                }
            }
        }
    }
}

@Composable
fun ProgramDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onStartWorkout: (Long) -> Unit,
    viewModel: ProgramDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val program = state.program
    Scaffold(
        topBar = {
            EvoTopBar(
                title = "Programme",
                onBack = onBack,
                actions = {
                    if (program != null) {
                        IconButton(onClick = { onEdit(program.id) }) { Icon(Icons.Rounded.Edit, contentDescription = "Modifier le programme") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Supprimer le programme") }
                    }
                },
            )
        },
        bottomBar = {
            val next = state.nextSession
            if (next != null && next.exercises.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        BigActionButton("Démarrer · ${next.name}", onClick = { onStartWorkout(next.id) }, icon = Icons.Rounded.PlayArrow)
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            program == null -> EmptyState(
                icon = Icons.Rounded.CalendarMonth,
                title = "Programme introuvable",
                message = "Il a peut-être été supprimé.",
                actionLabel = "Retour",
                onAction = onBack,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") {
                    GradientCard(Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GoalBadge(program.goal)
                            if (program.isDemo) DemoBadge()
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(program.name, style = MaterialTheme.typography.headlineMedium)
                        if (program.description.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(program.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column {
                                Text("${program.sessions.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                                Text("séances", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column {
                                Text(
                                    Formatters.duration(program.sessions.sumOf { it.estimatedDurationSeconds } * 1000L),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Text("par cycle", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (state.locations.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.locations.forEach { Pill(it.name, icon = Icons.Rounded.Place) }
                            }
                        }
                    }
                }
                item(key = "sessions-header") {
                    SectionHeader("Séances du cycle", subtitle = "Dans l'ordre d'enchaînement")
                }
                if (program.sessions.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Rounded.Schedule,
                            title = "Programme vide",
                            message = "Ajoutez des séances à ce programme.",
                            actionLabel = "Modifier",
                            onAction = { onEdit(program.id) },
                        )
                    }
                }
                itemsIndexed(program.sessions, key = { _, session -> session.id }) { index, session ->
                    val isNext = state.nextSession?.id == session.id
                    SessionCard(
                        session = session,
                        onClick = { onOpenSession(session.id) },
                        lastPerformedAt = state.lastPerformed[session.id],
                        onStart = { onStartWorkout(session.id) },
                        leadingLabel = "Jour ${index + 1}" + if (isNext) " · prochaine séance" else "",
                    )
                }
                item(key = "hint") {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.History, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "La prochaine séance suit la dernière séance du programme que vous avez réalisée.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    if (confirmDelete && program != null) {
        ConfirmDialog(
            title = "Supprimer « ${program.name} » ?",
            message = "Le programme sera supprimé. Ses séances et votre historique sont conservés.",
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
