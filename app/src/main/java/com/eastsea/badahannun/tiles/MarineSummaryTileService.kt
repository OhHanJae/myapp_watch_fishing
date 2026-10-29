package com.eastsea.badahannun.tiles

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
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
 * The tile is a glanceable cached snapshot, NOT a constantly running service.
 * Background tile requests never start GPS or make HTTP calls; missing/stale data
 * are marked as such and a tap opens the app for foreground refresh.
 */
class MarineSummaryTileService : Material3TileService() {
    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val snapshot = TileSnapshotStore.read(this@MarineSummaryTileService)
        val launch = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build()
            ).build()
        val info = if (snapshot.fresh) {
            Column.Builder().setWidth(expand())
                .addContent(text("파고  ${snapshot.wave}".layoutString))
                .addContent(text("바람  ${snapshot.wind}".layoutString))
                .addContent(text("물때  ${snapshot.tide}".layoutString))
                .addContent(text("추천  ${snapshot.topFish}".layoutString))
                .build()
        } else {
            Column.Builder().setWidth(expand())
                .addContent(text("예보 갱신 필요".layoutString))
                .addContent(text("앱에서 현재 정보 확인".layoutString))
                .addContent(text("물때  ${snapshot.tide}".layoutString))
                .build()
        }
        return Tile.Builder()
            .setFreshnessIntervalMillis(15 * 60 * 1000L)
            .setTileTimeline(
                Timeline.fromLayoutElement(
                    primaryLayout(
                        titleSlot = { text(snapshot.pointName.layoutString) },
                        mainSlot = { info },
                        bottomSlot = {
                            textEdgeButton(
                                onClick = clickable(action = launch, id = "open_fishing")
                            ) { text("앱 열기".layoutString) }
                        }
                    )
                )
            ).build()
    }
}

/** One small shared cache so the app and the system tile stay in sync. */
internal object TileSnapshotStore {
    private const val PREF = "watch_tile_snapshot_v1"
    private const val MAX_AGE_MS = 45 * 60 * 1000L

    data class Snapshot(
        val pointName: String,
        val wave: String,
        val wind: String,
        val tide: String,
        val topFish: String,
        val fresh: Boolean
    )

    fun clear(context: Context, point: FishingPoint, gpsMode: Boolean, hasLocation: Boolean) {
        // Never show weather from a previously selected point while the new one loads.
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
            .putString("wave", show(report.ocean?.waveM, "m"))
            .putString("wind", show(report.weather?.windMs, "m/s"))
            .putString("fish", topFish.ifBlank { "--" })
            .putLong("time", report.fetchedAtMs)
            .putBoolean("stale", report.stale)
            .apply()
        requestUpdate(context)
    }

    fun read(context: Context, now: Long = System.currentTimeMillis()): Snapshot {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong("time", 0L)
        val fresh = timestamp > 0 && now >= timestamp &&
            now - timestamp <= MAX_AGE_MS && !prefs.getBoolean("stale", true)
        return Snapshot(
            pointName = prefs.getString("point", null) ?: "바다한눈",
            wave = prefs.getString("wave", null) ?: "--",
            wind = prefs.getString("wind", null) ?: "--",
            tide = TideCalculator.onDate(LocalDate.now(FishingLogic.KST)).name,
            topFish = prefs.getString("fish", null) ?: "--",
            fresh = fresh
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
        else String.format(Locale.KOREA, "%.1f %s", value, unit)

    private fun requestUpdate(context: Context) {
        runCatching {
            TileService.getUpdater(context).requestUpdate(MarineSummaryTileService::class.java)
        }
    }
}
