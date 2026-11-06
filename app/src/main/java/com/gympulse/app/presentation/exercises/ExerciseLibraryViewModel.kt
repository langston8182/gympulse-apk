package com.gympulse.app.presentation.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gympulse.app.domain.model.Equipment
import com.gympulse.app.domain.model.Exercise
import com.gympulse.app.domain.model.ExercisePerformance
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.core.designsystem.components.ExerciseArt
import com.gympulse.app.domain.model.TrainingSplit
import com.gympulse.app.domain.repository.ExerciseRepository
import com.gympulse.app.domain.repository.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.text.Normalizer
import javax.inject.Inject

data class ExerciseFilter(
    val query: String = "",
    val split: TrainingSplit? = null,
    val equipment: Equipment? = null,
    val timedOnly: Boolean = false,
)

/**
 * Library exercises carry their split in their illustration (e.g. the deadlift is a pull);
 * the others fall back on their main muscle.
 */
internal fun Exercise.split(): TrainingSplit = when (ExerciseArt.tintOf(imageUri)) {
    "push" -> TrainingSplit.PUSH
    "pull" -> TrainingSplit.PULL
    "legs" -> TrainingSplit.LEGS
    else -> TrainingSplit.fromMuscle(primaryMuscle)
}

data class ExerciseListItem(val exercise: Exercise, val lastPerformance: ExercisePerformance?)

data class ExerciseLibraryState(
    val loading: Boolean = true,
    val items: List<ExerciseListItem> = emptyList(),
    val totalCount: Int = 0,
    val filter: ExerciseFilter = ExerciseFilter(),
    val equipment: List<Equipment> = emptyList(),
)

internal fun String.normalizedForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace("\\p{Mn}+".toRegex(), "").lowercase().trim()

@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    exerciseRepository: ExerciseRepository,
    progressRepository: ProgressRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(ExerciseFilter())

    val state: StateFlow<ExerciseLibraryState> = combine(
        exerciseRepository.observeExercises(),
        progressRepository.observeLastPerformances().onStart { emit(emptyMap()) },
        filter,
    ) { exercises, last, filter ->
        val query = filter.query.normalizedForSearch()
        val items = exercises
            .asSequence()
            .filter { query.isEmpty() || it.name.normalizedForSearch().contains(query) }
            .filter { filter.split == null || filter.split == it.split() }
            .filter { filter.equipment == null || filter.equipment in it.equipment }
            .filter { !filter.timedOnly || it.type == ExerciseType.DURATION }
            .map { ExerciseListItem(it, last[it.id]) }
            .toList()
        ExerciseLibraryState(
            loading = false,
            items = items,
            totalCount = exercises.size,
            filter = filter,
            equipment = Equipment.entries.filter { equipment -> exercises.any { equipment in it.equipment } },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseLibraryState())

    fun setQuery(query: String) = filter.update { it.copy(query = query) }
    fun setSplit(split: TrainingSplit?) = filter.update { it.copy(split = split) }
    fun setEquipment(equipment: Equipment?) = filter.update { it.copy(equipment = equipment) }
    fun setTimedOnly(timed: Boolean) = filter.update { it.copy(timedOnly = timed) }
    fun clearFilters() = filter.update { ExerciseFilter() }
}
