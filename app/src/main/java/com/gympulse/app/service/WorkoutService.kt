package com.gympulse.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gympulse.app.domain.model.UserPreferences
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.model.WorkoutStatus
import com.gympulse.app.domain.repository.PreferencesRepository
import com.gympulse.app.domain.workout.ActiveWorkoutManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Foreground service kept alive while a workout is active. It shows the live chronometer in the
 * notification and holds a partial wake lock during rests only, so that the end-of-rest alert fires
 * on time even when the phone is locked.
 */
@AndroidEntryPoint
class WorkoutService : LifecycleService() {

    @Inject lateinit var manager: ActiveWorkoutManager
    @Inject lateinit var notifications: WorkoutNotifications
    @Inject lateinit var preferences: PreferencesRepository

    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!started) {
            started = true
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            try {
                ServiceCompat.startForeground(
                    this,
                    WorkoutNotifications.ONGOING_ID,
                    notifications.buildOngoing(manager.active.value, manager.now(), UserPreferences().weightUnit),
                    type,
                )
            } catch (_: Exception) {
                stopSelf()
                return START_NOT_STICKY
            }
            observeWorkout()
        }
        return START_NOT_STICKY
    }

    private fun observeWorkout() {
        lifecycleScope.launch {
            manager.events.collect {
                val execution = manager.active.value ?: return@collect
                val unit = preferences.preferences.first().weightUnit
                notifications.updateOngoing(notifications.buildOngoing(execution, manager.now(), unit))
                updateWakeLock(execution)
            }
        }
        lifecycleScope.launch {
            manager.restored.first { it }
            combine(manager.active, preferences.preferences) { execution, prefs -> execution to prefs }
                .collect { (execution, prefs) ->
                    if (execution == null || !execution.isActive) {
                        releaseWakeLock()
                        notifications.cancelRestFinished()
                        ServiceCompat.stopForeground(this@WorkoutService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@collect
                    }
                    notifications.updateOngoing(notifications.buildOngoing(execution, manager.now(), prefs.weightUnit))
                    updateWakeLock(execution)
                    if (execution.rest == null) notifications.cancelRestFinished()
                }
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun updateWakeLock(execution: WorkoutExecution) {
        val rest = execution.rest
        val remaining = rest?.remainingMs(manager.now()) ?: 0
        if (execution.status == WorkoutStatus.IN_PROGRESS && rest != null && !rest.isPaused && remaining > 0) {
            val lock = wakeLock ?: (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GymPulse:rest-timer")
                .also { it.setReferenceCounted(false); wakeLock = it }
            lock.acquire(remaining + WAKE_LOCK_MARGIN_MS)
        } else {
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private companion object {
        const val WAKE_LOCK_MARGIN_MS = 15_000L
    }
}

/** Starts the workout service from a foreground context (an Activity or a visible screen). */
@Singleton
class WorkoutServiceController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun ensureRunning() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java))
        } catch (_: Exception) {
            // Starting a foreground service from the background is forbidden on recent Android
            // versions; the workout keeps working in-app and the service starts on next resume.
        }
    }
}
