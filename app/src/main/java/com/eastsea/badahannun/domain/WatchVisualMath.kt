package com.eastsea.badahannun.domain

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Small, platform-independent helpers used by the watch dial and 12h forecast. */
object WatchVisualMath {
    data class FishRun(val name: String, val startIndex: Int, val endExclusive: Int)

    /** Wind forecasts express the direction wind COMES FROM, measured clockwise from true north. */
    fun relativeWind(windFromDegrees: Double?, headingDegrees: Float?): Float? {
        if (windFromDegrees == null || !windFromDegrees.isFinite()) return null
        val heading = headingDegrees?.takeIf { it.isFinite() } ?: 0f
        val value = windFromDegrees.toFloat() - heading
        return ((value % 360f) + 360f) % 360f
    }

    /** Absolute Korean local wall clock time. The date matters across midnight. */
    fun dateTimeLabel(time: LocalDateTime): String =
        time.format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))

    fun dateRangeLabel(times: List<LocalDateTime>): String {
        if (times.isEmpty()) return "시각 없음 · KST"
        val start = dateTimeLabel(times.first())
        val end = dateTimeLabel(times.last())
        return "$start ~ $end · KST"
    }

    /** Group contiguous leading-fish segments without inventing gaps. */
    fun runs(names: List<String>): List<FishRun> {
        if (names.isEmpty()) return emptyList()
        val result = mutableListOf<FishRun>()
        var start = 0
        for (i in 1..names.size) {
            if (i == names.size || names[i] != names[start]) {
                result += FishRun(names[start], start, i)
                start = i
            }
        }
        return result
    }
}
