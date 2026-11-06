package com.gympulse.app.presentation.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gympulse.app.data.remote.auth.AccountManager
import com.gympulse.app.data.sync.SyncScheduler
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import com.gympulse.app.domain.model.UserPreferences
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.repository.LocationRepository
import com.gympulse.app.domain.repository.PreferencesRepository
import com.gympulse.app.domain.workout.ActiveWorkoutManager
import com.gympulse.app.domain.workout.StartWorkoutResult
import com.gympulse.app.service.WorkoutServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUiState(
    val loading: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val activeWorkout: WorkoutExecution? = null,
    val showResumePrompt: Boolean = false,
)

sealed interface AppEvent {
    data object OpenWorkout : AppEvent
    data class WorkoutAlreadyActive(val requestedSessionId: Long, val activeName: String) : AppEvent
    data class Message(val text: String) : AppEvent
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val workoutManager: ActiveWorkoutManager,
    private val locationRepository: LocationRepository,
    private val serviceController: WorkoutServiceController,
    private val accountManager: AccountManager,
    syncScheduler: SyncScheduler,
) : ViewModel() {

    /** False while the login screen must be shown (Cognito builds without a signed-in account). */
    val signedIn: StateFlow<Boolean> = accountManager.signedIn

    private val resumePromptHandled = MutableStateFlow(false)
    private val events = Channel<AppEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val uiState: StateFlow<AppUiState> = combine(
        preferencesRepository.preferences,
        workoutManager.active,
        workoutManager.restored,
        resumePromptHandled,
    ) { preferences, active, restored, handled ->
        AppUiState(
            loading = !restored,
            preferences = preferences,
            activeWorkout = active,
            showResumePrompt = restored && active != null && !handled && preferences.onboardingCompleted,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    init {
        // A freshly signed-in account has nothing locally yet: download its data right away.
        viewModelScope.launch { accountManager.signedIn.filter { it }.collect {
            syncScheduler.requestSync(true)
            // Greet the user by the first name of the account when none was entered yet.
            val name = accountManager.displayName.value
            if (name != null && preferencesRepository.preferences.first().userName.isBlank()) preferencesRepository.setUserName(name)
        } }
        viewModelScope.launch { locationRepository.refreshDetection() }
    }

    fun now(): Long = workoutManager.now()

    fun startWorkout(sessionId: Long) {
        viewModelScope.launch {
            when (val result = workoutManager.start(sessionId, locationRepository.context.value.location)) {
                is StartWorkoutResult.Started -> {
                    resumePromptHandled.value = true
                    serviceController.ensureRunning()
                    events.send(AppEvent.OpenWorkout)
                }
                is StartWorkoutResult.AlreadyActive -> events.send(AppEvent.WorkoutAlreadyActive(sessionId, result.sessionName))
                StartWorkoutResult.EmptySession -> events.send(AppEvent.Message("Ajoutez au moins un exercice à cette séance pour la démarrer."))
                StartWorkoutResult.SessionNotFound -> events.send(AppEvent.Message("Cette séance n'existe plus."))
            }
        }
    }

    /** Abandons the current workout and starts [sessionId] instead. */
    fun replaceActiveWorkout(sessionId: Long) {
        viewModelScope.launch {
            workoutManager.cancel()
            startWorkout(sessionId)
        }
    }

    fun resumeWorkout() {
        resumePromptHandled.value = true
        serviceController.ensureRunning()
        viewModelScope.launch { events.send(AppEvent.OpenWorkout) }
    }

    fun dismissResumePrompt() {
        resumePromptHandled.value = true
    }

    fun abandonInterruptedWorkout() {
        resumePromptHandled.value = true
        viewModelScope.launch { workoutManager.cancel() }
    }
}
