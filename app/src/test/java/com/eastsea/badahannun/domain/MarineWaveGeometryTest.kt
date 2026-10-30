package com.eastsea.badahannun.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarineWaveGeometryTest {
    @Test fun waveTouchesLeftAndRightCompassEdges() {
        val points = MarineWaveGeometry.sample(180f, 180f, 159f, 1.2)
        assertEquals(97, points.size)
        assertEquals(21f, points.first().x, 0.001f)
        assertEquals(339f, points.last().x, 0.001f)
        assertEquals(180f, points.first().y, 0.001f)
        assertEquals(180f, points.last().y, 0.001f)
        assertTrue(points.zipWithNext().all { (a, b) -> b.x > a.x })
    }

    @Test fun higherWaveHeightIncreasesRippleAtSamePhase() {
        val calm = MarineWaveGeometry.sample(0f, 0f, 100f, 0.1)
        val rough = MarineWaveGeometry.sample(0f, 0f, 100f, 3.0)
        assertTrue(rough[12].y > calm[12].y)
    }

    @Test fun missingHeightDoesNotCreateNonFiniteGeometry() {
        val points = MarineWaveGeometry.sample(0f, 0f, 100f, Double.NaN, 0.5f)
        assertTrue(points.all { it.x.isFinite() && it.y.isFinite() })
    }
}
