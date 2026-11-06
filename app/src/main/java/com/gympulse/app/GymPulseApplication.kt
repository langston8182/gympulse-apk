package com.gympulse.app

import android.app.Application
import com.gympulse.app.service.WorkoutNotifications
import dagger.hilt.android.HiltAndroidApp
import com.gympulse.app.data.sync.SyncScheduler
import javax.inject.Inject

@HiltAndroidApp
class GymPulseApplication : Application() {

    @Inject lateinit var notifications: WorkoutNotifications
    @Inject lateinit var syncScheduler: SyncScheduler

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
        syncScheduler.requestSync(full = true)
    }
}
