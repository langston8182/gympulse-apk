package com.gympulse.app.data.sync

import com.gympulse.app.data.local.entity.ExerciseEntity
import com.gympulse.app.data.local.entity.LocationEntity
import com.gympulse.app.data.remote.dto.ExerciseDto
import com.gympulse.app.data.remote.dto.LocationDto
import com.gympulse.app.data.remote.dto.PreferencesDto
import com.gympulse.app.data.remote.dto.RestDto
import com.gympulse.app.data.remote.dto.SetTargetDto
import com.gympulse.app.data.remote.dto.SetTimerDto
import com.gympulse.app.data.remote.dto.WorkoutDto
import com.gympulse.app.data.remote.dto.WorkoutExerciseDto
import com.gympulse.app.data.remote.dto.WorkoutSetDto
import com.gympulse.app.domain.model.Equipment
import com.gympulse.app.domain.model.ExecutedExercise
import com.gympulse.app.domain.model.ExecutedSet
import com.gympulse.app.domain.model.ExerciseStatus
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.LoadMode
import com.gympulse.app.domain.model.LocationType
import com.gympulse.app.domain.model.MuscleGroup
import com.gympulse.app.domain.model.RestTimer
import com.gympulse.app.domain.model.SetStatus
import com.gympulse.app.domain.model.SetTarget
import com.gympulse.app.domain.model.SetTimer
import com.gympulse.app.domain.model.ThemeMode
import com.gympulse.app.domain.model.TrainingGoal
import com.gympulse.app.domain.model.UserPreferences
import com.gympulse.app.domain.model.WeightUnit
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.model.WorkoutStatus
import java.util.UUID

internal inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? = enumValues<T>().firstOrNull { it.name == name }

internal inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T = enumOrNull<T>(name) ?: default

private const val PRESET_PREFIX = "preset:"

/** Photos live in the app private storage: only built-in illustrations make sense on the server. */
internal fun portableImage(uri: String?): String? = uri?.takeIf { it.startsWith(PRESET_PREFIX) }

/** A local photo is kept even though the server only knows the illustration (or nothing). */
internal fun mergeImage(local: String?, remote: String?): String? =
    if (local != null && !local.startsWith(PRESET_PREFIX)) local else remote

// --- Exercises -------------------------------------------------------------------------------

internal fun ExerciseEntity.toDto() = ExerciseDto(
    ref = remoteRef,
    name = name,
    type = type.name,
    muscleGroups = muscleGroups.map { it.name },
    equipment = equipment.map { it.name },
    loadMode = loadMode.name,
    imageUri = portableImage(imageUri),
    notes = notes,
)

internal fun ExerciseDto.toEntity(existing: ExerciseEntity?, library: Boolean, now: Long) = ExerciseEntity(
    id = existing?.id ?: 0,
    name = name,
    imageUri = mergeImage(existing?.imageUri, imageUri),
    notes = notes,
    type = enumOr(type, ExerciseType.REPETITIONS),
    muscleGroups = muscleGroups.mapNotNull { enumOrNull<MuscleGroup>(it) },
    equipment = equipment.mapNotNull { enumOrNull<Equipment>(it) },
    loadMode = enumOr(loadMode, LoadMode.TOTAL),
    createdAt = existing?.createdAt ?: now,
    remoteRef = ref,
    isLibrary = library,
)

// --- Locations -------------------------------------------------------------------------------

internal fun LocationEntity.toDto() = LocationDto(
    ref = remoteRef,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters.coerceIn(10, 5000),
    type = type.name,
    equipment = availableEquipment.map { it.name },
)

internal fun LocationDto.toEntity(existing: LocationEntity?, now: Long) = LocationEntity(
    id = existing?.id ?: 0,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    type = enumOr(type, LocationType.OTHER),
    availableEquipment = equipment.mapNotNull { enumOrNull<Equipment>(it) },
    isDemo = isDemo,
    createdAt = existing?.createdAt ?: now,
    remoteRef = ref,
)

// --- Session sets ----------------------------------------------------------------------------

internal fun SetTarget.toDto() = SetTargetDto(
    reps = targetRepetitions?.coerceIn(1, 1000),
    weightKg = targetWeightKg?.takeIf { it > 0 },
    durationSeconds = targetDurationSeconds?.coerceIn(1, 86_400),
    restSeconds = restSeconds?.coerceIn(0, 3600),
)

internal fun SetTargetDto.toDomain() = SetTarget(
    targetRepetitions = reps,
    targetWeightKg = weightKg,
    targetDurationSeconds = durationSeconds,
    restSeconds = restSeconds,
)

// --- Workouts --------------------------------------------------------------------------------

/** Server refs needed to describe a workout, resolved by the caller. */
internal data class WorkoutRefs(
    val sessionRef: String?,
    val locationRef: String?,
    val exerciseRef: (Long?) -> String?,
)

