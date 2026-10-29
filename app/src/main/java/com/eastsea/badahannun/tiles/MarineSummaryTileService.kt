package com.eastsea.badahannun.tiles

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Arc
import androidx.wear.protolayout.LayoutElementBuilders.ArcLine
import androidx.wear.protolayout.LayoutElementBuilders.ArcText
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.text
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
import kotlin.math.abs

/**
 * Watch tile = cached sea summary with a perimeter compass dial.
 * Tiles cannot keep compass sensors active; the dial uses the last compass reading
 * from the app (expires after two minutes). Tap the tile for the LIVE compass.
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
        // Compass ring occupies the outer, bezel-side arc without using centre space.
        rim.addContent(
            Arc.Builder().setAnchorAngle(degrees(0f))
                .addContent(
                    ArcLine.Builder().setLength(degrees(358f)).setThickness(dp(2f))
                        .setColor(argb(0xFF315262.toInt())).build()
                ).build()
        )
        val savedHeading = snap.headingDegrees?.takeIf { snap.headingRecent } ?: 0f
        for ((label, angle) in listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)) {
            val bearing = (angle - savedHeading + 360f) % 360f
            rim.addContent(
                Arc.Builder().setAnchorAngle(degrees(bearing))
                    .addContent(ArcLine.Builder().setLength(degrees(5f))
                        .setColor(argb(if (label == "N") 0xFF63DFD4.toInt() else 0xFF7296AA.toInt()))
                        .setThickness(dp(3f)).build())
                    .addContent(ArcText.Builder().setText(label)
                        .setFontStyle(FontStyle.Builder()
                            .setColor(argb(if (label == "N") 0xFF63DFD4.toInt() else 0xFFA1B6C5.toInt()))
                            .build())
                        .build())
                    .build()
            )
        }
        // Compact middle: one-line facts, not the mobile dashboard.
        val body = Column.Builder().setWidth(dp(151f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        // One line per item: the round 40mm display must not wrap dashboard text.
        body.addContent(text(snap.pointName.take(12).layoutString,
            typography = Typography.LABEL_MEDIUM, maxLines = 1))
        if (snap.fresh) {
            body.addContent(text("${snap.weather} ${snap.temperature}".layoutString,
                typography = Typography.LABEL_SMALL, maxLines = 1))
            body.addContent(text("수온 ${snap.seaTemp} · 파고 ${snap.wave}".layoutString,
                typography = Typography.LABEL_SMALL, maxLines = 1))
            body.addContent(text("${snap.windDir} ${snap.wind}".layoutString,
                typography = Typography.LABEL_SMALL, maxLines = 1))
            body.addContent(text("${snap.tide}* · ${snap.tidePhase}".layoutString,
                typography = Typography.LABEL_SMALL, maxLines = 1))
            body.addContent(text("추천 ${snap.topFish}".layoutString,
                typography = Typography.LABEL_SMALL, maxLines = 1))
        } else {
            body.addContent(text("예보 갱신 필요".layoutString,
                typography = Typography.LABEL_SMALL, maxLines = 1))
            body.addContent(text("앱을 열어 새로고침".layoutString,
                typography = Typography.LABEL_SMALL, maxLines = 1))
            body.addContent(text("${snap.tide}* (간이)".layoutString,
                typography = Typography.LABEL_SMALL, maxLines = 1))
        }
        body.addContent(text((if (snap.headingRecent) "최근 ${snap.headingDegrees?.toInt()}°" else "방위: 앱에서 실시간").layoutString,
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
    private const val MAX_HEADING_AGE_MS = 2 * 60 * 1000L

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
        val headingRecent: Boolean
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
            .putString("phase", report.ocean?.tideFlow ?: "--")
            .putString("fish", topFish.ifBlank { "--" })
            .putLong("time", report.fetchedAtMs)
            .putBoolean("stale", report.stale)
            .apply()
        requestUpdate(context)
    }

    /** Save no more often than every 20s; a tile compass is a *last sample*, never live. */
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
            headingRecent = recent
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
