package com.gympulse.app.presentation.sessions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympulse.app.core.designsystem.components.ConfirmDialog
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.EvoTopBar
import com.gympulse.app.core.designsystem.components.ExerciseImage
import com.gympulse.app.core.designsystem.components.LoadingState
import com.gympulse.app.core.designsystem.components.LocalSnackbarHostState
import com.gympulse.app.core.designsystem.components.NumberInputDialog
import com.gympulse.app.core.designsystem.components.PillSelector
import com.gympulse.app.core.designsystem.components.SectionHeader
import com.gympulse.app.core.designsystem.components.dragHandle
import com.gympulse.app.core.designsystem.components.rememberReorderableListState
import com.gympulse.app.core.designsystem.components.reorderableItem
import com.gympulse.app.core.designsystem.theme.TabularNumbers
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.core.util.label
import com.gympulse.app.domain.model.Exercise
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.LoadMode
import com.gympulse.app.domain.model.TrainingGoal
import com.gympulse.app.domain.model.WeightUnit
import com.gympulse.app.presentation.exercises.ExerciseLibraryContent
import com.gympulse.app.presentation.exercises.ExerciseLibraryViewModel
import kotlinx.coroutines.launch

private val restPresets = listOf(30, 60, 90, 120, 180, 240)

@Composable
fun SessionEditScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onCreateExercise: () -> Unit,
    viewModel: SessionEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var reorderMode by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val requestBack = { if (state.hasChanges) confirmDiscard = true else onBack() }
    BackHandler(enabled = state.hasChanges && !reorderMode) { confirmDiscard = true }
    BackHandler(enabled = reorderMode) { reorderMode = false }

    Scaffold(
        topBar = {
            EvoTopBar(
                title = state.title,
                onBack = requestBack,
                actions = {
                    TextButton(
                        enabled = !state.saving && !state.loading,
                        onClick = {
                            viewModel.save { id ->
                                scope.launch { snackbar.showSnackbar("Séance enregistrée") }
                                onSaved(id)
                            }
                        },
                    ) { Text("Enregistrer") }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.notFound -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cette séance n'existe plus.")
            }
            reorderMode -> ReorderList(
                state = state,
                onMove = viewModel::moveBlock,
                onDone = { reorderMode = false },
                modifier = Modifier.padding(padding),
            )
            else -> EditorList(
                state = state,
                viewModel = viewModel,
                onReorder = { reorderMode = true },
                onAddExercises = { showPicker = true },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            alreadyAdded = state.form.blocks.map { it.exercise.id }.toSet(),
            onDismiss = { showPicker = false },
            onConfirm = { exercises ->
                viewModel.addExercises(exercises)
                showPicker = false
            },
            onCreateExercise = {
                showPicker = false
                onCreateExercise()
            },
        )
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

@Composable
private fun EditorList(
    state: SessionEditState,
    viewModel: SessionEditViewModel,
    onReorder: () -> Unit,
    onAddExercises: () -> Unit,
    modifier: Modifier,
) {
    val form = state.form
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "name") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Nom de la séance") },
                    placeholder = { Text("Ex. Push — Hypertrophie") },
                    isError = state.nameError,
                    supportingText = if (state.nameError) ({ Text("Le nom est obligatoire") }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.description,
                    onValueChange = viewModel::setDescription,
                    label = { Text("Description (facultatif)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
            }
        }
        item(key = "goal") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Objectif")
                PillSelector(TrainingGoal.entries, form.goal, viewModel::setGoal, label = { it.label })
            }
        }
        if (state.locations.isNotEmpty()) {
            item(key = "locations") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Lieux compatibles", subtitle = "La séance sera proposée en priorité dans ces lieux")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.locations.forEach { location ->
                            FilterChip(
                                selected = location.id in form.locationIds,
                                onClick = { viewModel.toggleLocation(location.id) },
                                label = { Text(location.name) },
                            )
                        }
                    }
                }
            }
        }
        item(key = "exercises-header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("Exercices (${form.blocks.size})", modifier = Modifier.weight(1f))
                if (form.blocks.size > 1) {
                    TextButton(onClick = onReorder) {
                        Icon(Icons.Rounded.SwapVert, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Réorganiser")
                    }
                }
            }
        }
        itemsIndexed(form.blocks, key = { _, block -> block.uid }) { index, block ->
            ExerciseBlockCard(
                block = block,
                index = index,
                count = form.blocks.size,
                unit = state.unit,
                viewModel = viewModel,
                modifier = Modifier.animateItem(),
            )
        }
        item(key = "add") {
            OutlinedButton(onClick = onAddExercises, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Ajouter des exercices")
            }
        }
    }
}

