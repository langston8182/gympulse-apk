package com.gympulse.app.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.gympulse.app.core.time.TimeProvider
import com.gympulse.app.data.local.WorkoutLocalStore
import com.gympulse.app.data.local.db.GymPulseDatabase
import com.gympulse.app.data.local.entity.LocationEntity
import com.gympulse.app.data.local.entity.ProgramEntity
import com.gympulse.app.data.local.entity.ProgramLocationCrossRef
import com.gympulse.app.data.local.entity.ProgramSessionCrossRef
import com.gympulse.app.data.local.entity.SessionEntity
import com.gympulse.app.data.local.entity.SessionExerciseEntity
import com.gympulse.app.data.local.entity.SessionLocationCrossRef
import com.gympulse.app.data.local.entity.SyncOpEntity
import com.gympulse.app.data.local.toDomain
import com.gympulse.app.data.remote.api.GymPulseApi
import com.gympulse.app.data.remote.dto.ProgramDto
import com.gympulse.app.data.remote.dto.SessionDto
import com.gympulse.app.data.remote.dto.SessionExerciseDto
import com.gympulse.app.data.remote.dto.WorkoutDto
import com.gympulse.app.domain.model.TrainingGoal
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.model.WorkoutStatus
import com.gympulse.app.domain.repository.PreferencesRepository
import com.gympulse.app.domain.workout.ActiveWorkoutRestorer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncError { OFFLINE, UNAUTHORIZED, SERVER, UNKNOWN }

enum class SyncOutcome { SUCCESS, UNAUTHORIZED, RETRY }

data class SyncState(
    val running: Boolean = false,
    val lastSyncAt: Long? = null,
    val error: SyncError? = null,
)

private class StopSync(val error: SyncError, val outcome: SyncOutcome) : Exception()

