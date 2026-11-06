package com.gympulse.app.data.remote.dto

import kotlinx.serialization.Serializable

/*
 * Wire format of the GymPulse REST API (see gympulse-api). Resources reference each other through
 * the opaque `ref` identifiers assigned by the server; an exercise ref is either a key of the
 * provided library (e.g. "bench_db") or the ref of a user exercise.
 */

@Serializable
data class ExerciseDto(
    val ref: String? = null,
    val name: String,
    val type: String = "REPETITIONS",
    val muscleGroups: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val loadMode: String = "TOTAL",
    val imageUri: String? = null,
    val notes: String = "",
    val isDemo: Boolean = false,
)

@Serializable
data class SetTargetDto(
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val restSeconds: Int? = null,
)

@Serializable
data class SessionExerciseDto(
    val exerciseRef: String,
    val restSeconds: Int,
    val sets: List<SetTargetDto>,
    val notes: String = "",
)

@Serializable
data class SessionDto(
    val ref: String? = null,
    val name: String,
    val description: String = "",
    val goal: String = "CUSTOM",
    val exercises: List<SessionExerciseDto> = emptyList(),
    val locationRefs: List<String> = emptyList(),
    val favorite: Boolean = false,
    val order: Int = 0,
    val isDemo: Boolean = false,
)

@Serializable
data class ProgramDto(
    val ref: String? = null,
    val name: String,
    val description: String = "",
    val goal: String = "CUSTOM",
    val sessionRefs: List<String> = emptyList(),
    val locationRefs: List<String> = emptyList(),
    val isDemo: Boolean = false,
)

@Serializable
data class LocationDto(
    val ref: String? = null,
    val name: String,
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int = 150,
    val type: String = "OTHER",
    val equipment: List<String> = emptyList(),
    val isDemo: Boolean = false,
)

@Serializable
data class RestDto(
    val startedAt: Long,
    val endsAt: Long,
    val totalSeconds: Int,
    val exerciseKey: String,
    val setIndex: Int,
    val pausedRemainingMs: Long? = null,
)

@Serializable
data class SetTimerDto(
    val accumulatedMs: Long = 0,
    val runningSince: Long? = null,
)

@Serializable
data class WorkoutSetDto(
    val targetReps: Int? = null,
    val targetWeightKg: Double? = null,
    val targetDurationSeconds: Int? = null,
    val restSeconds: Int = 0,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val actualRestSeconds: Int? = null,
    val status: String = "PENDING",
    val completedAt: Long? = null,
    val isExtra: Boolean = false,
)

@Serializable
data class WorkoutExerciseDto(
    val key: String? = null,
    val exerciseRef: String? = null,
    val name: String,
    val type: String = "REPETITIONS",
    val loadMode: String = "TOTAL",
    val imageUri: String? = null,
    val muscleGroups: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val restSeconds: Int = 90,
    val notes: String = "",
    val status: String? = null,
    val sets: List<WorkoutSetDto> = emptyList(),
)

@Serializable
data class WorkoutDto(
    val ref: String? = null,
    val sessionRef: String? = null,
    val sessionName: String,
    val goal: String = "CUSTOM",
    val status: String = "COMPLETED",
    val startedAt: Long,
    val endedAt: Long? = null,
    val pausedAt: Long? = null,
    val totalPausedMs: Long = 0,
    val locationRef: String? = null,
    val locationName: String? = null,
    val notes: String = "",
    val currentExerciseIndex: Int = 0,
    val currentSetIndex: Int = 0,
    val rest: RestDto? = null,
    val setTimer: SetTimerDto? = null,
    val exercises: List<WorkoutExerciseDto> = emptyList(),
    val isDemo: Boolean = false,
)

@Serializable
data class PreferencesDto(
    val onboardingCompleted: Boolean = false,
    val userName: String = "",
    val themeMode: String = "DARK",
    val dynamicColor: Boolean = false,
    val weightUnit: String = "KG",
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val defaultRestSeconds: Int = 90,
    val weeklyGoal: Int = 3,
    val autoDetectLocation: Boolean = true,
    val manualLocationRef: String? = null,
)

@Serializable
data class LinkSessionsRequest(val sessionRefs: List<String>)

@Serializable
data class LinkProgramsRequest(val programRefs: List<String>)

@Serializable
data class DeleteAccountRequest(val confirm: Boolean = true)

@Serializable
data class DemoStatusDto(val hasDemo: Boolean = false)
