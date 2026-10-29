package com.eastsea.badahannun.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlin.math.abs
import kotlin.math.roundToInt

data class WeatherSnapshot(
    val temperatureC: Double?,
    val weatherCode: Int?,
    val windMs: Double?,
    val gustMs: Double?,
    val windDirectionDegrees: Double?,
    val rainProbability: Double?,
    val relativeHumidity: Double?
)

data class OceanSnapshot(
    val surfaceTemperatureC: Double?,
    val waveM: Double?,
    val wavePeriodSeconds: Double?,
    val waveDirectionDegrees: Double?,
    val seaLevelMslM: Double?,
    val tideFlow: String?
)

data class TideEvent(val label: String, val time: LocalDateTime, val seaLevelMslM: Double)

data class SeaReport(
    val weather: WeatherSnapshot?,
    val ocean: OceanSnapshot?,
    val tideEvents: List<TideEvent>,
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    val fetchedAtMs: Long,
    val stale: Boolean,
    val notice: String?
)

/**
 * Network calls are made from the watch via HTTPS (phone proxy or Wi-Fi).
 * 15-minute foreground cache; stale data are clearly labelled after failure.
 */
class ForecastRepository(context: Context) {
    private val preferences = context.getSharedPreferences("marine_cache_v1", Context.MODE_PRIVATE)
    private val zone = ZoneId.of("Asia/Seoul")
    private val cacheMs = 15 * 60 * 1000L

    suspend fun load(
        point: FishingPoint,
        weatherLatitude: Double,
        weatherLongitude: Double,
        force: Boolean = false
    ): SeaReport = withContext(Dispatchers.IO) {
        // Rounded GPS in the *cache key* only; this is not a location history log.
        val key = "point_${point.id}_${(weatherLatitude * 100).roundToInt()}_${(weatherLongitude * 100).roundToInt()}"
        val cached = preferences.getString(key, null)?.let {
            runCatching { JSONObject(it) }.getOrNull()
        }
        val cacheAge = cached?.optLong("fetchedAt", 0L)?.let { System.currentTimeMillis() - it }
            ?: Long.MAX_VALUE
        if (!force && cacheAge in 0 until cacheMs && cached != null) {
            return@withContext parse(cached.optJSONObject("weather"),
                cached.optJSONObject("marine"),
                cached.optLong("fetchedAt"), stale = false, notice = null)
        }

        val latitude = "%.4f".format(Locale.US, weatherLatitude)
        val longitude = "%.4f".format(Locale.US, weatherLongitude)
        val seaLat = "%.4f".format(Locale.US, point.latitude)
        val seaLon = "%.4f".format(Locale.US, point.longitude)

        val weatherUrl = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,relative_humidity_2m" +
            "&hourly=precipitation_probability&daily=sunrise,sunset" +
            "&timezone=Asia%2FSeoul&forecast_days=2&wind_speed_unit=ms"
        val marineUrl = "https://marine-api.open-meteo.com/v1/marine" +
            "?latitude=$seaLat&longitude=$seaLon" +
            "&current=wave_height,wave_period,wave_direction,sea_surface_temperature,sea_level_height_msl" +
            "&hourly=sea_level_height_msl" +
            "&timezone=Asia%2FSeoul&forecast_days=3&past_days=1&cell_selection=sea"

        val (weatherNew, marineNew) = coroutineScope {
            val w = async { runCatching { fetchJson(weatherUrl) }.getOrNull() }
            val m = async { runCatching { fetchJson(marineUrl) }.getOrNull() }
            w.await() to m.await()
        }

        if (weatherNew == null && marineNew == null) {
            if (cached != null) {
                return@withContext parse(
                    cached.optJSONObject("weather"),
                    cached.optJSONObject("marine"),
                    cached.optLong("fetchedAt"),
                    stale = true,
                    notice = "네트워크 오류 · 이전 예보 표시"
                )
            }
            return@withContext SeaReport(
                null, null, emptyList(), null, null,
                0L, stale = true, notice = "인터넷 연결 또는 Open-Meteo 응답 확인"
            )
        }

        // A mixed fresh/stale response is explicitly marked stale.
        val fallbackUsed = weatherNew == null || marineNew == null
        val weatherJson = weatherNew ?: cached?.optJSONObject("weather")
        val marineJson = marineNew ?: cached?.optJSONObject("marine")
        val savedAt = if (fallbackUsed && cached != null)
            cached.optLong("fetchedAt", System.currentTimeMillis())
        else System.currentTimeMillis()
        val record = JSONObject()
            .put("weather", weatherJson)
            .put("marine", marineJson)
            .put("fetchedAt", savedAt)

        // Prevent stale response from overwriting a good cache after a partial outage.
        if (!fallbackUsed) preferences.edit().putString(key, record.toString()).apply()
        // Never persist an incomplete response as a fresh full-data cache.

        parse(weatherJson, marineJson, savedAt,
            stale = fallbackUsed,
            notice = if (fallbackUsed) "일부 예보를 받지 못함 · 값 확인 필요" else null)
    }

