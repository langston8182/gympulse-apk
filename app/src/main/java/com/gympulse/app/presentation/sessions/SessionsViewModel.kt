package com.gympulse.app.presentation.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gympulse.app.domain.model.LocationContext
import com.gympulse.app.domain.model.TrainingGoal
import com.gympulse.app.domain.model.WorkoutSession
import com.gympulse.app.domain.repository.LocationRepository
import com.gympulse.app.domain.repository.SessionRepository
import com.gympulse.app.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SessionFilter(val label: String) {
    ALL("Toutes"), FAVORITES("Favorites"), HYPERTROPHY("Hypertrophie"), MIXED("Force-hypertrophie"), STRENGTH("Force"), ENDURANCE("Endurance"), CUSTOM("Perso");

    fun accepts(session: WorkoutSession): Boolean = when (this) {
        ALL -> true
        FAVORITES -> session.isFavorite
        HYPERTROPHY -> session.trainingGoal == TrainingGoal.HYPERTROPHY
        MIXED -> session.trainingGoal == TrainingGoal.STRENGTH_HYPERTROPHY
        STRENGTH -> session.trainingGoal == TrainingGoal.STRENGTH
        ENDURANCE -> session.trainingGoal == TrainingGoal.ENDURANCE
        CUSTOM -> session.trainingGoal == TrainingGoal.CUSTOM
    }
}

data class SessionsState(
    val loading: Boolean = true,
    val location: LocationContext = LocationContext(),
    /** Sessions matching the filter, grouped by training place. */
    val groups: List<SessionGroup> = emptyList(),
    val lastPerformed: Map<Long, Long> = emptyMap(),
    val filter: SessionFilter = SessionFilter.ALL,
    val totalCount: Int = 0,
    /** All the sessions in the user's order, grouped by place, shown while reordering. */
    val reorderGroups: List<SessionGroup> = emptyList(),
    val ordered: List<WorkoutSession> = emptyList(),
    val reordering: Boolean = false,
)

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    workoutRepository: WorkoutRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(SessionFilter.ALL)
    private val reordering = MutableStateFlow(false)

    private val lastPerformed = workoutRepository.observeHistory()
        .map { history -> history.filter { it.sessionId != null }.groupBy { it.sessionId!! }.mapValues { (_, list) -> list.maxOf { it.startedAt } } }
        .onStart { emit(emptyMap()) }

    private val catalog = combine(sessionRepository.observeSessions(), locationRepository.observeLocations()) { sessions, locations -> sessions to locations }

    val state: StateFlow<SessionsState> = combine(
        catalog,
        lastPerformed,
        locationRepository.context,
        filter,
        reordering,
    ) { (sessions, locations), last, context, filter, reordering ->
        val ordered = sessions.sortedWith(compareBy({ it.sortOrder }, { it.createdAt }, { it.id }))
        val currentId = context.location?.id
        SessionsState(
            loading = false,
            location = context,
            groups = groupSessionsByLocation(ordered.filter(filter::accepts), locations, currentId),
            lastPerformed = last,
            filter = filter,
            totalCount = sessions.size,
            reorderGroups = groupSessionsByLocation(ordered, locations, currentId),
            ordered = ordered,
            reordering = reordering,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionsState())

    init {
        viewModelScope.launch { locationRepository.refreshDetection() }
    }

    fun setReordering(value: Boolean) {
        reordering.value = value
    }

    /** Stores the order obtained by dragging: the first id comes first everywhere. */
    fun saveOrder(orderedIds: List<Long>) {
        viewModelScope.launch { sessionRepository.reorderSessions(orderedIds) }
    }

    fun setFilter(value: SessionFilter) {
        filter.value = value
    }

    fun toggleFavorite(session: WorkoutSession) {
        viewModelScope.launch { sessionRepository.setFavorite(session.id, !session.isFavorite) }
    }
}
