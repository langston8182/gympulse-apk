package com.gympulse.app.presentation

import com.gympulse.app.domain.model.TrainingLocation
import com.gympulse.app.domain.model.WorkoutSession
import com.gympulse.app.presentation.sessions.groupSessionsByLocation
import com.gympulse.app.presentation.sessions.mergeGroupOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionGroupsTest {
    private val home = TrainingLocation(id = 1, name = "Maison")
    private val gym = TrainingLocation(id = 2, name = "Salle")
    private val work = TrainingLocation(id = 3, name = "Bureau")

    private fun session(id: Long, vararg places: Long) = WorkoutSession(id = id, name = "S$id", compatibleLocationIds = places.toSet())

    @Test
    fun `current place first then by name then sessions without a place and empty groups are left out`() {
        val sessions = listOf(session(1, 2), session(2, 1), session(3), session(4, 2, 1))
        val groups = groupSessionsByLocation(sessions, listOf(home, gym, work), currentLocationId = 2)

        assertEquals(listOf("Salle", "Maison", "Sans lieu"), groups.map { it.title })
        assertEquals(listOf(listOf(1L, 4L), listOf(2L, 4L), listOf(3L)), groups.map { g -> g.sessions.map { it.id } })
        assertEquals(listOf(true, false, false), groups.map { it.isCurrent })
    }

    @Test
    fun `without a current place the places are sorted by name`() {
        val groups = groupSessionsByLocation(listOf(session(1, 1), session(2, 3)), listOf(home, work), currentLocationId = null)
        assertEquals(listOf("Bureau", "Maison"), groups.map { it.title })
    }

    @Test
    fun `reordering one place keeps the slots of the other sessions`() {
        // Global order: 1 2 3 4 5. Place A holds 1, 3, 5 and the user puts them as 5, 1, 3.
        assertEquals(listOf(5L, 2L, 1L, 4L, 3L), mergeGroupOrder(listOf(1, 2, 3, 4, 5), listOf(5, 1, 3)))
        // Place B holds 2 and 4, swapped.
        assertEquals(listOf(1L, 4L, 3L, 2L, 5L), mergeGroupOrder(listOf(1, 2, 3, 4, 5), listOf(4, 2)))
    }
}
