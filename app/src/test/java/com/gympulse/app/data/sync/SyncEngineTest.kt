package com.gympulse.app.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gympulse.app.core.time.TimeProvider
import com.gympulse.app.data.image.ImageStorage
import com.gympulse.app.data.local.WorkoutLocalStore
import com.gympulse.app.data.local.db.GymPulseDatabase
import com.gympulse.app.data.remote.api.GymPulseApi
import com.gympulse.app.data.repository.LocalExerciseRepository
import com.gympulse.app.data.repository.LocalSessionRepository
import com.gympulse.app.domain.model.Exercise
import com.gympulse.app.domain.model.SessionExercise
import com.gympulse.app.domain.model.SetTarget
import com.gympulse.app.domain.model.ThemeMode
import com.gympulse.app.domain.model.UserPreferences
import com.gympulse.app.domain.model.WeightUnit
import com.gympulse.app.domain.model.WorkoutSession
import com.gympulse.app.domain.repository.PreferencesRepository
import com.gympulse.app.domain.workout.ActiveWorkoutRestorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private class FakePreferences : PreferencesRepository {
    private val state = MutableStateFlow(UserPreferences())
    override val preferences = state
    override suspend fun setOnboardingCompleted(completed: Boolean) { state.value = state.value.copy(onboardingCompleted = completed) }
    override suspend fun setUserName(name: String) { state.value = state.value.copy(userName = name) }
    override suspend fun setThemeMode(mode: ThemeMode) { state.value = state.value.copy(themeMode = mode) }
    override suspend fun setDynamicColor(enabled: Boolean) { state.value = state.value.copy(dynamicColor = enabled) }
    override suspend fun setWeightUnit(unit: WeightUnit) { state.value = state.value.copy(weightUnit = unit) }
    override suspend fun setSoundEnabled(enabled: Boolean) { state.value = state.value.copy(soundEnabled = enabled) }
    override suspend fun setVibrationEnabled(enabled: Boolean) { state.value = state.value.copy(vibrationEnabled = enabled) }
    override suspend fun setKeepScreenOn(enabled: Boolean) { state.value = state.value.copy(keepScreenOn = enabled) }
    override suspend fun setDefaultRestSeconds(seconds: Int) { state.value = state.value.copy(defaultRestSeconds = seconds) }
    override suspend fun setWeeklyGoal(goal: Int) { state.value = state.value.copy(weeklyGoal = goal) }
    override suspend fun setAutoDetectLocation(enabled: Boolean) { state.value = state.value.copy(autoDetectLocation = enabled) }
    override suspend fun setManualLocationId(id: Long?) { state.value = state.value.copy(manualLocationId = id) }
    override suspend fun applyFromServer(preferences: UserPreferences) { state.value = preferences }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncEngineTest {

    private lateinit var db: GymPulseDatabase
    private lateinit var server: MockWebServer
    private lateinit var engine: SyncEngine
    private lateinit var exercises: LocalExerciseRepository
    private lateinit var sessions: LocalSessionRepository
    private val time = TimeProvider { 1_700_000_000_000L }

    private val requests = mutableListOf<String>()
    private val bodies = mutableMapOf<String, String>()

    /** Minimal in-memory API: stores what is created and serves it back, like the real one. */
    private val library = mutableListOf<JsonObject>()
    private val userExercises = mutableListOf<JsonObject>()
    private val serverSessions = mutableListOf<JsonObject>()
    private var failWith: Int? = null
    private var counter = 0

    private val dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val route = "${request.method} ${request.url.encodedPath.removePrefix("/v1")}"
            requests += route
            val text = request.body?.utf8().orEmpty()
            if (text.isNotEmpty()) bodies[route] = text
            failWith?.let { return respond(it, "{}") }
            return when (route) {
                "GET /exercises/library" -> respond(200, library.toString())
                "GET /exercises" -> respond(200, userExercises.toString())
                "GET /sessions" -> respond(200, serverSessions.toString())
                "GET /locations", "GET /programs", "GET /workouts" -> respond(200, "[]")
                "GET /preferences" -> respond(200, PREFERENCES)
                "PUT /preferences" -> respond(200, text)
                "GET /workouts/active" -> respond(204, "")
                "POST /exercises" -> created(text, "ex-${++counter}", userExercises)
                "POST /sessions" -> created(text, "se-${++counter}", serverSessions)
                else -> {
                    val match = Regex("""PUT /exercises/(.+)""").matchEntire(route)
                    if (match != null) respond(200, text) else respond(404, "{}")
                }
            }
        }

        private fun created(text: String, ref: String, store: MutableList<JsonObject>): MockResponse {
            val stored = JsonObject(Json.parseToJsonElement(text).jsonObject + ("ref" to JsonPrimitive(ref)))
            store += stored
            return respond(200, stored.toString())
        }

        private fun respond(code: Int, body: String) = MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GymPulseDatabase::class.java).allowMainThreadQueries().build()
        server = MockWebServer().apply {
            dispatcher = this@SyncEngineTest.dispatcher
            start()
        }
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true; coerceInputValues = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GymPulseApi::class.java)
        val outbox = SyncOutbox(db, time) { _ -> }
        engine = SyncEngine(db, api, FakePreferences(), WorkoutLocalStore(db), outbox, ActiveWorkoutRestorer { }, time)
        exercises = LocalExerciseRepository(db, time, ImageStorage(context, Dispatchers.Unconfined), outbox, Dispatchers.Unconfined)
        sessions = LocalSessionRepository(db, time, outbox, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun exerciseJson(ref: String, name: String) =
        Json.parseToJsonElement("""{"ref":"$ref","name":"$name"}""").jsonObject

    @Test
    fun `pushes an exercise before the session that uses it and stores the server refs`() = runBlocking {
        val exerciseId = exercises.saveExercise(Exercise(name = "Squat"))
        sessions.saveSession(
            WorkoutSession(
                name = "Legs",
                exercises = listOf(
                    SessionExercise(exercise = exercises.getExercise(exerciseId)!!, order = 0, sets = listOf(SetTarget(8, 80.0)), restDurationSeconds = 90),
                ),
            ),
        )

        assertEquals(SyncOutcome.SUCCESS, engine.sync())

        assertEquals(listOf("POST /exercises", "POST /sessions"), requests.filter { it.startsWith("POST") })
        val sent = Json.parseToJsonElement(bodies.getValue("POST /sessions")).jsonObject
        assertEquals("ex-1", sent["exercises"]!!.jsonArray.first().jsonObject["exerciseRef"]!!.jsonPrimitive.content)
        assertEquals(0, db.syncOpDao().pending().size)
        assertEquals("ex-1", db.exerciseDao().get(exerciseId)!!.remoteRef)
        assertEquals(1, db.sessionDao().getAllEntities().size)
    }

    @Test
    fun `pull imports the library as read-only exercises and drops rows deleted remotely`() = runBlocking {
        library += Json.parseToJsonElement(
            """{"ref":"bench_db","name":"Développé couché haltères","type":"REPETITIONS","muscleGroups":["CHEST"],"equipment":["DUMBBELL"],"loadMode":"PER_IMPLEMENT"}""",
        ).jsonObject
        userExercises += exerciseJson("mine-1", "Curl perso")
        assertEquals(SyncOutcome.SUCCESS, engine.sync())
        val all = db.exerciseDao().getAll()
        assertEquals(setOf("bench_db", "mine-1"), all.mapNotNull { it.remoteRef }.toSet())
        assertTrue(all.first { it.remoteRef == "bench_db" }.isLibrary)
        assertFalse(all.first { it.remoteRef == "mine-1" }.isLibrary)

        userExercises.clear()
        engine.sync()
        assertEquals(listOf("bench_db"), db.exerciseDao().getAll().mapNotNull { it.remoteRef })
    }

    @Test
    fun `pull never overwrites a row that still has a change waiting to be pushed`() = runBlocking {
        userExercises += exerciseJson("mine-1", "Curl perso")
        engine.sync()
        val id = db.exerciseDao().getAll().single().id

        exercises.saveExercise(exercises.getExercise(id)!!.copy(name = "Curl modifié"))
        failWith = 503
        assertEquals(SyncOutcome.RETRY, engine.sync())
        assertEquals("Curl modifié", exercises.getExercise(id)!!.name)
        assertEquals(1, db.syncOpDao().pending().size)

        failWith = null
        assertEquals(SyncOutcome.SUCCESS, engine.sync())
        assertEquals(0, db.syncOpDao().pending().size)
        assertTrue(requests.contains("PUT /exercises/mine-1"))
    }

    @Test
    fun `unauthorized stops the synchronisation and keeps the queue`() = runBlocking {
        exercises.saveExercise(Exercise(name = "Squat"))
        failWith = 401
        assertEquals(SyncOutcome.UNAUTHORIZED, engine.sync())
        assertEquals(SyncError.UNAUTHORIZED, engine.state.value.error)
        assertEquals(1, db.syncOpDao().pending().size)
    }

    @Test
    fun `a change rejected by the server is parked instead of blocking the queue`() = runBlocking {
        userExercises += exerciseJson("mine-1", "Curl perso")
        engine.sync()
        val id = db.exerciseDao().getAll().single().id
        exercises.saveExercise(exercises.getExercise(id)!!.copy(name = "Curl modifié"))
        failWith = 422
        engine.sync()
        assertEquals(0, db.syncOpDao().pending().size)
        assertEquals(1, db.syncOpDao().forEntity("EXERCISE", id).count { it.failed })
    }

    @Test
    fun `deleting a synced exercise queues a server deletion`() = runBlocking {
        userExercises += exerciseJson("mine-1", "Curl perso")
        engine.sync()
        val id = db.exerciseDao().getAll().single().id
        exercises.deleteExercise(id)
        val op = db.syncOpDao().pending().single()
        assertEquals("DELETE", op.op)
        assertEquals("mine-1", op.remoteRef)
        assertNull(db.exerciseDao().get(id))
    }

    private companion object {
        const val PREFERENCES = """{"onboardingCompleted":false,"userName":"","themeMode":"DARK","dynamicColor":true,"weightUnit":"KG","soundEnabled":true,"vibrationEnabled":true,"keepScreenOn":true,"defaultRestSeconds":90,"weeklyGoal":3,"autoDetectLocation":true}"""
    }
}
