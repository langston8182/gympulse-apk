package com.gympulse.app.domain

import com.gympulse.app.domain.model.LoadMode
import com.gympulse.app.domain.model.RecordType
import com.gympulse.app.domain.model.StatsPeriod
import com.gympulse.app.domain.stats.ExerciseMetric
import com.gympulse.app.domain.stats.RecordDetector
import com.gympulse.app.domain.stats.StatsCalculator
import com.gympulse.app.domain.stats.TrainingMath
import com.gympulse.app.testing.TestData
import com.gympulse.app.testing.TestData.DAY
import com.gympulse.app.testing.TestData.benchDumbbell
import com.gympulse.app.testing.TestData.completedSet
import com.gympulse.app.testing.TestData.completedWorkout
import com.gympulse.app.testing.TestData.executedExercise
import com.gympulse.app.testing.TestData.plank
import com.gympulse.app.testing.TestData.pullUp
import com.gympulse.app.testing.TestData.squat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class TrainingMathTest {

    @Test
    fun `volume is repetitions times load, and counts both dumbbells when load is per implement`() {
        val set = completedSet(0, 10, 20.0)
        assertEquals(200.0, TrainingMath.setVolumeKg(set, LoadMode.TOTAL), 0.0)
        assertEquals(400.0, TrainingMath.setVolumeKg(set, LoadMode.PER_IMPLEMENT), 0.0)
    }

    @Test
    fun `pending sets, bodyweight sets and timed exercises add no volume`() {
        assertEquals(0.0, TrainingMath.setVolumeKg(completedSet(0, 10, 20.0).copy(status = com.gympulse.app.domain.model.SetStatus.PENDING), LoadMode.TOTAL), 0.0)
        assertEquals(0.0, TrainingMath.setVolumeKg(completedSet(0, 12, null), LoadMode.TOTAL), 0.0)
        val timed = executedExercise(plank, listOf(completedSet(0, null, null, 45)))
        assertEquals(0.0, TrainingMath.exerciseVolumeKg(timed), 0.0)
    }

    @Test
    fun `workout totals aggregate volume, sets and repetitions`() {
        val workout = completedWorkout(
            1, 0,
            listOf(
                executedExercise(benchDumbbell, listOf(completedSet(0, 12, 20.0), completedSet(1, 8, 24.0))),
                executedExercise(squat, listOf(completedSet(0, 5, 100.0))),
            ),
        )
        // (12×20 + 8×24) × 2 dumbbells + 5×100
        assertEquals(864.0 + 500.0, TrainingMath.workoutVolumeKg(workout), 1e-9)
        val totals = TrainingMath.totals(listOf(workout))
        assertEquals(3, totals.sets)
        assertEquals(25, totals.repetitions)
        assertEquals(1, totals.workouts)
    }

    @Test
    fun `estimated one rep max follows Epley and is only computed in its validity range`() {
        assertEquals(100.0, TrainingMath.estimatedOneRepMax(100.0, 1)!!, 1e-9)
        assertEquals(100.0 * (1 + 5 / 30.0), TrainingMath.estimatedOneRepMax(100.0, 5)!!, 1e-9)
        assertNull(TrainingMath.estimatedOneRepMax(100.0, 13))
        assertNull(TrainingMath.estimatedOneRepMax(0.0, 5))
        assertNull(TrainingMath.estimatedOneRepMax(null, 5))
    }

    @Test
    fun `performance summary exposes top set and best estimate`() {
        val workout = completedWorkout(1, 0, listOf(executedExercise(benchDumbbell, listOf(completedSet(0, 12, 20.0), completedSet(1, 5, 32.0), completedSet(2, 6, 32.0)))))
        val performance = TrainingMath.performance(workout, workout.exercises[0])!!
        assertEquals(32.0, performance.topWeightKg!!, 0.0)
        assertEquals(6, performance.topWeightRepetitions)
        assertEquals(32.0 * (1 + 6 / 30.0), performance.estimatedOneRepMaxKg!!, 1e-9)
        assertEquals(23, performance.totalRepetitions)
    }
}

class RecordDetectorTest {

