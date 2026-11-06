package com.gympulse.app.data.local

import com.gympulse.app.data.local.entity.ExecutedExerciseEntity
import com.gympulse.app.data.local.entity.ExecutedSetEntity
import com.gympulse.app.data.local.entity.ExecutionWithDetails
import com.gympulse.app.data.local.entity.ExerciseEntity
import com.gympulse.app.data.local.entity.LocationEntity
import com.gympulse.app.data.local.entity.ProgramEntity
import com.gympulse.app.data.local.entity.ProgramWithRefs
import com.gympulse.app.data.local.entity.SessionEntity
import com.gympulse.app.data.local.entity.SessionExerciseEntity
import com.gympulse.app.data.local.entity.SessionWithDetails
import com.gympulse.app.data.local.entity.WorkoutExecutionEntity
import com.gympulse.app.domain.model.ExecutedExercise
import com.gympulse.app.domain.model.ExecutedSet
import com.gympulse.app.domain.model.Exercise
import com.gympulse.app.domain.model.RestTimer
import com.gympulse.app.domain.model.SessionExercise
import com.gympulse.app.domain.model.SetTimer
import com.gympulse.app.domain.model.TrainingLocation
import com.gympulse.app.domain.model.TrainingProgram
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.model.WorkoutSession

fun ExerciseEntity.toDomain() = Exercise(
    id = id,
    name = name,
    imageUri = imageUri,
    notes = notes,
    type = type,
    muscleGroups = muscleGroups,
    equipment = equipment.toSet(),
    loadMode = loadMode,
    createdAt = createdAt,
    isLibrary = isLibrary,
)

fun Exercise.toEntity() = ExerciseEntity(
    id = id,
    name = name.trim(),
    imageUri = imageUri,
    notes = notes.trim(),
    type = type,
    muscleGroups = muscleGroups,
    equipment = equipment.sortedBy { it.ordinal },
    loadMode = loadMode,
    createdAt = createdAt,
    isLibrary = isLibrary,
)

fun LocationEntity.toDomain() = TrainingLocation(
    id = id,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    type = type,
    availableEquipment = availableEquipment.toSet(),
    isDemo = isDemo,
    createdAt = createdAt,
)

fun TrainingLocation.toEntity() = LocationEntity(
    id = id,
    name = name.trim(),
    address = address.trim(),
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    type = type,
    availableEquipment = availableEquipment.sortedBy { it.ordinal },
    isDemo = isDemo,
    createdAt = createdAt,
)

fun SessionWithDetails.toDomain() = WorkoutSession(
    id = session.id,
    name = session.name,
    description = session.description,
    trainingGoal = session.trainingGoal,
    exercises = exercises
        .sortedBy { it.sessionExercise.position }
        .mapIndexed { index, item ->
            SessionExercise(
                id = item.sessionExercise.id,
                exercise = item.exercise.toDomain(),
                order = index,
                sets = item.sessionExercise.sets,
                restDurationSeconds = item.sessionExercise.restSeconds,
                notes = item.sessionExercise.notes,
            )
        },
    compatibleLocationIds = locations.map { it.locationId }.toSet(),
    isFavorite = session.isFavorite,
    isDemo = session.isDemo,
    createdAt = session.createdAt,
    updatedAt = session.updatedAt,
    sortOrder = session.sortOrder,
)

fun WorkoutSession.toEntity() = SessionEntity(
    id = id,
    name = name.trim(),
    description = description.trim(),
    trainingGoal = trainingGoal,
    isFavorite = isFavorite,
    isDemo = isDemo,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sortOrder = sortOrder,
)

fun WorkoutSession.exerciseEntities(sessionId: Long) = exercises.mapIndexed { index, sessionExercise ->
    SessionExerciseEntity(
        sessionId = sessionId,
        exerciseId = sessionExercise.exercise.id,
        position = index,
        restSeconds = sessionExercise.restDurationSeconds,
        notes = sessionExercise.notes.trim(),
        sets = sessionExercise.sets,
    )
}

fun ProgramWithRefs.toDomain(sessionsById: Map<Long, WorkoutSession>) = TrainingProgram(
    id = program.id,
    name = program.name,
    description = program.description,
    goal = program.goal,
    sessions = sessions.sortedBy { it.position }.mapNotNull { sessionsById[it.sessionId] },
    compatibleLocationIds = locations.map { it.locationId }.toSet(),
    isDemo = program.isDemo,
    createdAt = program.createdAt,
    updatedAt = program.updatedAt,
)

