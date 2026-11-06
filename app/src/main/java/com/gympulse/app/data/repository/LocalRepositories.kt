package com.gympulse.app.data.repository

import androidx.room.withTransaction
import com.gympulse.app.core.coroutines.ApplicationScope
import com.gympulse.app.core.coroutines.DefaultDispatcher
import com.gympulse.app.core.time.TimeProvider
import com.gympulse.app.data.image.ImageStorage
import com.gympulse.app.data.local.db.GymPulseDatabase
import com.gympulse.app.data.local.entity.ProgramLocationCrossRef
import com.gympulse.app.data.local.entity.ProgramSessionCrossRef
import com.gympulse.app.data.local.entity.SessionLocationCrossRef
import com.gympulse.app.data.local.WorkoutLocalStore
import com.gympulse.app.data.local.exerciseEntities
import com.gympulse.app.data.local.toDomain
import com.gympulse.app.data.local.toEntity
import com.gympulse.app.data.sync.SyncEntity
import com.gympulse.app.data.sync.SyncOutbox
import com.gympulse.app.domain.model.Exercise
import com.gympulse.app.domain.model.TrainingProgram
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.model.WorkoutSession
import com.gympulse.app.domain.model.WorkoutStatus
import com.gympulse.app.domain.repository.ExerciseRepository
import com.gympulse.app.domain.repository.ProgramRepository
import com.gympulse.app.domain.repository.SessionRepository
import com.gympulse.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-first repositories: Room is the source of truth of the UI, and every write also queues a
 * change in the [SyncOutbox] that the synchronisation pushes to the API.
 */
@Singleton
class LocalExerciseRepository @Inject constructor(
    private val db: GymPulseDatabase,
    private val time: TimeProvider,
    private val imageStorage: ImageStorage,
    private val outbox: SyncOutbox,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ExerciseRepository {
    private val dao = db.exerciseDao()

    override fun observeExercises(): Flow<List<Exercise>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }.flowOn(dispatcher)

    override fun observeExercise(id: Long): Flow<Exercise?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun getExercise(id: Long): Exercise? = dao.get(id)?.toDomain()

    override suspend fun saveExercise(exercise: Exercise): Long {
        val previous = if (exercise.id != 0L) dao.get(exercise.id) else null
        require(previous?.isLibrary != true) { "Library exercises are read-only" }
        val entity = exercise.copy(createdAt = exercise.createdAt.takeIf { it > 0 } ?: time.now(), isLibrary = false)
            .toEntity().copy(remoteRef = previous?.remoteRef)
        val id = db.withTransaction {
            val saved = if (entity.id == 0L) dao.insert(entity) else entity.id.also { dao.upsert(entity) }
            outbox.upsert(SyncEntity.EXERCISE, saved)
            saved
        }
        outbox.requestSync()
        if (previous?.imageUri != null && previous.imageUri != exercise.imageUri) imageStorage.delete(previous.imageUri)
        return id
    }

    override suspend fun deleteExercise(id: Long) {
        val existing = dao.get(id) ?: return
        require(!existing.isLibrary) { "Library exercises are read-only" }
        db.withTransaction {
            dao.delete(id)
            outbox.delete(SyncEntity.EXERCISE, id, existing.remoteRef)
        }
        outbox.requestSync()
        existing.imageUri?.let { imageStorage.delete(it) }
    }

    override suspend fun countSessionsUsing(exerciseId: Long): Int = dao.countSessionsUsing(exerciseId)
}

@Singleton
class LocalSessionRepository @Inject constructor(
    private val db: GymPulseDatabase,
    private val time: TimeProvider,
    private val outbox: SyncOutbox,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : SessionRepository {
    private val dao = db.sessionDao()

    override fun observeSessions(): Flow<List<WorkoutSession>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }.flowOn(dispatcher)

    override fun observeSession(id: Long): Flow<WorkoutSession?> = dao.observe(id).map { it?.toDomain() }.flowOn(dispatcher)

    override suspend fun getSession(id: Long): WorkoutSession? = dao.get(id)?.toDomain()

    override suspend fun saveSession(session: WorkoutSession): Long = db.withTransaction {
        val now = time.now()
        val entity = session.copy(
            createdAt = session.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now,
        ).toEntity().let { entity ->
            val previous = if (session.id != 0L) dao.getEntity(session.id) else null
            // The position belongs to the list, not to the editor: keep it, or append new sessions.
            entity.copy(remoteRef = previous?.remoteRef, sortOrder = previous?.sortOrder ?: (dao.maxSortOrder() + 1))
        }
        val id = if (entity.id == 0L) dao.insert(entity) else entity.id.also { dao.upsert(entity) }
        dao.deleteExercises(id)
        dao.insertExercises(session.exerciseEntities(id))
        dao.deleteLocations(id)
        dao.insertLocations(session.compatibleLocationIds.map { SessionLocationCrossRef(id, it) })
        outbox.upsert(SyncEntity.SESSION, id)
        id
    }.also { outbox.requestSync() }

    override suspend fun deleteSession(id: Long) {
        val remoteRef = dao.getEntity(id)?.remoteRef
        db.withTransaction {
            dao.delete(id)
            outbox.delete(SyncEntity.SESSION, id, remoteRef)
        }
        outbox.requestSync()
    }

    override suspend fun duplicateSession(id: Long, newName: String): Long {
        val source = getSession(id) ?: error("Session $id not found")
        return saveSession(
            source.copy(id = 0, name = newName, isFavorite = false, isDemo = false, createdAt = 0, updatedAt = 0)
        )
    }

    override suspend fun reorderSessions(orderedIds: List<Long>) {
        db.withTransaction {
            val current = dao.getAllEntities().associateBy { it.id }
            orderedIds.forEachIndexed { index, id ->
                if (current[id]?.sortOrder != index) {
                    dao.setSortOrder(id, index)
                    outbox.upsert(SyncEntity.SESSION, id)
                }
            }
        }
        outbox.requestSync()
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean) {
        db.withTransaction {
            dao.setFavorite(id, favorite)
            outbox.upsert(SyncEntity.SESSION, id)
        }
        outbox.requestSync()
    }
}

