package com.gympulse.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.gympulse.app.data.local.entity.ExecutedExerciseEntity
import com.gympulse.app.data.local.entity.ExecutedSetEntity
import com.gympulse.app.data.local.entity.ExecutionWithDetails
import com.gympulse.app.data.local.entity.ExerciseEntity
import com.gympulse.app.data.local.entity.LocationEntity
import com.gympulse.app.data.local.entity.ProgramEntity
import com.gympulse.app.data.local.entity.ProgramLocationCrossRef
import com.gympulse.app.data.local.entity.ProgramSessionCrossRef
import com.gympulse.app.data.local.entity.ProgramWithRefs
import com.gympulse.app.data.local.entity.SessionEntity
import com.gympulse.app.data.local.entity.SessionExerciseEntity
import com.gympulse.app.data.local.entity.SessionLocationCrossRef
import com.gympulse.app.data.local.entity.SyncOpEntity
import com.gympulse.app.data.local.entity.SessionWithDetails
import com.gympulse.app.data.local.entity.WorkoutExecutionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("UPDATE exercises SET remoteRef = :remoteRef WHERE id = :id")
    suspend fun setRemoteRef(id: Long, remoteRef: String?)

    @Query("DELETE FROM exercises WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observe(id: Long): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun get(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<ExerciseEntity>

    @Insert
    suspend fun insert(exercise: ExerciseEntity): Long

    @Upsert
    suspend fun upsert(exercise: ExerciseEntity): Long

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("SELECT COUNT(DISTINCT sessionId) FROM session_exercises WHERE exerciseId = :exerciseId")
    suspend fun countSessionsUsing(exerciseId: Long): Int
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions")
    suspend fun getAllEntities(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getEntity(id: Long): SessionEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM sessions")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE sessions SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Query("UPDATE sessions SET remoteRef = :remoteRef WHERE id = :id")
    suspend fun setRemoteRef(id: Long, remoteRef: String?)

    @Query("DELETE FROM sessions WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Transaction
    @Query("SELECT * FROM sessions ORDER BY sortOrder, createdAt, id")
    fun observeAll(): Flow<List<SessionWithDetails>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: Long): Flow<SessionWithDetails?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionWithDetails?

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Upsert
    suspend fun upsert(session: SessionEntity): Long

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sessions SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM session_exercises WHERE sessionId = :sessionId")
    suspend fun deleteExercises(sessionId: Long)

    @Insert
    suspend fun insertExercises(exercises: List<SessionExerciseEntity>)

    @Query("DELETE FROM session_locations WHERE sessionId = :sessionId")
    suspend fun deleteLocations(sessionId: Long)

    @Query("DELETE FROM session_locations WHERE locationId = :locationId")
    suspend fun deleteLocationLinks(locationId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLocations(refs: List<SessionLocationCrossRef>)

    @Query("SELECT sessionId FROM session_locations WHERE locationId = :locationId")
    suspend fun sessionIdsForLocation(locationId: Long): List<Long>

    @Query("DELETE FROM sessions WHERE isDemo = 1")
    suspend fun deleteDemo()

    @Query("SELECT COUNT(*) FROM sessions WHERE isDemo = 1")
    suspend fun countDemo(): Int
}

@Dao
interface ProgramDao {
    @Query("SELECT * FROM programs")
    suspend fun getAllEntities(): List<ProgramEntity>

    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun getEntity(id: Long): ProgramEntity?

    @Query("UPDATE programs SET remoteRef = :remoteRef WHERE id = :id")
    suspend fun setRemoteRef(id: Long, remoteRef: String?)

    @Query("DELETE FROM programs WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Transaction
    @Query("SELECT * FROM programs ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProgramWithRefs>>

    @Transaction
    @Query("SELECT * FROM programs WHERE id = :id")
    fun observe(id: Long): Flow<ProgramWithRefs?>

    @Transaction
    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun get(id: Long): ProgramWithRefs?

    @Insert
    suspend fun insert(program: ProgramEntity): Long

    @Upsert
    suspend fun upsert(program: ProgramEntity): Long

    @Query("DELETE FROM programs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM program_sessions WHERE programId = :programId")
    suspend fun deleteSessions(programId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(refs: List<ProgramSessionCrossRef>)

    @Query("DELETE FROM program_locations WHERE programId = :programId")
    suspend fun deleteLocations(programId: Long)

    @Query("DELETE FROM program_locations WHERE locationId = :locationId")
    suspend fun deleteLocationLinks(locationId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLocations(refs: List<ProgramLocationCrossRef>)

    @Query("SELECT programId FROM program_locations WHERE locationId = :locationId")
    suspend fun programIdsForLocation(locationId: Long): List<Long>

    @Query("DELETE FROM programs WHERE isDemo = 1")
    suspend fun deleteDemo()
}

@Dao
interface LocationDao {
    @Query("UPDATE locations SET remoteRef = :remoteRef WHERE id = :id")
    suspend fun setRemoteRef(id: Long, remoteRef: String?)

    @Query("DELETE FROM locations WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("SELECT * FROM locations ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE id = :id")
    fun observe(id: Long): Flow<LocationEntity?>

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun get(id: Long): LocationEntity?

    @Query("SELECT * FROM locations")
    suspend fun getAll(): List<LocationEntity>

    @Insert
    suspend fun insert(location: LocationEntity): Long

    @Upsert
    suspend fun upsert(location: LocationEntity): Long

    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM locations WHERE isDemo = 1")
    suspend fun deleteDemo()
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_executions")
    suspend fun getAllEntities(): List<WorkoutExecutionEntity>

    @Query("SELECT * FROM workout_executions WHERE id = :id")
    suspend fun getEntity(id: Long): WorkoutExecutionEntity?

    @Query("UPDATE workout_executions SET remoteRef = :remoteRef WHERE id = :id")
    suspend fun setRemoteRef(id: Long, remoteRef: String?)

    @Query("DELETE FROM workout_executions WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Transaction
    @Query("SELECT * FROM workout_executions WHERE status = 'COMPLETED' ORDER BY startedAt DESC")
    fun observeCompleted(): Flow<List<ExecutionWithDetails>>

    @Transaction
    @Query("SELECT * FROM workout_executions WHERE status = 'COMPLETED' ORDER BY startedAt DESC")
    suspend fun getCompleted(): List<ExecutionWithDetails>

    @Transaction
    @Query("SELECT * FROM workout_executions WHERE id = :id")
    fun observe(id: Long): Flow<ExecutionWithDetails?>

    @Transaction
    @Query("SELECT * FROM workout_executions WHERE id = :id")
    suspend fun get(id: Long): ExecutionWithDetails?

    @Transaction
    @Query("SELECT * FROM workout_executions WHERE status IN ('IN_PROGRESS', 'PAUSED') ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<ExecutionWithDetails?>

    @Transaction
    @Query("SELECT * FROM workout_executions WHERE status IN ('IN_PROGRESS', 'PAUSED') ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): ExecutionWithDetails?

    @Insert
    suspend fun insertExecution(execution: WorkoutExecutionEntity): Long

    @Upsert
    suspend fun upsertExecution(execution: WorkoutExecutionEntity)

    @Insert
    suspend fun insertExercise(exercise: ExecutedExerciseEntity): Long

    @Insert
    suspend fun insertSets(sets: List<ExecutedSetEntity>)

    @Query("DELETE FROM executed_exercises WHERE executionId = :executionId")
    suspend fun deleteExercises(executionId: Long)

    @Query("DELETE FROM workout_executions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM workout_executions WHERE isDemo = 1")
    suspend fun deleteDemo()

    @Query("SELECT COUNT(*) FROM workout_executions WHERE isDemo = 1")
    suspend fun countDemo(): Int
}

@Dao
interface SyncOpDao {
    @Insert
    suspend fun insert(op: SyncOpEntity): Long

    @Query("SELECT * FROM sync_ops WHERE failed = 0 ORDER BY id")
    suspend fun pending(): List<SyncOpEntity>

    @Query("SELECT * FROM sync_ops WHERE entity = :entity AND localId = :localId")
    suspend fun forEntity(entity: String, localId: Long): List<SyncOpEntity>

    @Query("SELECT * FROM sync_ops WHERE failed = 0 AND op = 'DELETE' AND entity = :entity")
    suspend fun pendingDeletes(entity: String): List<SyncOpEntity>

    @Query("DELETE FROM sync_ops WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sync_ops WHERE entity = :entity AND localId = :localId AND op = :op")
    suspend fun deleteFor(entity: String, localId: Long, op: String)

    @Query("DELETE FROM sync_ops WHERE entity = :entity AND localId = :localId")
    suspend fun deleteAllFor(entity: String, localId: Long)

    @Query("UPDATE sync_ops SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun recordAttempt(id: Long, error: String?)

    @Query("UPDATE sync_ops SET failed = 1, attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String?)

    @Query("DELETE FROM sync_ops WHERE failed = 1")
    suspend fun clearFailed()

    @Query("UPDATE sync_ops SET failed = 0, attempts = 0, lastError = NULL WHERE failed = 1")
    suspend fun retryFailed()

    @Query("SELECT COUNT(*) FROM sync_ops WHERE failed = 0")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_ops WHERE failed = 1")
    fun observeFailedCount(): Flow<Int>
}
