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
                        ExecutedSet(order = 0, actualRepetitions = 10, actualWeightKg = 42.5, status = SetStatus.COMPLETED),
                        ExecutedSet(order = 1, status = SetStatus.PENDING),
                    ),
                ),
            ),
        )
        val lines = WorkoutCsvExporter.toCsv(listOf(workout), ZoneOffset.UTC).trim().split("\r\n")
        assertEquals(2, lines.size)
        assertEquals("1970-01-01 00:00,\"Pecs, dos\",,10,'=Developpe,1,10,42.5,,,non", lines[1])
    }
}
