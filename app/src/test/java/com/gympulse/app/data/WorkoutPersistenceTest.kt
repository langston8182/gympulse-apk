package com.gympulse.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gympulse.app.core.time.TimeProvider
import com.gympulse.app.data.image.ImageStorage
import com.gympulse.app.data.local.WorkoutLocalStore
import com.gympulse.app.data.local.db.GymPulseDatabase
import com.gympulse.app.data.sync.SyncOutbox
import com.gympulse.app.data.repository.LocalExerciseRepository
import com.gympulse.app.data.repository.LocalSessionRepository
import com.gympulse.app.data.repository.LocalWorkoutRepository
import com.gympulse.app.domain.model.Exercise
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.LoadMode
import com.gympulse.app.domain.model.SessionExercise
import com.gympulse.app.domain.model.SetTarget
import com.gympulse.app.domain.model.WorkoutSession
import com.gympulse.app.domain.model.WorkoutStatus
import com.gympulse.app.domain.workout.WorkoutEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkoutPersistenceTest {

    private lateinit var db: GymPulseDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var exercises: LocalExerciseRepository
    private lateinit var sessions: LocalSessionRepository
    private lateinit var workouts: LocalWorkoutRepository
    private val time = TimeProvider { 1_700_000_000_000L }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GymPulseDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val outbox = SyncOutbox(db, time) { _ -> }
        exercises = LocalExerciseRepository(db, time, ImageStorage(context, Dispatchers.Unconfined), outbox, Dispatchers.Unconfined)
        sessions = LocalSessionRepository(db, time, outbox, Dispatchers.Unconfined)
        workouts = LocalWorkoutRepository(db, WorkoutLocalStore(db), outbox, scope, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private suspend fun createSession(): WorkoutSession {
        val benchId = exercises.saveExercise(Exercise(name = "Développé couché haltères", loadMode = LoadMode.PER_IMPLEMENT))
        val plankId = exercises.saveExercise(Exercise(name = "Gainage", type = ExerciseType.DURATION))
        val sessionId = sessions.saveSession(
            WorkoutSession(
                name = "Push",
                exercises = listOf(
                    SessionExercise(
                        exercise = exercises.getExercise(benchId)!!,
                        order = 0,
                        restDurationSeconds = 120,
                        sets = listOf(SetTarget(12, 20.0), SetTarget(8, 24.0, restSeconds = 90)),
                    ),
                    SessionExercise(exercise = exercises.getExercise(plankId)!!, order = 1, restDurationSeconds = 60, sets = listOf(SetTarget(targetDurationSeconds = 45))),
                ),
            )
        )
        return sessions.getSession(sessionId)!!
    }

    @Test
    fun sessionTemplateRoundTrip() = runBlocking {
        val session = createSession()
        assertEquals("Push", session.name)
        assertEquals(listOf("Développé couché haltères", "Gainage"), session.exercises.map { it.exercise.name })
        assertEquals(listOf(SetTarget(12, 20.0), SetTarget(8, 24.0, restSeconds = 90)), session.exercises[0].sets)
        assertEquals(LoadMode.PER_IMPLEMENT, session.exercises[0].exercise.loadMode)
        assertTrue(session.createdAt > 0)
    }

    @Test
    fun inProgressWorkoutIsPersistedAndRestorable() = runBlocking {
        val session = createSession()
        var execution = WorkoutEngine.create(session, time.now())
        val id = workouts.createExecution(execution)
        execution = WorkoutEngine.completeCurrentSet(execution.copy(id = id), 12, 20.0, null, time.now() + 30_000)
        workouts.saveExecution(execution)

        val restored = workouts.getActiveExecution()!!
        assertEquals(execution, restored)
        assertEquals(1, restored.completedSetCount)
        assertEquals(120, restored.rest!!.totalSeconds)
        assertTrue(workouts.observeHistory().first().isEmpty())
    }

    @Test
    fun finishedWorkoutGoesToHistoryAndSurvivesTemplateDeletion() = runBlocking {
        val session = createSession()
        var execution = WorkoutEngine.create(session, time.now())
        execution = execution.copy(id = workouts.createExecution(execution))
        execution = WorkoutEngine.completeCurrentSet(execution, 12, 20.0, null, time.now() + 30_000)
        execution = WorkoutEngine.finish(execution, time.now() + 60_000, "RAS")
        workouts.saveExecution(execution)

        sessions.deleteSession(session.id)
        session.exercises.forEach { exercises.deleteExercise(it.exerciseId) }

        assertNull(workouts.getActiveExecution())
        val history = workouts.observeHistory().first()
        assertEquals(1, history.size)
        val stored = history.single()
        assertEquals(WorkoutStatus.COMPLETED, stored.status)
        assertEquals("Push", stored.sessionName)
        assertEquals("Développé couché haltères", stored.exercises[0].name)
        assertEquals(20.0, stored.exercises[0].sets[0].actualWeightKg!!, 0.0)
        assertEquals(12, stored.exercises[0].sets[0].targetRepetitions)
        assertEquals("RAS", stored.notes)
    }

    @Test
    fun cancelledWorkoutIsNotInHistory() = runBlocking {
        val session = createSession()
        var execution = WorkoutEngine.create(session, time.now())
        execution = execution.copy(id = workouts.createExecution(execution))
        workouts.saveExecution(WorkoutEngine.cancel(execution, time.now()))
        assertNull(workouts.getActiveExecution())
        assertTrue(workouts.observeHistory().first().isEmpty())
    }

    @Test
    fun deletingAnExerciseRemovesItFromSessions() = runBlocking {
        val session = createSession()
        assertEquals(1, exercises.countSessionsUsing(session.exercises[0].exerciseId))
        exercises.deleteExercise(session.exercises[0].exerciseId)
        assertEquals(listOf("Gainage"), sessions.getSession(session.id)!!.exercises.map { it.exercise.name })
    }

    @Test
    fun sessionsKeepTheOrderChosenByTheUser() = runBlocking {
        val a = sessions.saveSession(WorkoutSession(name = "A"))
        val b = sessions.saveSession(WorkoutSession(name = "B"))
        val c = sessions.saveSession(WorkoutSession(name = "C"))
        assertEquals(listOf("A", "B", "C"), sessions.observeSessions().first().map { it.name })

        sessions.reorderSessions(listOf(c, a, b))
        assertEquals(listOf("C", "A", "B"), sessions.observeSessions().first().map { it.name })

        // Editing a session must not move it.
        sessions.saveSession(sessions.getSession(a)!!.copy(name = "A2"))
        assertEquals(listOf("C", "A2", "B"), sessions.observeSessions().first().map { it.name })
    }
}
