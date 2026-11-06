package com.gympulse.app.presentation.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gympulse.app.domain.model.TrainingSplit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympulse.app.core.designsystem.components.EmptyState
import com.gympulse.app.core.designsystem.components.EvoTopBar
import com.gympulse.app.core.designsystem.components.ExerciseImage
import com.gympulse.app.core.designsystem.components.LoadingState
import com.gympulse.app.core.designsystem.components.LocalWeightUnit
import com.gympulse.app.core.designsystem.components.SearchField
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.core.util.label
import com.gympulse.app.domain.model.Exercise
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.presentation.common.SetFormat

@Composable
fun ExerciseLibraryScreen(
    onBack: () -> Unit,
    onOpenExercise: (Long) -> Unit,
    onCreateExercise: () -> Unit,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { EvoTopBar("Bibliothèque d'exercices", onBack = onBack, subtitle = "${state.totalCount} exercices") },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateExercise,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Nouvel exercice") },
            )
        },
    ) { padding ->
        ExerciseLibraryContent(
            state = state,
            viewModel = viewModel,
            onExerciseClick = { onOpenExercise(it.id) },
            onCreateExercise = onCreateExercise,
            modifier = Modifier.padding(padding),
        )
    }
}

/** Search, filters and list of exercises; reused by the sessions tab. */
@Composable
fun ExerciseLibraryContent(
    state: ExerciseLibraryState,
    viewModel: ExerciseLibraryViewModel,
    onExerciseClick: (Exercise) -> Unit,
    onCreateExercise: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
    trailing: (@Composable (Exercise) -> Unit)? = null,
) {
    Column(modifier.fillMaxSize()) {
        ExerciseFilters(state, viewModel, Modifier.padding(horizontal = 20.dp))
        when {
            state.loading -> LoadingState()
            state.items.isEmpty() && state.totalCount == 0 -> EmptyState(
                icon = Icons.Rounded.FitnessCenter,
                title = "Aucun exercice",
                message = "Créez vos exercices pour composer vos séances.",
                actionLabel = "Créer un exercice",
                onAction = onCreateExercise,
            )
            state.items.isEmpty() -> EmptyState(
                icon = Icons.Rounded.SearchOff,
                title = "Aucun résultat",
                message = "Aucun exercice ne correspond à ces filtres.",
                actionLabel = "Réinitialiser les filtres",
                onAction = viewModel::clearFilters,
            )
            else -> LazyColumn(
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.items, key = { it.exercise.id }) { item ->
                    ExerciseRow(
                        item = item,
                        onClick = { onExerciseClick(item.exercise) },
                        trailing = trailing?.let { { it(item.exercise) } },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseFilters(state: ExerciseLibraryState, viewModel: ExerciseLibraryViewModel, modifier: Modifier) {
    var equipmentMenu by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchField(query = state.filter.query, onQueryChange = viewModel::setQuery, placeholder = "Rechercher un exercice")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            item {
                Box {
                    FilterChip(
                        selected = state.filter.equipment != null,
                        onClick = { equipmentMenu = true },
                        label = { Text(state.filter.equipment?.label ?: "Matériel") },
                        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenu(expanded = equipmentMenu, onDismissRequest = { equipmentMenu = false }) {
                        DropdownMenuItem(text = { Text("Tout le matériel") }, onClick = {
                            viewModel.setEquipment(null)
                            equipmentMenu = false
                        })
                        state.equipment.forEach { equipment ->
                            DropdownMenuItem(text = { Text(equipment.label) }, onClick = {
                                viewModel.setEquipment(equipment)
                                equipmentMenu = false
                            })
                        }
                    }
                }
            }
            item {
                FilterChip(
                    selected = state.filter.timedOnly,
                    onClick = { viewModel.setTimedOnly(!state.filter.timedOnly) },
                    label = { Text("Chronométrés") },
                    leadingIcon = { Icon(Icons.Rounded.Timer, null, Modifier.size(FilterChipDefaults.IconSize)) },
                )
            }
            items(TrainingSplit.entries, key = { it.name }) { split ->
                val selected = state.filter.split == split
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setSplit(if (selected) null else split) },
                    label = { Text(split.label) },
                )
            }
        }
    }
}

@Composable
internal fun ExerciseRow(
    item: ExerciseListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val exercise = item.exercise
    val unit = LocalWeightUnit.current
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ExerciseImage(exercise.imageUri, size = 56.dp, contentDescription = null)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(exercise.muscleGroups.take(2).joinToString { it.label }.ifEmpty { "Sans groupe musculaire" })
                        if (exercise.type == ExerciseType.DURATION) append(" · Durée")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.lastPerformance?.let { last ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${Formatters.relativeDay(last.date)} · ${SetFormat.performedSummary(last.sets, last.type, unit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}
