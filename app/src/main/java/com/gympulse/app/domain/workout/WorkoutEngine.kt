package com.gympulse.app.domain.workout

import com.gympulse.app.domain.model.ExecutedExercise
import com.gympulse.app.domain.model.ExecutedSet
import com.gympulse.app.domain.model.ExerciseStatus
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.RestTimer
import com.gympulse.app.domain.model.SetStatus
import com.gympulse.app.domain.model.SetTimer
import com.gympulse.app.domain.model.TrainingLocation
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.model.WorkoutSession
import com.gympulse.app.domain.model.WorkoutStatus
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Pure state machine driving a workout. Every function takes the current [WorkoutExecution] and
 * returns a new one; nothing here touches Android, the database or the clock, which makes the
 * whole workout flow deterministic and unit-testable.
 */
object WorkoutEngine {

    data class Position(val exerciseIndex: Int, val setIndex: Int)

    fun create(
        session: WorkoutSession,
        now: Long,
        location: TrainingLocation? = null,
        keyFactory: () -> String = { UUID.randomUUID().toString() },
    ): WorkoutExecution {
        val exercises = session.exercises
            .sortedBy { it.order }
            .filter { it.sets.isNotEmpty() }
            .map { sessionExercise ->
                val exercise = sessionExercise.exercise
                val timed = exercise.type == ExerciseType.DURATION
                ExecutedExercise(
                    key = keyFactory(),
                    exerciseId = exercise.id,
                    name = exercise.name,
                    type = exercise.type,
                    loadMode = exercise.loadMode,
                    imageUri = exercise.imageUri,
                    muscleGroups = exercise.muscleGroups,
                    equipment = exercise.equipment,
                    restDurationSeconds = sessionExercise.restDurationSeconds,
                    notes = sessionExercise.notes,
                    sets = sessionExercise.sets.mapIndexed { index, target ->
                        ExecutedSet(
                            order = index,
                            targetRepetitions = if (timed) null else target.targetRepetitions,
                            targetWeightKg = target.targetWeightKg,
                            targetDurationSeconds = if (timed) target.targetDurationSeconds else null,
                            restSeconds = sessionExercise.restAfterSet(index),
                        )
                    },
                )
            }
        require(exercises.isNotEmpty()) { "A workout needs at least one exercise with one set" }
        return normalize(
            WorkoutExecution(
                sessionId = session.id,
                sessionName = session.name,
                trainingGoal = session.trainingGoal,
                status = WorkoutStatus.IN_PROGRESS,
                startedAt = now,
                locationId = location?.id,
                locationName = location?.name,
                exercises = exercises,
                isDemo = false,
            )
        )
    }

    /**
     * Records the current set, moves to the next set (or next exercise) and starts the rest timer
     * when another effort follows. Completing an already completed set (after navigating back)
     * simply corrects it and returns to the next pending set without starting a rest.
     */
    fun completeCurrentSet(
        execution: WorkoutExecution,
        repetitions: Int?,
        weightKg: Double?,
        durationSeconds: Int?,
        now: Long,
    ): WorkoutExecution {
        if (execution.status != WorkoutStatus.IN_PROGRESS) return execution
        val exerciseIndex = execution.currentExerciseIndex
        val setIndex = execution.currentSetIndex
        val exercise = execution.exercises.getOrNull(exerciseIndex) ?: return execution
        val set = exercise.sets.getOrNull(setIndex) ?: return execution
        val wasAlreadyCompleted = set.isCompleted

        var result = endRest(execution, now)
        val measuredDuration = durationSeconds
            ?: if (exercise.isTimed) result.setTimer?.let { (it.elapsedMs(now) / 1000).toInt() } else null
        val updatedSet = set.copy(
            actualRepetitions = if (exercise.isTimed) null else repetitions,
            actualWeightKg = weightKg,
            actualDurationSeconds = if (exercise.isTimed) measuredDuration ?: 0 else null,
            status = SetStatus.COMPLETED,
            completedAt = if (wasAlreadyCompleted) set.completedAt else now,
        )
        result = result.updateExercise(exerciseIndex) { it.replaceSet(setIndex, updatedSet) }
            .copy(setTimer = null)

        val next = findNextPosition(result, exerciseIndex, setIndex) ?: return normalize(result)
        result = result.copy(currentExerciseIndex = next.exerciseIndex, currentSetIndex = next.setIndex)
        if (!wasAlreadyCompleted && updatedSet.restSeconds > 0) {
            result = result.copy(
                rest = RestTimer(
                    startedAt = now,
                    endsAt = now + updatedSet.restSeconds * 1000L,
                    totalSeconds = updatedSet.restSeconds,
                    exerciseKey = exercise.key,
                    setIndex = setIndex,
                )
            )
        }
        return normalize(result)
    }

