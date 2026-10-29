package com.eastsea.badahannun.domain

import com.eastsea.badahannun.data.FishSpecies
import com.eastsea.badahannun.data.FishingPoint
import com.eastsea.badahannun.data.SeaReport
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.*

object FishingLogic {
    val KST = java.time.ZoneId.of("Asia/Seoul")

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val latDelta = Math.toRadians(lat2 - lat1)
        val lonDelta = Math.toRadians(lon2 - lon1)
        val a = sin(latDelta / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(lonDelta / 2).pow(2.0)
        return 6371.0088 * 2 * atan2(sqrt(a.coerceIn(0.0, 1.0)), sqrt((1 - a).coerceAtLeast(0.0)))
    }

    fun nearest(points: List<FishingPoint>, lat: Double, lon: Double): FishingPoint? =
        points.minByOrNull { distanceKm(lat, lon, it.latitude, it.longitude) }

    fun timeBucket(time: LocalTime, sunrise: LocalTime?, sunset: LocalTime?): String {
        if (sunrise != null && sunset != null) {
            // Dynamic feeding windows, based on today's local sun times.
            if (within(time, sunset.minusMinutes(60), sunset.plusMinutes(60))) return "dusk"
            if (within(time, sunrise.minusMinutes(90), sunrise)) return "dawn"
            if (within(time, sunrise, sunrise.plusHours(3))) return "morning"
            if (time.isAfter(sunset.plusMinutes(60)) || time.isBefore(sunrise.minusMinutes(90)))
                return "night"
            return "day"
        }
        val hour = time.hour
        return when (hour) {
            in 4..6 -> "dawn"
            in 7..10 -> "morning"
            in 11..15 -> "day"
            in 16..18 -> "dusk"
            else -> "night"
        }
    }

    private fun within(t: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        if (start <= end) t >= start && t < end else t >= start || t < end

    data class ScoredFish(val fish: FishSpecies, val score: Int)

    fun topFish(
        fish: List<FishSpecies>,
        point: FishingPoint,
        report: SeaReport?,
        now: ZonedDateTime = ZonedDateTime.now(KST)
    ): List<ScoredFish> {
        val month = now.monthValue
        val time = timeBucket(now.toLocalTime(), report?.sunrise, report?.sunset)
        return fish.asSequence()
            .filter { it.name in point.fishNames }
            .map { f ->
                var score = 40 + when {
                    month in f.peakMonths -> 30
                    month in f.months -> 17
                    else -> -28
                }
                score += if (time in f.times) 18 else -10
                score += 18 // Selected point's target fish, as in source HTML.
                score += liveAdjustment(f, report)
                ScoredFish(f, score.coerceIn(0, 100))
            }
            .sortedWith(compareByDescending<ScoredFish> { it.score }
                .thenBy { it.fish.name })
            .take(3)
            .toList()
    }

    private fun liveAdjustment(fish: FishSpecies, report: SeaReport?): Int {
        val wind = report?.weather?.windMs
        val wave = report?.ocean?.waveM
        val rain = report?.weather?.rainProbability
        var value = 0

        // Missing measurements are NOT interpreted as zero wind/waves.
        if (wind != null) value += when {
            wind <= 4 -> 7
            wind <= 7 -> 2
            wind <= 10 -> -8
            else -> -22
        }
        if (wave != null) value += when {
            wave <= 0.55 -> 6
            wave <= 1.0 -> 1
            wave <= 1.5 -> -10
            else -> -25
        }
        if (rain != null && rain >= 70) value -= 7
        if (fish.name in setOf("농어", "광어") && wave != null && wave in 0.4..1.0)
            value += 4

        return value
    }

    enum class Risk { GOOD, CAUTION, BAD, UNKNOWN }
    data class Safety(val risk: Risk, val title: String, val detail: String)

    fun safety(report: SeaReport?): Safety {
        val wind = report?.weather?.windMs
        val gust = report?.weather?.gustMs
        val wave = report?.ocean?.waveM

        // Source HTML thresholds, with explicit missing-data handling.
        val effectiveWind = listOfNotNull(wind, gust?.times(0.65)).maxOrNull()
        if ((wave != null && wave >= 1.5) ||
            (effectiveWind != null && effectiveWind >= 11.0))
            return Safety(Risk.BAD, "출조 비추천", "강풍·파고 위험. 갯바위·낮은 방파제 제외")
        if ((wave != null && wave >= 0.9) ||
            (effectiveWind != null && effectiveWind >= 7.0))
            return Safety(Risk.CAUTION, "주의", "돌풍·너울 확인, 노출 포인트 피하기")
        if (wind == null || wave == null)
            return Safety(Risk.UNKNOWN, "정보 부족", "풍속·파고 모두 확인되기 전 안전 판단 불가")
        return Safety(Risk.GOOD, "수치상 무난", "현장 너울·출입통제·기상특보 별도 확인")
    }

    fun compassName(degrees: Float): String {
        val dirs = listOf("북", "북동", "동", "남동", "남", "남서", "서", "북서")
        val d = ((degrees % 360f + 360f) % 360f)
        return dirs[(floor((d + 22.5f) / 45f).toInt()) % 8]
    }

    fun weatherLabel(code: Int?): String = when (code) {
        0 -> "맑음"
        1, 2 -> "대체로 맑음"
        3 -> "흐림"
        45, 48 -> "안개"
        51, 53, 55, 56, 57 -> "이슬비"
        61, 63, 65, 66, 67, 80, 81, 82 -> "비"
        71, 73, 75, 77, 85, 86 -> "눈"
        95, 96, 99 -> "뇌우"
        else -> "확인 불가"
    }

    fun windDirection(degrees: Double?): String {
        if (degrees == null) return "--"
        val dirs = listOf("북", "북동", "동", "남동", "남", "남서", "서", "북서")
        return dirs[(floor(((degrees % 360 + 360) % 360 + 22.5) / 45).toInt()) % 8] + "풍"
    }
}