internal fun WorkoutExecution.toDto(refs: WorkoutRefs, remoteRef: String? = null) = WorkoutDto(
    ref = remoteRef,
    sessionRef = refs.sessionRef,
    sessionName = sessionName,
    goal = trainingGoal.name,
    status = status.name,
    startedAt = startedAt,
    endedAt = endedAt,
    pausedAt = pausedAt,
    totalPausedMs = totalPausedMs,
    locationRef = refs.locationRef,
    locationName = locationName,
    notes = notes,
    currentExerciseIndex = currentExerciseIndex,
    currentSetIndex = currentSetIndex,
    rest = rest?.let {
        RestDto(it.startedAt, it.endsAt, it.totalSeconds, it.exerciseKey, it.setIndex, it.pausedRemainingMs)
    },
    setTimer = setTimer?.let { SetTimerDto(it.accumulatedMs, it.runningSince) },
    exercises = exercises.map { exercise ->
        WorkoutExerciseDto(
            key = exercise.key,
            exerciseRef = refs.exerciseRef(exercise.exerciseId),
            name = exercise.name,
            type = exercise.type.name,
            loadMode = exercise.loadMode.name,
            imageUri = portableImage(exercise.imageUri),
            muscleGroups = exercise.muscleGroups.map { it.name },
            equipment = exercise.equipment.map { it.name },
            restSeconds = exercise.restDurationSeconds,
            notes = exercise.notes,
            status = exercise.status.name,
            sets = exercise.sets.map { set ->
                WorkoutSetDto(
                    targetReps = set.targetRepetitions,
                    targetWeightKg = set.targetWeightKg,
                    targetDurationSeconds = set.targetDurationSeconds,
                    restSeconds = set.restSeconds,
                    reps = set.actualRepetitions,
                    weightKg = set.actualWeightKg,
                    durationSeconds = set.actualDurationSeconds,
                    actualRestSeconds = set.actualRestSeconds,
                    status = set.status.name,
                    completedAt = set.completedAt,
                    isExtra = set.isExtra,
                )
            },
        )
    },
)

internal fun WorkoutDto.toDomain(
    sessionId: Long?,
    locationId: Long?,
    exerciseId: (String?) -> Long?,
    imageOf: (Long?) -> String? = { null },
): WorkoutExecution = WorkoutExecution(
    sessionId = sessionId,
    sessionName = sessionName,
    trainingGoal = enumOr(goal, TrainingGoal.CUSTOM),
    status = enumOr(status, WorkoutStatus.COMPLETED),
    startedAt = startedAt,
    endedAt = endedAt,
    pausedAt = pausedAt,
    totalPausedMs = totalPausedMs,
    locationId = locationId,
    locationName = locationName,
    notes = notes,
    currentExerciseIndex = currentExerciseIndex,
    currentSetIndex = currentSetIndex,
    rest = rest?.let { RestTimer(it.startedAt, it.endsAt, it.totalSeconds, it.exerciseKey, it.setIndex, it.pausedRemainingMs) },
    setTimer = setTimer?.let { SetTimer(it.accumulatedMs, it.runningSince) },
    isDemo = isDemo,
    exercises = exercises.map { exercise ->
        val localId = exerciseId(exercise.exerciseRef)
        ExecutedExercise(
            key = exercise.key ?: UUID.randomUUID().toString(),
            exerciseId = localId,
            name = exercise.name,
            type = enumOr(exercise.type, ExerciseType.REPETITIONS),
            loadMode = enumOr(exercise.loadMode, LoadMode.TOTAL),
            imageUri = imageOf(localId) ?: exercise.imageUri,
            muscleGroups = exercise.muscleGroups.mapNotNull { enumOrNull<MuscleGroup>(it) },
            equipment = exercise.equipment.mapNotNull { enumOrNull<Equipment>(it) }.toSet(),
            restDurationSeconds = exercise.restSeconds,
            notes = exercise.notes,
            status = enumOr(exercise.status, ExerciseStatus.COMPLETED),
            sets = exercise.sets.mapIndexed { index, set ->
                ExecutedSet(
                    order = index,
                    targetRepetitions = set.targetReps,
                    targetWeightKg = set.targetWeightKg,
                    targetDurationSeconds = set.targetDurationSeconds,
                    restSeconds = set.restSeconds,
                    actualRepetitions = set.reps,
                    actualWeightKg = set.weightKg,
                    actualDurationSeconds = set.durationSeconds,
                    actualRestSeconds = set.actualRestSeconds,
                    status = enumOr(set.status, SetStatus.PENDING),
                    completedAt = set.completedAt,
                    isExtra = set.isExtra,
                )
            },
        )
    },
)

// --- Preferences -----------------------------------------------------------------------------

internal fun UserPreferences.toDto(manualLocationRef: String?) = PreferencesDto(
    onboardingCompleted = onboardingCompleted,
    userName = userName,
    themeMode = themeMode.name,
    dynamicColor = dynamicColor,
    weightUnit = weightUnit.name,
    soundEnabled = soundEnabled,
    vibrationEnabled = vibrationEnabled,
    keepScreenOn = keepScreenOn,
    defaultRestSeconds = defaultRestSeconds,
    weeklyGoal = weeklyGoal,
    autoDetectLocation = autoDetectLocation,
    manualLocationRef = manualLocationRef,
)

internal fun PreferencesDto.toDomain(manualLocationId: Long?) = UserPreferences(
    onboardingCompleted = onboardingCompleted,
    userName = userName,
    themeMode = enumOr(themeMode, ThemeMode.DARK),
    dynamicColor = dynamicColor,
    weightUnit = enumOr(weightUnit, WeightUnit.KG),
    soundEnabled = soundEnabled,
    vibrationEnabled = vibrationEnabled,
    keepScreenOn = keepScreenOn,
    defaultRestSeconds = defaultRestSeconds,
    weeklyGoal = weeklyGoal,
    autoDetectLocation = autoDetectLocation,
    manualLocationId = manualLocationId,
)
