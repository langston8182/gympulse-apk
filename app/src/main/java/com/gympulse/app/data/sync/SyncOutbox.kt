package com.gympulse.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gympulse.app.core.time.TimeProvider
import com.gympulse.app.data.local.db.GymPulseDatabase
import com.gympulse.app.data.local.entity.SyncOpEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncEntity { EXERCISE, SESSION, PROGRAM, LOCATION, WORKOUT, ACTIVE_WORKOUT, PREFERENCES }

enum class SyncOperation { UPSERT, DELETE }

/** Triggers a synchronisation as soon as the device has a connection. */
fun interface SyncScheduler {
    /** [full] also reconciles the whole workout history, which is heavier. */
    fun requestSync(full: Boolean)
}

/**
 * Local changes waiting to reach the API. Rows are written in the same Room transaction as the
 * change itself, so a crash can never leave a modification that is neither sent nor queued.
 * Callers must call [requestSync] once the transaction has committed.
 */
@Singleton
class SyncOutbox @Inject constructor(
    private val db: GymPulseDatabase,
    private val time: TimeProvider,
    private val scheduler: SyncScheduler,
) {
    private val dao get() = db.syncOpDao()

    /**
     * Queues a create/update; at most one upsert is kept per entity and it always gets a new id.
     * The engine only removes the op it actually sent, so a change made during a push is never lost.
     */
    suspend fun upsert(entity: SyncEntity, localId: Long) {
        dao.deleteFor(entity.name, localId, SyncOperation.UPSERT.name)
        insert(entity, localId, null, SyncOperation.UPSERT)
    }

    /** Forgets every queued change of the entity; queues a server deletion when it was already synced. */
    suspend fun delete(entity: SyncEntity, localId: Long, remoteRef: String?) {
        dao.deleteAllFor(entity.name, localId)
        if (remoteRef != null) insert(entity, localId, remoteRef, SyncOperation.DELETE)
    }

    /** Drops the queued changes of an entity without deleting anything remotely. */
    suspend fun cancel(entity: SyncEntity, localId: Long) = dao.deleteAllFor(entity.name, localId)

    fun requestSync(full: Boolean = false) = scheduler.requestSync(full)

    private suspend fun insert(entity: SyncEntity, localId: Long, remoteRef: String?, op: SyncOperation) {
        dao.insert(SyncOpEntity(entity = entity.name, localId = localId, remoteRef = remoteRef, op = op.name, createdAt = time.now()))
    }
}

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SyncScheduler {
    override fun requestSync(full: Boolean) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_FULL to full))
            .build()
        // REPLACE: a request must never wait behind a failed attempt that is sleeping in its back-off.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "gympulse-sync"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncEntryPoint {
    fun syncEngine(): SyncEngine
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val engine = EntryPointAccessors.fromApplication(applicationContext, SyncEntryPoint::class.java).syncEngine()
        return when (engine.sync(includeHistory = inputData.getBoolean(KEY_FULL, false))) {
            SyncOutcome.SUCCESS, SyncOutcome.UNAUTHORIZED -> Result.success()
            SyncOutcome.RETRY -> Result.retry()
        }
    }

    companion object {
        const val KEY_FULL = "full"
    }
}
