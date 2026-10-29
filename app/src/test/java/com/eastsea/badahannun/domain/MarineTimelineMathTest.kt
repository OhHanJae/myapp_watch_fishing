package com.eastsea.badahannun.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class MarineTimelineMathTest {
    private val start = LocalDateTime.of(2026, 10, 29, 0, 0)
    private val hours = (0..24).map { start.plusHours(it.toLong()) }

    @Test fun roundsToPreviousHalfHour() {
        assertEquals(start.plusMinutes(30), MarineTimelineMath.halfHourStart(start.plusMinutes(43)))
        assertEquals(start, MarineTimelineMath.halfHourStart(start.plusMinutes(29)))
    }

    @Test fun interpolatesNumbersAndDirectionsAcrossNorth() {
        val points = hours.take(2)
        assertEquals(1.5, MarineTimelineMath.sample(points, listOf(1.0, 2.0), start.plusMinutes(30))!!, 0.0001)
        assertEquals(0.0, MarineTimelineMath.direction(points, listOf(350.0, 10.0), start.plusMinutes(30))!!, 0.0001)
        assertNull(MarineTimelineMath.sample(points, listOf(null, 2.0), start.plusMinutes(30)))
    }

    @Test fun neverExtrapolatesMissingForecast() {
        assertNull(MarineTimelineMath.sample(hours.take(2), listOf(1.0, 2.0), start.minusMinutes(30)))
        assertNull(MarineTimelineMath.sample(hours.take(2), listOf(1.0, 2.0), start.plusHours(2)))
    }

    @Test fun tideStageReportsFloodAndEbbFromModel() {
        // Low tide at 02:00, high tide at 08:00, low tide at 14:00.
        val levels = (0..24).map { i ->
            when {
                i <= 2 -> 2.0 - i * 0.5
                i <= 8 -> 1.0 + (i - 2) * 0.25
                i <= 14 -> 2.5 - (i - 8) * 0.25
                else -> 1.0 + (i - 14) * 0.2
            }
        }
        assertEquals("초들물", MarineTimelineMath.tideStage(hours, levels, start.plusHours(3)))
        assertEquals("중날물", MarineTimelineMath.tideStage(hours, levels, start.plusHours(11)))
        assertNull(MarineTimelineMath.tideStage(emptyList(), emptyList(), start))
    }
}
