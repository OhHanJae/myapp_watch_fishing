package com.eastsea.badahannun.tiles

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.backgroundImage
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.eastsea.badahannun.MainActivity
import com.eastsea.badahannun.data.FishingPoint
import com.eastsea.badahannun.data.SeaReport
import com.eastsea.badahannun.domain.FishingLogic
import com.eastsea.badahannun.domain.TideCalculator
import java.time.LocalDate
import java.util.Locale

/**
 * Wear Tile: full-size data-driven PNG background showing actual wind bearing
 * and clearly visible wave traces; unlike tiny overlapping ArcLine layers,
 * the image is registered with Material3TileService and rendered as a resource.
 * This is a static forecast snapshot, not a continuous wind or wave animation.
 * The foreground app remains the only place with continuously tracked sensors.
 */
class MarineSummaryTileService : Material3TileService() {
    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val snap = TileSnapshotStore.read(this@MarineSummaryTileService)
        val launch = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build()
            ).build()
        val click = ModifiersBuilders.Clickable.Builder()
            .setId("open_fishing_live")
            .setOnClick(launch)
            .build()
        val rim = Box.Builder().setWidth(expand()).setHeight(expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(click).build())
        // Image resource is embedded via Material3TileService's ProtoLayoutScope.
        // 210dp fits the Galaxy Watch9 40mm circle; readable at a glance.
        // Unlike the old thin ArcLine, the lower waves and directional streaks
        // are painted directly into a full-color PNG (with a visible baseline).
        rim.addContent(
            backgroundImage(
                resource = MarineTileArtwork.create(snap),
                width = dp(210f),
                height = dp(210f),
                overlayColor = null
            )
        )
        // Compact middle: one-line facts, not the mobile dashboard.
        val body = Column.Builder().setWidth(dp(151f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        // One line per item: the round 40mm display must not wrap dashboard text.
        body.addContent(text(snap.pointName.take(12).layoutString,
            typography = Typography.LABEL_MEDIUM, maxLines = 1))
        if (!snap.fresh) {
            body.addContent(text("이전/미수신 예보 · 앱 갱신".layoutString,
                typography = Typography.LABEL_SMALL, maxLines = 1))
        } else {
            body.addContent(text("${snap.weather} ${snap.temperature}".layoutString,
                typography = Typography.LABEL_SMALL, maxLines = 1))
        }
        // Keep the wind/wave legend visible even with stale/missing forecasts.
        // '--' stays '--': never present missing measurements as zero.
        body.addContent(text("풍 ${snap.windDir} ${snap.wind}".layoutString,
            typography = Typography.LABEL_SMALL, maxLines = 1))
        body.addContent(text("파고 ${snap.wave} · 수온 ${snap.seaTemp}".layoutString,
            typography = Typography.LABEL_SMALL, maxLines = 1))
        body.addContent(text("${snap.tide}* · ${snap.tidePhase}".layoutString,
            typography = Typography.LABEL_SMALL, maxLines = 1))
        body.addContent(text("추천 ${snap.topFish}".layoutString,
            typography = Typography.LABEL_SMALL, maxLines = 1))
        body.addContent(text((if (snap.headingRecent) "최근 방위 ${snap.headingDegrees?.toInt()}°" else "북 기준 · 앱에서 갱신").layoutString,
            typography = Typography.LABEL_SMALL, maxLines = 1))
        rim.addContent(body.build())
        return Tile.Builder()
            .setFreshnessIntervalMillis(15 * 60 * 1000L)
            .setTileTimeline(Timeline.fromLayoutElement(rim.build()))
            .build()
    }
}

/** Last known tile values from the foreground app. Never does GPS or HTTP in a tile request. */
internal object TileSnapshotStore {
    private const val PREF = "watch_tile_snapshot_v2"
    private const val MAX_AGE_MS = 45 * 60 * 1000L
    private const val MAX_HEADING_AGE_MS = 10 * 60 * 1000L

    data class Snapshot(
        val pointName: String,
        val weather: String,
        val temperature: String,
        val seaTemp: String,
        val wind: String,
        val windDir: String,
        val wave: String,
        val tide: String,
        val tidePhase: String,
        val topFish: String,
        val fresh: Boolean,
        val headingDegrees: Float?,
        val headingRecent: Boolean,
        val windDegrees: Float?,
        val windSpeedMs: Double?,
        val waveHeightM: Double?
    )

