package com.gympulse.app.presentation.sessions

import com.gympulse.app.domain.model.TrainingLocation
import com.gympulse.app.domain.model.WorkoutSession

/** Sessions of one training place ([locationId] = null for the sessions linked to no place). */
data class SessionGroup(
    val locationId: Long?,
    val title: String,
    val isCurrent: Boolean,
    val sessions: List<WorkoutSession>,
) {
    val key: String get() = locationId?.toString() ?: "none"
}

/**
 * Groups [sessions] (already in the user's order) by training place: the current place first, then the
 * others by name, then the sessions linked to no place. A session linked to several places appears in
 * each of them. Empty groups are left out.
 */
fun groupSessionsByLocation(
    sessions: List<WorkoutSession>,
    locations: List<TrainingLocation>,
    currentLocationId: Long?,
): List<SessionGroup> {
    val byPlace = locations
        .sortedWith(compareByDescending<TrainingLocation> { it.id == currentLocationId }.thenBy { it.name.lowercase() })
        .map { place ->
            SessionGroup(place.id, place.name, place.id == currentLocationId, sessions.filter { place.id in it.compatibleLocationIds })
        }
    val known = locations.map { it.id }.toSet()
    val unlinked = sessions.filter { session -> session.compatibleLocationIds.none { it in known } }
    return (byPlace + SessionGroup(null, "Sans lieu", false, unlinked)).filter { it.sessions.isNotEmpty() }
}

/**
 * New global order after the user reordered one group: the sessions of the group keep the slots they
 * already occupied in [global], refilled in the new order, so the other sessions do not move.
 */
fun mergeGroupOrder(global: List<Long>, groupOrder: List<Long>): List<Long> {
    val members = groupOrder.toSet()
    val slots = global.indices.filter { global[it] in members }
    val result = global.toMutableList()
    slots.zip(groupOrder).forEach { (slot, id) -> result[slot] = id }
    return result
}
