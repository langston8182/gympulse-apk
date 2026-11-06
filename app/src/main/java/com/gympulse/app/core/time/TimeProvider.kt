package com.gympulse.app.core.time

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/** Source of "now" in epoch millis. Abstracted so that the workout engine can be unit tested. */
fun interface TimeProvider {
    fun now(): Long
}

/**
 * Wall-clock time anchored once at process start, then advanced with [SystemClock.elapsedRealtime],
 * which is monotonic and keeps counting while the device sleeps. Changing the system clock during
 * a workout therefore cannot corrupt timers; after a process restart the anchor is re-taken, so
 * timestamps persisted in epoch millis stay meaningful.
 */
@Singleton
class MonotonicTimeProvider @Inject constructor() : TimeProvider {
    private val wallAnchor = System.currentTimeMillis()
    private val realtimeAnchor = SystemClock.elapsedRealtime()

    override fun now(): Long = wallAnchor + (SystemClock.elapsedRealtime() - realtimeAnchor)
}
