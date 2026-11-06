package com.gympulse.app.presentation.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gympulse.app.domain.model.DashboardStats
import com.gympulse.app.domain.model.ExercisePerformance
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.SeriesPoint
import com.gympulse.app.domain.model.StatsPeriod
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.repository.ProgressRepository
import com.gympulse.app.domain.repository.WorkoutRepository
import com.gympulse.app.domain.stats.StatsCalculator
import com.gympulse.app.domain.stats.TrainingMath
import com.gympulse.app.presentation.exercises.normalizedForSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HistoryItem(
    val execution: WorkoutExecution,
    val volumeKg: Double,
    val sets: Int,
    val exerciseNames: List<String>,
)

data class ExerciseSummaryItem(
    val exerciseId: Long?,
    val name: String,
    val imageUri: String?,
    val type: ExerciseType,
    val sessions: Int,
    val last: ExercisePerformance,
    val bestOneRepMaxKg: Double?,
    val bestDurationSeconds: Int?,
    val series: List<SeriesPoint>,
)

data class ProgressState(
    val loading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.QUARTER,
    val dashboard: DashboardStats? = null,
    val history: List<HistoryItem> = emptyList(),
    val exercises: List<ExerciseSummaryItem> = emptyList(),
    val query: String = "",
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    progressRepository: ProgressRepository,
) : ViewModel() {

    private val period = MutableStateFlow(StatsPeriod.QUARTER)
    private val query = MutableStateFlow("")

    private val historyItems = workoutRepository.observeHistory().map { history ->
        history.map { execution ->
            HistoryItem(
                execution = execution,
                volumeKg = TrainingMath.workoutVolumeKg(execution),
                sets = TrainingMath.completedSets(execution),
                exerciseNames = execution.exercises.filter { exercise -> exercise.sets.any { it.isCompleted } }.map { it.name },
            )
        }
    }.flowOn(Dispatchers.Default)

    private val exerciseItems = combine(workoutRepository.observeHistory(), query) { history, query ->
        val images = history.flatMap { it.exercises }.associate { TrainingMath.exerciseKey(it.exerciseId, it.name) to it.imageUri }
        val normalized = query.normalizedForSearch()
        TrainingMath.performancesByExercise(history).map { (key, performances) ->
            val last = performances.last()
            val metric = StatsCalculator.defaultMetric(last.type, performances)
            ExerciseSummaryItem(
                exerciseId = last.exerciseId,
                name = last.exerciseName,
                imageUri = images[key],
                type = last.type,
                sessions = performances.size,
                last = last,
                bestOneRepMaxKg = performances.mapNotNull { it.estimatedOneRepMaxKg }.maxOrNull(),
                bestDurationSeconds = performances.mapNotNull { it.bestDurationSeconds }.maxOrNull(),
                series = StatsCalculator.series(performances.takeLast(20), metric),
            )
        }
            .filter { normalized.isEmpty() || it.name.normalizedForSearch().contains(normalized) }
            .sortedWith(compareByDescending<ExerciseSummaryItem> { it.last.date }.thenBy { it.name })
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<ProgressState> = combine(
        period.flatMapLatest { progressRepository.observeDashboard(it) },
        historyItems,
        exerciseItems,
        query,
    ) { dashboard, history, exercises, query ->
        ProgressState(
            loading = false,
            period = dashboard.period,
            dashboard = dashboard,
            history = history,
            exercises = exercises,
            query = query,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressState())

    fun setPeriod(value: StatsPeriod) {
        period.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }
}
