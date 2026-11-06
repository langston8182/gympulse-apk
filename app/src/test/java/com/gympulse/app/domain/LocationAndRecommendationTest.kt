package com.gympulse.app.domain

import com.gympulse.app.domain.location.Compatibility
import com.gympulse.app.domain.location.LocationMatcher
import com.gympulse.app.domain.location.SessionRecommender
import com.gympulse.app.domain.location.SuggestionReason
import com.gympulse.app.domain.model.Equipment
import com.gympulse.app.domain.model.GeoPoint
import com.gympulse.app.domain.model.LocationType
import com.gympulse.app.domain.model.SessionExercise
import com.gympulse.app.domain.model.SetTarget
import com.gympulse.app.domain.model.TrainingLocation
import com.gympulse.app.domain.model.TrainingProgram
import com.gympulse.app.domain.model.WorkoutSession
import com.gympulse.app.testing.TestData
import com.gympulse.app.testing.TestData.DAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationMatcherTest {

    private val home = TrainingLocation(id = 1, name = "Maison", latitude = 48.8590, longitude = 2.3470, radiusMeters = 120)
    private val gym = TrainingLocation(id = 2, name = "Salle", latitude = 48.8530, longitude = 2.3690, radiusMeters = 150)
    private val noCoordinates = TrainingLocation(id = 3, name = "Parc")

    @Test
    fun `haversine distance matches a known value`() {
        val paris = GeoPoint(48.8566, 2.3522)
        val london = GeoPoint(51.5074, -0.1278)
        assertEquals(343_500.0, LocationMatcher.distanceMeters(paris, london), 1_500.0)
    }

    @Test
    fun `position inside a radius matches that location`() {
        val match = LocationMatcher.match(GeoPoint(48.8591, 2.3472), listOf(home, gym, noCoordinates))
        assertEquals(home.id, match!!.location.id)
    }

    @Test
    fun `position far from every location matches nothing`() {
        assertNull(LocationMatcher.match(GeoPoint(45.76, 4.83), listOf(home, gym)))
    }

    @Test
    fun `gps accuracy extends the radius within a bounded tolerance`() {
        val justOutside = GeoPoint(48.8590, 2.3470 + 0.0023) // ~170 m east of home
        assertNull(LocationMatcher.match(justOutside, listOf(home)))
        assertEquals(home.id, LocationMatcher.match(justOutside.copy(accuracyMeters = 80f), listOf(home))!!.location.id)
        assertNull(LocationMatcher.match(GeoPoint(48.8590, 2.3470 + 0.004, accuracyMeters = 5_000f), listOf(home)))
    }
}

class SessionRecommenderTest {

    private val home = TrainingLocation(
        id = 1, name = "Maison", type = LocationType.HOME,
        availableEquipment = setOf(Equipment.DUMBBELL, Equipment.BENCH, Equipment.MAT),
    )
    private val gym = TrainingLocation(id = 2, name = "Salle", type = LocationType.GYM, availableEquipment = Equipment.entries.toSet())

    private fun session(id: Long, name: String, vararg exercises: com.gympulse.app.domain.model.Exercise, locations: Set<Long> = emptySet()) =
        WorkoutSession(
            id = id,
            name = name,
            exercises = exercises.mapIndexed { i, e -> SessionExercise(exercise = e, order = i, sets = listOf(SetTarget(10, 20.0)), restDurationSeconds = 60) },
            compatibleLocationIds = locations,
        )

    private val push = session(1, "Push", TestData.benchDumbbell, locations = setOf(2))
    private val legs = session(2, "Legs", TestData.squat, locations = setOf(2))
    private val pull = session(3, "Pull", TestData.pullUp, locations = setOf(2))
    private val homeCore = session(4, "Core maison", TestData.plank, locations = setOf(1))
    private val program = TrainingProgram(id = 1, name = "PPL", sessions = listOf(push, pull, legs), compatibleLocationIds = setOf(2))

