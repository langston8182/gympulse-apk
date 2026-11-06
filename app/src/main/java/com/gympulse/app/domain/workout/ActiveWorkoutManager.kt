package com.gympulse.app.domain.workout

import com.gympulse.app.core.coroutines.ApplicationScope
import com.gympulse.app.core.time.TimeProvider
import com.gympulse.app.domain.model.TrainingLocation
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.model.WorkoutStatus
import com.gympulse.app.domain.repository.SessionRepository
import com.gympulse.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Side effects triggered by the workout (vibration, sound, notification). */
interface WorkoutFeedback {
    fun onRestFinished(execution: WorkoutExecution)
}

sealed interface StartWorkoutResult {
    data class Started(val executionId: Long) : StartWorkoutResult
    data class AlreadyActive(val executionId: Long, val sessionName: String) : StartWorkoutResult
    data object SessionNotFound : StartWorkoutResult
    data object EmptySession : StartWorkoutResult
}

sealed interface WorkoutEvent {
    data object RestFinished : WorkoutEvent
}

/**
 * Single owner of the active workout. Each mutation runs [WorkoutEngine] under a mutex, publishes
 * the new state immediately and persists it to Room before the next mutation, so a crash or a
 * process death never loses a recorded set. The state is restored from the database at startup.
 */
@Singleton
class ActiveWorkoutManager @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val sessionRepository: SessionRepository,
    private val time: TimeProvider,
    private val feedback: WorkoutFeedback,
    @param:ApplicationScope private val scope: CoroutineScope,
) : ActiveWorkoutRestorer {
    private val mutex = Mutex()
    private val _active = MutableStateFlow<WorkoutExecution?>(null)
    val active: StateFlow<WorkoutExecution?> = _active.asStateFlow()

    private val _restored = MutableStateFlow(false)
    val restored: StateFlow<Boolean> = _restored.asStateFlow()

    private val _events = MutableSharedFlow<WorkoutEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<WorkoutEvent> = _events.asSharedFlow()

    private var restAlertJob: Job? = null

    init {
        scope.launch { restore() }
    }

    fun now(): Long = time.now()

    override suspend fun restore() {
        mutex.withLock {
            if (_active.value == null) {
                _active.value = workoutRepository.getActiveExecution()
            }
        }
        _restored.value = true
        scheduleRestAlert(_active.value)
    }

    suspend fun start(sessionId: Long, location: TrainingLocation?): StartWorkoutResult = mutex.withLock {
        _active.value?.let { return@withLock StartWorkoutResult.AlreadyActive(it.id, it.sessionName) }
        val session = sessionRepository.getSession(sessionId) ?: return@withLock StartWorkoutResult.SessionNotFound
        if (session.exercises.none { it.sets.isNotEmpty() }) return@withLock StartWorkoutResult.EmptySession
        val created = WorkoutEngine.create(session, time.now(), location)
        val id = workoutRepository.createExecution(created)
        _active.value = created.copy(id = id)
        StartWorkoutResult.Started(id)
    }

    fun completeSet(repetitions: Int?, weightKg: Double?, durationSeconds: Int?) =
        mutate { execution, now -> WorkoutEngine.completeCurrentSet(execution, repetitions, weightKg, durationSeconds, now) }

    fun updateSet(exerciseIndex: Int, setIndex: Int, repetitions: Int?, weightKg: Double?, durationSeconds: Int?) =
        mutate { execution, _ -> WorkoutEngine.updateSet(execution, exerciseIndex, setIndex, repetitions, weightKg, durationSeconds) }

    fun skipRest() = mutate { execution, now -> WorkoutEngine.skipRest(execution, now) }

    fun extendRest(seconds: Int) = mutate { execution, now -> WorkoutEngine.extendRest(execution, seconds, now) }

    fun pause() = mutate { execution, now -> WorkoutEngine.pause(execution, now) }

    fun resume() = mutate { execution, now -> WorkoutEngine.resume(execution, now) }

    fun goTo(exerciseIndex: Int, setIndex: Int) =
        mutate { execution, _ -> WorkoutEngine.goTo(execution, exerciseIndex, setIndex) }

    fun goToExercise(exerciseIndex: Int) = mutate { execution, _ -> WorkoutEngine.goToExercise(execution, exerciseIndex) }

    fun skipExercise() = mutate { execution, _ -> WorkoutEngine.skipCurrentExercise(execution) }

    fun addSet(exerciseIndex: Int) = mutate { execution, _ -> WorkoutEngine.addSet(execution, exerciseIndex) }

    fun reorderRemaining(orderedKeys: List<String>) =
        mutate { execution, _ -> WorkoutEngine.reorderRemaining(execution, orderedKeys) }

    fun startSetTimer() = mutate { execution, now -> WorkoutEngine.startSetTimer(execution, now) }

    fun pauseSetTimer() = mutate { execution, now -> WorkoutEngine.pauseSetTimer(execution, now) }

    fun resetSetTimer() = mutate { execution, _ -> WorkoutEngine.resetSetTimer(execution) }

    fun updateNotes(notes: String) = mutate { execution, _ -> WorkoutEngine.updateNotes(execution, notes) }

    /** Completes and saves the workout. Returns its id, or null when no workout was active. */
    suspend fun finish(notes: String?): Long? {
        val finished = mutateNow { execution, now -> WorkoutEngine.finish(execution, now, notes) } ?: return null
        return finished.id
    }

    suspend fun cancel() {
        mutateNow { execution, now -> WorkoutEngine.cancel(execution, now) }
    }

    private fun mutate(transform: (WorkoutExecution, Long) -> WorkoutExecution) {
        scope.launch { mutateNow(transform) }
    }

    private suspend fun mutateNow(transform: (WorkoutExecution, Long) -> WorkoutExecution): WorkoutExecution? =
        mutex.withLock {
            val current = _active.value ?: return@withLock null
            val updated = transform(current, time.now())
            if (updated == current) return@withLock current
            _active.value = updated.takeIf { it.isActive }
            workoutRepository.saveExecution(updated)
            scheduleRestAlert(_active.value)
            updated
        }

    private fun scheduleRestAlert(execution: WorkoutExecution?) {
        restAlertJob?.cancel()
        val rest = execution?.rest ?: return
        if (execution.status != WorkoutStatus.IN_PROGRESS || rest.isPaused) return
        val remaining = rest.endsAt - time.now()
        if (remaining <= 0) return
        restAlertJob = scope.launch {
            delay(remaining)
            val current = _active.value
            if (current != null && current.rest == rest && current.status == WorkoutStatus.IN_PROGRESS) {
                feedback.onRestFinished(current)
                _events.emit(WorkoutEvent.RestFinished)
            }
        }
    }
}