fun TrainingProgram.toEntity() = ProgramEntity(
    id = id,
    name = name.trim(),
    description = description.trim(),
    goal = goal,
    isDemo = isDemo,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ExecutionWithDetails.toDomain(): WorkoutExecution {
    val e = execution
    val rest = if (e.restStartedAt != null && e.restEndsAt != null && e.restExerciseKey != null) {
        RestTimer(
            startedAt = e.restStartedAt,
            endsAt = e.restEndsAt,
            totalSeconds = e.restTotalSeconds ?: 0,
            exerciseKey = e.restExerciseKey,
            setIndex = e.restSetIndex ?: 0,
            pausedRemainingMs = e.restPausedRemainingMs,
        )
    } else {
        null
    }
    val setTimer = if (e.setTimerAccumulatedMs != null || e.setTimerRunningSince != null) {
        SetTimer(accumulatedMs = e.setTimerAccumulatedMs ?: 0, runningSince = e.setTimerRunningSince)
    } else {
        null
    }
    return WorkoutExecution(
        id = e.id,
        sessionId = e.sessionId,
        sessionName = e.sessionName,
        trainingGoal = e.trainingGoal,
        status = e.status,
        startedAt = e.startedAt,
        endedAt = e.endedAt,
        pausedAt = e.pausedAt,
        totalPausedMs = e.totalPausedMs,
        locationId = e.locationId,
        locationName = e.locationName,
        notes = e.notes,
        currentExerciseIndex = e.currentExerciseIndex,
        currentSetIndex = e.currentSetIndex,
        rest = rest,
        setTimer = setTimer,
        exercises = exercises.sortedBy { it.exercise.position }.map { item ->
            val x = item.exercise
            ExecutedExercise(
                key = x.key,
                exerciseId = x.exerciseId,
                name = x.name,
                type = x.type,
                loadMode = x.loadMode,
                imageUri = x.imageUri,
                muscleGroups = x.muscleGroups,
                equipment = x.equipment.toSet(),
                restDurationSeconds = x.restSeconds,
                notes = x.notes,
                status = x.status,
                sets = item.sets.sortedBy { it.position }.map { s ->
                    ExecutedSet(
                        order = s.position,
                        targetRepetitions = s.targetRepetitions,
                        targetWeightKg = s.targetWeightKg,
                        targetDurationSeconds = s.targetDurationSeconds,
                        restSeconds = s.restSeconds,
                        actualRepetitions = s.actualRepetitions,
                        actualWeightKg = s.actualWeightKg,
                        actualDurationSeconds = s.actualDurationSeconds,
                        actualRestSeconds = s.actualRestSeconds,
                        status = s.status,
                        completedAt = s.completedAt,
                        isExtra = s.isExtra,
                    )
                },
            )
        },
        isDemo = e.isDemo,
    )
}

fun WorkoutExecution.toEntity() = WorkoutExecutionEntity(
    id = id,
    sessionId = sessionId,
    sessionName = sessionName,
    trainingGoal = trainingGoal,
    status = status,
    startedAt = startedAt,
    endedAt = endedAt,
    pausedAt = pausedAt,
    totalPausedMs = totalPausedMs,
    locationId = locationId,
    locationName = locationName,
    notes = notes,
    currentExerciseIndex = currentExerciseIndex,
    currentSetIndex = currentSetIndex,
    restStartedAt = rest?.startedAt,
    restEndsAt = rest?.endsAt,
    restTotalSeconds = rest?.totalSeconds,
    restExerciseKey = rest?.exerciseKey,
    restSetIndex = rest?.setIndex,
    restPausedRemainingMs = rest?.pausedRemainingMs,
    setTimerAccumulatedMs = setTimer?.accumulatedMs,
    setTimerRunningSince = setTimer?.runningSince,
    isDemo = isDemo,
)

fun ExecutedExercise.toEntity(executionId: Long, position: Int) = ExecutedExerciseEntity(
    executionId = executionId,
    key = key,
    position = position,
    exerciseId = exerciseId,
    name = name,
    type = type,
    loadMode = loadMode,
    imageUri = imageUri,
    muscleGroups = muscleGroups,
    equipment = equipment.sortedBy { it.ordinal },
    restSeconds = restDurationSeconds,
    notes = notes,
    status = status,
)

fun ExecutedSet.toEntity(executedExerciseId: Long, position: Int) = ExecutedSetEntity(
    executedExerciseId = executedExerciseId,
    position = position,
    targetRepetitions = targetRepetitions,
    targetWeightKg = targetWeightKg,
    targetDurationSeconds = targetDurationSeconds,
    restSeconds = restSeconds,
    actualRepetitions = actualRepetitions,
    actualWeightKg = actualWeightKg,
    actualDurationSeconds = actualDurationSeconds,
    actualRestSeconds = actualRestSeconds,
    status = status,
    completedAt = completedAt,
    isExtra = isExtra,
)
