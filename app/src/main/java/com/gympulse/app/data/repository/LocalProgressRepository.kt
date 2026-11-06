package com.gympulse.app.data.repository

import com.gympulse.app.core.coroutines.DefaultDispatcher
import com.gympulse.app.core.time.TimeProvider
import com.gympulse.app.domain.model.DashboardStats
import com.gympulse.app.domain.model.ExercisePerformance
import com.gympulse.app.domain.model.ExerciseProgress
import com.gympulse.app.domain.model.PersonalRecord
import com.gympulse.app.domain.model.StatsPeriod
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.repository.ProgressRepository
import com.gympulse.app.domain.repository.WorkoutRepository
import com.gympulse.app.domain.stats.RecordDetector
import com.gympulse.app.domain.stats.StatsCalculator
import com.gympulse.app.domain.stats.TrainingMath
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Statistics are always derived from the real recorded history. */
@Singleton
class LocalProgressRepository @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val time: TimeProvider,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ProgressRepository {

    override fun observeDashboard(period: StatsPeriod): Flow<DashboardStats> =
        workoutRepository.observeHistory()
            .map { StatsCalculator.dashboard(it, period, time.now(), ZoneId.systemDefault()) }
            .flowOn(dispatcher)

    override fun observeExerciseProgress(exerciseId: Long): Flow<ExerciseProgress> =
        workoutRepository.observeHistory()
            .map { StatsCalculator.exerciseProgress(exerciseId, it) }
            .flowOn(dispatcher)

    override fun observeLastPerformances(): Flow<Map<Long, ExercisePerformance>> =
        workoutRepository.observeHistory()
            .map { history -> lastPerformances(history) }
            .flowOn(dispatcher)

    override suspend fun lastPerformance(
        exerciseId: Long,
        preferredSessionId: Long?,
        excludingExecutionId: Long?,
    ): ExercisePerformance? = withContext(dispatcher) {
        val history = workoutRepository.observeHistory().first()
            .filter { it.id != excludingExecutionId }
            .sortedByDescending { it.startedAt }
        fun find(candidates: List<WorkoutExecution>): ExercisePerformance? = candidates.firstNotNullOfOrNull { execution ->
            execution.exercises.firstOrNull { it.exerciseId == exerciseId }?.let { TrainingMath.performance(execution, it) }
        }
        preferredSessionId?.let { sessionId -> find(history.filter { it.sessionId == sessionId }) } ?: find(history)
    }

    override suspend fun detectRecords(execution: WorkoutExecution): List<PersonalRecord> = withContext(dispatcher) {
        RecordDetector.detectForWorkout(workoutRepository.observeHistory().first(), execution)
    }

    private fun lastPerformances(history: List<WorkoutExecution>): Map<Long, ExercisePerformance> {
        val result = mutableMapOf<Long, ExercisePerformance>()
        history.sortedByDescending { it.startedAt }.forEach { execution ->
            execution.exercises.forEach { exercise ->
                val id = exercise.exerciseId ?: return@forEach
                if (id !in result) TrainingMath.performance(execution, exercise)?.let { result[id] = it }
            }
        }
        return result
    }
}