    fun clear(context: Context, point: FishingPoint, gpsMode: Boolean, hasLocation: Boolean) {
        val label = label(point, gpsMode, hasLocation)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear()
            .putString("point", label).apply()
        requestUpdate(context)
    }

    fun write(
        context: Context,
        point: FishingPoint,
        gpsMode: Boolean,
        hasLocation: Boolean,
        report: SeaReport,
        topFish: String
    ) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("point", label(point, gpsMode, hasLocation))
            .putString("weather", FishingLogic.weatherLabel(report.weather?.weatherCode))
            .putString("temp", show(report.weather?.temperatureC, "°"))
            .putString("sst", show(report.ocean?.surfaceTemperatureC, "°"))
            .putString("wave", show(report.ocean?.waveM, "m"))
            .putString("wind", show(report.weather?.windMs, "m/s"))
            .putString("windDir", FishingLogic.windDirection(report.weather?.windDirectionDegrees))
            .putFloat("windDegrees", report.weather?.windDirectionDegrees?.toFloat() ?: Float.NaN)
            .putFloat("windSpeed", report.weather?.windMs?.toFloat() ?: Float.NaN)
            .putFloat("waveHeight", report.ocean?.waveM?.toFloat() ?: Float.NaN)
            .putString("phase", report.ocean?.tideFlow ?: "--")
            .putString("fish", topFish.ifBlank { "--" })
            .putLong("time", report.fetchedAtMs)
            .putBoolean("stale", report.stale)
            .apply()
        requestUpdate(context)
    }

    /** Save at most every 20s while the Home app is visible; tile reading is a snapshot. */
    fun saveHeading(context: Context, heading: Float) {
        if (!heading.isFinite()) return
        val pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = pref.getLong("headingTime", 0L)
        if (now - last < 20_000) return
        pref.edit().putFloat("heading", (heading % 360f + 360f) % 360f)
            .putLong("headingTime", now).apply()
        requestUpdate(context)
    }

    fun read(context: Context, now: Long = System.currentTimeMillis()): Snapshot {
        val pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val timestamp = pref.getLong("time", 0L)
        val headingTime = pref.getLong("headingTime", 0L)
        val recent = headingTime > 0 && now >= headingTime && now - headingTime <= MAX_HEADING_AGE_MS
        return Snapshot(
            pointName = pref.getString("point", null) ?: "바다한눈",
            weather = pref.getString("weather", null) ?: "--",
            temperature = pref.getString("temp", null) ?: "--",
            seaTemp = pref.getString("sst", null) ?: "--",
            wave = pref.getString("wave", null) ?: "--",
            wind = pref.getString("wind", null) ?: "--",
            windDir = pref.getString("windDir", null) ?: "--",
            tide = TideCalculator.onDate(LocalDate.now(FishingLogic.KST)).name,
            tidePhase = pref.getString("phase", null) ?: "--",
            topFish = pref.getString("fish", null) ?: "--",
            fresh = timestamp > 0 && now >= timestamp && now - timestamp <= MAX_AGE_MS &&
                !pref.getBoolean("stale", true),
            headingDegrees = pref.getFloat("heading", 0f).takeIf { headingTime > 0 },
            headingRecent = recent,
            windDegrees = pref.getFloat("windDegrees", Float.NaN).takeIf { it.isFinite() },
            windSpeedMs = pref.getFloat("windSpeed", Float.NaN)
                .takeIf { it.isFinite() }?.toDouble(),
            waveHeightM = pref.getFloat("waveHeight", Float.NaN)
                .takeIf { it.isFinite() }?.toDouble()
        )
    }

    private fun label(point: FishingPoint, gpsMode: Boolean, hasLocation: Boolean): String =
        when {
            gpsMode && hasLocation -> "내 위치 · ${point.name}"
            gpsMode -> "GPS 대기 · ${point.name}"
            else -> point.name
        }

    private fun show(value: Double?, unit: String): String =
        if (value == null || !value.isFinite()) "--"
        else String.format(Locale.KOREA, "%.1f%s", value, unit)

    private fun requestUpdate(context: Context) {
        runCatching {
            TileService.getUpdater(context).requestUpdate(MarineSummaryTileService::class.java)
        }
    }
}
