package com.gympulse.app.domain

import com.gympulse.app.domain.export.WorkoutCsvExporter
import com.gympulse.app.domain.model.ExecutedExercise
import com.gympulse.app.domain.model.ExecutedSet
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.SetStatus
import com.gympulse.app.domain.model.WorkoutExecution
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class WorkoutCsvExporterTest {
    @Test
    fun exportsCompletedSetsOnlyAndEscapesCells() {
        val workout = WorkoutExecution(
            sessionId = 1,
            sessionName = "Pecs, dos",
            startedAt = 0,
            endedAt = 600_000,
            exercises = listOf(
                ExecutedExercise(
                    key = "a",
                    exerciseId = 1,
                    name = "=Developpe",
                    type = ExerciseType.REPETITIONS,
                    sets = listOf(
                        ExecutedSet(order = 0, actualRepetitions = 10, actualWeightKg = 42.5, status = SetStatus.COMPLETED, completedAt = 5_000),
                        ExecutedSet(order = 1, status = SetStatus.PENDING),
                    ),
                ),
            ),
        )
        val lines = WorkoutCsvExporter.toCsv(listOf(workout), ZoneOffset.UTC).trim().split("\r\n")
        assertEquals(2, lines.size)
        assertEquals("1970-01-01 00:00,\"Pecs, dos\",,10,1,00:00:05,'=Developpe,1,10,42.5,,,non", lines[1])
    }

    @Test
    fun keepsExecutionOrderAcrossExercises() {
        fun ex(key: String, vararg times: Long) = ExecutedExercise(
            key = key, exerciseId = null, name = key, type = ExerciseType.REPETITIONS,
            sets = times.mapIndexed { i, t -> ExecutedSet(order = i, actualRepetitions = 8, status = SetStatus.COMPLETED, completedAt = t) },
        )
        val workout = WorkoutExecution(sessionId = 1, sessionName = "S", startedAt = 0, exercises = listOf(ex("A", 1000, 3000), ex("B", 2000, 4000)))
        val rows = WorkoutCsvExporter.toCsv(listOf(workout), ZoneOffset.UTC).trim().split("\r\n").drop(1)
        assertEquals(listOf("A", "B", "A", "B"), rows.map { it.split(",")[6] })
    }
}
