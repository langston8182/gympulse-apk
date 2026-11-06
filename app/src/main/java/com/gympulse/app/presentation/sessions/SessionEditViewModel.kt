package com.gympulse.app.presentation.sessions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gympulse.app.core.navigation.CREATED_EXERCISE_ID_KEY
import com.gympulse.app.core.navigation.SessionEditRoute
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.domain.model.Exercise
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.SessionExercise
import com.gympulse.app.domain.model.SetTarget
import com.gympulse.app.domain.model.TrainingGoal
import com.gympulse.app.domain.model.TrainingLocation
import com.gympulse.app.domain.model.WeightUnit
import com.gympulse.app.domain.model.WorkoutSession
import com.gympulse.app.domain.repository.ExerciseRepository
import com.gympulse.app.domain.repository.LocationRepository
import com.gympulse.app.domain.repository.PreferencesRepository
import com.gympulse.app.domain.repository.ProgressRepository
import com.gympulse.app.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Editable set row. Values are kept as text so that partial input ("22,") is preserved. */
data class SetRow(
    val reps: String = "",
    val weight: String = "",
    val duration: String = "",
    val restSeconds: Int? = null,
)

data class ExerciseBlock(
    val uid: Long,
    val exercise: Exercise,
    val restSeconds: Int,
    val notes: String,
    val sets: List<SetRow>,
)

data class SessionForm(
    val name: String = "",
    val description: String = "",
    val goal: TrainingGoal = TrainingGoal.HYPERTROPHY,
    val locationIds: Set<Long> = emptySet(),
    val blocks: List<ExerciseBlock> = emptyList(),
)

data class SessionEditState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val isDuplicate: Boolean = false,
    val form: SessionForm = SessionForm(),
    val initialForm: SessionForm = SessionForm(),
    val nameError: Boolean = false,
    val saving: Boolean = false,
    val notFound: Boolean = false,
    val locations: List<TrainingLocation> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
) {
    val hasChanges: Boolean get() = form != initialForm
    val title: String
        get() = when {
            isDuplicate -> "Dupliquer la séance"
            isNew -> "Nouvelle séance"
            else -> "Modifier la séance"
        }
}