    @Test
    fun `compatibility uses links first, then the available equipment`() {
        assertEquals(Compatibility.LINKED, SessionRecommender.compatibility(push, gym).compatibility)
        assertEquals(Compatibility.EQUIPMENT_MATCH, SessionRecommender.compatibility(push, home).compatibility)
        val legsAtHome = SessionRecommender.compatibility(legs, home)
        assertEquals(Compatibility.INCOMPATIBLE, legsAtHome.compatibility)
        assertEquals(setOf(Equipment.BARBELL, Equipment.SQUAT_RACK), legsAtHome.missingEquipment)
        assertEquals(Compatibility.UNKNOWN, SessionRecommender.compatibility(legs, null).compatibility)
    }

    @Test
    fun `ranking puts sessions usable at the location first`() {
        val ranked = SessionRecommender.rank(listOf(legs, homeCore, push), home).map { it.session.id }
        assertEquals(listOf(4L, 1L, 2L), ranked)
    }

    @Test
    fun `suggests the next session of the program after the last one performed`() {
        val history = listOf(TestData.completedWorkout(1, 0, emptyList(), sessionId = push.id))
        val suggestion = SessionRecommender.suggest(listOf(push, pull, legs, homeCore), listOf(program), history, gym)!!
        assertEquals(pull.id, suggestion.session.id)
        assertEquals(SuggestionReason.NEXT_IN_PROGRAM, suggestion.reason)
        assertEquals(program.id, suggestion.program!!.id)
    }

    @Test
    fun `skips program sessions that cannot be done at the current location`() {
        val history = listOf(TestData.completedWorkout(1, 0, emptyList(), sessionId = push.id))
        val atHome = SessionRecommender.suggest(listOf(push, pull, legs, homeCore), listOf(program), history, home)!!
        assertEquals(homeCore.id, atHome.session.id)
        assertEquals(SuggestionReason.LOCATION_MATCH, atHome.reason)
    }

    @Test
    fun `without history, proposes the start of the program suited to the location`() {
        val suggestion = SessionRecommender.suggest(listOf(push, pull, legs), listOf(program), emptyList(), gym)!!
        assertEquals(push.id, suggestion.session.id)
        assertEquals(SuggestionReason.PROGRAM_START, suggestion.reason)
    }

    @Test
    fun `falls back to the least recently performed usable session`() {
        val history = listOf(
            TestData.completedWorkout(1, 10 * DAY, emptyList(), sessionId = homeCore.id),
            TestData.completedWorkout(2, 5 * DAY, emptyList(), sessionId = 99),
        )
        val suggestion = SessionRecommender.suggest(listOf(homeCore, push), emptyList(), history, home)!!
        assertEquals(push.id, suggestion.session.id)
        assertEquals(SuggestionReason.LEAST_RECENT, suggestion.reason)
    }

    @Test
    fun `alternatives list linked sessions then those the equipment allows, without the main one`() {
        val all = listOf(push, pull, legs, homeCore)
        val alternatives = SessionRecommender.alternatives(all, emptyList(), home, exclude = null)
        assertEquals(listOf(homeCore.id, push.id), alternatives.map { it.session.id })
        assertEquals(listOf(SuggestionReason.LOCATION_MATCH, SuggestionReason.EQUIPMENT_MATCH), alternatives.map { it.reason })
        assertEquals(listOf(push.id), SessionRecommender.alternatives(all, emptyList(), home, exclude = homeCore.id).map { it.session.id })
    }

    @Test
    fun `no alternatives without a known location`() {
        assertEquals(emptyList<Any>(), SessionRecommender.alternatives(listOf(push, pull), emptyList(), null, exclude = null))
    }

    @Test
    fun `empty sessions are never suggested`() {
        val empty = WorkoutSession(id = 9, name = "Vide")
        assertNull(SessionRecommender.suggest(listOf(empty), emptyList(), emptyList(), null))
    }
}
