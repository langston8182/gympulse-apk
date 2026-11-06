package com.gympulse.app.domain.location

import com.gympulse.app.domain.model.Equipment
import com.gympulse.app.domain.model.TrainingLocation
import com.gympulse.app.domain.model.TrainingProgram
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.model.WorkoutSession

enum class Compatibility(val rank: Int) {
    /** Explicitly associated with the location. */
    LINKED(0),

    /** All the required equipment is available at the location. */
    EQUIPMENT_MATCH(1),

    /** No location known: cannot be evaluated. */
    UNKNOWN(2),

    /** Some required equipment is missing at the location. */
    INCOMPATIBLE(3),
}

data class SessionCompatibility(
    val session: WorkoutSession,
    val compatibility: Compatibility,
    val missingEquipment: Set<Equipment>,
) {
    val isUsable: Boolean get() = compatibility != Compatibility.INCOMPATIBLE
}

enum class SuggestionReason { NEXT_IN_PROGRAM, PROGRAM_START, LOCATION_MATCH, EQUIPMENT_MATCH, LEAST_RECENT }

data class SessionSuggestion(
    val session: WorkoutSession,
    val reason: SuggestionReason,
    val program: TrainingProgram? = null,
    val compatibility: Compatibility = Compatibility.UNKNOWN,
    val lastPerformedAt: Long? = null,
)

object SessionRecommender {

    fun compatibility(session: WorkoutSession, location: TrainingLocation?): SessionCompatibility {
        if (location == null) return SessionCompatibility(session, Compatibility.UNKNOWN, emptySet())
        val missing = session.requiredEquipment.filterNot { location.hasEquipment(it) }.toSet()
        val compatibility = when {
            location.id in session.compatibleLocationIds -> Compatibility.LINKED
            missing.isEmpty() -> Compatibility.EQUIPMENT_MATCH
            else -> Compatibility.INCOMPATIBLE
        }
        return SessionCompatibility(session, compatibility, missing)
    }

    /** Sessions ranked for a location: linked first, then feasible ones, then the others. */
    fun rank(sessions: List<WorkoutSession>, location: TrainingLocation?): List<SessionCompatibility> =
        sessions.map { compatibility(it, location) }
            .sortedWith(compareBy<SessionCompatibility> { it.compatibility.rank }.thenBy { it.session.sortOrder }.thenBy { it.session.name })

    fun isProgramCompatible(program: TrainingProgram, location: TrainingLocation?): Boolean {
        if (location == null) return true
        if (location.id in program.compatibleLocationIds) return true
        return program.sessions.isNotEmpty() && program.sessions.all { compatibility(it, location).isUsable }
    }

    /**
     * Other sessions that fit the location (linked to it or allowed by its equipment), to browse
     * next to the main [suggest]ion, in the order chosen by the user. Without a known location
     * there is nothing specific to propose, so the list is empty.
     */
    fun alternatives(
        sessions: List<WorkoutSession>,
        history: List<WorkoutExecution>,
        location: TrainingLocation?,
        exclude: Long?,
        limit: Int = MAX_ALTERNATIVES,
    ): List<SessionSuggestion> {
        if (location == null) return emptyList()
        val lastPerformed = history.filter { it.sessionId != null }.groupBy { it.sessionId!! }.mapValues { (_, w) -> w.maxOf { it.startedAt } }
        return rank(sessions.filter { s -> s.id != exclude && s.exercises.any { it.sets.isNotEmpty() } }, location)
            .filter { it.isUsable }
            .sortedWith(
                compareBy<SessionCompatibility> { it.session.sortOrder }
                    .thenBy { it.compatibility.rank }
            )
            .take(limit)
            .map {
                SessionSuggestion(
                    session = it.session,
                    reason = if (it.compatibility == Compatibility.LINKED) SuggestionReason.LOCATION_MATCH else SuggestionReason.EQUIPMENT_MATCH,
                    compatibility = it.compatibility,
                    lastPerformedAt = lastPerformed[it.session.id],
                )
            }
    }

    const val MAX_ALTERNATIVES = 4

    /**
     * Picks the session to propose today:
     * 1. the session following the last one performed in its program, if usable at the location;
     * 2. otherwise the start of a program suited to the location;
     * 3. otherwise the least recently performed usable session.
     */
    fun suggest(
        sessions: List<WorkoutSession>,
        programs: List<TrainingProgram>,
        history: List<WorkoutExecution>,
        location: TrainingLocation?,
    ): SessionSuggestion? {
        val candidates = sessions.filter { session -> session.exercises.any { it.sets.isNotEmpty() } }
        if (candidates.isEmpty()) return null
        val byId = candidates.associateBy { it.id }
        val lastPerformed: Map<Long, Long> = history
            .filter { it.sessionId != null }
            .groupBy { it.sessionId!! }
            .mapValues { (_, workouts) -> workouts.maxOf { it.startedAt } }

        fun usable(session: WorkoutSession) = compatibility(session, location).isUsable

        fun suggestion(session: WorkoutSession, reason: SuggestionReason, program: TrainingProgram? = null) = SessionSuggestion(
            session = session,
            reason = reason,
            program = program,
            compatibility = compatibility(session, location).compatibility,
            lastPerformedAt = lastPerformed[session.id],
        )

        val lastWorkout = history.filter { it.sessionId in byId }.maxByOrNull { it.startedAt }
        if (lastWorkout != null) {
            val programsWithLast = programs
                .filter { program -> program.sessions.any { it.id == lastWorkout.sessionId } }
                .sortedWith(compareByDescending<TrainingProgram> { location != null && location.id in it.compatibleLocationIds }
                    .thenByDescending { isProgramCompatible(it, location) })
            for (program in programsWithLast) {
                val ordered = program.sessions.mapNotNull { byId[it.id] }
                val lastIndex = ordered.indexOfFirst { it.id == lastWorkout.sessionId }
                if (lastIndex < 0 || ordered.size < 2) continue
                val next = (1 until ordered.size)
                    .map { ordered[(lastIndex + it) % ordered.size] }
                    .firstOrNull { usable(it) }
                if (next != null) return suggestion(next, SuggestionReason.NEXT_IN_PROGRAM, program)
            }
        }

        if (lastWorkout == null) {
            val program = programs
                .filter { it.sessions.isNotEmpty() }
                .sortedWith(compareByDescending<TrainingProgram> { location != null && location.id in it.compatibleLocationIds }
                    .thenByDescending { isProgramCompatible(it, location) })
                .firstOrNull()
            val first = program?.sessions?.mapNotNull { byId[it.id] }?.firstOrNull { usable(it) }
            if (program != null && first != null) return suggestion(first, SuggestionReason.PROGRAM_START, program)
        }

        val ranked = rank(candidates, location)
        val usableSessions = ranked.filter { it.isUsable }.map { it.session }.ifEmpty { candidates }
        val leastRecent = usableSessions.minWithOrNull(
            compareBy<WorkoutSession> { lastPerformed[it.id] ?: Long.MIN_VALUE }
                .thenBy { compatibility(it, location).compatibility.rank }
        ) ?: return null
        val reason = if (location != null && compatibility(leastRecent, location).compatibility == Compatibility.LINKED) {
            SuggestionReason.LOCATION_MATCH
        } else {
            SuggestionReason.LEAST_RECENT
        }
        return suggestion(leastRecent, reason)
    }
}
