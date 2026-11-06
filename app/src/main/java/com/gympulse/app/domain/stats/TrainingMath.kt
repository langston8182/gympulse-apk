package com.gympulse.app.domain.stats

import com.gympulse.app.domain.model.ExecutedExercise
import com.gympulse.app.domain.model.ExecutedSet
import com.gympulse.app.domain.model.ExercisePerformance
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.LoadMode
import com.gympulse.app.domain.model.PerformedSet
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.model.WorkoutTotals

object TrainingMath {

    /** Highest repetition count for which the 1RM estimation is considered meaningful. */
    const val MAX_REPS_FOR_ONE_REP_MAX = 12

    /**
     * Load actually moved by one repetition. With [LoadMode.PER_IMPLEMENT] the recorded weight is the
     * weight of one dumbbell/kettlebell, so two implements are counted.
     */
    fun effectiveLoadKg(weightKg: Double?, loadMode: LoadMode): Double {
        val weight = weightKg ?: return 0.0
        return if (loadMode == LoadMode.PER_IMPLEMENT) weight * 2 else weight
    }

    fun setVolumeKg(set: ExecutedSet, loadMode: LoadMode): Double {
        if (!set.isCompleted) return 0.0
        val reps = set.actualRepetitions ?: return 0.0
        return reps * effectiveLoadKg(set.actualWeightKg, loadMode)
    }

    fun exerciseVolumeKg(exercise: ExecutedExercise): Double =
        if (exercise.type == ExerciseType.DURATION) 0.0 else exercise.sets.sumOf { setVolumeKg(it, exercise.loadMode) }

    fun workoutVolumeKg(execution: WorkoutExecution): Double = execution.exercises.sumOf { exerciseVolumeKg(it) }

    fun completedSets(execution: WorkoutExecution): Int = execution.exercises.sumOf { ex -> ex.sets.count { it.isCompleted } }

    fun completedRepetitions(execution: WorkoutExecution): Int =
        execution.exercises.sumOf { ex -> ex.sets.filter { it.isCompleted }.sumOf { it.actualRepetitions ?: 0 } }

    /**
     * Epley estimation of the one-repetition maximum. It is an *estimate*, only computed for sets of
     * 1 to [MAX_REPS_FOR_ONE_REP_MAX] repetitions with a load, where the formula remains reasonable.
     * Computed on the recorded weight, so it follows the exercise load convention.
     */
    fun estimatedOneRepMax(weightKg: Double?, repetitions: Int?): Double? {
        if (weightKg == null || repetitions == null) return null
        if (weightKg <= 0.0 || repetitions <= 0 || repetitions > MAX_REPS_FOR_ONE_REP_MAX) return null
        if (repetitions == 1) return weightKg
        return weightKg * (1 + repetitions / 30.0)
    }

    fun totals(workouts: List<WorkoutExecution>): WorkoutTotals = WorkoutTotals(
        workouts = workouts.size,
        durationMs = workouts.sumOf { it.durationMs },
        volumeKg = workouts.sumOf { workoutVolumeKg(it) },
        sets = workouts.sumOf { completedSets(it) },
        repetitions = workouts.sumOf { completedRepetitions(it) },
    )

    fun performance(execution: WorkoutExecution, exercise: ExecutedExercise): ExercisePerformance? {
        val sets = exercise.sets.filter { it.isCompleted }
        if (sets.isEmpty()) return null
        val loaded = sets.filter { (it.actualWeightKg ?: 0.0) > 0 && (it.actualRepetitions ?: 0) > 0 }
        val topWeight = loaded.maxOfOrNull { it.actualWeightKg!! }
        val topWeightReps = topWeight?.let { top -> loaded.filter { it.actualWeightKg == top }.maxOf { it.actualRepetitions!! } }
        return ExercisePerformance(
            executionId = execution.id,
            sessionName = execution.sessionName,
            date = execution.startedAt,
            exerciseId = exercise.exerciseId,
            exerciseName = exercise.name,
            type = exercise.type,
            loadMode = exercise.loadMode,
            sets = sets.map { PerformedSet(it.actualRepetitions, it.actualWeightKg, it.actualDurationSeconds) },
            topWeightKg = topWeight,
            topWeightRepetitions = topWeightReps,
            estimatedOneRepMaxKg = sets.mapNotNull { estimatedOneRepMax(it.actualWeightKg, it.actualRepetitions) }.maxOrNull(),
            volumeKg = exerciseVolumeKg(exercise),
            totalRepetitions = sets.sumOf { it.actualRepetitions ?: 0 },
            bestDurationSeconds = sets.mapNotNull { it.actualDurationSeconds }.maxOrNull(),
            totalDurationSeconds = sets.sumOf { it.actualDurationSeconds ?: 0 },
        )
    }

    /** Identity used to group the history of an exercise, robust to deleted exercises. */
    fun exerciseKey(exerciseId: Long?, name: String): String = exerciseId?.let { "id:$it" } ?: "name:${name.trim().lowercase()}"

    fun performancesByExercise(history: List<WorkoutExecution>): Map<String, List<ExercisePerformance>> =
        history.sortedBy { it.startedAt }
            .flatMap { execution -> execution.exercises.mapNotNull { performance(execution, it) } }
            .groupBy { exerciseKey(it.exerciseId, it.exerciseName) }
}
