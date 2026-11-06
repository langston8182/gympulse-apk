package com.gympulse.app.domain.location

import com.gympulse.app.domain.model.GeoPoint

/** Turns a postal address into coordinates. Returns null when nothing matches or the service is unavailable. */
interface AddressGeocoder {
    suspend fun find(address: String): GeoPoint?
}
