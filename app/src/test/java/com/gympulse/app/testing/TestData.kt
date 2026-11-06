package com.gympulse.app.testing

import com.gympulse.app.domain.model.Equipment
import com.gympulse.app.domain.model.ExecutedExercise
import com.gympulse.app.domain.model.ExecutedSet
import com.gympulse.app.domain.model.Exercise
import com.gympulse.app.domain.model.ExerciseStatus
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.LoadMode
import com.gympulse.app.domain.model.MuscleGroup
import com.gympulse.app.domain.model.SessionExercise
import com.gympulse.app.domain.model.SetStatus
import com.gympulse.app.domain.model.SetTarget
import com.gympulse.app.domain.model.TrainingGoal
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.model.WorkoutSession
import com.gympulse.app.domain.model.WorkoutStatus

object TestData {
    const val MINUTE = 60_000L
    const val DAY = 86_400_000L

    val benchDumbbell = Exercise(
        id = 1,
        name = "Développé couché haltères",
        type = ExerciseType.REPETITIONS,
        muscleGroups = listOf(MuscleGroup.CHEST),
        equipment = setOf(Equipment.DUMBBELL, Equipment.BENCH),
        loadMode = LoadMode.PER_IMPLEMENT,
    )
    val squat = Exercise(
        id = 2,
        name = "Squat barre",
        muscleGroups = listOf(MuscleGroup.QUADRICEPS),
        equipment = setOf(Equipment.BARBELL, Equipment.SQUAT_RACK),
    )
    val plank = Exercise(id = 3, name = "Gainage", type = ExerciseType.DURATION, muscleGroups = listOf(MuscleGroup.ABS), equipment = setOf(Equipment.MAT))
    val pullUp = Exercise(id = 4, name = "Tractions", muscleGroups = listOf(MuscleGroup.BACK), equipment = setOf(Equipment.PULL_UP_BAR))

    /** Bench 4 sets (pyramid, 120 s rest, 150 s after the last one), squat 3 × 5 (180 s), plank 2 × 45 s (60 s). */
    fun session(id: Long = 10) = WorkoutSession(
        id = id,
        name = "Push test",
        trainingGoal = TrainingGoal.HYPERTROPHY,
        exercises = listOf(
            SessionExercise(
                exercise = benchDumbbell,
                order = 0,
                restDurationSeconds = 120,
                sets = listOf(
                    SetTarget(12, 20.0),
                    SetTarget(8, 24.0),
                    SetTarget(5, 28.0),
                    SetTarget(5, 32.0, restSeconds = 150),
                ),
            ),
            SessionExercise(exercise = squat, order = 1, restDurationSeconds = 180, sets = List(3) { SetTarget(5, 100.0) }),
            SessionExercise(exercise = plank, order = 2, restDurationSeconds = 60, sets = List(2) { SetTarget(targetDurationSeconds = 45) }),
        ),
    )

    fun keys(): () -> String {
        var counter = 0
        return { "k${counter++}" }
    }

    fun completedSet(order: Int, reps: Int?, weight: Double?, duration: Int? = null) = ExecutedSet(
        order = order,
        targetRepetitions = reps,
        targetWeightKg = weight,
        actualRepetitions = reps,
        actualWeightKg = weight,
        actualDurationSeconds = duration,
        status = SetStatus.COMPLETED,
        completedAt = 0,
    )

    fun executedExercise(exercise: Exercise, sets: List<ExecutedSet>, key: String = "key-${exercise.id}") = ExecutedExercise(
        key = key,
        exerciseId = exercise.id,
        name = exercise.name,
        type = exercise.type,
        loadMode = exercise.loadMode,
        muscleGroups = exercise.muscleGroups,
        equipment = exercise.equipment,
        status = ExerciseStatus.COMPLETED,
        sets = sets,
    )

    fun completedWorkout(
        id: Long,
        startedAt: Long,
        exercises: List<ExecutedExercise>,
        sessionId: Long? = 10,
        durationMs: Long = 45 * MINUTE,
    ) = WorkoutExecution(
        id = id,
        sessionId = sessionId,
        sessionName = "Push test",
        status = WorkoutStatus.COMPLETED,
        startedAt = startedAt,
        endedAt = startedAt + durationMs,
        exercises = exercises,
    )
}
