package com.gympulse.app.presentation.locations

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.rounded.Map
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympulse.app.core.designsystem.components.ConfirmDialog
import com.gympulse.app.core.designsystem.components.DemoBadge
import com.gympulse.app.core.designsystem.components.EmptyState
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.EvoTopBar
import com.gympulse.app.core.designsystem.components.IconBadge
import com.gympulse.app.core.designsystem.components.LoadingState
import com.gympulse.app.core.designsystem.components.LocalSnackbarHostState
import com.gympulse.app.core.designsystem.components.Pill
import com.gympulse.app.core.designsystem.components.PillSelector
import com.gympulse.app.core.designsystem.components.SectionHeader
import com.gympulse.app.core.designsystem.theme.GymPulseTheme
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.core.util.label
import com.gympulse.app.domain.model.Equipment
import com.gympulse.app.domain.model.LocationSource
import com.gympulse.app.domain.model.LocationType
import com.gympulse.app.presentation.common.icon
import com.gympulse.app.presentation.common.summary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val locationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@Composable
fun LocationsScreen(
    onBack: () -> Unit,
    onOpenLocation: (Long) -> Unit,
    onCreateLocation: () -> Unit,
    viewModel: LocationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refresh() }
    Scaffold(
        topBar = { EvoTopBar("Lieux d'entraînement", onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreateLocation, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Ajouter un lieu") })
        },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "info") {
                EvoCard(contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.MyLocation, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(state.context.summary(), style = MaterialTheme.typography.titleSmall)
                            state.context.distanceMeters?.takeIf { state.context.source == LocationSource.AUTO }?.let {
                                Text("À ${Formatters.distance(it)} du point enregistré", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = {
                            if (viewModel.hasPermission()) viewModel.refresh() else permission.launch(locationPermissions)
                        }) { Text("Détecter") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.PrivacyTip, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "La position est lue uniquement à l'ouverture de l'app et des séances, jamais en arrière-plan. Refuser la localisation ne bloque aucun entraînement : vous pouvez choisir le lieu manuellement.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.items.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.LocationOff,
                        title = "Aucun lieu",
                        message = "Ajoutez votre salle, votre domicile ou votre bureau avec le matériel disponible.",
                        actionLabel = "Ajouter un lieu",
                        onAction = onCreateLocation,
                    )
                }
            }
            items(state.items, key = { it.location.id }) { item ->
                val location = item.location
                val here = state.context.location?.id == location.id
                EvoCard(onClick = { onOpenLocation(location.id) }, contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(location.type.icon(), tint = if (here) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, size = 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(location.name, style = MaterialTheme.typography.titleMedium)
                                if (location.isDemo) DemoBadge()
                            }
                            if (location.address.isNotBlank()) {
                                Text(
                                    location.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                "${location.type.label} · ${location.availableEquipment.size} équipements · ${item.linkedSessions} séance(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (location.hasCoordinates) "Position enregistrée · rayon ${location.radiusMeters} m" else "Pas de position : sélection manuelle uniquement",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (location.hasCoordinates) MaterialTheme.colorScheme.onSurfaceVariant else GymPulseTheme.extendedColors.warning,
                            )
                        }
                        if (here) Pill("Vous êtes ici", containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), contentColor = MaterialTheme.colorScheme.primary)
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun LocationEditScreen(onBack: () -> Unit, viewModel: LocationEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) viewModel.useCurrentPosition()
        else scope.launch { snackbar.showSnackbar("Autorisation de localisation refusée : saisissez les coordonnées ou laissez-les vides.") }
    }
    val requestBack = { if (state.hasChanges) confirmDiscard = true else onBack() }
    BackHandler(enabled = state.hasChanges) { confirmDiscard = true }

    Scaffold(
        topBar = {
            EvoTopBar(
                title = if (state.isNew) "Nouveau lieu" else "Modifier le lieu",
                onBack = requestBack,
                actions = {
                    if (!state.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Supprimer le lieu") }
                    TextButton(enabled = !state.saving && state.canSave && !state.loading, onClick = {
                        viewModel.save {
                            scope.launch { snackbar.showSnackbar("Lieu enregistré") }
                            onBack()
                        }
                    }) { Text("Enregistrer") }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.notFound -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Ce lieu n'existe plus.") }
            else -> {
                val form = state.form
                Column(
                    Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    OutlinedTextField(
                        value = form.name,
                        onValueChange = viewModel::setName,
                        label = { Text("Nom du lieu") },
                        placeholder = { Text("Ex. Salle du centre-ville") },
                        isError = state.nameError,
                        supportingText = if (state.nameError) ({ Text("Le nom est obligatoire") }) else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader("Type")
                        PillSelector(LocationType.entries, form.type, viewModel::setType, label = { it.label })
                    }
                    EvoCard(contentPadding = PaddingValues(16.dp)) {
                        Text("Adresse", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Facultative. « Rechercher l'adresse » en déduit la position ci-dessous (réseau requis). « Voir sur la carte » l'ouvre dans votre application de cartes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = form.address,
                            onValueChange = viewModel::setAddress,
                            label = { Text("Adresse") },
                            placeholder = { Text("Ex. 10 rue de Rivoli, 75004 Paris") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { viewModel.findAddress() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = viewModel::findAddress,
                            enabled = form.address.isNotBlank() && state.addressStatus != PositionStatus.LOADING,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            if (state.addressStatus == PositionStatus.LOADING) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Search, null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Rechercher l'adresse")
                        }
                        when (state.addressStatus) {
                            PositionStatus.SUCCESS -> Text(
                                "Adresse trouvée : latitude et longitude renseignées.",
                                style = MaterialTheme.typography.bodySmall,
                                color = GymPulseTheme.extendedColors.success,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            PositionStatus.FAILED -> Text(
                                "Adresse introuvable ou service indisponible. Précisez l'adresse ou utilisez votre position.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            else -> Unit
                        }
                    }
                    EvoCard(contentPadding = PaddingValues(16.dp)) {
                        Text("Position", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Utilisée pour reconnaître automatiquement le lieu. Facultative.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = { if (viewModel.hasPermission()) viewModel.useCurrentPosition() else permission.launch(locationPermissions) },
                            enabled = state.positionStatus != PositionStatus.LOADING,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            if (state.positionStatus == PositionStatus.LOADING) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.GpsFixed, null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Utiliser ma position actuelle")
                        }
                        when (state.positionStatus) {
                            PositionStatus.SUCCESS -> Text(
                                "Position récupérée" + (state.accuracyMeters?.let { " (précision ± ${it.roundToInt()} m)" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = GymPulseTheme.extendedColors.success,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            PositionStatus.FAILED -> Text(
                                "Position indisponible. Vérifiez que la localisation est activée, ou saisissez les coordonnées.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            else -> Unit
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = form.latitude,
                                onValueChange = viewModel::setLatitude,
                                label = { Text("Latitude") },
                                isError = form.latitudeError || (form.coordinatesIncomplete && form.latitude.isBlank()),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = form.longitude,
                                onValueChange = viewModel::setLongitude,
                                label = { Text("Longitude") },
                                isError = form.longitudeError || (form.coordinatesIncomplete && form.longitude.isBlank()),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (form.latitude.isNotBlank() || form.longitude.isNotBlank()) {
                            val context = LocalContext.current
                            val latitude = form.latitudeValue
                            val longitude = form.longitudeValue
                            if (latitude != null && longitude != null && !form.latitudeError && !form.longitudeError) {
                                OutlinedButton(
                                    onClick = {
                                        val label = Uri.encode(form.name.ifBlank { "Lieu" })
                                        val geo = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($label)")
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geo)) }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                ) {
                                    Icon(Icons.Rounded.Map, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Voir sur la carte")
                                }
                            }
                            TextButton(onClick = viewModel::clearCoordinates) { Text("Effacer la position") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Rayon de détection : ${form.radiusMeters} m", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = form.radiusMeters.toFloat(),
                            onValueChange = { viewModel.setRadius((it / 50).roundToInt() * 50) },
                            valueRange = 50f..1000f,
                            steps = 18,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader("Matériel disponible", subtitle = "Les séances demandant du matériel absent seront signalées")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Equipment.entries.forEach { equipment ->
                                val selected = equipment in form.equipment || equipment.isUniversal
                                FilterChip(
                                    selected = selected,
                                    enabled = !equipment.isUniversal,
                                    onClick = { viewModel.toggleEquipment(equipment) },
                                    label = { Text(equipment.label) },
                                    leadingIcon = if (selected) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                                )
                            }
                        }
                    }
                    if (state.sessions.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader("Séances associées", subtitle = "Proposées en priorité dans ce lieu")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.sessions.forEach { session ->
                                    FilterChip(
                                        selected = session.id in form.sessionIds,
                                        onClick = { viewModel.toggleSession(session.id) },
                                        label = { Text(session.name) },
                                    )
                                }
                            }
                        }
                    }
                    if (state.programs.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader("Programmes associés")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.programs.forEach { program ->
                                    FilterChip(
                                        selected = program.id in form.programIds,
                                        onClick = { viewModel.toggleProgram(program.id) },
                                        label = { Text(program.name) },
                                    )
                                }
                            }
                        }
                    }
                    if (!state.isNew) {
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("Supprimer le lieu") }
                    }
                    Spacer(Modifier.height(24.dp))
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
    if (confirmDelete) {
        ConfirmDialog(
            title = "Supprimer « ${state.form.name} » ?",
            message = "Les séances et programmes ne seront plus associés à ce lieu. Votre historique est conservé.",
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
