package com.gympulse.app.domain.model

enum class WorkoutStatus { IN_PROGRESS, PAUSED, COMPLETED, CANCELLED }

enum class ExerciseStatus { PENDING, IN_PROGRESS, COMPLETED, SKIPPED }

enum class SetStatus { PENDING, COMPLETED, SKIPPED }

/** A set as planned at workout start (snapshot) and as actually performed. */
data class ExecutedSet(
    val order: Int,
    val targetRepetitions: Int? = null,
    val targetWeightKg: Double? = null,
    val targetDurationSeconds: Int? = null,
    val restSeconds: Int = 0,
    val actualRepetitions: Int? = null,
    val actualWeightKg: Double? = null,
    val actualDurationSeconds: Int? = null,
    val actualRestSeconds: Int? = null,
    val status: SetStatus = SetStatus.PENDING,
    val completedAt: Long? = null,
    val isExtra: Boolean = false,
) {
    val isCompleted: Boolean get() = status == SetStatus.COMPLETED
    val isPending: Boolean get() = status == SetStatus.PENDING
}

/**
 * Snapshot of an exercise inside a workout. Name, type and load convention are copied so that the
 * history stays exact even if the exercise or the session template is edited or deleted later.
 */
data class ExecutedExercise(
    val key: String,
    val exerciseId: Long?,
    val name: String,
    val type: ExerciseType,
    val loadMode: LoadMode = LoadMode.TOTAL,
    val imageUri: String? = null,
    val muscleGroups: List<MuscleGroup> = emptyList(),
    val equipment: Set<Equipment> = emptySet(),
    val restDurationSeconds: Int = 90,
    val notes: String = "",
    val status: ExerciseStatus = ExerciseStatus.PENDING,
    val sets: List<ExecutedSet> = emptyList(),
) {
    val completedSets: List<ExecutedSet> get() = sets.filter { it.isCompleted }
    val hasPendingSets: Boolean get() = sets.any { it.isPending }
    val isTimed: Boolean get() = type == ExerciseType.DURATION
}

/** Rest countdown. Times are epoch millis from the monotonic-anchored [com.gympulse.app.core.time.TimeProvider]. */
data class RestTimer(
    val startedAt: Long,
    val endsAt: Long,
    val totalSeconds: Int,
    val exerciseKey: String,
    val setIndex: Int,
    val pausedRemainingMs: Long? = null,
) {
    val isPaused: Boolean get() = pausedRemainingMs != null

    fun remainingMs(now: Long): Long = pausedRemainingMs ?: (endsAt - now).coerceAtLeast(0)

    fun isFinished(now: Long): Boolean = remainingMs(now) <= 0L
}

/** Stopwatch used by timed (DURATION) sets. */
data class SetTimer(
    val accumulatedMs: Long = 0,
    val runningSince: Long? = null,
) {
    val isRunning: Boolean get() = runningSince != null

    fun elapsedMs(now: Long): Long = accumulatedMs + (runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0)
}

data class WorkoutExecution(
    val id: Long = 0,
    val sessionId: Long?,
    val sessionName: String,
    val trainingGoal: TrainingGoal = TrainingGoal.CUSTOM,
    val status: WorkoutStatus = WorkoutStatus.IN_PROGRESS,
    val startedAt: Long,
    val endedAt: Long? = null,
    val pausedAt: Long? = null,
    val totalPausedMs: Long = 0,
    val locationId: Long? = null,
    val locationName: String? = null,
    val notes: String = "",
    val currentExerciseIndex: Int = 0,
    val currentSetIndex: Int = 0,
    val rest: RestTimer? = null,
    val setTimer: SetTimer? = null,
    val exercises: List<ExecutedExercise> = emptyList(),
    val isDemo: Boolean = false,
) {
    val isActive: Boolean get() = status == WorkoutStatus.IN_PROGRESS || status == WorkoutStatus.PAUSED
    val isPaused: Boolean get() = status == WorkoutStatus.PAUSED

    val currentExercise: ExecutedExercise? get() = exercises.getOrNull(currentExerciseIndex)
    val currentSet: ExecutedSet? get() = currentExercise?.sets?.getOrNull(currentSetIndex)

    val totalSets: Int get() = exercises.sumOf { it.sets.size }
    val completedSetCount: Int get() = exercises.sumOf { ex -> ex.sets.count { it.isCompleted } }

    val progress: Float
        get() = if (totalSets == 0) 0f else completedSetCount.toFloat() / totalSets

    /** True when no set remains to be performed (skipped exercises are ignored). */
    val isAllDone: Boolean
        get() = exercises.none { it.status != ExerciseStatus.SKIPPED && it.hasPendingSets }

    /** Active training time, pauses excluded. */
    fun elapsedMs(now: Long): Long {
        val end = endedAt ?: pausedAt ?: now
        return (end - startedAt - totalPausedMs).coerceAtLeast(0)
    }

    val durationMs: Long get() = elapsedMs(endedAt ?: startedAt)
}
