package com.eastsea.badahannun.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class WatchVisualMathTest {
    @Test fun relativeWindHandlesNorthCrossing() {
        assertEquals(20f, WatchVisualMath.relativeWind(10.0, 350f)!!, 0.001f)
        assertEquals(270f, WatchVisualMath.relativeWind(90.0, 180f)!!, 0.001f)
        assertEquals(90f, WatchVisualMath.relativeWind(90.0, null)!!, 0.001f)
        assertNull(WatchVisualMath.relativeWind(null, 50f))
        assertNull(WatchVisualMath.relativeWind(Double.NaN, 50f))
    }

    @Test fun formatsDatesAcrossMidnight() {
        val a = LocalDateTime.of(2026, 10, 29, 18, 30)
        val b = a.plusHours(12)
        assertEquals("10/29 18:30", WatchVisualMath.dateTimeLabel(a))
        assertEquals("10/29 18:30 ~ 10/30 06:30 · KST",
            WatchVisualMath.dateRangeLabel(listOf(a, b)))
    }

    @Test fun condensesSpeciesByContiguousHalfHourSegments() {
        assertEquals(
            listOf(
                WatchVisualMath.FishRun("삼치", 0, 2),
                WatchVisualMath.FishRun("전갱이", 2, 4),
                WatchVisualMath.FishRun("삼치", 4, 5)
            ),
            WatchVisualMath.runs(listOf("삼치", "삼치", "전갱이", "전갱이", "삼치"))
        )
        assertEquals(emptyList<WatchVisualMath.FishRun>(), WatchVisualMath.runs(emptyList()))
    }
}
