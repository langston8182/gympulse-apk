package com.gympulse.app.presentation

import com.gympulse.app.domain.model.MuscleGroup
import com.gympulse.app.domain.model.TrainingSplit
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingSplitTest {
    @Test
    fun `splits follow the main muscle`() {
        assertEquals(TrainingSplit.PUSH, TrainingSplit.fromMuscle(MuscleGroup.CHEST))
        assertEquals(TrainingSplit.PULL, TrainingSplit.fromMuscle(MuscleGroup.BICEPS))
        assertEquals(TrainingSplit.LEGS, TrainingSplit.fromMuscle(MuscleGroup.GLUTES))
        assertEquals(TrainingSplit.OTHER, TrainingSplit.fromMuscle(MuscleGroup.ABS))
        assertEquals(TrainingSplit.OTHER, TrainingSplit.fromMuscle(null))
    }
}