    /** Corrects the values of an already recorded set without changing the current position. */
    fun updateSet(
        execution: WorkoutExecution,
        exerciseIndex: Int,
        setIndex: Int,
        repetitions: Int?,
        weightKg: Double?,
        durationSeconds: Int?,
    ): WorkoutExecution {
        val exercise = execution.exercises.getOrNull(exerciseIndex) ?: return execution
        val set = exercise.sets.getOrNull(setIndex) ?: return execution
        if (!set.isCompleted) return execution
        val updated = set.copy(
            actualRepetitions = if (exercise.isTimed) null else repetitions,
            actualWeightKg = weightKg,
            actualDurationSeconds = if (exercise.isTimed) durationSeconds else null,
        )
        return execution.updateExercise(exerciseIndex) { it.replaceSet(setIndex, updated) }
    }

    /** Ends the rest (skip, acknowledgement or next effort) and records the real rest duration. */
    fun endRest(execution: WorkoutExecution, now: Long): WorkoutExecution {
        val rest = execution.rest ?: return execution
        val effectiveEnd = if (rest.isPaused) {
            rest.endsAt - (rest.pausedRemainingMs ?: 0)
        } else {
            min(now, rest.endsAt)
        }
        val actualSeconds = ((effectiveEnd - rest.startedAt) / 1000).toInt().coerceAtLeast(0)
        val exerciseIndex = execution.exercises.indexOfFirst { it.key == rest.exerciseKey }
        val withRecordedRest = if (exerciseIndex >= 0) {
            execution.updateExercise(exerciseIndex) { exercise ->
                val set = exercise.sets.getOrNull(rest.setIndex)
                if (set == null) exercise else exercise.replaceSet(rest.setIndex, set.copy(actualRestSeconds = actualSeconds))
            }
        } else {
            execution
        }
        return withRecordedRest.copy(rest = null)
    }

    fun skipRest(execution: WorkoutExecution, now: Long): WorkoutExecution = endRest(execution, now)

    /** Adds time to the rest. If the rest already ended, a new countdown of [seconds] starts now. */
    fun extendRest(execution: WorkoutExecution, seconds: Int, now: Long): WorkoutExecution {
        val rest = execution.rest ?: return execution
        if (seconds == 0) return execution
        val extended = if (rest.isPaused) {
            val remaining = ((rest.pausedRemainingMs ?: 0) + seconds * 1000L).coerceAtLeast(0)
            rest.copy(pausedRemainingMs = remaining, totalSeconds = max(0, rest.totalSeconds + seconds))
        } else {
            val base = if (seconds > 0) max(rest.endsAt, now) else rest.endsAt
            val endsAt = max(now, base + seconds * 1000L)
            rest.copy(endsAt = endsAt, totalSeconds = ((endsAt - rest.startedAt) / 1000).toInt())
        }
        return execution.copy(rest = extended)
    }

    fun pause(execution: WorkoutExecution, now: Long): WorkoutExecution {
        if (execution.status != WorkoutStatus.IN_PROGRESS) return execution
        return execution.copy(
            status = WorkoutStatus.PAUSED,
            pausedAt = now,
            rest = execution.rest?.let { if (it.isPaused) it else it.copy(pausedRemainingMs = it.remainingMs(now)) },
            setTimer = execution.setTimer?.let { SetTimer(accumulatedMs = it.elapsedMs(now), runningSince = null) },
        )
    }

    fun resume(execution: WorkoutExecution, now: Long): WorkoutExecution {
        if (execution.status != WorkoutStatus.PAUSED) return execution
        val pauseDuration = (now - (execution.pausedAt ?: now)).coerceAtLeast(0)
        return execution.copy(
            status = WorkoutStatus.IN_PROGRESS,
            pausedAt = null,
            totalPausedMs = execution.totalPausedMs + pauseDuration,
            rest = execution.rest?.let { rest ->
                val remaining = rest.pausedRemainingMs ?: rest.remainingMs(now)
                rest.copy(
                    startedAt = rest.startedAt + pauseDuration,
                    endsAt = now + remaining,
                    pausedRemainingMs = null,
                )
            },
        )
    }

