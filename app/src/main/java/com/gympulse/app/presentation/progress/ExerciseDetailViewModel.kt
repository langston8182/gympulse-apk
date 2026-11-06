package com.gympulse.app.presentation.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gympulse.app.core.navigation.ExerciseDetailRoute
import com.gympulse.app.core.time.TimeProvider
import com.gympulse.app.domain.model.Exercise
import com.gympulse.app.domain.model.ExercisePerformance
import com.gympulse.app.domain.model.ExerciseProgress
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.PeriodComparison
import com.gympulse.app.domain.model.SeriesPoint
import com.gympulse.app.domain.model.StatsPeriod
import com.gympulse.app.domain.repository.ExerciseRepository
import com.gympulse.app.domain.repository.ProgressRepository
import com.gympulse.app.domain.stats.ExerciseMetric
import com.gympulse.app.domain.stats.StatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.ZoneId
import javax.inject.Inject

data class ChartSelection(
    val period: StatsPeriod = StatsPeriod.QUARTER,
    val metric: ExerciseMetric? = null,
    val minRepetitions: Int = 5,
)

data class ExerciseDetailState(
    val loading: Boolean = true,
    val exercise: Exercise? = null,
    val progress: ExerciseProgress? = null,
    val selection: ChartSelection = ChartSelection(),
    val metric: ExerciseMetric = ExerciseMetric.ESTIMATED_ONE_REP_MAX,
    val metrics: List<ExerciseMetric> = emptyList(),
    val series: List<SeriesPoint> = emptyList(),
    val comparison: PeriodComparison? = null,
    val type: ExerciseType = ExerciseType.REPETITIONS,
) {
    val performances: List<ExercisePerformance> get() = progress?.performances.orEmpty()
    val name: String get() = exercise?.name ?: performances.lastOrNull()?.exerciseName.orEmpty()
}

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exerciseRepository: ExerciseRepository,
    progressRepository: ProgressRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val exerciseId = savedStateHandle.toRoute<ExerciseDetailRoute>().exerciseId
    private val selection = MutableStateFlow(ChartSelection())

    val state: StateFlow<ExerciseDetailState> = combine(
        exerciseRepository.observeExercise(exerciseId),
        progressRepository.observeExerciseProgress(exerciseId),
        selection,
    ) { exercise, progress, selection ->
        val performances = progress.performances
        val type = exercise?.type ?: performances.lastOrNull()?.type ?: ExerciseType.REPETITIONS
        val weighted = performances.any { it.topWeightKg != null }
        val metrics = when {
            type == ExerciseType.DURATION -> listOf(ExerciseMetric.BEST_DURATION, ExerciseMetric.TOTAL_DURATION)
            weighted -> listOf(
                ExerciseMetric.ESTIMATED_ONE_REP_MAX,
                ExerciseMetric.MAX_WEIGHT,
                ExerciseMetric.WEIGHT_AT_REPS,
                ExerciseMetric.VOLUME,
                ExerciseMetric.REPETITIONS,
            )
            else -> listOf(ExerciseMetric.REPETITIONS)
        }
        val metric = selection.metric?.takeIf { it in metrics } ?: StatsCalculator.defaultMetric(type, performances)
        val zone = ZoneId.systemDefault()
        val range = StatsCalculator.periodRange(selection.period, time.now(), zone, performances.firstOrNull()?.date)
        ExerciseDetailState(
            loading = false,
            exercise = exercise,
            progress = progress,
            selection = selection,
            metric = metric,
            metrics = metrics,
            series = StatsCalculator.series(performances.filter { it.date in range }, metric, selection.minRepetitions),
            comparison = if (selection.period != StatsPeriod.ALL) StatsCalculator.comparePeriods(performances, selection.period, time.now(), zone) else null,
            type = type,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailState())

    fun setPeriod(period: StatsPeriod) = selection.update { it.copy(period = period) }
    fun setMetric(metric: ExerciseMetric) = selection.update { it.copy(metric = metric) }
    fun setMinRepetitions(reps: Int) = selection.update { it.copy(minRepetitions = reps) }
}