    private fun fetchJson(endpoint: String): JSONObject {
        val connection = URL(endpoint).openConnection() as HttpsURLConnection
        connection.connectTimeout = 7_000
        connection.readTimeout = 7_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "BadaHannunWear/1.0")
        try {
            if (connection.responseCode !in 200..299)
                throw IOException("Open-Meteo HTTP ${connection.responseCode}")
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(text)
            if (json.optBoolean("error", false))
                throw IOException(json.optString("reason", "API rejected request"))
            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(
        weatherJson: JSONObject?,
        marineJson: JSONObject?,
        fetchedAtMs: Long,
        stale: Boolean,
        notice: String?
    ): SeaReport {
        val weatherCurrent = weatherJson?.optJSONObject("current")
        val marineCurrent = marineJson?.optJSONObject("current")
        val marineHourly = marineJson?.optJSONObject("hourly")
        val weatherHourly = weatherJson?.optJSONObject("hourly")
        val localNow = LocalDateTime.now(zone)

        val rain = weatherHourly?.let {
            val index = nearestTimeIndex(it.optJSONArray("time"), localNow)
            it.optJSONArray("precipitation_probability")?.numberAt(index)
        }
        val weather = weatherCurrent?.let {
            WeatherSnapshot(
                temperatureC = it.number("temperature_2m"),
                weatherCode = it.number("weather_code")?.toInt(),
                windMs = it.number("wind_speed_10m"),
                gustMs = it.number("wind_gusts_10m"),
                windDirectionDegrees = it.number("wind_direction_10m"),
                rainProbability = rain,
                relativeHumidity = it.number("relative_humidity_2m")
            )
        }

        val tideTimes = marineHourly?.optJSONArray("time")
        val hourlyLevels = marineHourly?.optJSONArray("sea_level_height_msl")
        val currentIndex = nearestTimeIndex(tideTimes, localNow)
        val seaLevel = marineCurrent?.number("sea_level_height_msl")
            ?: hourlyLevels?.numberAt(currentIndex)
        val trend = calculateTrend(hourlyLevels, currentIndex)

        val ocean = marineCurrent?.let {
            OceanSnapshot(
                surfaceTemperatureC = it.number("sea_surface_temperature"),
                waveM = it.number("wave_height"),
                wavePeriodSeconds = it.number("wave_period"),
                waveDirectionDegrees = it.number("wave_direction"),
                seaLevelMslM = seaLevel,
                tideFlow = trend
            )
        }
        val nextTides = turningPoints(tideTimes, hourlyLevels, localNow)
        val daily = weatherJson?.optJSONObject("daily")
        val dates = daily?.optJSONArray("time")
        val todayText = localNow.toLocalDate().toString()
        val todayIndex = (0 until (dates?.length() ?: 0))
            .firstOrNull { dates?.optString(it) == todayText } ?: 0

        val sunrise = daily?.optJSONArray("sunrise")?.optString(todayIndex)
            ?.let(::parseDateTime)?.toLocalTime()
        val sunset = daily?.optJSONArray("sunset")?.optString(todayIndex)
            ?.let(::parseDateTime)?.toLocalTime()

        return SeaReport(weather, ocean, nextTides, sunrise, sunset,
            fetchedAtMs, stale, notice)
    }

    private fun calculateTrend(levels: JSONArray?, index: Int): String? {
        if (levels == null || index !in 0 until levels.length()) return null
        val now = levels.numberAt(index) ?: return null
        val before = levels.numberAt(index - 1)
        val after = levels.numberAt(index + 1)
        val delta = if (before != null) now - before else if (after != null) after - now else return null
        return when {
            delta > 0.003 -> "들물 추정"
            delta < -0.003 -> "날물 추정"
            else -> "정조 부근"
        }
    }

    private fun turningPoints(
        times: JSONArray?,
        values: JSONArray?,
        now: LocalDateTime
    ): List<TideEvent> {
        if (times == null || values == null) return emptyList()
        val count = minOf(times.length(), values.length())
        val events = mutableListOf<TideEvent>()
        for (i in 1 until count - 1) {
            val previous = values.numberAt(i - 1) ?: continue
            val here = values.numberAt(i) ?: continue
            val next = values.numberAt(i + 1) ?: continue
            val time = parseDateTime(times.optString(i)) ?: continue
            if (time < now.minusMinutes(20)) continue
            val label = when {
                here > previous && here >= next -> "만조"
                here < previous && here <= next -> "간조"
                else -> continue
            }
            // Suppress plateau duplicate extrema.
            if (events.isNotEmpty() && Duration.between(events.last().time, time).toHours() < 3)
                continue
            events += TideEvent(label, time, here)
            if (events.size >= 3) break
        }
        return events
    }

    private fun nearestTimeIndex(array: JSONArray?, now: LocalDateTime): Int {
        if (array == null || array.length() == 0) return -1
        var bestIndex = -1
        var bestMinutes = Long.MAX_VALUE
        for (i in 0 until array.length()) {
            val date = parseDateTime(array.optString(i)) ?: continue
            val distance = abs(Duration.between(date, now).toMinutes())
            if (distance < bestMinutes) {
                bestMinutes = distance
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun parseDateTime(value: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(value) }.getOrNull()
}

private fun JSONObject.number(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val value = optDouble(key, Double.NaN)
    return value.takeIf { it.isFinite() }
}

private fun JSONArray.numberAt(index: Int): Double? {
    if (index < 0 || index >= length() || isNull(index)) return null
    val value = optDouble(index, Double.NaN)
    return value.takeIf { it.isFinite() }
}