    private fun benchWorkout(id: Long, day: Long, vararg sets: Pair<Int, Double>) = completedWorkout(
        id, day * DAY,
        listOf(executedExercise(benchDumbbell, sets.mapIndexed { i, (reps, weight) -> completedSet(i, reps, weight) })),
    )

    @Test
    fun `first performance of an exercise is never a record`() {
        val first = benchWorkout(1, 0, 10 to 20.0)
        assertTrue(RecordDetector.detectForWorkout(listOf(first), first).isEmpty())
    }

    @Test
    fun `heavier top set is a max weight record and keeps the previous value`() {
        val history = listOf(benchWorkout(1, 0, 5 to 30.0), benchWorkout(2, 7, 5 to 32.0))
        val records = RecordDetector.detectForWorkout(history, history[1])
        val maxWeight = records.single { it.type == RecordType.MAX_WEIGHT }
        assertEquals(32.0, maxWeight.value, 0.0)
        assertEquals(30.0, maxWeight.previousValue!!, 0.0)
        assertEquals(5, maxWeight.repetitions)
        assertTrue(records.any { it.type == RecordType.ESTIMATED_ONE_REP_MAX })
    }

    @Test
    fun `same load with fewer repetitions is not a record`() {
        val history = listOf(benchWorkout(1, 0, 8 to 30.0), benchWorkout(2, 7, 6 to 30.0))
        assertTrue(RecordDetector.detectForWorkout(history, history[1]).isEmpty())
    }

    @Test
    fun `heavier load with far fewer reps is a load record but not a 1RM record`() {
        val history = listOf(benchWorkout(1, 0, 12 to 30.0), benchWorkout(2, 7, 2 to 32.0))
        val types = RecordDetector.detectForWorkout(history, history[1]).map { it.type }
        assertTrue(RecordType.MAX_WEIGHT in types)
        assertTrue(RecordType.ESTIMATED_ONE_REP_MAX !in types)
    }

    @Test
    fun `bodyweight repetitions and durations produce their own records`() {
        val first = completedWorkout(1, 0, listOf(executedExercise(pullUp, listOf(completedSet(0, 8, null))), executedExercise(plank, listOf(completedSet(0, null, null, 45)))))
        val second = completedWorkout(2, DAY, listOf(executedExercise(pullUp, listOf(completedSet(0, 10, null))), executedExercise(plank, listOf(completedSet(0, null, null, 60)))))
        val types = RecordDetector.detectForWorkout(listOf(first, second), second).map { it.type }
        assertTrue(RecordType.MAX_REPETITIONS in types)
        assertTrue(RecordType.MAX_DURATION in types)
    }

    @Test
    fun `most relevant keeps one record per exercise and workout`() {
        val history = listOf(benchWorkout(1, 0, 5 to 30.0), benchWorkout(2, 7, 5 to 32.0))
        val relevant = RecordDetector.mostRelevant(RecordDetector.allRecords(history))
        assertEquals(1, relevant.size)
        assertEquals(RecordType.MAX_WEIGHT, relevant.single().type)
    }
}

class StatsCalculatorTest {

