package com.gympulse.app.domain.model

enum class StatsPeriod(val days: Int?) {
    WEEK(7), MONTH(30), QUARTER(91), HALF_YEAR(182), YEAR(365), ALL(null)
}

enum class Granularity { DAY, WEEK, MONTH }

data class SeriesPoint(val timestamp: Long, val value: Double)

enum class RecordType { MAX_WEIGHT, ESTIMATED_ONE_REP_MAX, BEST_SET_VOLUME, SESSION_VOLUME, MAX_REPETITIONS, MAX_DURATION }

data class PersonalRecord(
    val exerciseId: Long?,
    val exerciseName: String,
    val type: RecordType,
    val value: Double,
    val previousValue: Double?,
    val repetitions: Int? = null,
    val weightKg: Double? = null,
    val achievedAt: Long,
    val executionId: Long,
)

data class PerformedSet(
    val repetitions: Int?,
    val weightKg: Double?,
    val durationSeconds: Int?,
)

/** Everything one exercise produced during one workout. */
data class ExercisePerformance(
    val executionId: Long,
    val sessionName: String,
    val date: Long,
    val exerciseId: Long?,
    val exerciseName: String,
    val type: ExerciseType,
    val loadMode: LoadMode,
    val sets: List<PerformedSet>,
    val topWeightKg: Double?,
    val topWeightRepetitions: Int?,
    val estimatedOneRepMaxKg: Double?,
    val volumeKg: Double,
    val totalRepetitions: Int,
    val bestDurationSeconds: Int?,
    val totalDurationSeconds: Int,
)

data class RepMax(val repetitions: Int, val weightKg: Double, val achievedAt: Long)

data class PeriodComparison(
    val currentSessions: Int,
    val previousSessions: Int,
    val currentBestOneRepMax: Double?,
    val previousBestOneRepMax: Double?,
    val currentMaxWeight: Double?,
    val previousMaxWeight: Double?,
    val currentVolume: Double,
    val previousVolume: Double,
    val currentBestDuration: Int?,
    val previousBestDuration: Int?,
)

data class ExerciseProgress(
    val exerciseId: Long,
    val performances: List<ExercisePerformance>,
    val records: List<PersonalRecord>,
    val repMaxes: List<RepMax>,
    val maxWeightKg: Double?,
    val bestOneRepMaxKg: Double?,
    val bestSetVolumeKg: Double?,
    val bestSessionVolumeKg: Double?,
    val maxRepetitions: Int?,
    val maxDurationSeconds: Int?,
    val totalSets: Int,
    val totalRepetitions: Int,
)

data class ExerciseTrend(
    val exerciseId: Long?,
    val exerciseName: String,
    val type: ExerciseType,
    val sessions: Int,
    val series: List<SeriesPoint>,
    val firstValue: Double?,
    val lastValue: Double?,
) {
    val changeRatio: Double?
        get() = if (firstValue != null && lastValue != null && firstValue > 0) (lastValue - firstValue) / firstValue else null
}

data class WorkoutTotals(
    val workouts: Int = 0,
    val durationMs: Long = 0,
    val volumeKg: Double = 0.0,
    val sets: Int = 0,
    val repetitions: Int = 0,
)

data class DashboardStats(
    val period: StatsPeriod,
    val periodTotals: WorkoutTotals,
    val previousPeriodTotals: WorkoutTotals,
    val thisWeek: WorkoutTotals,
    val lastWeek: WorkoutTotals,
    val frequencyPerWeek: Double,
    val granularity: Granularity,
    val volumeSeries: List<SeriesPoint>,
    val durationSeries: List<SeriesPoint>,
    /** Workouts per bucket; never finer than a week, daily counts being meaningless. */
    val frequencySeries: List<SeriesPoint>,
    val frequencyGranularity: Granularity,
    val muscleDistribution: List<Pair<MuscleGroup, Int>>,
    val recentRecords: List<PersonalRecord>,
    val topExercises: List<ExerciseTrend>,
    val lastWorkout: WorkoutExecution?,
    val bestSessionVolumeKg: Double?,
    val totalWorkouts: Int,
)
