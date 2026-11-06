package com.gympulse.app.core.navigation

import kotlinx.serialization.Serializable

// Top-level destinations (bottom navigation)
@Serializable data object HomeRoute
@Serializable data object ProgramsRoute
@Serializable data object SessionsRoute
@Serializable data object ProgressRoute
@Serializable data object ProfileRoute

@Serializable data object OnboardingRoute

// Programs
@Serializable data class ProgramDetailRoute(val programId: Long)
/** [programId] = 0 creates a new program. */
@Serializable data class ProgramEditRoute(val programId: Long = 0L)

// Sessions
@Serializable data class SessionDetailRoute(val sessionId: Long)
/** [sessionId] = 0 creates a new session; [duplicateFromId] > 0 prefills from a copy. */
@Serializable data class SessionEditRoute(val sessionId: Long = 0L, val duplicateFromId: Long = 0L)

// Exercises
@Serializable data object ExerciseLibraryRoute
/** [exerciseId] = 0 creates a new exercise. With [returnResult], the created id is returned to the previous screen. */
@Serializable data class ExerciseEditRoute(val exerciseId: Long = 0L, val returnResult: Boolean = false)
@Serializable data class ExerciseDetailRoute(val exerciseId: Long)

// Workout
@Serializable data object WorkoutRoute
@Serializable data class WorkoutSummaryRoute(val executionId: Long)

// History
@Serializable data class HistoryDetailRoute(val executionId: Long)
@Serializable data class CompareWorkoutsRoute(val firstId: Long, val secondId: Long)

// Places & settings
@Serializable data object LocationsRoute
/** [locationId] = 0 creates a new location. */
@Serializable data class LocationEditRoute(val locationId: Long = 0L)
@Serializable data object EquipmentRoute
@Serializable data object SettingsRoute

/** Key used to return a newly created exercise id to the screen that requested it. */
const val CREATED_EXERCISE_ID_KEY = "created_exercise_id"
