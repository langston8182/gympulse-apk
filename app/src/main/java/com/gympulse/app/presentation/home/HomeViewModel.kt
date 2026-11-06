package com.gympulse.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gympulse.app.domain.location.SessionRecommender
import com.gympulse.app.domain.location.SessionSuggestion
import com.gympulse.app.domain.model.DashboardStats
import com.gympulse.app.domain.model.LocationContext
import com.gympulse.app.domain.model.StatsPeriod
import com.gympulse.app.domain.model.UserPreferences
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.repository.LocationRepository
import com.gympulse.app.domain.repository.PreferencesRepository
import com.gympulse.app.domain.repository.ProgramRepository
import com.gympulse.app.domain.repository.ProgressRepository
import com.gympulse.app.domain.repository.SessionRepository
import com.gympulse.app.domain.repository.WorkoutRepository
import com.gympulse.app.domain.workout.ActiveWorkoutManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeState(
    val loading: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val hasSessions: Boolean = false,
    /** The suggestion of the day first, then the other sessions that fit the current location. */
    val suggestions: List<SessionSuggestion> = emptyList(),
    val dashboard: DashboardStats? = null,
    val period: StatsPeriod = StatsPeriod.MONTH,
    val location: LocationContext = LocationContext(),
    val activeWorkout: WorkoutExecution? = null,
)

private data class SuggestionData(val hasSessions: Boolean, val suggestions: List<SessionSuggestion>)

@HiltViewModel
class HomeViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    sessionRepository: SessionRepository,
    programRepository: ProgramRepository,
    workoutRepository: WorkoutRepository,
    progressRepository: ProgressRepository,
    locationRepository: LocationRepository,
    private val workoutManager: ActiveWorkoutManager,
) : ViewModel() {

    private val period = MutableStateFlow(StatsPeriod.MONTH)

    private val suggestion = combine(
        sessionRepository.observeSessions(),
        programRepository.observePrograms(),
        workoutRepository.observeHistory(),
        locationRepository.context,
    ) { sessions, programs, history, context ->
        val main = SessionRecommender.suggest(sessions, programs, history, context.location)
        val others = SessionRecommender.alternatives(sessions, history, context.location, exclude = main?.session?.id)
        SuggestionData(sessions.isNotEmpty(), listOfNotNull(main) + others)
    }.flowOn(Dispatchers.Default)

    private val dashboard = period.flatMapLatest { progressRepository.observeDashboard(it) }

    val state: StateFlow<HomeState> = combine(
        preferencesRepository.preferences,
        suggestion,
        dashboard,
        locationRepository.context,
        workoutManager.active,
    ) { preferences, suggestion, dashboard, context, active ->
        HomeState(
            loading = false,
            preferences = preferences,
            hasSessions = suggestion.hasSessions,
            suggestions = suggestion.suggestions,
            dashboard = dashboard,
            period = dashboard.period,
            location = context,
            activeWorkout = active,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun now(): Long = workoutManager.now()

    fun setPeriod(value: StatsPeriod) {
        period.value = value
    }
}
