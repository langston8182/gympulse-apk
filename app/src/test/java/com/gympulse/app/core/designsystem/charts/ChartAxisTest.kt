package com.gympulse.app.core.designsystem.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartAxisTest {

    @Test
    fun `counts get integer ticks starting at zero`() {
        val axis = niceAxis(0.0, 1.0, startAtZero = true, integerOnly = true)
        assertEquals(listOf(0.0, 1.0), axis.ticks)
    }

    @Test
    fun `volumes get round ticks covering the data`() {
        val axis = niceAxis(0.0, 17_800.0, startAtZero = true, integerOnly = false)
        assertEquals(listOf(0.0, 5_000.0, 10_000.0, 15_000.0, 20_000.0), axis.ticks)
    }

    @Test
    fun `non zero based axes stay round around the data`() {
        val axis = niceAxis(93.6, 129.1, startAtZero = false, integerOnly = false)
        assertTrue(axis.min <= 93.6 && axis.max >= 129.1)
        assertTrue(axis.ticks.all { it % 10.0 == 0.0 })
    }

    @Test
    fun `flat data still produces a valid axis`() {
        val axis = niceAxis(100.0, 100.0, startAtZero = false, integerOnly = false)
        assertTrue(axis.max > axis.min)
        assertTrue(axis.ticks.size >= 2)
    }
}