@HiltViewModel
class SessionEditViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val exerciseRepository: ExerciseRepository,
    private val progressRepository: ProgressRepository,
    private val preferencesRepository: PreferencesRepository,
    locationRepository: LocationRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<SessionEditRoute>()
    private var original: WorkoutSession? = null
    private var nextUid = 1L
    private var defaultRest = 90

    private val _state = MutableStateFlow(
        SessionEditState(isNew = route.sessionId == 0L, isDuplicate = route.duplicateFromId != 0L)
    )
    val state: StateFlow<SessionEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            defaultRest = prefs.defaultRestSeconds
            val unit = prefs.weightUnit
            val sourceId = route.sessionId.takeIf { it != 0L } ?: route.duplicateFromId.takeIf { it != 0L }
            val source = sourceId?.let { sessionRepository.getSession(it) }
            if (sourceId != null && source == null) {
                _state.update { it.copy(loading = false, notFound = true, unit = unit) }
                return@launch
            }
            if (route.sessionId != 0L) original = source
            val form = source?.let { session ->
                SessionForm(
                    name = if (route.duplicateFromId != 0L) "${session.name} (copie)" else session.name,
                    description = session.description,
                    goal = session.trainingGoal,
                    locationIds = session.compatibleLocationIds,
                    blocks = session.exercises.map { it.toBlock(unit) },
                )
            } ?: SessionForm()
            val initial = if (route.duplicateFromId != 0L) SessionForm() else form
            _state.update { it.copy(loading = false, form = form, initialForm = initial, unit = unit) }
        }
        viewModelScope.launch {
            locationRepository.observeLocations().collect { locations -> _state.update { it.copy(locations = locations) } }
        }
        viewModelScope.launch {
            savedStateHandle.getStateFlow(CREATED_EXERCISE_ID_KEY, 0L).collect { id ->
                if (id != 0L) {
                    savedStateHandle[CREATED_EXERCISE_ID_KEY] = 0L
                    exerciseRepository.getExercise(id)?.let { addExercises(listOf(it)) }
                }
            }
        }
    }

    private fun SessionExercise.toBlock(unit: WeightUnit) = ExerciseBlock(
        uid = nextUid++,
        exercise = exercise,
        restSeconds = restDurationSeconds,
        notes = notes,
        sets = sets.map { target ->
            SetRow(
                reps = target.targetRepetitions?.toString().orEmpty(),
                weight = target.targetWeightKg?.let { weightText(it, unit) }.orEmpty(),
                duration = target.targetDurationSeconds?.toString().orEmpty(),
                restSeconds = target.restSeconds,
            )
        },
    )

    private fun weightText(kg: Double, unit: WeightUnit): String =
        Formatters.weightValue(kg, unit).replace("\u202F", "")

    private fun editForm(transform: (SessionForm) -> SessionForm) =
        _state.update { it.copy(form = transform(it.form), nameError = false) }

    private fun editBlock(uid: Long, transform: (ExerciseBlock) -> ExerciseBlock) = editForm { form ->
        form.copy(blocks = form.blocks.map { if (it.uid == uid) transform(it) else it })
    }

    fun setName(value: String) = editForm { it.copy(name = value) }
    fun setDescription(value: String) = editForm { it.copy(description = value) }
    fun setGoal(goal: TrainingGoal) = editForm { it.copy(goal = goal) }
    fun toggleLocation(id: Long) = editForm { it.copy(locationIds = if (id in it.locationIds) it.locationIds - id else it.locationIds + id) }

    /** Adds exercises with sensible defaults for the goal, prefilled with the last loads used. */
    fun addExercises(exercises: List<Exercise>) {
        if (exercises.isEmpty()) return
        viewModelScope.launch {
            val goal = _state.value.form.goal
            val unit = _state.value.unit
            val blocks = exercises.map { exercise ->
                val last = progressRepository.lastPerformance(exercise.id)
                val (count, reps, rest) = defaultsFor(goal, exercise.type)
                val sets = List(count) { index ->
                    if (exercise.type == ExerciseType.DURATION) {
                        SetRow(duration = (last?.bestDurationSeconds ?: if (goal == TrainingGoal.ENDURANCE) 60 else 45).toString())
                    } else {
                        val lastWeight = last?.sets?.getOrNull(index)?.weightKg ?: last?.topWeightKg
                        SetRow(reps = reps.toString(), weight = lastWeight?.takeIf { it > 0 }?.let { weightText(it, unit) }.orEmpty())
                    }
                }
                ExerciseBlock(uid = nextUid++, exercise = exercise, restSeconds = rest, notes = "", sets = sets)
            }
            editForm { it.copy(blocks = it.blocks + blocks) }
        }
    }

    private fun defaultsFor(goal: TrainingGoal, type: ExerciseType): Triple<Int, Int, Int> = when {
        type == ExerciseType.DURATION -> Triple(3, 0, 60)
        goal == TrainingGoal.STRENGTH -> Triple(4, 5, 180)
        goal == TrainingGoal.STRENGTH_HYPERTROPHY -> Triple(4, 8, 90)
        goal == TrainingGoal.ENDURANCE -> Triple(3, 15, 60)
        goal == TrainingGoal.HYPERTROPHY -> Triple(3, 12, 90)
        else -> Triple(3, 10, defaultRest)
    }

    fun removeBlock(uid: Long) = editForm { form -> form.copy(blocks = form.blocks.filterNot { it.uid == uid }) }

    fun moveBlock(from: Int, to: Int) = editForm { form ->
        if (from !in form.blocks.indices || to !in form.blocks.indices) form
        else form.copy(blocks = form.blocks.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun setRest(uid: Long, seconds: Int) = editBlock(uid) { it.copy(restSeconds = seconds.coerceIn(0, 900)) }
    fun setNotes(uid: Long, notes: String) = editBlock(uid) { it.copy(notes = notes) }

    fun addSet(uid: Long) = editBlock(uid) { block ->
        block.copy(sets = block.sets + (block.sets.lastOrNull() ?: SetRow(reps = "10")))
    }

    fun removeSet(uid: Long, index: Int) = editBlock(uid) { block ->
        if (block.sets.size <= 1) block else block.copy(sets = block.sets.filterIndexed { i, _ -> i != index })
    }

    fun updateSet(uid: Long, index: Int, transform: (SetRow) -> SetRow) = editBlock(uid) { block ->
        block.copy(sets = block.sets.mapIndexed { i, row -> if (i == index) transform(row) else row })
    }

    fun save(onSaved: (Long) -> Unit) {
        val current = _state.value
        if (current.saving) return
        if (current.form.name.isBlank()) {
            _state.update { it.copy(nameError = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        val unit = current.unit
        val form = current.form
        val session = WorkoutSession(
            id = original?.id ?: 0L,
            name = form.name.trim(),
            description = form.description.trim(),
            trainingGoal = form.goal,
            exercises = form.blocks.mapIndexed { index, block ->
                SessionExercise(
                    exercise = block.exercise,
                    order = index,
                    restDurationSeconds = block.restSeconds,
                    notes = block.notes.trim(),
                    sets = block.sets.map { row ->
                        if (block.exercise.type == ExerciseType.DURATION) {
                            SetTarget(targetDurationSeconds = row.duration.toIntOrNull()?.coerceIn(1, 3600) ?: 30, restSeconds = row.restSeconds)
                        } else {
                            SetTarget(
                                targetRepetitions = row.reps.toIntOrNull()?.coerceIn(1, 999),
                                targetWeightKg = Formatters.parseDecimal(row.weight)?.takeIf { it > 0 }?.let { unit.toKg(it) },
                                restSeconds = row.restSeconds,
                            )
                        }
                    },
                )
            },
            compatibleLocationIds = form.locationIds,
            isFavorite = original?.isFavorite ?: false,
            isDemo = original?.isDemo ?: false,
            createdAt = original?.createdAt ?: 0L,
        )
        viewModelScope.launch {
            val id = sessionRepository.saveSession(session)
            _state.update { it.copy(saving = false, initialForm = it.form) }
            onSaved(id)
        }
    }
}
