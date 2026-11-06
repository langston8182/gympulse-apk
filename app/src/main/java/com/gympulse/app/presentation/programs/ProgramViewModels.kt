package com.gympulse.app.presentation.programs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gympulse.app.core.navigation.ProgramDetailRoute
import com.gympulse.app.core.navigation.ProgramEditRoute
import com.gympulse.app.domain.model.TrainingGoal
import com.gympulse.app.domain.model.TrainingLocation
import com.gympulse.app.domain.model.TrainingProgram
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.model.WorkoutSession
import com.gympulse.app.domain.repository.LocationRepository
import com.gympulse.app.domain.repository.ProgramRepository
import com.gympulse.app.domain.repository.SessionRepository
import com.gympulse.app.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Session following the last one performed in the program (cyclic), or the first one. */
internal fun nextSessionOf(program: TrainingProgram, history: List<WorkoutExecution>): WorkoutSession? {
    if (program.sessions.isEmpty()) return null
    val ids = program.sessions.map { it.id }
    val last = history.filter { it.sessionId in ids }.maxByOrNull { it.startedAt } ?: return program.sessions.first()
    val index = ids.indexOf(last.sessionId)
    return program.sessions[(index + 1) % program.sessions.size]
}

internal fun lastPerformedBySession(history: List<WorkoutExecution>): Map<Long, Long> =
    history.filter { it.sessionId != null }.groupBy { it.sessionId!! }.mapValues { (_, list) -> list.maxOf { it.startedAt } }

data class ProgramItem(
    val program: TrainingProgram,
    val nextSession: WorkoutSession?,
    val locationNames: List<String>,
    val lastPerformedAt: Long?,
)

data class ProgramsState(val loading: Boolean = true, val items: List<ProgramItem> = emptyList())

@HiltViewModel
class ProgramsViewModel @Inject constructor(
    programRepository: ProgramRepository,
    workoutRepository: WorkoutRepository,
    locationRepository: LocationRepository,
) : ViewModel() {
    val state: StateFlow<ProgramsState> = combine(
        programRepository.observePrograms(),
        workoutRepository.observeHistory().onStart { emit(emptyList()) },
        locationRepository.observeLocations(),
    ) { programs, history, locations ->
        val names = locations.associate { it.id to it.name }
        val last = lastPerformedBySession(history)
        ProgramsState(
            loading = false,
            items = programs.map { program ->
                ProgramItem(
                    program = program,
                    nextSession = nextSessionOf(program, history),
                    locationNames = program.compatibleLocationIds.mapNotNull { names[it] },
                    lastPerformedAt = program.sessions.mapNotNull { last[it.id] }.maxOrNull(),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramsState())
}

data class ProgramDetailState(
    val loading: Boolean = true,
    val program: TrainingProgram? = null,
    val nextSession: WorkoutSession? = null,
    val lastPerformed: Map<Long, Long> = emptyMap(),
    val locations: List<TrainingLocation> = emptyList(),
)

@HiltViewModel
class ProgramDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val programRepository: ProgramRepository,
    workoutRepository: WorkoutRepository,
    locationRepository: LocationRepository,
) : ViewModel() {
    private val programId = savedStateHandle.toRoute<ProgramDetailRoute>().programId

    val state: StateFlow<ProgramDetailState> = combine(
        programRepository.observeProgram(programId),
        workoutRepository.observeHistory().onStart { emit(emptyList()) },
        locationRepository.observeLocations(),
    ) { program, history, locations ->
        ProgramDetailState(
            loading = false,
            program = program,
            nextSession = program?.let { nextSessionOf(it, history) },
            lastPerformed = lastPerformedBySession(history),
            locations = locations.filter { program != null && it.id in program.compatibleLocationIds },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramDetailState())

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            programRepository.deleteProgram(programId)
            onDeleted()
        }
    }
}

data class ProgramForm(
    val name: String = "",
    val description: String = "",
    val goal: TrainingGoal = TrainingGoal.HYPERTROPHY,
    val locationIds: Set<Long> = emptySet(),
    val sessions: List<WorkoutSession> = emptyList(),
)

data class ProgramEditState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val form: ProgramForm = ProgramForm(),
    val initialForm: ProgramForm = ProgramForm(),
    val nameError: Boolean = false,
    val saving: Boolean = false,
    val notFound: Boolean = false,
    val allSessions: List<WorkoutSession> = emptyList(),
    val locations: List<TrainingLocation> = emptyList(),
) {
    val hasChanges: Boolean get() = form.copy(sessions = emptyList()) != initialForm.copy(sessions = emptyList()) ||
        form.sessions.map { it.id } != initialForm.sessions.map { it.id }
}

@HiltViewModel
class ProgramEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val programRepository: ProgramRepository,
    sessionRepository: SessionRepository,
    locationRepository: LocationRepository,
) : ViewModel() {
    private val programId = savedStateHandle.toRoute<ProgramEditRoute>().programId
    private var original: TrainingProgram? = null

    private val _state = MutableStateFlow(ProgramEditState(isNew = programId == 0L))
    val state: StateFlow<ProgramEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (programId != 0L) {
                val program = programRepository.getProgram(programId)
                if (program == null) {
                    _state.update { it.copy(loading = false, notFound = true) }
                    return@launch
                }
                original = program
                val form = ProgramForm(program.name, program.description, program.goal, program.compatibleLocationIds, program.sessions)
                _state.update { it.copy(loading = false, form = form, initialForm = form) }
            } else {
                _state.update { it.copy(loading = false) }
            }
        }
        viewModelScope.launch {
            combine(sessionRepository.observeSessions(), locationRepository.observeLocations(), ::Pair).collect { (sessions, locations) ->
                val byId = sessions.associateBy { it.id }
                _state.update { state ->
                    state.copy(
                        allSessions = sessions,
                        locations = locations,
                        form = state.form.copy(sessions = state.form.sessions.mapNotNull { byId[it.id] }),
                    )
                }
            }
        }
    }

    private fun edit(transform: (ProgramForm) -> ProgramForm) = _state.update { it.copy(form = transform(it.form), nameError = false) }

    fun setName(value: String) = edit { it.copy(name = value) }
    fun setDescription(value: String) = edit { it.copy(description = value) }
    fun setGoal(goal: TrainingGoal) = edit { it.copy(goal = goal) }
    fun toggleLocation(id: Long) = edit { it.copy(locationIds = if (id in it.locationIds) it.locationIds - id else it.locationIds + id) }

    fun setSelectedSessions(ids: Set<Long>) = edit { form ->
        val kept = form.sessions.filter { it.id in ids }
        val added = _state.value.allSessions.filter { it.id in ids && kept.none { k -> k.id == it.id } }
        form.copy(sessions = kept + added)
    }

    fun removeSession(id: Long) = edit { form -> form.copy(sessions = form.sessions.filterNot { it.id == id }) }

    fun moveSession(from: Int, to: Int) = edit { form ->
        if (from !in form.sessions.indices || to !in form.sessions.indices) form
        else form.copy(sessions = form.sessions.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        if (current.saving) return
        if (current.form.name.isBlank()) {
            _state.update { it.copy(nameError = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val form = current.form
            programRepository.saveProgram(
                TrainingProgram(
                    id = original?.id ?: 0L,
                    name = form.name.trim(),
                    description = form.description.trim(),
                    goal = form.goal,
                    sessions = form.sessions,
                    compatibleLocationIds = form.locationIds,
                    isDemo = original?.isDemo ?: false,
                    createdAt = original?.createdAt ?: 0L,
                )
            )
            _state.update { it.copy(saving = false, initialForm = it.form) }
            onSaved()
        }
    }
}
