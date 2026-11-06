package com.gympulse.app.domain.model

import kotlinx.serialization.Serializable

enum class TrainingGoal { HYPERTROPHY, STRENGTH_HYPERTROPHY, STRENGTH, ENDURANCE, CUSTOM }

/** Target of a single planned set. [restSeconds] overrides the exercise rest for this set only. */
@Serializable
data class SetTarget(
    val targetRepetitions: Int? = null,
    val targetWeightKg: Double? = null,
    val targetDurationSeconds: Int? = null,
    val restSeconds: Int? = null,
)

data class SessionExercise(
    val id: Long = 0,
    val exercise: Exercise,
    val order: Int,
    val sets: List<SetTarget>,
    val restDurationSeconds: Int,
    val notes: String = "",
) {
    val exerciseId: Long get() = exercise.id

    fun restAfterSet(index: Int): Int = sets.getOrNull(index)?.restSeconds ?: restDurationSeconds
}

data class WorkoutSession(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val trainingGoal: TrainingGoal = TrainingGoal.HYPERTROPHY,
    val exercises: List<SessionExercise> = emptyList(),
    val compatibleLocationIds: Set<Long> = emptySet(),
    val isFavorite: Boolean = false,
    val isDemo: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    /** Position chosen by the user; sessions are listed by ascending order everywhere. */
    val sortOrder: Int = 0,
) {
    val totalSets: Int get() = exercises.sumOf { it.sets.size }

    val requiredEquipment: Set<Equipment>
        get() = exercises.flatMap { it.exercise.equipment }.filterNot { it.isUniversal }.toSet()

    val muscleGroups: List<MuscleGroup>
        get() = exercises.mapNotNull { it.exercise.primaryMuscle }.distinct()

    /** Rough duration estimate: ~3 s per repetition, timed sets as planned, plus rests. */
    val estimatedDurationSeconds: Int
        get() = exercises.withIndex().sumOf { (exerciseIndex, sessionExercise) ->
            sessionExercise.sets.withIndex().sumOf { (setIndex, set) ->
                val work = set.targetDurationSeconds ?: ((set.targetRepetitions ?: 10) * 3 + 10)
                val isLastSetOfWorkout = exerciseIndex == exercises.lastIndex && setIndex == sessionExercise.sets.lastIndex
                work + if (isLastSetOfWorkout) 0 else sessionExercise.restAfterSet(setIndex)
            }
        }
}

data class TrainingProgram(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val goal: TrainingGoal = TrainingGoal.HYPERTROPHY,
    val sessions: List<WorkoutSession> = emptyList(),
    val compatibleLocationIds: Set<Long> = emptySet(),
    val isDemo: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)
