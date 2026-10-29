package com.eastsea.badahannun.domain

import android.icu.util.Calendar
import android.icu.util.ChineseCalendar
import android.icu.util.TimeZone
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * Mirrors the source HTML's simplified 15-day / 8-multye scheme.
 *
 * IMPORTANT:
 * ChineseCalendar's lunar day is an approximation to local Korean tide-name
 * conventions; it is NOT an official KHOA tide table or local tidal measurement.
 * Display with an explicit "approximate" qualifier.
 */
object TideCalculator {
    data class TideLabel(val name: String, val band: String, val lunarDay: Int?)

    fun fromLunarDay(day: Int?): TideLabel {
        if (day == null || day !in 1..30) return TideLabel("--", "음력 정보 없음", null)
        val n = ((day - 1) % 15) + 1
        val name = when (n) {
            1 -> "8물"
            2 -> "9물"
            3 -> "10물"
            4 -> "11물"
            5 -> "12물"
            6 -> "13물"
            7 -> "14물"
            8 -> "조금"
            9 -> "1물"
            10 -> "2물"
            11 -> "3물"
            12 -> "4물"
            13 -> "5물"
            14 -> "6물"
            else -> "7물"
        }
        val band = when (name) {
            "7물", "8물", "9물" -> "사리권"
            "14물", "조금", "1물" -> "조금권"
            else -> "중간물"
        }
        return TideLabel(name, band, day)
    }

    fun onDate(date: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))): TideLabel {
        val lunarDay = runCatching {
            val cal = ChineseCalendar(
                TimeZone.getTimeZone("Asia/Seoul"),
                Locale.KOREA
            )
            val noon = date.atTime(12, 0).atZone(ZoneId.of("Asia/Seoul"))
            cal.time = Date.from(noon.toInstant())
            cal.get(Calendar.DAY_OF_MONTH)
        }.getOrNull()
        return fromLunarDay(lunarDay)
    }
}
