package com.gympulse.app.data.repository

import androidx.room.withTransaction
import com.gympulse.app.core.coroutines.ApplicationScope
import com.gympulse.app.core.time.TimeProvider
import com.gympulse.app.data.local.db.GymPulseDatabase
import com.gympulse.app.data.sync.SyncEntity
import com.gympulse.app.data.sync.SyncOutbox
import com.gympulse.app.data.local.entity.ProgramLocationCrossRef
import com.gympulse.app.data.local.entity.SessionLocationCrossRef
import com.gympulse.app.data.local.toDomain
import com.gympulse.app.data.local.toEntity
import com.gympulse.app.domain.location.DeviceLocationProvider
import com.gympulse.app.domain.location.LocationMatcher
import com.gympulse.app.domain.model.DetectionStatus
import com.gympulse.app.domain.model.GeoPoint
import com.gympulse.app.domain.model.LocationContext
import com.gympulse.app.domain.model.LocationSource
import com.gympulse.app.domain.model.TrainingLocation
import com.gympulse.app.domain.repository.LocationRepository
import com.gympulse.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saved training places and current place resolution. Detection is a one-shot position request
 * when the app or the sessions screen opens: there is no background tracking in this version.
 */
@Singleton
class LocalLocationRepository @Inject constructor(
    private val db: GymPulseDatabase,
    private val preferences: PreferencesRepository,
    private val deviceLocation: DeviceLocationProvider,
    private val time: TimeProvider,
    private val outbox: SyncOutbox,
    @ApplicationScope scope: CoroutineScope,
) : LocationRepository {

    private data class Detection(
        val status: DetectionStatus = DetectionStatus.IDLE,
        val locationId: Long? = null,
        val distanceMeters: Double? = null,
        val at: Long = 0,
    )

    private val dao = db.locationDao()
    private val detection = MutableStateFlow(Detection())
    private val detectionMutex = Mutex()

    override val context: StateFlow<LocationContext> =
        combine(preferences.preferences, observeLocations(), detection) { prefs, locations, detected ->
            val manual = prefs.manualLocationId?.let { id -> locations.firstOrNull { it.id == id } }
            when {
                manual != null -> LocationContext(manual, LocationSource.MANUAL, DetectionStatus.MATCHED)
                prefs.autoDetectLocation -> {
                    val location = detected.locationId?.let { id -> locations.firstOrNull { it.id == id } }
                    LocationContext(
                        location = location,
                        source = if (location != null) LocationSource.AUTO else LocationSource.NONE,
                        status = detected.status,
                        distanceMeters = detected.distanceMeters,
                    )
                }
                else -> LocationContext()
            }
        }.stateIn(scope, SharingStarted.Eagerly, LocationContext())

    override fun observeLocations(): Flow<List<TrainingLocation>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeLocation(id: Long): Flow<TrainingLocation?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun getLocation(id: Long): TrainingLocation? = dao.get(id)?.toDomain()

    override suspend fun saveLocation(location: TrainingLocation): Long {
        val previous = if (location.id != 0L) dao.get(location.id) else null
        val entity = location.copy(createdAt = location.createdAt.takeIf { it > 0 } ?: time.now()).toEntity()
            .copy(remoteRef = previous?.remoteRef)
        val id = db.withTransaction {
            val saved = if (entity.id == 0L) dao.insert(entity) else entity.id.also { dao.upsert(entity) }
            outbox.upsert(SyncEntity.LOCATION, saved)
            saved
        }
        outbox.requestSync()
        detection.value = detection.value.copy(at = 0)
        return id
    }

    override suspend fun deleteLocation(id: Long) {
        val remoteRef = dao.get(id)?.remoteRef
        db.withTransaction {
            dao.delete(id)
            outbox.delete(SyncEntity.LOCATION, id, remoteRef)
        }
        outbox.requestSync()
        if (preferences.preferences.first().manualLocationId == id) preferences.setManualLocationId(null)
        if (detection.value.locationId == id) detection.value = Detection()
    }

    /** Links are carried by the sessions and programs themselves: those whose links changed are re-sent. */
    override suspend fun linkSessions(locationId: Long, sessionIds: Set<Long>) {
        db.withTransaction {
            val dao = db.sessionDao()
            val before = dao.sessionIdsForLocation(locationId).toSet()
            dao.deleteLocationLinks(locationId)
            dao.insertLocations(sessionIds.map { SessionLocationCrossRef(it, locationId) })
            (before.symmetricDifference(sessionIds)).forEach { outbox.upsert(SyncEntity.SESSION, it) }
        }
        outbox.requestSync()
    }

    override suspend fun linkPrograms(locationId: Long, programIds: Set<Long>) {
        db.withTransaction {
            val dao = db.programDao()
            val before = dao.programIdsForLocation(locationId).toSet()
            dao.deleteLocationLinks(locationId)
            dao.insertLocations(programIds.map { ProgramLocationCrossRef(it, locationId) })
            (before.symmetricDifference(programIds)).forEach { outbox.upsert(SyncEntity.PROGRAM, it) }
        }
        outbox.requestSync()
    }

    private fun Set<Long>.symmetricDifference(other: Set<Long>): Set<Long> = (this - other) + (other - this)

    override suspend fun refreshDetection() {
        detectionMutex.withLock {
            val prefs = preferences.preferences.first()
            if (!prefs.autoDetectLocation || prefs.manualLocationId != null) return
            val current = detection.value
            val isFresh = time.now() - current.at < DETECTION_TTL_MS &&
                current.status in setOf(DetectionStatus.MATCHED, DetectionStatus.NO_MATCH)
            if (isFresh) return
            if (!deviceLocation.hasPermission()) {
                detection.value = Detection(DetectionStatus.PERMISSION_DENIED, at = time.now())
                return
            }
            detection.value = current.copy(status = DetectionStatus.DETECTING)
            val position = deviceLocation.currentPosition()
            if (position == null) {
                detection.value = Detection(DetectionStatus.UNAVAILABLE, at = time.now())
                return
            }
            val locations = dao.getAll().map { it.toDomain() }
            val match = LocationMatcher.match(position, locations)
            detection.value = if (match != null) {
                Detection(DetectionStatus.MATCHED, match.location.id, match.distanceMeters, time.now())
            } else {
                Detection(DetectionStatus.NO_MATCH, at = time.now())
            }
        }
    }

    override suspend fun selectManually(locationId: Long?) {
        preferences.setManualLocationId(locationId)
    }

    override suspend fun enableAutoDetection() {
        preferences.setManualLocationId(null)
        preferences.setAutoDetectLocation(true)
        detection.value = Detection()
        refreshDetection()
    }

    override suspend fun currentPosition(): GeoPoint? =
        if (deviceLocation.hasPermission()) deviceLocation.currentPosition() else null

    override fun hasLocationPermission(): Boolean = deviceLocation.hasPermission()

    private companion object {
        const val DETECTION_TTL_MS = 2 * 60_000L
    }
}
