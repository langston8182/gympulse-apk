package com.gympulse.app.domain.stats

import com.gympulse.app.domain.model.DashboardStats
import com.gympulse.app.domain.model.ExercisePerformance
import com.gympulse.app.domain.model.ExerciseProgress
import com.gympulse.app.domain.model.ExerciseTrend
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.Granularity
import com.gympulse.app.domain.model.MuscleGroup
import com.gympulse.app.domain.model.PeriodComparison
import com.gympulse.app.domain.model.RepMax
import com.gympulse.app.domain.model.SeriesPoint
import com.gympulse.app.domain.model.StatsPeriod
import com.gympulse.app.domain.model.WorkoutExecution
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class ExerciseMetric { ESTIMATED_ONE_REP_MAX, MAX_WEIGHT, WEIGHT_AT_REPS, VOLUME, REPETITIONS, BEST_DURATION, TOTAL_DURATION }

object StatsCalculator {

    val repMaxTargets = listOf(1, 3, 5, 8, 10, 12, 15)

    data class Range(val start: Long, val end: Long) {
        operator fun contains(time: Long): Boolean = time in start until end
    }

    fun periodRange(period: StatsPeriod, now: Long, zone: ZoneId, firstWorkoutAt: Long?): Range {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val startDate = when (val days = period.days) {
            null -> firstWorkoutAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: today
            else -> today.minusDays(days.toLong() - 1)
        }
        return Range(startDate.atStartOfDay(zone).toInstant().toEpochMilli(), end)
    }

    fun previousRange(range: Range): Range = Range(range.start - (range.end - range.start), range.start)

    fun weekRange(now: Long, zone: ZoneId, weeksAgo: Long = 0): Range {
        val monday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks(weeksAgo)
        return Range(
            monday.atStartOfDay(zone).toInstant().toEpochMilli(),
            monday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }

    fun granularityFor(period: StatsPeriod, range: Range): Granularity = when (period) {
        StatsPeriod.WEEK, StatsPeriod.MONTH -> Granularity.DAY
        StatsPeriod.QUARTER, StatsPeriod.HALF_YEAR -> Granularity.WEEK
        StatsPeriod.YEAR -> Granularity.MONTH
        StatsPeriod.ALL -> if (range.end - range.start <= 120L * DAY_MS) Granularity.WEEK else Granularity.MONTH
    }

    fun bucketStarts(range: Range, granularity: Granularity, zone: ZoneId): List<Long> {
        val startDate = Instant.ofEpochMilli(range.start).atZone(zone).toLocalDate()
        var cursor: LocalDate = when (granularity) {
            Granularity.DAY -> startDate
            Granularity.WEEK -> startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            Granularity.MONTH -> startDate.withDayOfMonth(1)
        }
        val buckets = mutableListOf<Long>()
        while (true) {
            val millis = cursor.atStartOfDay(zone).toInstant().toEpochMilli()
            if (millis >= range.end) break
            buckets += millis
            cursor = when (granularity) {
                Granularity.DAY -> cursor.plusDays(1)
                Granularity.WEEK -> cursor.plusWeeks(1)
                Granularity.MONTH -> cursor.plusMonths(1)
            }
        }
        return buckets
    }

    private fun <T> bucketize(
        buckets: List<Long>,
        items: List<T>,
        timeOf: (T) -> Long,
        valueOf: (List<T>) -> Double,
    ): List<SeriesPoint> {
        if (buckets.isEmpty()) return emptyList()
        val grouped = items.groupBy { item ->
            val time = timeOf(item)
            val index = buckets.indexOfLast { it <= time }
            if (index < 0) 0 else index
        }
        return buckets.mapIndexed { index, start -> SeriesPoint(start, valueOf(grouped[index].orEmpty())) }
    }

    fun dashboard(history: List<WorkoutExecution>, period: StatsPeriod, now: Long, zone: ZoneId): DashboardStats {
        val sorted = history.sortedBy { it.startedAt }
        val range = periodRange(period, now, zone, sorted.firstOrNull()?.startedAt)
        val previous = previousRange(range)
        val inPeriod = sorted.filter { it.startedAt in range }
        val inPrevious = sorted.filter { it.startedAt in previous }
        val thisWeek = weekRange(now, zone)
        val lastWeek = weekRange(now, zone, 1)
        val granularity = granularityFor(period, range)
        val buckets = bucketStarts(range, granularity, zone)
        val frequencyGranularity = if (granularity == Granularity.DAY) Granularity.WEEK else granularity
        val frequencyBuckets = bucketStarts(range, frequencyGranularity, zone)
        val periodDays = ((range.end - range.start) / DAY_MS).coerceAtLeast(1)

        val muscleDistribution = inPeriod
            .flatMap { execution -> execution.exercises.flatMap { exercise -> List(exercise.sets.count { it.isCompleted }) { exercise.muscleGroups.firstOrNull() } } }
            .filterNotNull()
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }

        val recordsFrom = now - 30L * DAY_MS
        val recentRecords = RecordDetector.mostRelevant(RecordDetector.allRecords(sorted))
            .filter { it.achievedAt >= minOf(recordsFrom, range.start) }
            .take(8)

        return DashboardStats(
            period = period,
            periodTotals = TrainingMath.totals(inPeriod),
            previousPeriodTotals = TrainingMath.totals(inPrevious),
            thisWeek = TrainingMath.totals(sorted.filter { it.startedAt in thisWeek }),
            lastWeek = TrainingMath.totals(sorted.filter { it.startedAt in lastWeek }),
            frequencyPerWeek = inPeriod.size / (periodDays / 7.0).coerceAtLeast(1.0),
            granularity = granularity,
            volumeSeries = bucketize(buckets, inPeriod, { it.startedAt }) { list -> list.sumOf { TrainingMath.workoutVolumeKg(it) } },
            durationSeries = bucketize(buckets, inPeriod, { it.startedAt }) { list -> list.sumOf { it.durationMs } / 60_000.0 },
            frequencySeries = bucketize(frequencyBuckets, inPeriod, { it.startedAt }) { list -> list.size.toDouble() },
            frequencyGranularity = frequencyGranularity,
            muscleDistribution = muscleDistribution,
            recentRecords = recentRecords,
            topExercises = topExercises(inPeriod),
            lastWorkout = sorted.lastOrNull(),
            bestSessionVolumeKg = sorted.maxOfOrNull { TrainingMath.workoutVolumeKg(it) },
            totalWorkouts = sorted.size,
        )
    }

