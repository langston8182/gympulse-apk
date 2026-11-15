package com.gympulse.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gympulse.app.core.time.TimeProvider
import com.gympulse.app.data.local.db.GymPulseDatabase
import com.gympulse.app.data.repository.LocalLocationRepository
import com.gympulse.app.data.sync.FakePreferences
import com.gympulse.app.data.sync.SyncOutbox
import com.gympulse.app.domain.location.DeviceLocationProvider
import com.gympulse.app.domain.model.GeoPoint
import com.gympulse.app.domain.model.TrainingLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeDeviceLocation(var granted: Boolean = true, var position: GeoPoint? = null) : DeviceLocationProvider {
    override fun hasPermission() = granted
    override suspend fun currentPosition() = position
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocationDetectionTest {
    private lateinit var db: GymPulseDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var repository: LocalLocationRepository
    private val preferences = FakePreferences()
    private val device = FakeDeviceLocation()
    private var now = 1_700_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GymPulseDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val time = TimeProvider { now }
        repository = LocalLocationRepository(db, preferences, device, time, SyncOutbox(db, time) { _ -> }, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private suspend fun saveLocations(): Pair<Long, Long> {
        val home = repository.saveLocation(TrainingLocation(name = "Maison", latitude = 48.8590, longitude = 2.3470, radiusMeters = 120))
        val gym = repository.saveLocation(TrainingLocation(name = "Salle", latitude = 48.8530, longitude = 2.3690, radiusMeters = 150))
        return home to gym
    }

    @Test
    fun `opening the app detects the place and replaces an earlier manual choice`() = runBlocking {
        val (home, gym) = saveLocations()
        repository.selectManually(home)
        device.position = GeoPoint(48.8531, 2.3691) // at the gym

        repository.detectOnAppOpen()

        assertNull(preferences.preferences.first().manualLocationId)
        assertEquals(gym, repository.context.first { it.location != null }.location!!.id)
    }

    @Test
    fun `when no place matches the manual choice is kept`() = runBlocking {
        val (home, _) = saveLocations()
        repository.selectManually(home)
        device.position = GeoPoint(45.76, 4.83) // far from every place

        repository.detectOnAppOpen()

        assertEquals(home, preferences.preferences.first().manualLocationId)
        assertEquals(home, repository.context.first { it.location != null }.location!!.id)
    }

    @Test
    fun `without the location permission nothing changes`() = runBlocking {
        val (home, _) = saveLocations()
        repository.selectManually(home)
        device.granted = false

        repository.detectOnAppOpen()

        assertEquals(home, preferences.preferences.first().manualLocationId)
    }
}
