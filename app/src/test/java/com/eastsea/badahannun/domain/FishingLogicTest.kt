package com.eastsea.badahannun.domain

import com.eastsea.badahannun.data.FishSpecies
import com.eastsea.badahannun.data.FishingPoint
import com.eastsea.badahannun.data.SeaReport
import com.eastsea.badahannun.data.WeatherSnapshot
import com.eastsea.badahannun.data.OceanSnapshot
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class FishingLogicTest {
    private val point = FishingPoint(
        id = "ulsan-jeongja", region = "울산", name = "정자항",
        latitude = 35.615, longitude = 129.454,
        types = listOf("방파제"), fishNames = listOf("삼치", "볼락"),
        note = "", direction = ""
    )
    private fun species(
        name: String, months: Set<Int>, peak: Set<Int>, times: Set<String>
    ) = FishSpecies(
        id = name, name = name, months = months, peakMonths = peak,
        times = times, types = setOf("방파제"),
        lureMinGram = 1.0, lureMaxGram = 20.0,
        mainGear = 2, subGear = 1,
        lures = listOf("메탈지그 20g"), colors = listOf("실버"),
        action = "", water = "", note = ""
    )

    @Test fun haversineSamePositionIsZero() {
        assertEquals(0.0,
            FishingLogic.distanceKm(35.615, 129.454, 35.615, 129.454), 0.001)
    }

    @Test fun nearestReturnsUlsanPoint() {
        val far = point.copy(id = "pohang", latitude = 36.1)
        assertEquals(point,
            FishingLogic.nearest(listOf(far, point), 35.614, 129.454))
    }

    @Test fun twilightUsesSunTimes() {
        assertEquals("dusk", FishingLogic.timeBucket(
            LocalTime.of(17, 45), LocalTime.of(6, 30), LocalTime.of(18, 15)))
        assertEquals("dawn", FishingLogic.timeBucket(
            LocalTime.of(5, 40), LocalTime.of(6, 30), LocalTime.of(18, 15)))
    }

    @Test fun topThreePrioritizesSeasonAndTime() {
        val now = ZonedDateTime.of(2026, 10, 28, 6, 0, 0, 0, ZoneId.of("Asia/Seoul"))
        val inSeason = species("삼치", setOf(9, 10, 11), setOf(9, 10), setOf("dawn"))
        val offSeason = species("볼락", setOf(11, 12, 1, 2), setOf(1, 2), setOf("night"))
        val results = FishingLogic.topFish(listOf(offSeason, inSeason), point, null, now)
        assertEquals("삼치", results.first().fish.name)
        assertEquals(2, results.size)
    }

    @Test fun unknownWeatherDoesNotMeanGoodConditions() {
        assertEquals(FishingLogic.Risk.UNKNOWN, FishingLogic.safety(null).risk)
    }

    @Test fun dangerousWaveOverridesMissingWind() {
        val report = SeaReport(
            weather = null,
            ocean = OceanSnapshot(null, 1.8, null, null, null, null),
            tideEvents = emptyList(), sunrise = null, sunset = null,
            fetchedAtMs = 0L, stale = false, notice = null
        )
        assertEquals(FishingLogic.Risk.BAD, FishingLogic.safety(report).risk)
    }

    @Test fun windyButNotDangerousIsCaution() {
        val report = SeaReport(
            weather = WeatherSnapshot(20.0, 3, 8.0, 8.0, 20.0, 0.0, 50.0),
            ocean = OceanSnapshot(21.0, 0.4, 5.0, 90.0, null, null),
            tideEvents = emptyList(), sunrise = null, sunset = null,
            fetchedAtMs = 0L, stale = false, notice = null
        )
        assertEquals(FishingLogic.Risk.CAUTION, FishingLogic.safety(report).risk)
    }
}

class TideCalculatorTest {
    @Test fun simplifiedEightMultyeMappingMatchesHtml() {
        assertEquals("8물", TideCalculator.fromLunarDay(1).name)
        assertEquals("조금", TideCalculator.fromLunarDay(8).name)
        assertEquals("7물", TideCalculator.fromLunarDay(15).name)
        assertEquals("8물", TideCalculator.fromLunarDay(16).name)
        assertEquals("7물", TideCalculator.fromLunarDay(30).name)
    }
}
