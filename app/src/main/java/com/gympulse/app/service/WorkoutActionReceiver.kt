package com.gympulse.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gympulse.app.domain.workout.ActiveWorkoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Handles the actions of the workout notification. */
@AndroidEntryPoint
class WorkoutActionReceiver : BroadcastReceiver() {

    @Inject lateinit var manager: ActiveWorkoutManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SKIP_REST -> manager.skipRest()
            ACTION_ADD_30 -> manager.extendRest(30)
            ACTION_PAUSE -> manager.pause()
            ACTION_RESUME -> manager.resume()
        }
    }

    companion object {
        const val ACTION_SKIP_REST = "com.gympulse.app.action.SKIP_REST"
        const val ACTION_ADD_30 = "com.gympulse.app.action.ADD_30"
        const val ACTION_PAUSE = "com.gympulse.app.action.PAUSE"
        const val ACTION_RESUME = "com.gympulse.app.action.RESUME"
    }
}
