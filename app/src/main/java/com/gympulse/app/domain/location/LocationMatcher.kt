package com.gympulse.app.domain.location

import com.gympulse.app.domain.model.GeoPoint
import com.gympulse.app.domain.model.TrainingLocation
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LocationMatch(val location: TrainingLocation, val distanceMeters: Double)

object LocationMatcher {

    private const val EARTH_RADIUS_METERS = 6_371_008.8

    /** Maximum part of the reported GPS accuracy that can extend a location radius. */
    private const val MAX_ACCURACY_TOLERANCE_METERS = 100.0

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Closest saved location whose detection radius contains [position], or null. */
    fun match(position: GeoPoint, locations: List<TrainingLocation>): LocationMatch? {
        val tolerance = (position.accuracyMeters?.toDouble() ?: 0.0).coerceIn(0.0, MAX_ACCURACY_TOLERANCE_METERS)
        return locations
            .filter { it.hasCoordinates }
            .map { LocationMatch(it, distanceMeters(position, GeoPoint(it.latitude!!, it.longitude!!))) }
            .filter { it.distanceMeters <= it.location.radiusMeters + tolerance }
            .minByOrNull { it.distanceMeters }
    }
}
