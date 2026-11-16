package com.gympulse.app.domain.export

import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.WorkoutExecution
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Flat CSV export, one line per completed set. Weights are always in kg. */
object WorkoutCsvExporter {
    private val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val timeOnly = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val fileDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val header = listOf(
        "date", "seance", "lieu", "duree_min", "ordre", "heure", "exercice", "serie", "repetitions",
        "poids_kg", "duree_serie_s", "repos_s", "serie_supplementaire",
    )

    fun toCsv(workouts: List<WorkoutExecution>, zone: ZoneId = ZoneId.systemDefault()): String {
        val out = StringBuilder("\uFEFF")
        out.append(header.joinToString(",")).append("\r\n")
        workouts.sortedBy { it.startedAt }.forEach { workout ->
            val date = dateTime.format(Instant.ofEpochMilli(workout.startedAt).atZone(zone))
            val minutes = workout.durationMs / 60_000
            // Execution order: by completion time; sets without a timestamp keep their planned position at the end.
            val executed = workout.exercises.flatMapIndexed { exerciseIndex, exercise ->
                exercise.sets.filter { it.isCompleted }.map { Triple(exerciseIndex, exercise, it) }
            }.sortedWith(compareBy({ it.third.completedAt ?: Long.MAX_VALUE }, { it.first }, { it.third.order }))
            executed.forEachIndexed { position, (_, exercise, set) ->
                run {
                    val timed = exercise.type == ExerciseType.DURATION
                    val row = listOf(
                        date,
                        workout.sessionName,
                        workout.locationName.orEmpty(),
                        minutes.toString(),
                        (position + 1).toString(),
                        set.completedAt?.let { timeOnly.format(Instant.ofEpochMilli(it).atZone(zone)) }.orEmpty(),
                        exercise.name,
                        (set.order + 1).toString(),
                        if (timed) "" else set.actualRepetitions?.toString().orEmpty(),
                        set.actualWeightKg?.let { number(it) }.orEmpty(),
                        if (timed) set.actualDurationSeconds?.toString().orEmpty() else "",
                        set.actualRestSeconds?.toString().orEmpty(),
                        if (set.isExtra) "oui" else "non",
                    )
                    out.append(row.joinToString(",") { escape(it) }).append("\r\n")
                }
            }
        }
        return out.toString()
    }

    fun fileName(workouts: List<WorkoutExecution>, zone: ZoneId = ZoneId.systemDefault()): String {
        val single = workouts.singleOrNull()
        val date = fileDate.format(Instant.ofEpochMilli(single?.startedAt ?: System.currentTimeMillis()).atZone(zone))
        return if (single != null) "gympulse-seance-$date.csv" else "gympulse-historique-$date.csv"
    }

    private fun number(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, value).trimEnd('0')

    // Cells starting with a formula character are prefixed to avoid spreadsheet formula injection.
    private fun escape(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