    fun topExercises(workouts: List<WorkoutExecution>, limit: Int = 4): List<ExerciseTrend> =
        TrainingMath.performancesByExercise(workouts).values
            .sortedByDescending { it.size }
            .take(limit)
            .map { performances ->
                val first = performances.first()
                val metric = defaultMetric(first.type, performances)
                val series = series(performances, metric)
                ExerciseTrend(
                    exerciseId = first.exerciseId,
                    exerciseName = performances.last().exerciseName,
                    type = first.type,
                    sessions = performances.size,
                    series = series,
                    firstValue = series.firstOrNull()?.value,
                    lastValue = series.lastOrNull()?.value,
                )
            }

    fun defaultMetric(type: ExerciseType, performances: List<ExercisePerformance>): ExerciseMetric = when {
        type == ExerciseType.DURATION -> ExerciseMetric.BEST_DURATION
        performances.any { it.estimatedOneRepMaxKg != null } -> ExerciseMetric.ESTIMATED_ONE_REP_MAX
        else -> ExerciseMetric.REPETITIONS
    }

    /**
     * One point per workout for the chosen metric. [WEIGHT_AT_REPS][ExerciseMetric.WEIGHT_AT_REPS]
     * only compares sets of at least [minRepetitions] reps, so that a heavier set done with far fewer
     * repetitions is not presented as an equivalent progression.
     */
    fun series(performances: List<ExercisePerformance>, metric: ExerciseMetric, minRepetitions: Int = 5): List<SeriesPoint> =
        performances.sortedBy { it.date }.mapNotNull { performance ->
            val value: Double? = when (metric) {
                ExerciseMetric.ESTIMATED_ONE_REP_MAX -> performance.estimatedOneRepMaxKg
                ExerciseMetric.MAX_WEIGHT -> performance.topWeightKg
                ExerciseMetric.WEIGHT_AT_REPS -> performance.sets
                    .filter { (it.repetitions ?: 0) >= minRepetitions && (it.weightKg ?: 0.0) > 0 }
                    .maxOfOrNull { it.weightKg!! }
                ExerciseMetric.VOLUME -> performance.volumeKg.takeIf { it > 0 }
                ExerciseMetric.REPETITIONS -> performance.totalRepetitions.toDouble().takeIf { it > 0 }
                ExerciseMetric.BEST_DURATION -> performance.bestDurationSeconds?.toDouble()
                ExerciseMetric.TOTAL_DURATION -> performance.totalDurationSeconds.toDouble().takeIf { it > 0 }
            }
            value?.let { SeriesPoint(performance.date, it) }
        }

