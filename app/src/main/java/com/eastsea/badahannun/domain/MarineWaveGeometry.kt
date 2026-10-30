package com.eastsea.badahannun.domain

import kotlin.math.PI
import kotlin.math.sin

/**
 * One continuous wave between the left and right edges of a compass ring.
 *
 * Both the Wear tile (static frame) and the Compose app (animated phase)
 * use the same geometry. Ends touch the horizontal compass diameter;
 * the center of the wave dips beneath the compact weather text.
 * This is a visual representation of forecast wave height, not observed surf.
 */
object MarineWaveGeometry {
    data class WavePoint(val x: Float, val y: Float)

    fun sample(
        centerX: Float,
        centerY: Float,
        radius: Float,
        waveHeightM: Double?,
        phaseTurns: Float = 0f,
        segments: Int = 96
    ): List<WavePoint> {
        require(radius.isFinite() && radius > 0f) { "radius must be positive" }
        require(segments >= 2) { "need at least two segments" }
        val height = waveHeightM?.takeIf { it.isFinite() }
            ?.coerceIn(0.0, 4.0)?.toFloat() ?: 0f
        val amplitude = radius * (0.035f + height * 0.026f)
        val bend = radius * 0.52f

        return (0..segments).map { i ->
            val t = i.toFloat() / segments
            val arch = sin(PI * t).toFloat()
            val ripple = sin(4.0 * PI * t + 2.0 * PI * phaseTurns).toFloat()
            WavePoint(
                x = centerX + (2f * t - 1f) * radius,
                y = centerY + bend * arch + amplitude * ripple * arch
            )
        }
    }
}
