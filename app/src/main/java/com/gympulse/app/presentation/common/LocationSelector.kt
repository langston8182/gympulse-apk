package com.gympulse.app.presentation.common

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gympulse.app.core.designsystem.components.IconBadge
import com.gympulse.app.core.designsystem.components.ListRow
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.domain.model.DetectionStatus
import com.gympulse.app.domain.model.LocationContext
import com.gympulse.app.domain.model.LocationSource
import com.gympulse.app.domain.model.LocationType
import com.gympulse.app.domain.model.TrainingLocation
import com.gympulse.app.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationSelectorViewModel @Inject constructor(
    private val repository: LocationRepository,
) : ViewModel() {
    val context: StateFlow<LocationContext> = repository.context
    val locations: StateFlow<List<TrainingLocation>> =
        repository.observeLocations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun hasPermission() = repository.hasLocationPermission()

    fun refresh() {
        viewModelScope.launch { repository.refreshDetection() }
    }

    fun select(id: Long) {
        viewModelScope.launch { repository.selectManually(id) }
    }

    fun auto() {
        viewModelScope.launch { repository.enableAutoDetection() }
    }
}

fun LocationType.icon(): ImageVector = when (this) {
    LocationType.HOME -> Icons.Rounded.Home
    LocationType.GYM -> Icons.Rounded.FitnessCenter
    LocationType.WORK -> Icons.Rounded.Business
    LocationType.OTHER -> Icons.Rounded.Apartment
}

fun LocationContext.summary(): String = when {
    location != null && source == LocationSource.MANUAL -> "${location.name} · choisi"
    location != null -> "${location.name} · détecté"
    status == DetectionStatus.DETECTING -> "Détection du lieu…"
    status == DetectionStatus.PERMISSION_DENIED -> "Lieu non détecté"
    status == DetectionStatus.NO_MATCH -> "Lieu inconnu"
    status == DetectionStatus.UNAVAILABLE -> "Position indisponible"
    else -> "Choisir un lieu"
}

/**
 * Chip showing where the user trains; opens a sheet to pick a place manually or re-enable
 * automatic detection (asking for the location permission when needed). Never blocks a workout.
 */
@Composable
fun LocationSelectorChip(
    onManageLocations: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocationSelectorViewModel = hiltViewModel(),
) {
    val context by viewModel.context.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.auto()
    }

    Surface(
        onClick = { showSheet = true },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (context.status == DetectionStatus.DETECTING && context.location == null) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    context.location?.type?.icon() ?: Icons.Rounded.Place,
                    contentDescription = null,
                    tint = if (context.location != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(context.summary(), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Icon(Icons.Rounded.ExpandMore, contentDescription = "Changer de lieu", modifier = Modifier.size(18.dp))
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                Text("Où vous entraînez-vous ?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    "Les séances proposées s'adaptent au matériel disponible.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.height(12.dp))
                ListRow(
                    title = "Détection automatique",
                    subtitle = when {
                        !viewModel.hasPermission() -> "Autoriser l'accès à la position"
                        context.source == LocationSource.AUTO && context.distanceMeters != null && context.location != null ->
                            "À ${Formatters.distance(context.distanceMeters!!)} de ${context.location!!.name}"
                        else -> "Position lue à l'ouverture, sans suivi en arrière-plan"
                    },
                    leading = { IconBadge(Icons.Rounded.MyLocation) },
                    trailing = {
                        if (context.source != LocationSource.MANUAL) {
                            Icon(Icons.Rounded.Check, contentDescription = "Actif", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    onClick = {
                        if (viewModel.hasPermission()) {
                            viewModel.auto()
                        } else {
                            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                        showSheet = false
                    },
                )
                locations.forEach { location ->
                    val selected = context.source == LocationSource.MANUAL && context.location?.id == location.id
                    ListRow(
                        title = location.name,
                        subtitle = "${location.availableEquipment.size} équipements",
                        leading = { IconBadge(location.type.icon(), tint = MaterialTheme.colorScheme.secondary) },
                        trailing = {
                            if (selected) Icon(Icons.Rounded.Check, contentDescription = "Sélectionné", tint = MaterialTheme.colorScheme.primary)
                        },
                        onClick = {
                            viewModel.select(location.id)
                            showSheet = false
                        },
                    )
                }
                if (locations.isEmpty()) {
                    ListRow(
                        title = "Aucun lieu enregistré",
                        subtitle = "Ajoutez votre salle ou votre domicile",
                        leading = { IconBadge(Icons.Rounded.LocationOff, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                }
                ListRow(
                    title = "Gérer les lieux",
                    leading = { IconBadge(Icons.Rounded.Settings, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {
                        showSheet = false
                        onManageLocations()
                    },
                )
            }
        }
    }
}
