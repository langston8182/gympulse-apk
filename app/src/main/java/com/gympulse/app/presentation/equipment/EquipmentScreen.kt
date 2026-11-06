package com.gympulse.app.presentation.equipment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gympulse.app.core.designsystem.components.EmptyState
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.EvoTopBar
import com.gympulse.app.core.designsystem.components.IconBadge
import com.gympulse.app.core.designsystem.components.LoadingState
import com.gympulse.app.core.designsystem.components.rememberEvoHaptics
import com.gympulse.app.core.util.label
import com.gympulse.app.domain.model.Equipment
import com.gympulse.app.domain.model.TrainingLocation
import com.gympulse.app.domain.repository.ExerciseRepository
import com.gympulse.app.domain.repository.LocationRepository
import com.gympulse.app.presentation.common.icon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EquipmentRow(val equipment: Equipment, val exerciseCount: Int)

data class EquipmentState(
    val loading: Boolean = true,
    val locations: List<TrainingLocation> = emptyList(),
    val rows: List<EquipmentRow> = emptyList(),
)

@HiltViewModel
class EquipmentViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    exerciseRepository: ExerciseRepository,
) : ViewModel() {
    val state: StateFlow<EquipmentState> = combine(locationRepository.observeLocations(), exerciseRepository.observeExercises()) { locations, exercises ->
        EquipmentState(
            loading = false,
            locations = locations,
            rows = Equipment.entries.filterNot { it.isUniversal }.map { equipment -> EquipmentRow(equipment, exercises.count { equipment in it.equipment }) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EquipmentState())

    fun toggle(location: TrainingLocation, equipment: Equipment) {
        val updated = if (equipment in location.availableEquipment) location.availableEquipment - equipment else location.availableEquipment + equipment
        viewModelScope.launch { locationRepository.saveLocation(location.copy(availableEquipment = updated)) }
    }
}

@Composable
fun EquipmentScreen(
    onBack: () -> Unit,
    onOpenLocation: (Long) -> Unit,
    viewModel: EquipmentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberEvoHaptics()
    Scaffold(topBar = { EvoTopBar("Matériel", onBack = onBack) }) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.locations.isEmpty() -> EmptyState(
                icon = Icons.Rounded.LocationOff,
                title = "Aucun lieu",
                message = "Le matériel est associé à vos lieux d'entraînement. Ajoutez d'abord un lieu depuis le profil.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "intro") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Indiquez le matériel disponible dans chaque lieu : GymPulse propose les séances réalisables là où vous êtes. Le poids du corps est toujours disponible.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.locations, key = { it.id }) { location ->
                                AssistChip(
                                    onClick = { onOpenLocation(location.id) },
                                    label = { Text("${location.name} · ${location.availableEquipment.size}") },
                                    leadingIcon = { Icon(location.type.icon(), null, Modifier.size(18.dp)) },
                                    trailingIcon = { Icon(Icons.Rounded.Edit, contentDescription = "Modifier ${location.name}", modifier = Modifier.size(16.dp)) },
                                )
                            }
                        }
                    }
                }
                items(state.rows, key = { it.equipment.name }) { row ->
                    EvoCard(contentPadding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.FitnessCenter, size = 36.dp)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(row.equipment.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Utilisé par ${row.exerciseCount} exercice(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.locations.forEach { location ->
                                val available = row.equipment in location.availableEquipment
                                FilterChip(
                                    selected = available,
                                    onClick = {
                                        haptics.toggle(!available)
                                        viewModel.toggle(location, row.equipment)
                                    },
                                    label = { Text(location.name) },
                                    leadingIcon = if (available) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
