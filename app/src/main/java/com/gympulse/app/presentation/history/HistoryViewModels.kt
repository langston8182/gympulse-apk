package com.gympulse.app.presentation.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gympulse.app.core.navigation.CompareWorkoutsRoute
import com.gympulse.app.core.navigation.HistoryDetailRoute
import com.gympulse.app.domain.model.PersonalRecord
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.repository.WorkoutRepository
import com.gympulse.app.domain.stats.RecordDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryDetailState(
    val loading: Boolean = true,
    val execution: WorkoutExecution? = null,
    val records: List<PersonalRecord> = emptyList(),
    /** Other workouts it can be compared with, same session first. */
    val candidates: List<WorkoutExecution> = emptyList(),
)

@HiltViewModel
class HistoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {
    private val executionId = savedStateHandle.toRoute<HistoryDetailRoute>().executionId

    val state: StateFlow<HistoryDetailState> = combine(
        workoutRepository.observeExecution(executionId),
        workoutRepository.observeHistory(),
    ) { execution, history ->
        HistoryDetailState(
            loading = false,
            execution = execution,
            records = execution?.let { RecordDetector.mostRelevant(RecordDetector.detectForWorkout(history, it)) }.orEmpty(),
            candidates = history
                .filter { it.id != executionId }
                .sortedWith(compareByDescending<WorkoutExecution> { it.sessionId == execution?.sessionId }.thenByDescending { it.startedAt }),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryDetailState())

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            workoutRepository.deleteExecution(executionId)
            onDeleted()
        }
    }
}

data class CompareState(
    val loading: Boolean = true,
    val first: WorkoutExecution? = null,
    val second: WorkoutExecution? = null,
)

@HiltViewModel
class CompareWorkoutsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    workoutRepository: WorkoutRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<CompareWorkoutsRoute>()

    val state: StateFlow<CompareState> = combine(
        workoutRepository.observeExecution(route.firstId),
        workoutRepository.observeExecution(route.secondId),
    ) { a, b ->
        val ordered = listOfNotNull(a, b).sortedBy { it.startedAt }
        CompareState(loading = false, first = ordered.getOrNull(0), second = ordered.getOrNull(1))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareState())
}
