package com.gympulse.app.domain.model

enum class LocationType { HOME, GYM, WORK, OTHER }

data class GeoPoint(val latitude: Double, val longitude: Double, val accuracyMeters: Float? = null)

data class TrainingLocation(
    val id: Long = 0,
    val name: String,
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int = 150,
    val type: LocationType = LocationType.OTHER,
    val availableEquipment: Set<Equipment> = emptySet(),
    val isDemo: Boolean = false,
    val createdAt: Long = 0,
) {
    val hasCoordinates: Boolean get() = latitude != null && longitude != null

    fun hasEquipment(equipment: Equipment): Boolean =
        equipment.isUniversal || equipment in availableEquipment
}

enum class LocationSource { AUTO, MANUAL, NONE }

enum class DetectionStatus { IDLE, DETECTING, MATCHED, NO_MATCH, PERMISSION_DENIED, UNAVAILABLE }

/** Where the user is currently considered to train, and how this was determined. */
data class LocationContext(
    val location: TrainingLocation? = null,
    val source: LocationSource = LocationSource.NONE,
    val status: DetectionStatus = DetectionStatus.IDLE,
    val distanceMeters: Double? = null,
)
