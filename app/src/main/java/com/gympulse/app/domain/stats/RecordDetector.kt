package com.gympulse.app.domain.stats

import com.gympulse.app.domain.model.ExercisePerformance
import com.gympulse.app.domain.model.PersonalRecord
import com.gympulse.app.domain.model.RecordType
import com.gympulse.app.domain.model.WorkoutExecution

/**
 * Detects personal records by comparing a performance with all earlier performances of the same
 * exercise. A first performance is never a record: there is nothing to beat yet.
 */
object RecordDetector {

    private const val EPSILON = 1e-6

    /** Display priority when several records are set on the same exercise during one workout. */
    val priority = listOf(
        RecordType.MAX_WEIGHT,
        RecordType.ESTIMATED_ONE_REP_MAX,
        RecordType.MAX_REPETITIONS,
        RecordType.MAX_DURATION,
        RecordType.SESSION_VOLUME,
        RecordType.BEST_SET_VOLUME,
    )

    fun detect(previous: List<ExercisePerformance>, current: ExercisePerformance): List<PersonalRecord> {
        if (previous.isEmpty()) return emptyList()
        val records = mutableListOf<PersonalRecord>()

        fun record(type: RecordType, value: Double?, best: Double?, reps: Int? = null, weight: Double? = null) {
            if (value == null || value <= 0) return
            if (best != null && value <= best + EPSILON) return
            records += PersonalRecord(
                exerciseId = current.exerciseId,
                exerciseName = current.exerciseName,
                type = type,
                value = value,
                previousValue = best,
                repetitions = reps,
                weightKg = weight,
                achievedAt = current.date,
                executionId = current.executionId,
            )
        }

        val bestWeight = previous.mapNotNull { it.topWeightKg }.maxOrNull()
        record(RecordType.MAX_WEIGHT, current.topWeightKg, bestWeight, current.topWeightRepetitions, current.topWeightKg)

        val bestOneRepMax = previous.mapNotNull { it.estimatedOneRepMaxKg }.maxOrNull()
        record(RecordType.ESTIMATED_ONE_REP_MAX, current.estimatedOneRepMaxKg, bestOneRepMax)

        val currentIsBodyweight = current.sets.all { (it.weightKg ?: 0.0) <= 0.0 }
        if (currentIsBodyweight) {
            val bestReps = previous.flatMap { it.sets }.filter { (it.weightKg ?: 0.0) <= 0.0 }
                .mapNotNull { it.repetitions }.maxOrNull()
            record(RecordType.MAX_REPETITIONS, current.sets.mapNotNull { it.repetitions }.maxOrNull()?.toDouble(), bestReps?.toDouble())
        }

        val bestDuration = previous.mapNotNull { it.bestDurationSeconds }.maxOrNull()
        record(RecordType.MAX_DURATION, current.bestDurationSeconds?.toDouble(), bestDuration?.toDouble())

        val bestVolume = previous.map { it.volumeKg }.filter { it > 0 }.maxOrNull()
        record(RecordType.SESSION_VOLUME, current.volumeKg, bestVolume)

        return records
    }

    /** Records set during [workout], compared with every earlier workout of [history]. */
    fun detectForWorkout(history: List<WorkoutExecution>, workout: WorkoutExecution): List<PersonalRecord> {
        val earlier = history.filter { it.id != workout.id && it.startedAt < workout.startedAt }
        val previousByExercise = TrainingMath.performancesByExercise(earlier)
        return workout.exercises.mapNotNull { TrainingMath.performance(workout, it) }.flatMap { performance ->
            val key = TrainingMath.exerciseKey(performance.exerciseId, performance.exerciseName)
            detect(previousByExercise[key].orEmpty(), performance)
        }
    }

    /** Every record ever set, in chronological order. */
    fun allRecords(history: List<WorkoutExecution>): List<PersonalRecord> =
        TrainingMath.performancesByExercise(history).values.flatMap { performances ->
            performances.indices.flatMap { index -> detect(performances.subList(0, index), performances[index]) }
        }.sortedBy { it.achievedAt }

    /** Keeps the most meaningful record per exercise and workout. */
    fun mostRelevant(records: List<PersonalRecord>): List<PersonalRecord> =
        records.groupBy { it.executionId to TrainingMath.exerciseKey(it.exerciseId, it.exerciseName) }
            .values
            .mapNotNull { group -> group.minByOrNull { priority.indexOf(it.type) } }
            .sortedByDescending { it.achievedAt }
}