    private val zone: ZoneId = ZoneOffset.UTC
    // Wednesday 2026-11-04 12:00 UTC
    private val now = LocalDateTime.of(2026, 11, 4, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun workoutDaysAgo(id: Long, daysAgo: Long, weight: Double = 100.0, reps: Int = 5) =
        completedWorkout(id, now - daysAgo * DAY, listOf(executedExercise(squat, listOf(completedSet(0, reps, weight), completedSet(1, reps, weight)))))

    @Test
    fun `dashboard counts this week and last week separately`() {
        val history = listOf(workoutDaysAgo(1, 0), workoutDaysAgo(2, 1), workoutDaysAgo(3, 5), workoutDaysAgo(4, 10))
        val stats = StatsCalculator.dashboard(history, StatsPeriod.MONTH, now, zone)
        assertEquals(2, stats.thisWeek.workouts)
        assertEquals(1, stats.lastWeek.workouts)
        assertEquals(4, stats.periodTotals.workouts)
        assertEquals(4 * 1000.0, stats.periodTotals.volumeKg, 1e-9)
        assertEquals(4, stats.totalWorkouts)
    }

    @Test
    fun `volume series has one bucket per day and sums workouts`() {
        val history = listOf(workoutDaysAgo(1, 0), workoutDaysAgo(2, 0, weight = 50.0), workoutDaysAgo(3, 3))
        val stats = StatsCalculator.dashboard(history, StatsPeriod.WEEK, now, zone)
        assertEquals(7, stats.volumeSeries.size)
        assertEquals(1500.0, stats.volumeSeries.last().value, 1e-9)
        assertEquals(1000.0, stats.volumeSeries[3].value, 1e-9)
        assertEquals(3.0, stats.frequencySeries.sumOf { it.value }, 0.0)
    }

    @Test
    fun `workouts outside the period are excluded and compared with the previous period`() {
        val history = listOf(workoutDaysAgo(1, 2), workoutDaysAgo(2, 10))
        val stats = StatsCalculator.dashboard(history, StatsPeriod.WEEK, now, zone)
        assertEquals(1, stats.periodTotals.workouts)
        assertEquals(1, stats.previousPeriodTotals.workouts)
    }

    @Test
    fun `weight at equivalent repetitions ignores lower rep sets`() {
        val heavyFewReps = completedWorkout(1, now - 7 * DAY, listOf(executedExercise(squat, listOf(completedSet(0, 2, 120.0), completedSet(1, 5, 100.0)))))
        val laterFiveReps = completedWorkout(2, now, listOf(executedExercise(squat, listOf(completedSet(0, 5, 105.0)))))
        val performances = TrainingMath.performancesByExercise(listOf(heavyFewReps, laterFiveReps)).values.single()
        val atFive = StatsCalculator.series(performances, ExerciseMetric.WEIGHT_AT_REPS, minRepetitions = 5)
        assertEquals(listOf(100.0, 105.0), atFive.map { it.value })
        val maxWeight = StatsCalculator.series(performances, ExerciseMetric.MAX_WEIGHT)
        assertEquals(listOf(120.0, 105.0), maxWeight.map { it.value })
    }

    @Test
    fun `exercise progress computes rep maxes and best values`() {
        val history = listOf(
            completedWorkout(1, now - 14 * DAY, listOf(executedExercise(squat, listOf(completedSet(0, 8, 90.0), completedSet(1, 3, 110.0))))),
            completedWorkout(2, now, listOf(executedExercise(squat, listOf(completedSet(0, 5, 105.0))))),
        )
        val progress = StatsCalculator.exerciseProgress(squat.id, history)
        assertEquals(2, progress.performances.size)
        assertEquals(110.0, progress.maxWeightKg!!, 0.0)
        assertEquals(110.0, progress.repMaxes.first { it.repetitions == 3 }.weightKg, 0.0)
        assertEquals(105.0, progress.repMaxes.first { it.repetitions == 5 }.weightKg, 0.0)
        assertEquals(90.0, progress.repMaxes.first { it.repetitions == 8 }.weightKg, 0.0)
        assertEquals(3, progress.totalSets)
    }

    @Test
    fun `period comparison splits current and previous periods`() {
        val history = listOf(workoutDaysAgo(1, 40, weight = 90.0), workoutDaysAgo(2, 3, weight = 100.0))
        val performances = TrainingMath.performancesByExercise(history).values.single()
        val comparison = StatsCalculator.comparePeriods(performances, StatsPeriod.MONTH, now, zone)
        assertEquals(1, comparison.currentSessions)
        assertEquals(1, comparison.previousSessions)
        assertEquals(100.0, comparison.currentMaxWeight!!, 0.0)
        assertEquals(90.0, comparison.previousMaxWeight!!, 0.0)
    }

    @Test
    fun `granularity adapts to the period`() {
        val range = StatsCalculator.periodRange(StatsPeriod.YEAR, now, zone, null)
        assertEquals(com.gympulse.app.domain.model.Granularity.MONTH, StatsCalculator.granularityFor(StatsPeriod.YEAR, range))
        assertEquals(13, StatsCalculator.bucketStarts(range, com.gympulse.app.domain.model.Granularity.MONTH, zone).size)
        assertTrue(TestData.session().estimatedDurationSeconds > 0)
    }
}
