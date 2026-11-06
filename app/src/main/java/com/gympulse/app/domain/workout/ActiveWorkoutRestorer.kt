package com.gympulse.app.domain.workout

/** Reloads the active workout from the database, e.g. after the synchronisation brought one in. */
fun interface ActiveWorkoutRestorer {
    suspend fun restore()
}
