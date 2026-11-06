package com.gympulse.app.domain

import com.gympulse.app.domain.model.ExerciseStatus
import com.gympulse.app.domain.model.SetStatus
import com.gympulse.app.domain.model.WorkoutStatus
import com.gympulse.app.domain.workout.WorkoutEngine
import com.gympulse.app.testing.TestData
import com.gympulse.app.testing.TestData.session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutEngineTest {

    private val t0 = 1_000_000L
    private fun start() = WorkoutEngine.create(session(), t0, keyFactory = TestData.keys()).copy(id = 1)

    @Test
    fun `create snapshots planned sets and rest per set`() {
        val execution = start()
        assertEquals(3, execution.exercises.size)
        val bench = execution.exercises[0]
        assertEquals("Développé couché haltères", bench.name)
        assertEquals(listOf(12, 8, 5, 5), bench.sets.map { it.targetRepetitions })
        assertEquals(listOf(120, 120, 120, 150), bench.sets.map { it.restSeconds })
        assertEquals(ExerciseStatus.IN_PROGRESS, bench.status)
        assertEquals(9, execution.totalSets)
        assertEquals(WorkoutStatus.IN_PROGRESS, execution.status)
    }

    @Test
    fun `completing a set records values, moves to next set and starts rest`() {
        val execution = WorkoutEngine.completeCurrentSet(start(), repetitions = 12, weightKg = 20.0, durationSeconds = null, now = t0 + 60_000)
        val first = execution.exercises[0].sets[0]
        assertEquals(SetStatus.COMPLETED, first.status)
        assertEquals(12, first.actualRepetitions)
        assertEquals(20.0, first.actualWeightKg!!, 0.0)
        assertEquals(0, execution.currentExerciseIndex)
        assertEquals(1, execution.currentSetIndex)
        val rest = assertNotNullAndGet(execution.rest)
        assertEquals(120, rest.totalSeconds)
        assertEquals(t0 + 60_000 + 120_000, rest.endsAt)
    }

    @Test
    fun `last set of an exercise moves to the next exercise with its configured rest`() {
        var execution = start()
        repeat(4) { i -> execution = WorkoutEngine.completeCurrentSet(execution, 5, 30.0, null, t0 + i * 10_000L) }
        assertEquals(1, execution.currentExerciseIndex)
        assertEquals(0, execution.currentSetIndex)
        assertEquals(ExerciseStatus.COMPLETED, execution.exercises[0].status)
        assertEquals(ExerciseStatus.IN_PROGRESS, execution.exercises[1].status)
        assertEquals(150, execution.rest!!.totalSeconds)
    }

    @Test
    fun `last set of the workout does not start a rest and marks everything done`() {
        var execution = start()
        repeat(7) { execution = WorkoutEngine.completeCurrentSet(execution, 5, 30.0, null, t0) }
        repeat(2) { execution = WorkoutEngine.completeCurrentSet(execution, null, null, 45, t0) }
        assertTrue(execution.isAllDone)
        assertNull(execution.rest)
        assertEquals(1f, execution.progress, 0f)
    }

    @Test
    fun `skipping the rest records the real rest duration`() {
        var execution = WorkoutEngine.completeCurrentSet(start(), 12, 20.0, null, t0)
        execution = WorkoutEngine.skipRest(execution, t0 + 47_000)
        assertNull(execution.rest)
        assertEquals(47, execution.exercises[0].sets[0].actualRestSeconds)
    }

    @Test
    fun `natural end of rest records the planned duration even if acknowledged later`() {
        var execution = WorkoutEngine.completeCurrentSet(start(), 12, 20.0, null, t0)
        execution = WorkoutEngine.endRest(execution, t0 + 300_000)
        assertEquals(120, execution.exercises[0].sets[0].actualRestSeconds)
    }

    @Test
    fun `completing the next set directly ends the running rest`() {
        var execution = WorkoutEngine.completeCurrentSet(start(), 12, 20.0, null, t0)
        execution = WorkoutEngine.completeCurrentSet(execution, 8, 24.0, null, t0 + 30_000)
        assertEquals(30, execution.exercises[0].sets[0].actualRestSeconds)
        assertEquals(120, execution.rest!!.totalSeconds)
        assertEquals(t0 + 30_000, execution.rest!!.startedAt)
    }

    @Test
    fun `extending rest adds time, and restarts a finished rest`() {
        var execution = WorkoutEngine.completeCurrentSet(start(), 12, 20.0, null, t0)
        execution = WorkoutEngine.extendRest(execution, 30, t0 + 10_000)
        assertEquals(t0 + 150_000, execution.rest!!.endsAt)
        execution = WorkoutEngine.extendRest(execution, 15, t0 + 200_000)
        assertEquals(t0 + 215_000, execution.rest!!.endsAt)
        assertFalse(execution.rest!!.isFinished(t0 + 210_000))
    }

    @Test
    fun `pause freezes the chronometer and the rest, resume restores them`() {
        var execution = WorkoutEngine.completeCurrentSet(start(), 12, 20.0, null, t0 + 60_000)
        execution = WorkoutEngine.pause(execution, t0 + 90_000)
        assertEquals(WorkoutStatus.PAUSED, execution.status)
        assertEquals(90_000L, execution.elapsedMs(t0 + 500_000))
        assertEquals(90_000L, execution.rest!!.remainingMs(t0 + 500_000))

        execution = WorkoutEngine.resume(execution, t0 + 600_000)
        assertEquals(WorkoutStatus.IN_PROGRESS, execution.status)
        assertEquals(510_000L, execution.totalPausedMs)
        assertEquals(100_000L, execution.elapsedMs(t0 + 610_000))
        assertEquals(t0 + 690_000, execution.rest!!.endsAt)

        execution = WorkoutEngine.skipRest(execution, t0 + 610_000)
        assertEquals(40, execution.exercises[0].sets[0].actualRestSeconds)
    }

    @Test
    fun `completing a set is ignored while paused`() {
        val paused = WorkoutEngine.pause(start(), t0)
        assertEquals(paused, WorkoutEngine.completeCurrentSet(paused, 10, 20.0, null, t0))
    }

    @Test
    fun `going back to a previous set allows correcting it without starting a rest`() {
        var execution = WorkoutEngine.completeCurrentSet(start(), 12, 20.0, null, t0)
        execution = WorkoutEngine.completeCurrentSet(execution, 8, 24.0, null, t0 + 1_000)
        execution = WorkoutEngine.skipRest(execution, t0 + 2_000)
        execution = WorkoutEngine.goTo(execution, 0, 0)
        assertEquals(0, execution.currentSetIndex)

        execution = WorkoutEngine.completeCurrentSet(execution, 11, 22.0, null, t0 + 3_000)
        assertEquals(11, execution.exercises[0].sets[0].actualRepetitions)
        assertEquals(22.0, execution.exercises[0].sets[0].actualWeightKg!!, 0.0)
        assertEquals(2, execution.currentSetIndex)
        assertNull(execution.rest)
    }

    @Test
    fun `updateSet corrects a recorded set in place`() {
        var execution = WorkoutEngine.completeCurrentSet(start(), 12, 20.0, null, t0)
        execution = WorkoutEngine.updateSet(execution, 0, 0, 10, 22.5, null)
        assertEquals(10, execution.exercises[0].sets[0].actualRepetitions)
        assertEquals(22.5, execution.exercises[0].sets[0].actualWeightKg!!, 0.0)
        assertEquals(1, execution.currentSetIndex)
    }

    @Test
    fun `skipping an exercise moves to the next one and finish marks pending sets skipped`() {
        var execution = WorkoutEngine.skipCurrentExercise(start())
        assertEquals(ExerciseStatus.SKIPPED, execution.exercises[0].status)
        assertEquals(1, execution.currentExerciseIndex)

        execution = WorkoutEngine.completeCurrentSet(execution, 5, 100.0, null, t0)
        execution = WorkoutEngine.finish(execution, t0 + 3_600_000, notes = "RAS")
        assertEquals(WorkoutStatus.COMPLETED, execution.status)
        assertEquals("RAS", execution.notes)
        assertEquals(ExerciseStatus.SKIPPED, execution.exercises[0].status)
        assertEquals(ExerciseStatus.COMPLETED, execution.exercises[1].status)
        assertTrue(execution.exercises.flatMap { it.sets }.none { it.isPending })
        assertNull(execution.rest)
    }

    @Test
    fun `adding a set copies the last targets`() {
        val execution = WorkoutEngine.addSet(start(), 1)
        val added = execution.exercises[1].sets.last()
        assertEquals(4, execution.exercises[1].sets.size)
        assertEquals(5, added.targetRepetitions)
        assertEquals(100.0, added.targetWeightKg!!, 0.0)
        assertTrue(added.isExtra)
        assertEquals(0, execution.currentExerciseIndex)
    }

    @Test
    fun `adding a set after everything is done makes it the current set`() {
        var execution = start()
        repeat(7) { execution = WorkoutEngine.completeCurrentSet(execution, 5, 30.0, null, t0) }
        repeat(2) { execution = WorkoutEngine.completeCurrentSet(execution, null, null, 45, t0) }
        execution = WorkoutEngine.addSet(execution, 1)
        assertFalse(execution.isAllDone)
        assertEquals(1, execution.currentExerciseIndex)
        assertEquals(3, execution.currentSetIndex)
    }

    @Test
    fun `remaining exercises can be reordered without touching the current one`() {
        val execution = start()
        val keys = execution.exercises.map { it.key }
        val reordered = WorkoutEngine.reorderRemaining(execution, listOf(keys[2], keys[1]))
        assertEquals(listOf(keys[0], keys[2], keys[1]), reordered.exercises.map { it.key })
        assertEquals(0, reordered.currentExerciseIndex)
        assertEquals(execution, WorkoutEngine.reorderRemaining(execution, listOf(keys[0], keys[1])))
    }

    @Test
    fun `timed set uses the stopwatch and pauses with the workout`() {
        var execution = WorkoutEngine.goToExercise(start(), 2)
        execution = WorkoutEngine.startSetTimer(execution, t0)
        execution = WorkoutEngine.pause(execution, t0 + 20_000)
        assertEquals(20_000L, execution.setTimer!!.elapsedMs(t0 + 90_000))
        execution = WorkoutEngine.resume(execution, t0 + 90_000)
        execution = WorkoutEngine.startSetTimer(execution, t0 + 90_000)
        execution = WorkoutEngine.completeCurrentSet(execution, null, null, null, t0 + 117_000)
        assertEquals(47, execution.exercises[2].sets[0].actualDurationSeconds)
        assertEquals(60, execution.rest!!.totalSeconds)
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
