package com.gympulse.app.presentation.exercises

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gympulse.app.core.navigation.ExerciseEditRoute
import com.gympulse.app.data.image.ImageStorage
import com.gympulse.app.domain.model.Equipment
import com.gympulse.app.domain.model.Exercise
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.LoadMode
import com.gympulse.app.domain.model.MuscleGroup
import com.gympulse.app.domain.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseForm(
    val name: String = "",
    val type: ExerciseType = ExerciseType.REPETITIONS,
    val muscles: List<MuscleGroup> = emptyList(),
    val equipment: Set<Equipment> = emptySet(),
    val loadMode: LoadMode = LoadMode.TOTAL,
    val notes: String = "",
    /** Persisted image (file or preset). */
    val imageUri: String? = "preset:chest",
    /** Image picked in the gallery, copied into the app storage only when saving. */
    val pickedImage: Uri? = null,
) {
    val supportsPerImplementLoad: Boolean
        get() = type == ExerciseType.REPETITIONS && (Equipment.DUMBBELL in equipment || Equipment.KETTLEBELL in equipment)

    val previewUri: String? get() = pickedImage?.toString() ?: imageUri
}

data class ExerciseEditState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val form: ExerciseForm = ExerciseForm(),
    val initialForm: ExerciseForm = ExerciseForm(),
    val nameError: Boolean = false,
    val saving: Boolean = false,
    val sessionsUsing: Int = 0,
    val notFound: Boolean = false,
) {
    val hasChanges: Boolean get() = form != initialForm
}

@HiltViewModel
class ExerciseEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExerciseRepository,
    private val imageStorage: ImageStorage,
) : ViewModel() {

    private val exerciseId = savedStateHandle.toRoute<ExerciseEditRoute>().exerciseId
    private var original: Exercise? = null

    private val _state = MutableStateFlow(ExerciseEditState(isNew = exerciseId == 0L, loading = exerciseId != 0L))
    val state: StateFlow<ExerciseEditState> = _state.asStateFlow()

    init {
        if (exerciseId != 0L) {
            viewModelScope.launch {
                val exercise = repository.getExercise(exerciseId)
                if (exercise == null) {
                    _state.update { it.copy(loading = false, notFound = true) }
                    return@launch
                }
                // A catalogue exercise is read-only: editing it creates the user's own copy.
                original = if (exercise.isLibrary) exercise.copy(id = 0, isLibrary = false, createdAt = 0) else exercise
                val form = ExerciseForm(
                    name = exercise.name,
                    type = exercise.type,
                    muscles = exercise.muscleGroups,
                    equipment = exercise.equipment,
                    loadMode = exercise.loadMode,
                    notes = exercise.notes,
                    imageUri = exercise.imageUri,
                )
                _state.update {
                    it.copy(
                        loading = false,
                        form = form,
                        initialForm = form,
                        isNew = exercise.isLibrary,
                        sessionsUsing = if (exercise.isLibrary) 0 else repository.countSessionsUsing(exerciseId),
                    )
                }
            }
        }
    }

    private fun edit(transform: (ExerciseForm) -> ExerciseForm) = _state.update { it.copy(form = transform(it.form), nameError = false) }

    fun setName(name: String) = edit { it.copy(name = name) }
    fun setType(type: ExerciseType) = edit { it.copy(type = type) }
    fun setNotes(notes: String) = edit { it.copy(notes = notes) }
    fun setLoadMode(mode: LoadMode) = edit { it.copy(loadMode = mode) }

    fun toggleMuscle(muscle: MuscleGroup) = edit {
        it.copy(muscles = if (muscle in it.muscles) it.muscles - muscle else it.muscles + muscle)
    }

    fun toggleEquipment(equipment: Equipment) = edit {
        it.copy(equipment = if (equipment in it.equipment) it.equipment - equipment else it.equipment + equipment)
    }

    fun selectPreset(uri: String) = edit { it.copy(imageUri = uri, pickedImage = null) }

    fun pickImage(uri: Uri) = edit { it.copy(pickedImage = uri) }

    fun save(onSaved: (Long) -> Unit) {
        val current = _state.value
        if (current.saving) return
        if (current.form.name.isBlank()) {
            _state.update { it.copy(nameError = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val form = current.form
            val imageUri = form.pickedImage?.let { imageStorage.import(it) } ?: form.imageUri
            val exercise = (original ?: Exercise(name = form.name)).copy(
                name = form.name.trim(),
                type = form.type,
                muscleGroups = form.muscles,
                equipment = form.equipment,
                loadMode = if (form.supportsPerImplementLoad) form.loadMode else LoadMode.TOTAL,
                notes = form.notes.trim(),
                imageUri = imageUri,
            )
            val id = repository.saveExercise(exercise)
            _state.update { it.copy(saving = false, initialForm = it.form) }
            onSaved(id)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        if (exerciseId == 0L) return
        viewModelScope.launch {
            repository.deleteExercise(exerciseId)
            onDeleted()
        }
    }
}