@Composable
private fun ExerciseBlockCard(
    block: ExerciseBlock,
    index: Int,
    count: Int,
    unit: WeightUnit,
    viewModel: SessionEditViewModel,
    modifier: Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    var customRest by remember { mutableStateOf(false) }
    var setRestIndex by remember { mutableStateOf<Int?>(null) }
    val timed = block.exercise.type == ExerciseType.DURATION
    EvoCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExerciseImage(block.exercise.imageUri, size = 44.dp, shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(block.exercise.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (timed) "Exercice chronométré" else block.exercise.muscleGroups.take(2).joinToString { it.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Actions pour ${block.exercise.name}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (index > 0) {
                        DropdownMenuItem(text = { Text("Monter") }, leadingIcon = { Icon(Icons.Rounded.KeyboardArrowUp, null) }, onClick = {
                            menu = false
                            viewModel.moveBlock(index, index - 1)
                        })
                    }
                    if (index < count - 1) {
                        DropdownMenuItem(text = { Text("Descendre") }, leadingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null) }, onClick = {
                            menu = false
                            viewModel.moveBlock(index, index + 1)
                        })
                    }
                    DropdownMenuItem(
                        text = { Text("Retirer de la séance", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menu = false
                            viewModel.removeBlock(block.uid)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Repos après chaque série", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        val restOptions = (restPresets + block.restSeconds).distinct().sorted()
        PillSelector(
            options = restOptions + -1,
            selected = block.restSeconds,
            onSelect = { if (it == -1) customRest = true else viewModel.setRest(block.uid, it) },
            label = { if (it == -1) "Autre…" else Formatters.seconds(it) },
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderCell("Série", Modifier.width(44.dp))
            if (timed) {
                HeaderCell("Durée (s)", Modifier.weight(1f))
            } else {
                HeaderCell("Reps", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                HeaderCell("Charge (${unit.symbol}${if (block.exercise.loadMode == LoadMode.PER_IMPLEMENT) "/haltère" else ""})", Modifier.weight(1.3f))
            }
            Spacer(Modifier.width(8.dp))
            HeaderCell("Repos", Modifier.width(72.dp))
            Spacer(Modifier.width(40.dp))
        }
        block.sets.forEachIndexed { setIndex, row ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${setIndex + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(44.dp),
                    textAlign = TextAlign.Center,
                )
                if (timed) {
                    CompactNumberField(
                        value = row.duration,
                        onValueChange = { value -> viewModel.updateSet(block.uid, setIndex) { it.copy(duration = value.filter(Char::isDigit).take(4)) } },
                        placeholder = "45",
                        description = "Durée de la série ${setIndex + 1} en secondes",
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    CompactNumberField(
                        value = row.reps,
                        onValueChange = { value -> viewModel.updateSet(block.uid, setIndex) { it.copy(reps = value.filter(Char::isDigit).take(3)) } },
                        placeholder = "10",
                        description = "Répétitions de la série ${setIndex + 1}",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    CompactNumberField(
                        value = row.weight,
                        onValueChange = { value ->
                            viewModel.updateSet(block.uid, setIndex) { it.copy(weight = value.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6)) }
                        },
                        placeholder = "—",
                        decimal = true,
                        description = "Charge de la série ${setIndex + 1}",
                        modifier = Modifier.weight(1.3f),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = { setRestIndex = setIndex },
                    shape = RoundedCornerShape(12.dp),
                    color = if (row.restSeconds != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.width(72.dp).height(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            Formatters.seconds(row.restSeconds ?: block.restSeconds).replace(" min ", ":").replace(" min", " min"),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
                IconButton(onClick = { viewModel.removeSet(block.uid, setIndex) }, enabled = block.sets.size > 1, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Supprimer la série ${setIndex + 1}", modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { viewModel.addSet(block.uid) }) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Ajouter une série")
        }
        OutlinedTextField(
            value = block.notes,
            onValueChange = { viewModel.setNotes(block.uid, it) },
            placeholder = { Text("Notes pour cette séance (tempo, variante…)") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
        )
    }

    if (customRest) {
        NumberInputDialog(
            title = "Repos après chaque série",
            initialValue = block.restSeconds.toString(),
            decimal = false,
            suffix = "s",
            validate = { it.toIntOrNull()?.let { value -> value in 0..900 } == true },
            onDismiss = { customRest = false },
            onConfirm = {
                it.toIntOrNull()?.let { seconds -> viewModel.setRest(block.uid, seconds) }
                customRest = false
            },
        )
    }
    setRestIndex?.let { setIndex ->
        NumberInputDialog(
            title = "Repos après la série ${setIndex + 1}",
            initialValue = block.sets.getOrNull(setIndex)?.restSeconds?.toString().orEmpty(),
            decimal = false,
            suffix = "s",
            supportingText = "Laisser vide pour utiliser le repos de l'exercice (${Formatters.seconds(block.restSeconds)})",
            validate = { it.isBlank() || it.toIntOrNull()?.let { value -> value in 0..900 } == true },
            onDismiss = { setRestIndex = null },
            onConfirm = { text ->
                viewModel.updateSet(block.uid, setIndex) { it.copy(restSeconds = text.toIntOrNull()) }
                setRestIndex = null
            },
        )
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    description: String,
    modifier: Modifier,
    decimal: Boolean = false,
) {
    val textStyle = MaterialTheme.typography.titleMedium.merge(TabularNumbers).copy(
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.SemiBold,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { contentDescription = description },
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) {
                    Text(placeholder, style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
                }
                inner()
            }
        },
    )
}

@Composable
private fun ReorderList(state: SessionEditState, onMove: (Int, Int) -> Unit, onDone: () -> Unit, modifier: Modifier) {
    val listState = rememberLazyListState()
    val reorder = rememberReorderableListState(listState, onMove = onMove)
    Column(modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Maintenez la poignée et faites glisser pour changer l'ordre.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onDone) { Text("Terminé") }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxHeight(),
        ) {
            itemsIndexed(state.form.blocks, key = { _, block -> block.uid }) { index, block ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().reorderableItem(reorder, block.uid),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
                        ExerciseImage(block.exercise.imageUri, size = 40.dp, shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(block.exercise.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Monter ${block.exercise.name}")
                        }
                        IconButton(onClick = { onMove(index, index + 1) }, enabled = index < state.form.blocks.lastIndex) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Descendre ${block.exercise.name}")
                        }
                        Icon(
                            Icons.Rounded.DragIndicator,
                            contentDescription = "Faire glisser ${block.exercise.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp).padding(10.dp).dragHandle(reorder, block.uid),
                        )
                    }
                }
            }
        }
    }
}

/** Multi-selection of exercises from the library, with a shortcut to create a new one. */
@Composable
private fun ExercisePickerSheet(
    alreadyAdded: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Exercise>) -> Unit,
    onCreateExercise: () -> Unit,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ajouter des exercices", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onCreateExercise) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Créer")
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f)) {
                ExerciseLibraryContent(
                    state = state,
                    viewModel = viewModel,
                    onExerciseClick = { exercise ->
                        selected = if (selected.any { it.id == exercise.id }) selected.filterNot { it.id == exercise.id } else selected + exercise
                    },
                    onCreateExercise = onCreateExercise,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    trailing = { exercise ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Checkbox(
                                checked = selected.any { it.id == exercise.id },
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + exercise else selected.filterNot { it.id == exercise.id }
                                },
                            )
                            if (exercise.id in alreadyAdded) {
                                Text("déjà ajouté", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                )
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    Button(
                        onClick = { onConfirm(selected) },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(if (selected.isEmpty()) "Sélectionnez des exercices" else "Ajouter ${selected.size} exercice(s)")
                    }
                }
            }
        }
    }
}
