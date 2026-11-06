package com.gympulse.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.gympulse.app.domain.model.Equipment
import com.gympulse.app.domain.model.ExerciseStatus
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.LoadMode
import com.gympulse.app.domain.model.LocationType
import com.gympulse.app.domain.model.MuscleGroup
import com.gympulse.app.domain.model.SetStatus
import com.gympulse.app.domain.model.SetTarget
import com.gympulse.app.domain.model.TrainingGoal
import com.gympulse.app.domain.model.WorkoutStatus

@Entity(tableName = "exercises", indices = [Index("name"), Index("remoteRef", unique = true)])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val imageUri: String?,
    val notes: String,
    val type: ExerciseType,
    val muscleGroups: List<MuscleGroup>,
    val equipment: List<Equipment>,
    val loadMode: LoadMode,
    val createdAt: Long,
    val remoteRef: String? = null,
    /** Provided by the server library: shared, read-only, never pushed. */
    @ColumnInfo(defaultValue = "0") val isLibrary: Boolean = false,
)

@Entity(tableName = "locations", indices = [Index("remoteRef", unique = true)])
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "''") val address: String = "",
    val latitude: Double?,
    val longitude: Double?,
    val radiusMeters: Int,
    val type: LocationType,
    val availableEquipment: List<Equipment>,
    val isDemo: Boolean,
    val createdAt: Long,
    val remoteRef: String? = null,
)

@Entity(tableName = "sessions", indices = [Index("remoteRef", unique = true)])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val trainingGoal: TrainingGoal,
    val isFavorite: Boolean,
    val isDemo: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val remoteRef: String? = null,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
)

@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val position: Int,
    val restSeconds: Int,
    val notes: String,
    val sets: List<SetTarget>,
)

@Entity(
    tableName = "session_locations",
    primaryKeys = ["sessionId", "locationId"],
    foreignKeys = [
        ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(LocationEntity::class, ["id"], ["locationId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("locationId")],
)
data class SessionLocationCrossRef(val sessionId: Long, val locationId: Long)

@Entity(tableName = "programs", indices = [Index("remoteRef", unique = true)])
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val goal: TrainingGoal,
    val isDemo: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val remoteRef: String? = null,
)

@Entity(
    tableName = "program_sessions",
    primaryKeys = ["programId", "sessionId"],
    foreignKeys = [
        ForeignKey(ProgramEntity::class, ["id"], ["programId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sessionId")],
)
data class ProgramSessionCrossRef(val programId: Long, val sessionId: Long, val position: Int)

@Entity(
    tableName = "program_locations",
    primaryKeys = ["programId", "locationId"],
    foreignKeys = [
        ForeignKey(ProgramEntity::class, ["id"], ["programId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(LocationEntity::class, ["id"], ["locationId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("locationId")],
)
data class ProgramLocationCrossRef(val programId: Long, val locationId: Long)

/**
 * A performed workout. Session, exercise and location data are snapshotted (no foreign keys) so
 * the history survives edits and deletions of templates.
 */
@Entity(
    tableName = "workout_executions",
    indices = [Index("status"), Index("startedAt"), Index("sessionId"), Index("remoteRef", unique = true)],
)
data class WorkoutExecutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long?,
    val sessionName: String,
    val trainingGoal: TrainingGoal,
    val status: WorkoutStatus,
    val startedAt: Long,
    val endedAt: Long?,
    val pausedAt: Long?,
    val totalPausedMs: Long,
    val locationId: Long?,
    val locationName: String?,
    val notes: String,
    val currentExerciseIndex: Int,
    val currentSetIndex: Int,
    val restStartedAt: Long?,
    val restEndsAt: Long?,
    val restTotalSeconds: Int?,
    val restExerciseKey: String?,
    val restSetIndex: Int?,
    val restPausedRemainingMs: Long?,
    val setTimerAccumulatedMs: Long?,
    val setTimerRunningSince: Long?,
    val isDemo: Boolean,
    val remoteRef: String? = null,
)

@Entity(
    tableName = "executed_exercises",
    foreignKeys = [ForeignKey(WorkoutExecutionEntity::class, ["id"], ["executionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("executionId"), Index("exerciseId")],
)
data class ExecutedExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val executionId: Long,
    val key: String,
    val position: Int,
    val exerciseId: Long?,
    val name: String,
    val type: ExerciseType,
    val loadMode: LoadMode,
    val imageUri: String?,
    val muscleGroups: List<MuscleGroup>,
    val equipment: List<Equipment>,
    val restSeconds: Int,
    val notes: String,
    val status: ExerciseStatus,
)

@Entity(
    tableName = "executed_sets",
    foreignKeys = [ForeignKey(ExecutedExerciseEntity::class, ["id"], ["executedExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("executedExerciseId")],
)
data class ExecutedSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val executedExerciseId: Long,
    val position: Int,
    val targetRepetitions: Int?,
    val targetWeightKg: Double?,
    val targetDurationSeconds: Int?,
    val restSeconds: Int,
    val actualRepetitions: Int?,
    val actualWeightKg: Double?,
    val actualDurationSeconds: Int?,
    val actualRestSeconds: Int?,
    val status: SetStatus,
    val completedAt: Long?,
    val isExtra: Boolean,
)

data class SessionExerciseWithExercise(
    @Embedded val sessionExercise: SessionExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)

data class SessionWithDetails(
    @Embedded val session: SessionEntity,
    @Relation(entity = SessionExerciseEntity::class, parentColumn = "id", entityColumn = "sessionId")
    val exercises: List<SessionExerciseWithExercise>,
    @Relation(entity = SessionLocationCrossRef::class, parentColumn = "id", entityColumn = "sessionId")
    val locations: List<SessionLocationCrossRef>,
)

data class ProgramWithRefs(
    @Embedded val program: ProgramEntity,
    @Relation(entity = ProgramSessionCrossRef::class, parentColumn = "id", entityColumn = "programId")
    val sessions: List<ProgramSessionCrossRef>,
    @Relation(entity = ProgramLocationCrossRef::class, parentColumn = "id", entityColumn = "programId")
    val locations: List<ProgramLocationCrossRef>,
)

data class ExecutedExerciseWithSets(
    @Embedded val exercise: ExecutedExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "executedExerciseId")
    val sets: List<ExecutedSetEntity>,
)

data class ExecutionWithDetails(
    @Embedded val execution: WorkoutExecutionEntity,
    @Relation(entity = ExecutedExerciseEntity::class, parentColumn = "id", entityColumn = "executionId")
    val exercises: List<ExecutedExerciseWithSets>,
)

/** Local change waiting to be sent to the API. See [com.gympulse.app.data.sync.SyncOutbox]. */
@Entity(tableName = "sync_ops", indices = [Index("entity", "localId")])
data class SyncOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entity: String,
    val localId: Long,
    /** Server ref captured at deletion time, when the local row no longer exists. */
    val remoteRef: String?,
    val op: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    /** Rejected by the server (4xx): kept for diagnostics, no longer retried. */
    val failed: Boolean = false,
)
