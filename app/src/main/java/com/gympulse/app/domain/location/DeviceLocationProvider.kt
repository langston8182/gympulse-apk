package com.gympulse.app.domain.location

import com.gympulse.app.domain.model.GeoPoint

/** Abstraction over the platform location services, so detection logic can be tested. */
interface DeviceLocationProvider {
    fun hasPermission(): Boolean
    suspend fun currentPosition(): GeoPoint?
}
