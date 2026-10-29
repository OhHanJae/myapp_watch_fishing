package com.eastsea.badahannun.domain

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * Time-series math only. Open-Meteo marine waves/SST are hourly model values in Korea.
 * Half-hour forecasts below are interpolated, never represented as observations.
 */
object MarineTimelineMath {
    fun halfHourStart(now: LocalDateTime): LocalDateTime =
        now.withMinute(if (now.minute < 30) 0 else 30).withSecond(0).withNano(0)

    fun sample(
        times: List<LocalDateTime>, values: List<Double?>, target: LocalDateTime
    ): Double? {
        if (times.isEmpty()) return null
        val exact = times.indexOf(target)
        if (exact >= 0) return values.getOrNull(exact)?.takeIf { it.isFinite() }
        val right = times.indexOfFirst { it.isAfter(target) }
        if (right <= 0) return null
        val left = right - 1
        val before = values.getOrNull(left)?.takeIf { it.isFinite() } ?: return null
        val after = values.getOrNull(right)?.takeIf { it.isFinite() } ?: return null
        val minutes = Duration.between(times[left], times[right]).toMinutes()
        // Do not bridge holes in forecast coverage.
        if (minutes <= 0 || minutes > 90) return null
        val elapsed = Duration.between(times[left], target).toMinutes()
        if (elapsed !in 0..minutes) return null
        return before + (after - before) * elapsed.toDouble() / minutes
    }

    /** Circular interpolation, e.g. 350° -> 10° must pass through 0°, not 180°. */
    fun direction(
        times: List<LocalDateTime>, values: List<Double?>, target: LocalDateTime
    ): Double? {
        val exact = times.indexOf(target)
        if (exact >= 0) return values.getOrNull(exact)?.takeIf { it.isFinite() }
            ?.let { (it % 360 + 360) % 360 }
        val right = times.indexOfFirst { it.isAfter(target) }
        if (right <= 0) return null
        val left = right - 1
        val a = values.getOrNull(left)?.takeIf { it.isFinite() } ?: return null
        val b = values.getOrNull(right)?.takeIf { it.isFinite() } ?: return null
        val minutes = Duration.between(times[left], times[right]).toMinutes()
        if (minutes <= 0 || minutes > 90) return null
        val part = Duration.between(times[left], target).toMinutes().toDouble() / minutes
        val shortest = ((b - a + 540) % 360) - 180
        return ((a + shortest * part) % 360 + 360) % 360
    }

    fun code(times: List<LocalDateTime>, values: List<Int?>, target: LocalDateTime): Int? {
        if (times.isEmpty()) return null
        val next = times.indexOfFirst { !it.isBefore(target) }
        if (next < 0) return null
        val before = (next - 1).takeIf { it >= 0 }
        val near = if (before == null ||
            Duration.between(times[before], target).abs() >= Duration.between(target, times[next]).abs()
        ) next else before
        if (abs(Duration.between(times[near], target).toMinutes()) > 60) return null
        return values.getOrNull(near)
    }

    /**
     * Estimated flood/ebb stage from model sea level; NOT observed KHOA tide times.
     * Returns only trend when preceding/following turn times cannot be determined.
     */
    fun tideStage(
        times: List<LocalDateTime>, levels: List<Double?>, target: LocalDateTime
    ): String? {
        val delta = sample(times, levels, target.plusMinutes(30))?.minus(
            sample(times, levels, target.minusMinutes(30)) ?: return null
        ) ?: return null
        if (abs(delta) <= 0.004) return "정조 부근"
        val rising = delta > 0
        val extrema = mutableListOf<Pair<LocalDateTime, Boolean>>() // true = high
        for (i in 1 until minOf(times.size, levels.size) - 1) {
            val a = levels[i - 1] ?: continue
            val b = levels[i] ?: continue
            val c = levels[i + 1] ?: continue
            val high = b > a + 0.003 && b >= c
            val low = b < a - 0.003 && b <= c
            if (!high && !low) continue
            if (extrema.isNotEmpty() &&
                Duration.between(extrema.last().first, times[i]).toMinutes() < 180
            ) continue
            extrema += times[i] to high
        }
        val start = extrema.lastOrNull { !it.first.isAfter(target) }
        val end = extrema.firstOrNull { it.first.isAfter(target) }
        val direction = if (rising) "들물" else "날물"
        if (start == null || end == null || start.second == end.second ||
            start.second == rising || end.second != rising
        ) return "$direction 추정"
        val period = Duration.between(start.first, end.first).toMinutes()
        if (period < 180 || period > 720) return "$direction 추정"
        val passed = Duration.between(start.first, target).toMinutes()
        val fraction = passed.toDouble() / period
        return when {
            fraction < 1.0 / 3 -> "초$direction"
            fraction < 2.0 / 3 -> "중$direction"
            else -> "끝$direction"
        }
    }
}