    /** Navigates to any set, for instance to review or correct a previous one. */
    fun goTo(execution: WorkoutExecution, exerciseIndex: Int, setIndex: Int): WorkoutExecution {
        val exercise = execution.exercises.getOrNull(exerciseIndex) ?: return execution
        if (setIndex !in exercise.sets.indices) return execution
        if (exerciseIndex == execution.currentExerciseIndex && setIndex == execution.currentSetIndex) return execution
        val unskipped = if (exercise.status == ExerciseStatus.SKIPPED) {
            execution.updateExercise(exerciseIndex) { it.copy(status = ExerciseStatus.PENDING) }
        } else {
            execution
        }
        return normalize(
            unskipped.copy(currentExerciseIndex = exerciseIndex, currentSetIndex = setIndex, setTimer = null)
        )
    }

    /** Moves to the first pending set of an exercise. */
    fun goToExercise(execution: WorkoutExecution, exerciseIndex: Int): WorkoutExecution {
        val exercise = execution.exercises.getOrNull(exerciseIndex) ?: return execution
        val setIndex = exercise.sets.indexOfFirst { it.isPending }.takeIf { it >= 0 } ?: exercise.sets.lastIndex
        return goTo(execution, exerciseIndex, setIndex)
    }

    fun skipCurrentExercise(execution: WorkoutExecution): WorkoutExecution {
        val index = execution.currentExerciseIndex
        if (execution.exercises.getOrNull(index) == null) return execution
        val skipped = execution.updateExercise(index) { it.copy(status = ExerciseStatus.SKIPPED) }.copy(setTimer = null)
        val next = findNextExercisePosition(skipped, index) ?: return normalize(skipped)
        return normalize(skipped.copy(currentExerciseIndex = next.exerciseIndex, currentSetIndex = next.setIndex))
    }

    /** Appends a set to an exercise, copying the targets of its last set. */
    fun addSet(execution: WorkoutExecution, exerciseIndex: Int): WorkoutExecution {
        val exercise = execution.exercises.getOrNull(exerciseIndex) ?: return execution
        val template = exercise.sets.lastOrNull()
        val newSet = ExecutedSet(
            order = exercise.sets.size,
            targetRepetitions = template?.targetRepetitions,
            targetWeightKg = template?.actualWeightKg ?: template?.targetWeightKg,
            targetDurationSeconds = template?.targetDurationSeconds,
            restSeconds = template?.restSeconds ?: exercise.restDurationSeconds,
            isExtra = true,
        )
        var result = execution.updateExercise(exerciseIndex) {
            it.copy(
                sets = it.sets + newSet,
                status = if (it.status == ExerciseStatus.SKIPPED) ExerciseStatus.PENDING else it.status,
            )
        }
        val currentSetPending = result.currentSet?.isPending == true &&
            result.currentExercise?.status != ExerciseStatus.SKIPPED
        if (!currentSetPending) {
            result = result.copy(currentExerciseIndex = exerciseIndex, currentSetIndex = newSet.order, setTimer = null)
        }
        return normalize(result)
    }

    /**
     * Reorders the exercises that have not been started yet. [orderedKeys] is the new order of
     * those remaining exercises; they are placed back into the slots they occupied.
     */
    fun reorderRemaining(execution: WorkoutExecution, orderedKeys: List<String>): WorkoutExecution {
        val remainingSlots = remainingExerciseIndices(execution)
        val remainingKeys = remainingSlots.map { execution.exercises[it].key }
        if (orderedKeys.size != remainingKeys.size || orderedKeys.toSet() != remainingKeys.toSet()) return execution
        val byKey = execution.exercises.associateBy { it.key }
        val reordered = execution.exercises.toMutableList()
        remainingSlots.forEachIndexed { slotPosition, slot -> reordered[slot] = byKey.getValue(orderedKeys[slotPosition]) }
        return execution.copy(exercises = reordered)
    }

    /** Indices of exercises that were not started and are not the current one. */
    fun remainingExerciseIndices(execution: WorkoutExecution): List<Int> =
        execution.exercises.indices.filter { index ->
            val exercise = execution.exercises[index]
            index != execution.currentExerciseIndex &&
                exercise.status == ExerciseStatus.PENDING &&
                exercise.sets.none { it.isCompleted }
        }

