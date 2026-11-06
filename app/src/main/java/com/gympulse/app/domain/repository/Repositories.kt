package com.gympulse.app.domain.repository

import com.gympulse.app.domain.model.DashboardStats
import com.gympulse.app.domain.model.Exercise
import com.gympulse.app.domain.model.ExercisePerformance
import com.gympulse.app.domain.model.ExerciseProgress
import com.gympulse.app.domain.model.GeoPoint
import com.gympulse.app.domain.model.LocationContext
import com.gympulse.app.domain.model.PersonalRecord
import com.gympulse.app.domain.model.StatsPeriod
import com.gympulse.app.domain.model.ThemeMode
import com.gympulse.app.domain.model.TrainingLocation
import com.gympulse.app.domain.model.TrainingProgram
import com.gympulse.app.domain.model.UserPreferences
import com.gympulse.app.domain.model.WeightUnit
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ExerciseRepository {
    fun observeExercises(): Flow<List<Exercise>>
    fun observeExercise(id: Long): Flow<Exercise?>
    suspend fun getExercise(id: Long): Exercise?
    suspend fun saveExercise(exercise: Exercise): Long
    suspend fun deleteExercise(id: Long)
    suspend fun countSessionsUsing(exerciseId: Long): Int
}

interface SessionRepository {
    fun observeSessions(): Flow<List<WorkoutSession>>
    fun observeSession(id: Long): Flow<WorkoutSession?>
    suspend fun getSession(id: Long): WorkoutSession?
    suspend fun saveSession(session: WorkoutSession): Long
    suspend fun deleteSession(id: Long)
    suspend fun duplicateSession(id: Long, newName: String): Long
    suspend fun setFavorite(id: Long, favorite: Boolean)

    /** Stores the order chosen by the user: the session at index 0 comes first everywhere. */
    suspend fun reorderSessions(orderedIds: List<Long>)
}

interface ProgramRepository {
    fun observePrograms(): Flow<List<TrainingProgram>>
    fun observeProgram(id: Long): Flow<TrainingProgram?>
    suspend fun getProgram(id: Long): TrainingProgram?
    suspend fun saveProgram(program: TrainingProgram): Long
    suspend fun deleteProgram(id: Long)
}

interface WorkoutRepository {
    /** Completed workouts, most recent first. */
    fun observeHistory(): Flow<List<WorkoutExecution>>
    fun observeExecution(id: Long): Flow<WorkoutExecution?>
    fun observeActiveExecution(): Flow<WorkoutExecution?>
    suspend fun getExecution(id: Long): WorkoutExecution?
    suspend fun getActiveExecution(): WorkoutExecution?
    suspend fun createExecution(execution: WorkoutExecution): Long
    suspend fun saveExecution(execution: WorkoutExecution)
    suspend fun deleteExecution(id: Long)
}

interface ProgressRepository {
    fun observeDashboard(period: StatsPeriod): Flow<DashboardStats>
    fun observeExerciseProgress(exerciseId: Long): Flow<ExerciseProgress>
    /** Last completed performance of each exercise, keyed by exercise id. */
    fun observeLastPerformances(): Flow<Map<Long, ExercisePerformance>>
    /**
     * Last completed performance of an exercise. When [preferredSessionId] is given, a performance
     * done within that same session template is preferred, as it is the most comparable one.
     */
    suspend fun lastPerformance(
        exerciseId: Long,
        preferredSessionId: Long? = null,
        excludingExecutionId: Long? = null,
    ): ExercisePerformance?
    suspend fun detectRecords(execution: WorkoutExecution): List<PersonalRecord>
}

interface LocationRepository {
    val context: StateFlow<LocationContext>
    fun observeLocations(): Flow<List<TrainingLocation>>
    fun observeLocation(id: Long): Flow<TrainingLocation?>
    suspend fun getLocation(id: Long): TrainingLocation?
    suspend fun saveLocation(location: TrainingLocation): Long
    suspend fun deleteLocation(id: Long)
    suspend fun linkSessions(locationId: Long, sessionIds: Set<Long>)
    suspend fun linkPrograms(locationId: Long, programIds: Set<Long>)
    /** Detects the current place with the device position, if the permission is granted. */
    suspend fun refreshDetection()
    suspend fun selectManually(locationId: Long?)
    suspend fun enableAutoDetection()
    suspend fun currentPosition(): GeoPoint?
    fun hasLocationPermission(): Boolean
}

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setUserName(name: String)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setWeightUnit(unit: WeightUnit)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setVibrationEnabled(enabled: Boolean)
    suspend fun setKeepScreenOn(enabled: Boolean)
    suspend fun setDefaultRestSeconds(seconds: Int)
    suspend fun setWeeklyGoal(goal: Int)
    suspend fun setAutoDetectLocation(enabled: Boolean)
    suspend fun setManualLocationId(id: Long?)

    /** Stores values received from the server without queueing a new upload. */
    suspend fun applyFromServer(preferences: UserPreferences)
}

/** Seeds and removes demonstration content. */
interface DemoDataRepository {
    suspend fun seedLibrary()
    suspend fun seedDemoContent()
    suspend fun deleteDemoContent()
    suspend fun hasDemoContent(): Boolean
}
