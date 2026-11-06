package com.gympulse.app.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gympulse.app.domain.model.Equipment
import com.gympulse.app.domain.model.ExecutedExercise
import com.gympulse.app.domain.model.ExercisePerformance
import com.gympulse.app.domain.model.UserPreferences
import com.gympulse.app.domain.model.WeightUnit
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.repository.PreferencesRepository
import com.gympulse.app.domain.repository.ProgressRepository
import com.gympulse.app.domain.workout.ActiveWorkoutManager
import com.gympulse.app.domain.workout.WorkoutEngine
import com.gympulse.app.domain.workout.WorkoutEvent
import com.gympulse.app.service.WorkoutServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/** Values being entered for the current set, before it is recorded. Weight is kept in kg. */
data class SetDraft(
    val exerciseKey: String = "",
    val setIndex: Int = -1,
    val repetitions: Int = 0,
    val weightKg: Double? = null,
    val durationSeconds: Int = 0,
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val manager: ActiveWorkoutManager,
    private val progressRepository: ProgressRepository,
    preferencesRepository: PreferencesRepository,
    private val serviceController: WorkoutServiceController,
) : ViewModel() {

    val execution: StateFlow<WorkoutExecution?> = manager.active
    val restored: StateFlow<Boolean> = manager.restored
    val events: Flow<WorkoutEvent> = manager.events

    val preferences: StateFlow<UserPreferences> =
        preferencesRepository.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    /** Last comparable performance of each exercise of the workout, keyed by exercise snapshot key. */
    val previous: StateFlow<Map<String, ExercisePerformance>> = manager.active
        .filterNotNull()
        .map { execution -> Triple(execution.id, execution.sessionId, execution.exercises.map { it.key to it.exerciseId }) }
        .distinctUntilChanged()
        .mapLatest { (executionId, sessionId, exercises) ->
            exercises.mapNotNull { (key, exerciseId) ->
                exerciseId?.let { id ->
                    progressRepository.lastPerformance(id, preferredSessionId = sessionId, excludingExecutionId = executionId)?.let { key to it }
                }
            }.toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _draft = MutableStateFlow(SetDraft())
    val draft: StateFlow<SetDraft> = _draft.asStateFlow()

    private var lastCompletionAt = 0L

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(manager.active, previous) { execution, previous -> execution to previous }
                .collect { (execution, previous) -> syncDraft(execution, previous) }
        }
    }

    fun now(): Long = manager.now()

    fun ensureServiceRunning() = serviceController.ensureRunning()

    /** Resets the inputs when the current set changes, prefilling targets or the last loads. */
    private fun syncDraft(execution: WorkoutExecution?, previous: Map<String, ExercisePerformance>, force: Boolean = false) {
        val exercise = execution?.currentExercise ?: return
        val set = execution.currentSet ?: return
        val current = _draft.value
        val samePosition = current.exerciseKey == exercise.key && current.setIndex == execution.currentSetIndex
        if (samePosition && !force) {
            if (current.weightKg == null && !set.isCompleted) {
                suggestedWeight(exercise, execution.currentSetIndex, previous)?.let { _draft.value = current.copy(weightKg = it) }
            }
            return
        }
        val last = previous[exercise.key]
        _draft.value = if (set.isCompleted) {
            SetDraft(
                exerciseKey = exercise.key,
                setIndex = execution.currentSetIndex,
                repetitions = set.actualRepetitions ?: 0,
                weightKg = set.actualWeightKg,
                durationSeconds = set.actualDurationSeconds ?: 0,
            )
        } else {
            SetDraft(
                exerciseKey = exercise.key,
                setIndex = execution.currentSetIndex,
                repetitions = set.targetRepetitions
                    ?: last?.sets?.getOrNull(execution.currentSetIndex)?.repetitions
                    ?: last?.sets?.lastOrNull()?.repetitions
                    ?: 10,
                weightKg = suggestedWeight(exercise, execution.currentSetIndex, previous),
                durationSeconds = set.targetDurationSeconds ?: last?.bestDurationSeconds ?: 30,
            )
        }
    }

    /**
     * Load proposed for a set: its target, else the load of the same set last time, else the load
     * just used in this workout for the same exercise.
     */
    private fun suggestedWeight(exercise: ExecutedExercise, setIndex: Int, previous: Map<String, ExercisePerformance>): Double? {
        val set = exercise.sets.getOrNull(setIndex) ?: return null
        set.targetWeightKg?.let { return it }
        val last = previous[exercise.key]
        last?.sets?.getOrNull(setIndex)?.weightKg?.takeIf { it > 0 }?.let { return it }
        exercise.sets.take(setIndex).lastOrNull { it.isCompleted && it.actualWeightKg != null }?.actualWeightKg?.let { return it }
        return last?.topWeightKg
    }

    fun weightStep(exercise: ExecutedExercise?, unit: WeightUnit): Double {
        val heavy = exercise?.equipment?.any { it in HEAVY_EQUIPMENT } == true
        return when (unit) {
            WeightUnit.KG -> if (heavy) 2.5 else 1.0
            WeightUnit.LB -> if (heavy) 5.0 else 2.5
        }
    }

    fun adjustRepetitions(delta: Int) = _draft.value.let { _draft.value = it.copy(repetitions = max(0, it.repetitions + delta)) }

    fun setRepetitions(value: Int) = _draft.value.let { _draft.value = it.copy(repetitions = value.coerceIn(0, 999)) }

    fun adjustWeight(direction: Int, unit: WeightUnit) {
        val draft = _draft.value
        val exercise = execution.value?.currentExercise
        val step = weightStep(exercise, unit)
        val display = unit.fromKg(draft.weightKg ?: 0.0)
        val snapped = ((display / step).roundToInt() + direction) * step
        _draft.value = draft.copy(weightKg = unit.toKg(snapped.coerceAtLeast(0.0)))
    }

    fun setWeight(valueInUnit: Double?, unit: WeightUnit) {
        _draft.value = _draft.value.copy(weightKg = valueInUnit?.takeIf { it >= 0 }?.let { unit.toKg(it) })
    }

    fun adjustDuration(deltaSeconds: Int) = _draft.value.let { _draft.value = it.copy(durationSeconds = max(0, it.durationSeconds + deltaSeconds)) }

    fun setDuration(seconds: Int) = _draft.value.let { _draft.value = it.copy(durationSeconds = seconds.coerceIn(0, 3600)) }

    /** Records the current set. Ignores accidental double taps. */
    fun completeSet(): Boolean {
        val now = manager.now()
        if (now - lastCompletionAt < DOUBLE_TAP_GUARD_MS) return false
        val execution = execution.value ?: return false
        val exercise = execution.currentExercise ?: return false
        lastCompletionAt = now
        val draft = _draft.value
        if (exercise.isTimed) {
            val timer = execution.setTimer
            val measured = timer?.let { (it.elapsedMs(now) / 1000).toInt() }?.takeIf { it > 0 }
            manager.completeSet(null, draft.weightKg, measured ?: draft.durationSeconds)
        } else {
            manager.completeSet(draft.repetitions, draft.weightKg?.takeIf { it > 0 }, null)
        }
        return true
    }

    fun skipRest() = manager.skipRest()
    fun extendRest(seconds: Int) = manager.extendRest(seconds)
    fun pause() = manager.pause()
    fun resume() = manager.resume()
    fun goTo(exerciseIndex: Int, setIndex: Int) = manager.goTo(exerciseIndex, setIndex)
    fun goToExercise(exerciseIndex: Int) = manager.goToExercise(exerciseIndex)
    fun skipExercise() = manager.skipExercise()
    fun addSet(exerciseIndex: Int) = manager.addSet(exerciseIndex)
    fun reorderRemaining(keys: List<String>) = manager.reorderRemaining(keys)
    fun startTimer() = manager.startSetTimer()
    fun pauseTimer() = manager.pauseSetTimer()
    fun resetTimer() = manager.resetSetTimer()

    fun correctSet(exerciseIndex: Int, setIndex: Int, repetitions: Int?, weightKg: Double?, durationSeconds: Int?) {
        manager.updateSet(exerciseIndex, setIndex, repetitions, weightKg, durationSeconds)
        val execution = execution.value
        if (execution != null && exerciseIndex == execution.currentExerciseIndex && setIndex == execution.currentSetIndex) {
            _draft.value = _draft.value.copy(repetitions = repetitions ?: 0, weightKg = weightKg, durationSeconds = durationSeconds ?: 0)
        }
    }

    fun remainingIndices(execution: WorkoutExecution): List<Int> = WorkoutEngine.remainingExerciseIndices(execution)

    fun abandon(onDone: () -> Unit) {
        viewModelScope.launch {
            manager.cancel()
            onDone()
        }
    }

    private companion object {
        const val DOUBLE_TAP_GUARD_MS = 700L
        val HEAVY_EQUIPMENT = setOf(Equipment.BARBELL, Equipment.EZ_BAR, Equipment.SMITH_MACHINE, Equipment.CABLE, Equipment.MACHINE, Equipment.LEG_PRESS)
    }
}
