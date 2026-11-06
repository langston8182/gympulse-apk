package com.gympulse.app.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.gympulse.app.core.coroutines.ApplicationScope
import com.gympulse.app.domain.model.UserPreferences
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.repository.PreferencesRepository
import com.gympulse.app.domain.workout.WorkoutFeedback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Rest-end alert: vibration and sound according to the preferences, notification when in background. */
@Singleton
class AndroidWorkoutFeedback @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notifications: WorkoutNotifications,
    preferencesRepository: PreferencesRepository,
    @ApplicationScope scope: CoroutineScope,
) : WorkoutFeedback {

    private val preferences: StateFlow<UserPreferences> =
        preferencesRepository.preferences.stateIn(scope, SharingStarted.Eagerly, UserPreferences())

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onRestFinished(execution: WorkoutExecution) {
        val prefs = preferences.value
        if (prefs.vibrationEnabled) vibrate()
        if (prefs.soundEnabled) beep()
        val inForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (!inForeground) notifications.showRestFinished(execution, prefs.weightUnit)
    }

    private fun vibrate() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator?.hasVibrator() != true) return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 220, 120, 220, 120, 360), -1))
    }

    private fun beep() {
        mainHandler.post {
            val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull() ?: return@post
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 450)
            mainHandler.postDelayed({ tone.release() }, 700)
        }
    }
}
