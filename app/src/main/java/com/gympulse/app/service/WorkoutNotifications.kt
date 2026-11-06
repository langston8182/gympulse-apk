package com.gympulse.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gympulse.app.MainActivity
import com.gympulse.app.R
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.domain.model.ExecutedExercise
import com.gympulse.app.domain.model.ExecutedSet
import com.gympulse.app.domain.model.WeightUnit
import com.gympulse.app.domain.model.WorkoutExecution
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.notification_channel_workout), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.notification_channel_workout_description)
                setShowBadge(false)
            }
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.notification_channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notification_channel_alerts_description)
                // Sound and vibration are played by the app itself, according to the user preferences.
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun buildOngoing(execution: WorkoutExecution?, now: Long, unit: WeightUnit): android.app.Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_gympulse)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (execution == null) {
            return builder.setContentTitle("GymPulse").setContentText("Préparation de la séance…").build()
        }
        val wallOffset = System.currentTimeMillis() - now
        val exercise = execution.currentExercise
        val rest = execution.rest
        builder.setContentTitle(execution.sessionName)
            .setProgress(execution.totalSets.coerceAtLeast(1), execution.completedSetCount, false)
        when {
            execution.isPaused -> {
                builder.setContentText("Séance en pause · ${Formatters.clock(execution.elapsedMs(now))}")
                    .addAction(0, "Reprendre", actionIntent(WorkoutActionReceiver.ACTION_RESUME))
            }
            rest != null && !rest.isFinished(now) -> {
                builder.setContentText("Repos · ensuite ${nextEffortLabel(execution, unit)}")
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setShowWhen(true)
                    .setWhen(rest.endsAt + wallOffset)
                    .addAction(0, "Passer le repos", actionIntent(WorkoutActionReceiver.ACTION_SKIP_REST))
                    .addAction(0, "+30 s", actionIntent(WorkoutActionReceiver.ACTION_ADD_30))
            }
            else -> {
                builder.setContentText(if (exercise != null) "${exercise.name} · ${setLabel(execution)}" else "Séance en cours")
                    .setUsesChronometer(true)
                    .setShowWhen(true)
                    .setWhen(System.currentTimeMillis() - execution.elapsedMs(now))
                    .addAction(0, "Pause", actionIntent(WorkoutActionReceiver.ACTION_PAUSE))
            }
        }
        return builder.build()
    }

    fun showRestFinished(execution: WorkoutExecution, unit: WeightUnit) {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_gympulse)
            .setContentTitle("Série suivante prête")
            .setContentText(nextEffortLabel(execution, unit))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(90_000)
            .setContentIntent(openAppIntent())
            .build()
        notifySafely(REST_FINISHED_ID, notification)
    }

    fun cancelRestFinished() = manager.cancel(REST_FINISHED_ID)

    fun updateOngoing(notification: android.app.Notification) = notifySafely(ONGOING_ID, notification)

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        if (!canPost()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    private fun setLabel(execution: WorkoutExecution): String {
        val exercise = execution.currentExercise ?: return ""
        return "Série ${execution.currentSetIndex + 1}/${exercise.sets.size}"
    }

    private fun nextEffortLabel(execution: WorkoutExecution, unit: WeightUnit): String {
        val exercise = execution.currentExercise ?: return "fin de séance"
        val set = execution.currentSet ?: return exercise.name
        return "${exercise.name} · ${setLabel(execution)} · ${targetLabel(exercise, set, unit)}"
    }

    private fun targetLabel(exercise: ExecutedExercise, set: ExecutedSet, unit: WeightUnit): String =
        if (exercise.isTimed) {
            Formatters.seconds(set.targetDurationSeconds ?: 0)
        } else {
            val reps = set.targetRepetitions?.let { "$it reps" } ?: "reps libres"
            val weight = set.targetWeightKg?.let { " · ${Formatters.weight(it, unit)}" } ?: ""
            reps + weight
        }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_WORKOUT, true)
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(context, WorkoutActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL_ONGOING = "workout_ongoing"
        const val CHANNEL_ALERTS = "workout_alerts"
        const val ONGOING_ID = 1001
        const val REST_FINISHED_ID = 1002
    }
}
