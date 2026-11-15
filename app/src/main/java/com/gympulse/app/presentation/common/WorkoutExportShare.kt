package com.gympulse.app.presentation.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.gympulse.app.domain.export.WorkoutCsvExporter
import com.gympulse.app.domain.model.WorkoutExecution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Writes the workouts to a CSV in the cache and opens the Android share sheet (email, Drive, ...). */
suspend fun shareWorkoutsCsv(context: Context, workouts: List<WorkoutExecution>) {
    if (workouts.isEmpty()) return
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, WorkoutCsvExporter.fileName(workouts))
        file.writeText(WorkoutCsvExporter.toCsv(workouts))
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val subject = if (workouts.size == 1) "GymPulse - ${workouts[0].sessionName}" else "GymPulse - historique de ${workouts.size} séances"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Partager l'export").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