    fun startSetTimer(execution: WorkoutExecution, now: Long): WorkoutExecution {
        if (execution.status != WorkoutStatus.IN_PROGRESS) return execution
        val timer = execution.setTimer ?: SetTimer()
        if (timer.isRunning) return execution
        return endRest(execution, now).copy(setTimer = timer.copy(runningSince = now))
    }

    fun pauseSetTimer(execution: WorkoutExecution, now: Long): WorkoutExecution {
        val timer = execution.setTimer ?: return execution
        if (!timer.isRunning) return execution
        return execution.copy(setTimer = SetTimer(accumulatedMs = timer.elapsedMs(now), runningSince = null))
    }

    fun resetSetTimer(execution: WorkoutExecution): WorkoutExecution = execution.copy(setTimer = null)

    fun updateNotes(execution: WorkoutExecution, notes: String): WorkoutExecution = execution.copy(notes = notes)

    fun finish(execution: WorkoutExecution, now: Long, notes: String? = null): WorkoutExecution =
        close(execution, now, WorkoutStatus.COMPLETED, notes)

    fun cancel(execution: WorkoutExecution, now: Long): WorkoutExecution =
        close(execution, now, WorkoutStatus.CANCELLED, null)

    private fun close(execution: WorkoutExecution, now: Long, status: WorkoutStatus, notes: String?): WorkoutExecution {
        val running = if (execution.status == WorkoutStatus.PAUSED) resume(execution, now) else execution
        val rested = endRest(running, now)
        return rested.copy(
            status = status,
            endedAt = now,
            pausedAt = null,
            setTimer = null,
            notes = notes ?: rested.notes,
            exercises = rested.exercises.map { exercise ->
                val sets = exercise.sets.map { if (it.isPending) it.copy(status = SetStatus.SKIPPED) else it }
                exercise.copy(
                    sets = sets,
                    status = if (sets.any { it.isCompleted }) ExerciseStatus.COMPLETED else ExerciseStatus.SKIPPED,
                )
            },
        )
    }

    fun findNextPosition(execution: WorkoutExecution, exerciseIndex: Int, setIndex: Int): Position? {
        val exercise = execution.exercises.getOrNull(exerciseIndex) ?: return null
        if (exercise.status != ExerciseStatus.SKIPPED) {
            val after = (setIndex + 1 until exercise.sets.size).firstOrNull { exercise.sets[it].isPending }
            val any = after ?: exercise.sets.indices.firstOrNull { exercise.sets[it].isPending }
            if (any != null) return Position(exerciseIndex, any)
        }
        return findNextExercisePosition(execution, exerciseIndex)
    }

    private fun findNextExercisePosition(execution: WorkoutExecution, fromExerciseIndex: Int): Position? {
        val size = execution.exercises.size
        val candidates = (fromExerciseIndex + 1 until size) + (0 until fromExerciseIndex)
        for (index in candidates) {
            val candidate = execution.exercises[index]
            if (candidate.status == ExerciseStatus.SKIPPED) continue
            val firstPending = candidate.sets.indexOfFirst { it.isPending }
            if (firstPending >= 0) return Position(index, firstPending)
        }
        return null
    }

    /** Recomputes every exercise status from its sets and the current position. */
    fun normalize(execution: WorkoutExecution): WorkoutExecution {
        val exercises = execution.exercises.mapIndexed { index, exercise ->
            val status = when {
                exercise.status == ExerciseStatus.SKIPPED -> ExerciseStatus.SKIPPED
                !exercise.hasPendingSets -> ExerciseStatus.COMPLETED
                index == execution.currentExerciseIndex -> ExerciseStatus.IN_PROGRESS
                exercise.sets.any { it.isCompleted } -> ExerciseStatus.IN_PROGRESS
                else -> ExerciseStatus.PENDING
            }
            if (status == exercise.status) exercise else exercise.copy(status = status)
        }
        return execution.copy(exercises = exercises)
    }

    private fun WorkoutExecution.updateExercise(index: Int, transform: (ExecutedExercise) -> ExecutedExercise): WorkoutExecution =
        copy(exercises = exercises.mapIndexed { i, exercise -> if (i == index) transform(exercise) else exercise })

    private fun ExecutedExercise.replaceSet(index: Int, set: ExecutedSet): ExecutedExercise =
        copy(sets = sets.mapIndexed { i, existing -> if (i == index) set else existing })
}