    fun exerciseProgress(exerciseId: Long, history: List<WorkoutExecution>): ExerciseProgress {
        val performances = TrainingMath.performancesByExercise(history)[TrainingMath.exerciseKey(exerciseId, "")].orEmpty()
        val allSets = performances.flatMap { performance -> performance.sets.map { performance to it } }
        val repMaxes = repMaxTargets.mapNotNull { target ->
            allSets.filter { (_, set) -> (set.repetitions ?: 0) >= target && (set.weightKg ?: 0.0) > 0 }
                .maxByOrNull { (_, set) -> set.weightKg!! }
                ?.let { (performance, set) -> RepMax(target, set.weightKg!!, performance.date) }
        }
        val records = performances.indices.flatMap { index ->
            RecordDetector.detect(performances.subList(0, index), performances[index])
        }.sortedByDescending { it.achievedAt }
        return ExerciseProgress(
            exerciseId = exerciseId,
            performances = performances,
            records = records,
            repMaxes = repMaxes,
            maxWeightKg = performances.mapNotNull { it.topWeightKg }.maxOrNull(),
            bestOneRepMaxKg = performances.mapNotNull { it.estimatedOneRepMaxKg }.maxOrNull(),
            bestSetVolumeKg = allSets.mapNotNull { (performance, set) ->
                val reps = set.repetitions ?: return@mapNotNull null
                reps * TrainingMath.effectiveLoadKg(set.weightKg, performance.loadMode)
            }.filter { it > 0 }.maxOrNull(),
            bestSessionVolumeKg = performances.map { it.volumeKg }.filter { it > 0 }.maxOrNull(),
            maxRepetitions = allSets.mapNotNull { it.second.repetitions }.maxOrNull(),
            maxDurationSeconds = performances.mapNotNull { it.bestDurationSeconds }.maxOrNull(),
            totalSets = allSets.size,
            totalRepetitions = performances.sumOf { it.totalRepetitions },
        )
    }

    fun comparePeriods(performances: List<ExercisePerformance>, period: StatsPeriod, now: Long, zone: ZoneId): PeriodComparison {
        val range = periodRange(period, now, zone, performances.minOfOrNull { it.date })
        val previous = previousRange(range)
        val current = performances.filter { it.date in range }
        val before = performances.filter { it.date in previous }
        return PeriodComparison(
            currentSessions = current.size,
            previousSessions = before.size,
            currentBestOneRepMax = current.mapNotNull { it.estimatedOneRepMaxKg }.maxOrNull(),
            previousBestOneRepMax = before.mapNotNull { it.estimatedOneRepMaxKg }.maxOrNull(),
            currentMaxWeight = current.mapNotNull { it.topWeightKg }.maxOrNull(),
            previousMaxWeight = before.mapNotNull { it.topWeightKg }.maxOrNull(),
            currentVolume = current.sumOf { it.volumeKg },
            previousVolume = before.sumOf { it.volumeKg },
            currentBestDuration = current.mapNotNull { it.bestDurationSeconds }.maxOrNull(),
            previousBestDuration = before.mapNotNull { it.bestDurationSeconds }.maxOrNull(),
        )
    }

    fun muscleLabelOrder(distribution: List<Pair<MuscleGroup, Int>>): List<Pair<MuscleGroup, Int>> =
        distribution.sortedByDescending { it.second }

    fun daysBetween(from: Long, to: Long, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(from).atZone(zone).toLocalDate(),
            Instant.ofEpochMilli(to).atZone(zone).toLocalDate(),
        )

    const val DAY_MS = 86_400_000L
}