/**
 * Keeps Room (the source of truth of the UI) and the API consistent: [push] sends the queued local
 * changes first, then [pull] refreshes the local data from the server, leaving untouched whatever
 * still has a change waiting to be sent.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val db: GymPulseDatabase,
    private val api: GymPulseApi,
    private val preferences: PreferencesRepository,
    private val workoutStore: WorkoutLocalStore,
    private val outbox: SyncOutbox,
    private val restorer: ActiveWorkoutRestorer,
    private val time: TimeProvider,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val ops get() = db.syncOpDao()

    val pendingCount: Flow<Int> get() = ops.observePendingCount()
    val failedCount: Flow<Int> get() = ops.observeFailedCount()

    /** Gives another chance to the changes the server rejected earlier (e.g. after an API fix). */
    suspend fun retryFailed() = ops.retryFailed()

    /** [includeHistory] also downloads the full workout history and reconciles deletions. */
    suspend fun sync(includeHistory: Boolean = false): SyncOutcome = mutex.withLock {
        _state.update { it.copy(running = true) }
        try {
            push()
            pull(includeHistory)
            _state.update { SyncState(running = false, lastSyncAt = time.now(), error = null) }
            SyncOutcome.SUCCESS
        } catch (stop: StopSync) {
            _state.update { it.copy(running = false, error = stop.error) }
            stop.outcome
        } catch (e: CancellationException) {
            _state.update { it.copy(running = false) }
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Synchronisation failed", e)
            val (error, outcome) = classify(e)
            _state.update { it.copy(running = false, error = error) }
            outcome
        }
    }

    private fun classify(e: Exception): Pair<SyncError, SyncOutcome> = when {
        e is HttpException && e.code() == 401 -> SyncError.UNAUTHORIZED to SyncOutcome.UNAUTHORIZED
        e is HttpException && (e.code() >= 500 || e.code() == 408 || e.code() == 429) -> SyncError.SERVER to SyncOutcome.RETRY
        e is IOException -> SyncError.OFFLINE to SyncOutcome.RETRY
        else -> SyncError.UNKNOWN to SyncOutcome.SUCCESS
    }

    // --- Push ----------------------------------------------------------------------------------

    private suspend fun push() {
        for (op in ops.pending()) {
            try {
                process(op)
                ops.delete(op.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Change ${op.entity}/${op.op} failed", e)
                val (error, outcome) = classify(e)
                val permanent = e !is IOException && !(e is HttpException && (e.code() == 401 || e.code() >= 500 || e.code() == 408 || e.code() == 429))
                if (permanent) {
                    ops.markFailed(op.id, e.describe())
                } else {
                    ops.recordAttempt(op.id, e.describe())
                    throw StopSync(error, outcome)
                }
            }
        }
    }

    private fun Exception.describe(): String = if (this is HttpException) "HTTP ${code()}" else "${javaClass.simpleName}: $message"

    private suspend fun process(op: SyncOpEntity) {
        val upsert = op.op == SyncOperation.UPSERT.name
        when (SyncEntity.valueOf(op.entity)) {
            SyncEntity.EXERCISE -> if (upsert) pushExercise(op.localId) else remoteDelete(op) { api.deleteExercise(it) }
            SyncEntity.LOCATION -> if (upsert) pushLocation(op.localId) else remoteDelete(op) { api.deleteLocation(it) }
            SyncEntity.SESSION -> if (upsert) pushSession(op.localId) else remoteDelete(op) { api.deleteSession(it) }
            SyncEntity.PROGRAM -> if (upsert) pushProgram(op.localId) else remoteDelete(op) { api.deleteProgram(it) }
            SyncEntity.WORKOUT -> if (upsert) pushWorkout(op.localId) else remoteDelete(op) { api.deleteWorkout(it) }
            SyncEntity.ACTIVE_WORKOUT -> if (upsert) pushActiveWorkout() else remoteDelete(op) { api.abandonActiveWorkout() }
            SyncEntity.PREFERENCES -> pushPreferences()
        }
    }

    private suspend fun remoteDelete(op: SyncOpEntity, call: suspend (String) -> Unit) {
        val ref = op.remoteRef ?: return
        try {
            call(ref)
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
        }
    }

    private suspend fun <T> createOrUpdate(ref: String?, update: suspend (String) -> T, create: suspend () -> T): T {
        if (ref == null) return create()
        return try {
            update(ref)
        } catch (e: HttpException) {
            if (e.code() == 404) create() else throw e
        }
    }

    private suspend fun pushExercise(id: Long): String? {
        val entity = db.exerciseDao().get(id) ?: return null
        if (entity.isLibrary) return entity.remoteRef
        val dto = entity.toDto()
        val saved = createOrUpdate(entity.remoteRef, { api.updateExercise(it, dto) }, { api.createExercise(dto.copy(ref = null)) })
        db.exerciseDao().setRemoteRef(id, saved.ref)
        return saved.ref
    }

    private suspend fun ensureExercise(id: Long): String? =
        db.exerciseDao().get(id)?.let { it.remoteRef ?: pushExercise(id) }

    private suspend fun pushLocation(id: Long): String? {
        val entity = db.locationDao().get(id) ?: return null
        val dto = entity.toDto()
        val saved = createOrUpdate(entity.remoteRef, { api.updateLocation(it, dto) }, { api.createLocation(dto.copy(ref = null)) })
        db.locationDao().setRemoteRef(id, saved.ref)
        return saved.ref
    }

    private suspend fun ensureLocation(id: Long): String? =
        db.locationDao().get(id)?.let { it.remoteRef ?: pushLocation(id) }

    private suspend fun pushSession(id: Long): String? {
        val details = db.sessionDao().get(id) ?: return null
        val dto = SessionDto(
            ref = details.session.remoteRef,
            name = details.session.name,
            description = details.session.description,
            goal = details.session.trainingGoal.name,
            favorite = details.session.isFavorite,
            order = details.session.sortOrder,
            isDemo = details.session.isDemo,
            exercises = details.exercises.sortedBy { it.sessionExercise.position }.mapNotNull { item ->
                ensureExercise(item.sessionExercise.exerciseId)?.let { ref ->
                    SessionExerciseDto(ref, item.sessionExercise.restSeconds, item.sessionExercise.sets.map { it.toDto() }, item.sessionExercise.notes)
                }
            },
            locationRefs = details.locations.mapNotNull { ensureLocation(it.locationId) },
        )
        val saved = createOrUpdate(dto.ref, { api.updateSession(it, dto) }, { api.createSession(dto.copy(ref = null)) })
        db.sessionDao().setRemoteRef(id, saved.ref)
        return saved.ref
    }

    private suspend fun ensureSession(id: Long): String? =
        db.sessionDao().getEntity(id)?.let { it.remoteRef ?: pushSession(id) }

    private suspend fun pushProgram(id: Long): String? {
        val details = db.programDao().get(id) ?: return null
        val dto = ProgramDto(
            ref = details.program.remoteRef,
            name = details.program.name,
            description = details.program.description,
            goal = details.program.goal.name,
            isDemo = details.program.isDemo,
            sessionRefs = details.sessions.sortedBy { it.position }.mapNotNull { ensureSession(it.sessionId) },
            locationRefs = details.locations.mapNotNull { ensureLocation(it.locationId) },
        )
        val saved = createOrUpdate(dto.ref, { api.updateProgram(it, dto) }, { api.createProgram(dto.copy(ref = null)) })
        db.programDao().setRemoteRef(id, saved.ref)
        return saved.ref
    }

    /** Refs are only looked up, never created: the history is a snapshot and must not push templates. */
    private suspend fun refsOf(execution: WorkoutExecution): WorkoutRefs {
        val exerciseRefs = execution.exercises.mapNotNull { it.exerciseId }.distinct()
            .associateWith { db.exerciseDao().get(it)?.remoteRef }
        return WorkoutRefs(
            sessionRef = execution.sessionId?.let { db.sessionDao().getEntity(it)?.remoteRef },
            locationRef = execution.locationId?.let { db.locationDao().get(it)?.remoteRef },
            exerciseRef = { id -> id?.let { exerciseRefs[it] } },
        )
    }

    private suspend fun pushWorkout(id: Long) {
        val details = db.workoutDao().get(id) ?: return
        val execution = details.toDomain()
        if (execution.status != WorkoutStatus.COMPLETED) return
        val dto = execution.toDto(refsOf(execution))
        val remoteRef = details.execution.remoteRef
        val saved = if (remoteRef != null) {
            createOrUpdate(remoteRef, { api.updateWorkout(it, dto.copy(ref = it)) }, { api.createWorkout(dto.copy(ref = null)) })
        } else {
            // A previous attempt may have reached the server without us seeing the answer.
            api.workouts(limit = 5, from = execution.startedAt, to = execution.startedAt)
                .firstOrNull { it.startedAt == execution.startedAt }
                ?: api.createWorkout(dto)
        }
        db.workoutDao().setRemoteRef(id, saved.ref)
    }

    private suspend fun pushActiveWorkout() {
        val execution = db.workoutDao().getActive()?.toDomain() ?: return
        api.saveActiveWorkout(execution.toDto(refsOf(execution)))
    }

    private suspend fun pushPreferences() {
        val prefs = preferences.preferences.first()
        val manualRef = prefs.manualLocationId?.let { ensureLocation(it) }
        api.updatePreferences(prefs.toDto(manualRef))
    }

    // --- Pull ----------------------------------------------------------------------------------

    private suspend fun pull(includeHistory: Boolean) {
        val library = api.exerciseLibrary()
        val libraryRefs = library.mapNotNull { it.ref }.toSet()
        val mine = api.exercises().filter { it.ref !in libraryRefs }
        val locations = api.locations()
        val sessions = api.sessions()
        val programs = api.programs()
        val workouts = fetchWorkouts(includeHistory)
        val active = api.activeWorkout().body()
        val remotePrefs = api.preferences()

        var restoreActive = false
        var manualLocationId: Long? = null
        var applyPrefs = false
        db.withTransaction {
            val pending = ops.pending()
            val upserts = pending.filter { it.op == SyncOperation.UPSERT.name }.map { it.entity to it.localId }.toSet()
            val deletes = pending.filter { it.op == SyncOperation.DELETE.name && it.remoteRef != null }.map { it.entity to it.remoteRef }.toSet()
            val now = time.now()

            val exerciseIds = applyExercises(library.map { it to true } + mine.map { it to false }, upserts, deletes, now)
            val locationIds = applyLocations(locations, upserts, deletes, now)
            val sessionIds = applySessions(sessions, exerciseIds, locationIds, upserts, deletes, now)
            applyPrograms(programs, sessionIds, locationIds, upserts, deletes, now)
            applyWorkouts(workouts, includeHistory, sessionIds, locationIds, exerciseIds, upserts, deletes)

            if (pending.none { it.entity == SyncEntity.ACTIVE_WORKOUT.name }) {
                val local = db.workoutDao().getActive()
                if (active != null && local == null) {
                    workoutStore.insert(active.toExecution(sessionIds, locationIds, exerciseIds))
                    restoreActive = true
                }
            }
            if (pending.none { it.entity == SyncEntity.PREFERENCES.name }) {
                manualLocationId = remotePrefs.manualLocationRef?.let { locationIds[it] }
                applyPrefs = true
            }
        }
        if (restoreActive) restorer.restore()
        if (applyPrefs) {
            val local = preferences.preferences.first()
            preferences.applyFromServer(remotePrefs.toDomain(manualLocationId).copy(onboardingCompleted = local.onboardingCompleted || remotePrefs.onboardingCompleted))
        }
        if (db.workoutDao().getActive() != null && ops.forEntity(SyncEntity.ACTIVE_WORKOUT.name, 0).isEmpty() && active == null) {
            outbox.upsert(SyncEntity.ACTIVE_WORKOUT, 0)
        }
    }

    private suspend fun fetchWorkouts(all: Boolean): List<WorkoutDto> {
        val result = mutableListOf<WorkoutDto>()
        var before: Long? = null
        while (true) {
            val page = api.workouts(limit = PAGE_SIZE, before = before)
            result += page
            if (!all || page.size < PAGE_SIZE) break
            before = page.last().startedAt
        }
        return result
    }

    private suspend fun applyExercises(
        remote: List<Pair<com.gympulse.app.data.remote.dto.ExerciseDto, Boolean>>,
        upserts: Set<Pair<String, Long>>,
        deletes: Set<Pair<String, String?>>,
        now: Long,
    ): Map<String, Long> {
        val dao = db.exerciseDao()
        val existing = dao.getAll()
        val byRef = existing.filter { it.remoteRef != null }.associateBy { it.remoteRef!! }
        val ids = HashMap<String, Long>()
        remote.forEach { (dto, library) ->
            val ref = dto.ref ?: return@forEach
            val local = byRef[ref]
            if (SyncEntity.EXERCISE.name to ref in deletes) return@forEach
            if (local != null && SyncEntity.EXERCISE.name to local.id in upserts) {
                ids[ref] = local.id
                return@forEach
            }
            val entity = dto.toEntity(local, library, now)
            ids[ref] = local?.id ?: dao.insert(entity)
            if (local != null) dao.upsert(entity)
        }
        val gone = existing.filter { it.remoteRef != null && it.remoteRef !in ids && SyncEntity.EXERCISE.name to it.id !in upserts }
        if (gone.isNotEmpty()) dao.deleteAll(gone.map { it.id })
        return ids
    }

    private suspend fun applyLocations(
        remote: List<com.gympulse.app.data.remote.dto.LocationDto>,
        upserts: Set<Pair<String, Long>>,
        deletes: Set<Pair<String, String?>>,
        now: Long,
    ): Map<String, Long> {
        val dao = db.locationDao()
        val existing = dao.getAll()
        val byRef = existing.filter { it.remoteRef != null }.associateBy { it.remoteRef!! }
        val ids = HashMap<String, Long>()
        remote.forEach { dto ->
            val ref = dto.ref ?: return@forEach
            val local = byRef[ref]
            if (SyncEntity.LOCATION.name to ref in deletes) return@forEach
            if (local != null && SyncEntity.LOCATION.name to local.id in upserts) {
                ids[ref] = local.id
                return@forEach
            }
            val entity: LocationEntity = dto.toEntity(local, now)
            ids[ref] = local?.id ?: dao.insert(entity)
            if (local != null) dao.upsert(entity)
        }
        val gone = existing.filter { it.remoteRef != null && it.remoteRef !in ids && SyncEntity.LOCATION.name to it.id !in upserts }
        if (gone.isNotEmpty()) dao.deleteAll(gone.map { it.id })
        return ids
    }

    private suspend fun applySessions(
        remote: List<SessionDto>,
        exerciseIds: Map<String, Long>,
        locationIds: Map<String, Long>,
        upserts: Set<Pair<String, Long>>,
        deletes: Set<Pair<String, String?>>,
        now: Long,
    ): Map<String, Long> {
        val dao = db.sessionDao()
        val existing = dao.getAllEntities()
        val byRef = existing.filter { it.remoteRef != null }.associateBy { it.remoteRef!! }
        val ids = HashMap<String, Long>()
        remote.forEach { dto ->
            val ref = dto.ref ?: return@forEach
            val local = byRef[ref]
            if (SyncEntity.SESSION.name to ref in deletes) return@forEach
            if (local != null && SyncEntity.SESSION.name to local.id in upserts) {
                ids[ref] = local.id
                return@forEach
            }
            val entity = SessionEntity(
                id = local?.id ?: 0,
                name = dto.name,
                description = dto.description,
                trainingGoal = enumOr(dto.goal, TrainingGoal.CUSTOM),
                isFavorite = dto.favorite,
                sortOrder = dto.order,
                isDemo = dto.isDemo,
                createdAt = local?.createdAt ?: now,
                updatedAt = local?.updatedAt ?: now,
                remoteRef = ref,
            )
            val id = local?.id ?: dao.insert(entity)
            if (local != null) dao.upsert(entity)
            ids[ref] = id
            dao.deleteExercises(id)
            dao.insertExercises(
                dto.exercises.mapNotNull { item -> exerciseIds[item.exerciseRef]?.let { item to it } }
                    .mapIndexed { index, (item, exerciseId) ->
                        SessionExerciseEntity(
                            sessionId = id,
                            exerciseId = exerciseId,
                            position = index,
                            restSeconds = item.restSeconds,
                            notes = item.notes,
                            sets = item.sets.map { it.toDomain() },
                        )
                    },
            )
            dao.deleteLocations(id)
            dao.insertLocations(dto.locationRefs.mapNotNull { locationIds[it] }.distinct().map { SessionLocationCrossRef(id, it) })
        }
        val gone = existing.filter { it.remoteRef != null && it.remoteRef !in ids && SyncEntity.SESSION.name to it.id !in upserts }
        if (gone.isNotEmpty()) dao.deleteAll(gone.map { it.id })
        return ids
    }

    private suspend fun applyPrograms(
        remote: List<ProgramDto>,
        sessionIds: Map<String, Long>,
        locationIds: Map<String, Long>,
        upserts: Set<Pair<String, Long>>,
        deletes: Set<Pair<String, String?>>,
        now: Long,
    ) {
        val dao = db.programDao()
        val existing = dao.getAllEntities()
        val byRef = existing.filter { it.remoteRef != null }.associateBy { it.remoteRef!! }
        val seen = HashSet<String>()
        remote.forEach { dto ->
            val ref = dto.ref ?: return@forEach
            val local = byRef[ref]
            if (SyncEntity.PROGRAM.name to ref in deletes) return@forEach
            seen += ref
            if (local != null && SyncEntity.PROGRAM.name to local.id in upserts) return@forEach
            val entity = ProgramEntity(
                id = local?.id ?: 0,
                name = dto.name,
                description = dto.description,
                goal = enumOr(dto.goal, TrainingGoal.CUSTOM),
                isDemo = dto.isDemo,
                createdAt = local?.createdAt ?: now,
                updatedAt = local?.updatedAt ?: now,
                remoteRef = ref,
            )
            val id = local?.id ?: dao.insert(entity)
            if (local != null) dao.upsert(entity)
            dao.deleteSessions(id)
            dao.insertSessions(
                dto.sessionRefs.mapNotNull { sessionIds[it] }.distinct().mapIndexed { index, sessionId -> ProgramSessionCrossRef(id, sessionId, index) },
            )
            dao.deleteLocations(id)
            dao.insertLocations(dto.locationRefs.mapNotNull { locationIds[it] }.distinct().map { ProgramLocationCrossRef(id, it) })
        }
        val gone = existing.filter { it.remoteRef != null && it.remoteRef !in seen && SyncEntity.PROGRAM.name to it.id !in upserts }
        if (gone.isNotEmpty()) dao.deleteAll(gone.map { it.id })
    }

    private suspend fun applyWorkouts(
        remote: List<WorkoutDto>,
        reconcile: Boolean,
        sessionIds: Map<String, Long>,
        locationIds: Map<String, Long>,
        exerciseIds: Map<String, Long>,
        upserts: Set<Pair<String, Long>>,
        deletes: Set<Pair<String, String?>>,
    ) {
        val dao = db.workoutDao()
        val existing = dao.getAllEntities()
        val byRef = existing.filter { it.remoteRef != null }.associateBy { it.remoteRef!! }
        val unsyncedByStart = existing.filter { it.remoteRef == null && it.status == WorkoutStatus.COMPLETED }.associateBy { it.startedAt }
        remote.forEach { dto ->
            val ref = dto.ref ?: return@forEach
            if (SyncEntity.WORKOUT.name to ref in deletes || ref in byRef) return@forEach
            val unsynced = unsyncedByStart[dto.startedAt]
            if (unsynced != null) {
                dao.setRemoteRef(unsynced.id, ref)
            } else {
                workoutStore.insert(dto.toExecution(sessionIds, locationIds, exerciseIds), ref)
            }
        }
        if (reconcile) {
            val remoteRefs = remote.mapNotNull { it.ref }.toSet()
            val gone = existing.filter {
                it.remoteRef != null && it.remoteRef !in remoteRefs && it.status == WorkoutStatus.COMPLETED &&
                    SyncEntity.WORKOUT.name to it.id !in upserts
            }
            if (gone.isNotEmpty()) dao.deleteAll(gone.map { it.id })
        }
    }

    private fun WorkoutDto.toExecution(
        sessionIds: Map<String, Long>,
        locationIds: Map<String, Long>,
        exerciseIds: Map<String, Long>,
    ) = toDomain(
        sessionId = sessionRef?.let { sessionIds[it] },
        locationId = locationRef?.let { locationIds[it] },
        exerciseId = { ref -> ref?.let { exerciseIds[it] } },
    )

    private companion object {
        const val PAGE_SIZE = 500
        const val TAG = "GymPulseSync"
    }
}
