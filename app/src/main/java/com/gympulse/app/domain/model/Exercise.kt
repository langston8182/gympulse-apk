package com.gympulse.app.domain.model

enum class ExerciseType { REPETITIONS, DURATION }

/**
 * How the recorded load must be interpreted. For dumbbells/kettlebells the user usually records
 * the weight of one implement: the volume must then count both implements.
 */
enum class LoadMode { TOTAL, PER_IMPLEMENT }

enum class MuscleGroup {
    CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, FOREARMS, TRAPS, LOWER_BACK,
    ABS, OBLIQUES, QUADRICEPS, HAMSTRINGS, GLUTES, CALVES, ADDUCTORS, FULL_BODY
}

enum class Equipment {
    BODYWEIGHT, DUMBBELL, BARBELL, EZ_BAR, KETTLEBELL, BENCH, SQUAT_RACK, SMITH_MACHINE,
    CABLE, MACHINE, LEG_PRESS, PULL_UP_BAR, DIP_STATION, RESISTANCE_BAND, MAT;

    /** Equipment that is always available, wherever the user trains. */
    val isUniversal: Boolean get() = this == BODYWEIGHT
}

/** Training split an exercise belongs to; anything that is neither pushing, pulling nor legs is [OTHER]. */
enum class TrainingSplit {
    PUSH, PULL, LEGS, OTHER;

    companion object {
        /** Split of an exercise from its main muscle, for exercises that have no explicit classification. */
        fun fromMuscle(muscle: MuscleGroup?): TrainingSplit = when (muscle) {
            MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS -> PUSH
            MuscleGroup.BACK, MuscleGroup.BICEPS, MuscleGroup.FOREARMS, MuscleGroup.TRAPS, MuscleGroup.LOWER_BACK -> PULL
            MuscleGroup.QUADRICEPS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES, MuscleGroup.ADDUCTORS -> LEGS
            else -> OTHER
        }
    }
}

data class Exercise(
    val id: Long = 0,
    val name: String,
    val imageUri: String? = null,
    val notes: String = "",
    val type: ExerciseType = ExerciseType.REPETITIONS,
    val muscleGroups: List<MuscleGroup> = emptyList(),
    val equipment: Set<Equipment> = emptySet(),
    val loadMode: LoadMode = LoadMode.TOTAL,
    val createdAt: Long = 0,
    /** Shared catalogue entry: read-only, to be duplicated to be customised. */
    val isLibrary: Boolean = false,
) {
    val primaryMuscle: MuscleGroup? get() = muscleGroups.firstOrNull()
    val isTimed: Boolean get() = type == ExerciseType.DURATION
}
