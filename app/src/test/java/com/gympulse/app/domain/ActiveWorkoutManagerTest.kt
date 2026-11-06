package com.gympulse.app.domain

import com.gympulse.app.domain.model.SetStatus
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.model.WorkoutSession
import com.gympulse.app.domain.model.WorkoutStatus
import com.gympulse.app.domain.repository.SessionRepository
import com.gympulse.app.domain.repository.WorkoutRepository
import com.gympulse.app.domain.workout.ActiveWorkoutManager
import com.gympulse.app.domain.workout.StartWorkoutResult
import com.gympulse.app.domain.workout.WorkoutEngine
import com.gympulse.app.domain.workout.WorkoutFeedback
import com.gympulse.app.testing.TestData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeWorkoutRepository : WorkoutRepository {
    val executions = MutableStateFlow<Map<Long, WorkoutExecution>>(emptyMap())
    val saves = mutableListOf<WorkoutExecution>()
    private var nextId = 1L

    override fun observeHistory(): Flow<List<WorkoutExecution>> =
        executions.map { all -> all.values.filter { it.status == WorkoutStatus.COMPLETED }.sortedByDescending { it.startedAt } }

    override fun observeExecution(id: Long): Flow<WorkoutExecution?> = executions.map { it[id] }
    override fun observeActiveExecution(): Flow<WorkoutExecution?> = executions.map { all -> all.values.firstOrNull { it.isActive } }
    override suspend fun getExecution(id: Long): WorkoutExecution? = executions.value[id]
    override suspend fun getActiveExecution(): WorkoutExecution? = executions.value.values.firstOrNull { it.isActive }

    override suspend fun createExecution(execution: WorkoutExecution): Long {
        val id = nextId++
        executions.value = executions.value + (id to execution.copy(id = id))
        return id
    }

    override suspend fun saveExecution(execution: WorkoutExecution) {
        saves += execution
        executions.value = executions.value + (execution.id to execution)
    }

    override suspend fun deleteExecution(id: Long) {
        executions.value = executions.value - id
    }
}

private class FakeSessionRepository(private val sessions: List<WorkoutSession>) : SessionRepository {
    override fun observeSessions() = MutableStateFlow(sessions)
    override fun observeSession(id: Long) = MutableStateFlow(sessions.firstOrNull { it.id == id })
    override suspend fun getSession(id: Long) = sessions.firstOrNull { it.id == id }
    override suspend fun saveSession(session: WorkoutSession) = session.id
    override suspend fun deleteSession(id: Long) = Unit
    override suspend fun duplicateSession(id: Long, newName: String) = id
    override suspend fun setFavorite(id: Long, favorite: Boolean) = Unit
    override suspend fun reorderSessions(orderedIds: List<Long>) = Unit
}

private class RecordingFeedback : WorkoutFeedback {
    var restFinished = 0
    override fun onRestFinished(execution: WorkoutExecution) {
        restFinished++
    }
}

class ActiveWorkoutManagerTest {

    private val base = 1_700_000_000_000L

    private fun TestScope.manager(
        repository: FakeWorkoutRepository = FakeWorkoutRepository(),
        feedback: RecordingFeedback = RecordingFeedback(),
    ) = ActiveWorkoutManager(
        workoutRepository = repository,
        sessionRepository = FakeSessionRepository(listOf(TestData.session(id = 10))),
        time = { base + testScheduler.currentTime },
        feedback = feedback,
        scope = backgroundScope,
    )

    @Test
    fun `every recorded set is persisted immediately`() = runTest {
        val repository = FakeWorkoutRepository()
        val manager = manager(repository)
        runCurrent()
        val started = manager.start(10, null)
        assertTrue(started is StartWorkoutResult.Started)

        manager.completeSet(12, 20.0, null)
        runCurrent()

        val saved = repository.saves.last()
        assertEquals(SetStatus.COMPLETED, saved.exercises[0].sets[0].status)
        assertEquals(12, saved.exercises[0].sets[0].actualRepetitions)
        assertNotNull(saved.rest)
        assertEquals(saved, manager.active.value)
    }

    @Test
    fun `rest end triggers the feedback exactly when the rest expires`() = runTest {
        val feedback = RecordingFeedback()
        val manager = manager(feedback = feedback)
        runCurrent()
        manager.start(10, null)
        manager.completeSet(12, 20.0, null)
        runCurrent()

        advanceTimeBy(119_000)
        runCurrent()
        assertEquals(0, feedback.restFinished)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, feedback.restFinished)
    }

    @Test
    fun `skipping the rest cancels the pending alert`() = runTest {
        val feedback = RecordingFeedback()
        val manager = manager(feedback = feedback)
        runCurrent()
        manager.start(10, null)
        manager.completeSet(12, 20.0, null)
        runCurrent()
        manager.skipRest()
        runCurrent()
        advanceTimeBy(300_000)
        runCurrent()
        assertEquals(0, feedback.restFinished)
        assertNull(manager.active.value!!.rest)
    }

    @Test
    fun `pausing during a rest postpones the alert`() = runTest {
        val feedback = RecordingFeedback()
        val manager = manager(feedback = feedback)
        runCurrent()
        manager.start(10, null)
        manager.completeSet(12, 20.0, null)
        runCurrent()
        advanceTimeBy(60_000)
        manager.pause()
        runCurrent()
        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(0, feedback.restFinished)
        manager.resume()
        runCurrent()
        advanceTimeBy(61_000)
        runCurrent()
        assertEquals(1, feedback.restFinished)
    }

    @Test
    fun `an interrupted workout is restored after a restart`() = runTest {
        val repository = FakeWorkoutRepository()
        val interrupted = WorkoutEngine.completeCurrentSet(
            WorkoutEngine.create(TestData.session(), base, keyFactory = TestData.keys()),
            12, 20.0, null, base + 1_000,
        )
        val id = repository.createExecution(interrupted)

        val manager = manager(repository)
        runCurrent()

        assertTrue(manager.restored.value)
        val restored = manager.active.value!!
        assertEquals(id, restored.id)
        assertEquals(1, restored.completedSetCount)
        assertEquals(1, restored.currentSetIndex)
        assertTrue(manager.start(10, null) is StartWorkoutResult.AlreadyActive)
    }

    @Test
    fun `finishing saves a completed workout and clears the active one`() = runTest {
        val repository = FakeWorkoutRepository()
        val manager = manager(repository)
        runCurrent()
        manager.start(10, null)
        manager.completeSet(12, 20.0, null)
        runCurrent()

        val id = manager.finish("Bonne séance")
        assertNull(manager.active.value)
        val stored = repository.getExecution(id!!)!!
        assertEquals(WorkoutStatus.COMPLETED, stored.status)
        assertEquals("Bonne séance", stored.notes)
        assertNotNull(stored.endedAt)
        assertNull(repository.getActiveExecution())
    }

    @Test
    fun `abandoning marks the workout cancelled and keeps it out of history`() = runTest {
        val repository = FakeWorkoutRepository()
        val manager = manager(repository)
        runCurrent()
        manager.start(10, null)
        runCurrent()
        manager.cancel()
        assertNull(manager.active.value)
        assertEquals(WorkoutStatus.CANCELLED, repository.saves.last().status)
        assertTrue(repository.executions.value.values.none { it.status == WorkoutStatus.COMPLETED })
    }

    @Test
    fun `unknown or empty sessions cannot be started`() = runTest {
        val manager = manager()
        runCurrent()
        assertEquals(StartWorkoutResult.SessionNotFound, manager.start(999, null))
    }
}