@Singleton
class LocalProgramRepository @Inject constructor(
    private val db: GymPulseDatabase,
    private val sessionRepository: SessionRepository,
    private val time: TimeProvider,
    private val outbox: SyncOutbox,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ProgramRepository {
    private val dao = db.programDao()

    override fun observePrograms(): Flow<List<TrainingProgram>> =
        combine(dao.observeAll(), sessionRepository.observeSessions()) { programs, sessions ->
            val byId = sessions.associateBy { it.id }
            programs.map { it.toDomain(byId) }
        }.flowOn(dispatcher)

    override fun observeProgram(id: Long): Flow<TrainingProgram?> =
        combine(dao.observe(id), sessionRepository.observeSessions()) { program, sessions ->
            program?.toDomain(sessions.associateBy { it.id })
        }.flowOn(dispatcher)

    override suspend fun getProgram(id: Long): TrainingProgram? {
        val program = dao.get(id) ?: return null
        val sessions = program.sessions.mapNotNull { sessionRepository.getSession(it.sessionId) }
        return program.toDomain(sessions.associateBy { it.id })
    }

    override suspend fun saveProgram(program: TrainingProgram): Long = db.withTransaction {
        val now = time.now()
        val entity = program.copy(createdAt = program.createdAt.takeIf { it > 0 } ?: now, updatedAt = now).toEntity()
            .copy(remoteRef = if (program.id != 0L) dao.getEntity(program.id)?.remoteRef else null)
        val id = if (entity.id == 0L) dao.insert(entity) else entity.id.also { dao.upsert(entity) }
        dao.deleteSessions(id)
        dao.insertSessions(program.sessions.distinctBy { it.id }.mapIndexed { index, session -> ProgramSessionCrossRef(id, session.id, index) })
        dao.deleteLocations(id)
        dao.insertLocations(program.compatibleLocationIds.map { ProgramLocationCrossRef(id, it) })
        outbox.upsert(SyncEntity.PROGRAM, id)
        id
    }.also { outbox.requestSync() }

    override suspend fun deleteProgram(id: Long) {
        val remoteRef = dao.getEntity(id)?.remoteRef
        db.withTransaction {
            dao.delete(id)
            outbox.delete(SyncEntity.PROGRAM, id, remoteRef)
        }
        outbox.requestSync()
    }
}

@Singleton
class LocalWorkoutRepository @Inject constructor(
    private val db: GymPulseDatabase,
    private val store: WorkoutLocalStore,
    private val outbox: SyncOutbox,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : WorkoutRepository {
    private val dao = db.workoutDao()

    /** Shared so that the dashboard, progress screens and workout screen map the history only once. */
    private val history: Flow<List<WorkoutExecution>> = dao.observeCompleted()
        .map { list -> list.map { it.toDomain() } }
        .flowOn(dispatcher)
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 10_000, replayExpirationMillis = 0), replay = 1)

    override fun observeHistory(): Flow<List<WorkoutExecution>> = history

    override fun observeExecution(id: Long): Flow<WorkoutExecution?> =
        dao.observe(id).map { it?.toDomain() }.distinctUntilChanged().flowOn(dispatcher)

    override fun observeActiveExecution(): Flow<WorkoutExecution?> = dao.observeActive().map { it?.toDomain() }

    override suspend fun getExecution(id: Long): WorkoutExecution? = dao.get(id)?.toDomain()

    override suspend fun getActiveExecution(): WorkoutExecution? = dao.getActive()?.toDomain()

    override suspend fun createExecution(execution: WorkoutExecution): Long = db.withTransaction {
        val id = store.insert(execution)
        if (execution.isActive) outbox.upsert(SyncEntity.ACTIVE_WORKOUT, ACTIVE_ID)
        id
    }.also { outbox.requestSync() }

    override suspend fun saveExecution(execution: WorkoutExecution) {
        db.withTransaction {
            store.replace(execution)
            when (execution.status) {
                WorkoutStatus.IN_PROGRESS, WorkoutStatus.PAUSED -> outbox.upsert(SyncEntity.ACTIVE_WORKOUT, ACTIVE_ID)
                WorkoutStatus.COMPLETED -> {
                    outbox.upsert(SyncEntity.WORKOUT, execution.id)
                    outbox.delete(SyncEntity.ACTIVE_WORKOUT, ACTIVE_ID, ACTIVE_REF)
                }
                WorkoutStatus.CANCELLED -> outbox.delete(SyncEntity.ACTIVE_WORKOUT, ACTIVE_ID, ACTIVE_REF)
            }
        }
        outbox.requestSync()
    }

    override suspend fun deleteExecution(id: Long) {
        val remoteRef = dao.getEntity(id)?.remoteRef
        db.withTransaction {
            dao.delete(id)
            outbox.delete(SyncEntity.WORKOUT, id, remoteRef)
        }
        outbox.requestSync()
    }

    private companion object {
        /** The active workout is a singleton on the server: it has no local id of its own. */
        const val ACTIVE_ID = 0L
        const val ACTIVE_REF = "active"
    }
}
