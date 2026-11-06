package com.gympulse.app.presentation.exercises

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympulse.app.core.designsystem.components.ConfirmDialog
import com.gympulse.app.core.designsystem.components.EvoTopBar
import com.gympulse.app.core.designsystem.components.ExerciseImage
import com.gympulse.app.core.designsystem.components.ExerciseImagePresets
import com.gympulse.app.core.designsystem.components.LoadingState
import com.gympulse.app.core.designsystem.components.LocalSnackbarHostState
import com.gympulse.app.core.designsystem.components.PillSelector
import com.gympulse.app.core.designsystem.components.PresetArtwork
import com.gympulse.app.core.designsystem.components.SectionHeader
import com.gympulse.app.core.util.label
import com.gympulse.app.domain.model.Equipment
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.LoadMode
import com.gympulse.app.domain.model.MuscleGroup
import kotlinx.coroutines.launch

@Composable
fun ExerciseEditScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onDeleted: () -> Unit,
    viewModel: ExerciseEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.pickImage(uri)
    }
    val requestBack = { if (state.hasChanges) confirmDiscard = true else onBack() }
    BackHandler(enabled = state.hasChanges) { confirmDiscard = true }

    Scaffold(
        topBar = {
            EvoTopBar(
                title = if (state.isNew) "Nouvel exercice" else "Modifier l'exercice",
                onBack = requestBack,
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Supprimer l'exercice") }
                    }
                    TextButton(onClick = {
                        viewModel.save { id ->
                            scope.launch { snackbar.showSnackbar("Exercice enregistré") }
                            onSaved(id)
                        }
                    }, enabled = !state.saving) { Text("Enregistrer") }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.notFound -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cet exercice n'existe plus.")
            }
            else -> {
                val form = state.form
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    ExerciseImage(
                        imageUri = form.previewUri,
                        size = null,
                        shape = RoundedCornerShape(28.dp),
                        contentDescription = "Image de l'exercice",
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Rounded.PhotoLibrary, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Choisir une photo")
                        }
                        Text("ou une illustration :", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(ExerciseImagePresets.all, key = { it.key }) { preset ->
                            val selected = form.pickedImage == null && form.imageUri == preset.uri
                            Box(
                                Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(18.dp))
                                    .semantics {
                                        contentDescription = "Illustration ${preset.label}"
                                        this.selected = selected
                                    },
                            ) {
                                Surface(onClick = { viewModel.selectPreset(preset.uri) }, shape = RoundedCornerShape(18.dp)) {
                                    PresetArtwork(preset, Modifier.fillMaxSize())
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        null,
                                        tint = Color.White,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = form.name,
                        onValueChange = viewModel::setName,
                        label = { Text("Nom de l'exercice") },
                        isError = state.nameError,
                        supportingText = if (state.nameError) ({ Text("Le nom est obligatoire") }) else null,
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader("Type")
                        PillSelector(
                            options = ExerciseType.entries,
                            selected = form.type,
                            onSelect = viewModel::setType,
                            label = { if (it == ExerciseType.REPETITIONS) "Répétitions (charge × reps)" else "Durée (chronométré)" },
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader("Groupes musculaires", subtitle = "Le premier sélectionné est le muscle principal")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MuscleGroup.entries.forEach { muscle ->
                                val index = form.muscles.indexOf(muscle)
                                FilterChip(
                                    selected = index >= 0,
                                    onClick = { viewModel.toggleMuscle(muscle) },
                                    label = { Text(if (index == 0) "${muscle.label} · principal" else muscle.label) },
                                    leadingIcon = if (index >= 0) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader("Matériel")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Equipment.entries.forEach { equipment ->
                                val selected = equipment in form.equipment
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.toggleEquipment(equipment) },
                                    label = { Text(equipment.label) },
                                    leadingIcon = if (selected) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                                )
                            }
                        }
                    }

                    if (form.supportsPerImplementLoad) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader(
                                "Convention de charge",
                                subtitle = "Avec « par haltère », vous saisissez le poids d'un haltère et le volume compte les deux.",
                            )
                            PillSelector(options = LoadMode.entries, selected = form.loadMode, onSelect = viewModel::setLoadMode, label = { it.label })
                        }
                    }

                    OutlinedTextField(
                        value = form.notes,
                        onValueChange = viewModel::setNotes,
                        label = { Text("Notes techniques") },
                        placeholder = { Text("Placement, tempo, consignes…") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    )

                    if (!state.isNew) {
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("Supprimer l'exercice") }
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
            message = buildString {
                if (state.sessionsUsing > 0) append("Cet exercice sera retiré de ${state.sessionsUsing} séance(s). ")
                append("Votre historique d'entraînement est conservé.")
            },
            confirmLabel = "Supprimer",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onDeleted)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
