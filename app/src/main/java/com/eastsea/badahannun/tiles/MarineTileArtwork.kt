package com.eastsea.badahannun.tiles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.wear.protolayout.ResourceBuilders
import com.eastsea.badahannun.domain.AppColorMode
import com.eastsea.badahannun.domain.MarineWaveGeometry
import com.eastsea.badahannun.domain.WatchVisualMath
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.sin

/**
 * A visible, single-frame tile background rendered from cached forecast values.
 *
 * Unlike foreground Compose Canvas, Wear tiles cannot rely on continuously running
 * app-side drawing/sensor callbacks. A Material3 Tile must register its own images.
 * The PNG is an inline ImageResource registered by Material3TileService's scope.
 *
 * Colors and stroke thickness deliberately survive small 40-mm round displays.
 * No animation, fabricated wind direction, or fabricated wave height is implied.
 */
internal object MarineTileArtwork {
    private const val SIZE = 360
    private const val C = 180f
    private const val R = 164f

    /** Background theme is already resolved from app + tile preferences. */
    fun create(
        snapshot: TileSnapshotStore.Snapshot,
        theme: AppColorMode
    ): ResourceBuilders.ImageResource {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream(48_000)
        try {
            draw(Canvas(bitmap), snapshot, palette(theme))
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Failed to encode tile background PNG"
            }
        } finally {
            bitmap.recycle()
        }

        val image = ResourceBuilders.InlineImageResource.Builder()
            .setData(output.toByteArray())
            .setWidthPx(SIZE)
            .setHeightPx(SIZE)
            .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED) // PNG; decoder reads its header
            .build()
        return ResourceBuilders.ImageResource.Builder()
            .setInlineResource(image)
            .build()
    }

    private data class Palette(
        val backgroundTop: Int,
        val backgroundBottom: Int,
        val compassRing: Int,
        val compassTick: Int,
        val compassNorth: Int,
        val compassLetter: Int,
        val wind: Int,
        val waveRising: Int,
        val waveFalling: Int
    )

    private fun palette(theme: AppColorMode): Palette =
        if (theme == AppColorMode.LIGHT) Palette(
            backgroundTop = Color.rgb(247, 251, 254),
            backgroundBottom = Color.rgb(219, 237, 245),
            compassRing = Color.rgb(112, 159, 177),
            compassTick = Color.rgb(94, 133, 154),
            compassNorth = Color.rgb(0, 122, 124),
            compassLetter = Color.rgb(65, 104, 124),
            wind = Color.rgb(0, 113, 177),
            waveRising = Color.rgb(0, 138, 124),
            waveFalling = Color.rgb(32, 122, 183)
        ) else Palette(
            backgroundTop = Color.rgb(5, 19, 32),
            backgroundBottom = Color.rgb(7, 40, 57),
            compassRing = Color.rgb(63, 126, 153),
            compassTick = Color.rgb(148, 196, 215),
            compassNorth = Color.rgb(75, 238, 216),
            compassLetter = Color.rgb(167, 211, 224),
            wind = Color.rgb(113, 220, 255),
            waveRising = Color.rgb(68, 234, 209),
            waveFalling = Color.rgb(91, 173, 254)
        )

    private fun draw(canvas: Canvas, snap: TileSnapshotStore.Snapshot, palette: Palette) {
        val brush = Paint(Paint.ANTI_ALIAS_FLAG)
        // Opaque ocean background: no dependence on a renderer's tile background color.
        brush.shader = LinearGradient(0f, 0f, 0f, SIZE.toFloat(),
            palette.backgroundTop, palette.backgroundBottom, Shader.TileMode.CLAMP)
        canvas.drawColor(palette.backgroundTop)
        canvas.drawCircle(C, C, 178f, brush)
        brush.shader = null

        // Fixed cardinal ring: N=top, E=right, S=bottom, W=left.
        // Never rotate the tile compass using a stale watch sensor heading.
        drawCompass(canvas, brush, palette)

        // Wind and waves are DRAWN EVEN IF old, but dimmed. No model value means
        // a neutral decorative symbol and a "--" numerical text in the tile.
        drawWind(canvas, brush, snap, palette)
        drawWaves(canvas, brush, snap, palette)

        // Deliberately NO rounded rectangle behind text.
        // The background itself is theme-aware and remains continuous.

    }

    private fun drawCompass(canvas: Canvas, brush: Paint, palette: Palette) {
        brush.style = Paint.Style.STROKE
        brush.strokeWidth = 3f
        brush.color = palette.compassRing
        canvas.drawCircle(C, C, R - 5f, brush)

        for (index in 0 until 36) {
            val angle = radians(index * 10f)
            val major = index % 9 == 0
            val inner = if (major) R - 28f else R - 15f
            val outer = R - 4f
            brush.strokeWidth = if (major) 4.2f else 1.8f
            brush.color = if (index == 0) palette.compassNorth
                else Color.argb(if (major) 220 else 130,
                    Color.red(palette.compassTick),
                    Color.green(palette.compassTick),
                    Color.blue(palette.compassTick))
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            canvas.drawLine(C + dx * inner, C + dy * inner,
                C + dx * outer, C + dy * outer, brush)
        }

        brush.style = Paint.Style.FILL
        brush.textSize = 19f
        brush.textAlign = Paint.Align.CENTER
        brush.isFakeBoldText = true
        for ((label, bearing) in listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)) {
            val angle = radians(bearing)
            val x = C + cos(angle).toFloat() * (R - 48f)
            val y = C + sin(angle).toFloat() * (R - 48f) + 7f
            // Only show compass letters near the outer ring: center text overlays them
            // when they are behind the dashboard, but the north tick stays visible.
            brush.color = if (label == "N") palette.compassNorth
                else palette.compassLetter
            canvas.drawText(label, x, y, brush)
        }
        brush.isFakeBoldText = false
    }

    private fun drawWind(
        canvas: Canvas, brush: Paint, snap: TileSnapshotStore.Snapshot, palette: Palette
    ) {
        // Relative to TRUE north-up screen, not the last captured sensor direction.
        val from = WatchVisualMath.relativeWind(snap.windDegrees?.toDouble(), null)
        val freshOpacity = if (snap.fresh) 240 else 125
        val speed = snap.windSpeedMs?.coerceIn(0.0, 20.0)?.toFloat()
        if (from == null) {
            // No wind model: show a neutral, subdued streak - not a direction arrow.
            brush.color = Color.argb(90, Color.red(palette.wind),
                Color.green(palette.wind), Color.blue(palette.wind))
            brush.style = Paint.Style.STROKE
            brush.strokeWidth = 3f
            canvas.drawArc(RectF(30f, 35f, 327f, 330f), 298f, 35f, false, brush)
            brush.style = Paint.Style.FILL
            return
        }

        // A real directional arrow, showing where the reported wind comes FROM.
        val angle = radians(from)
        val ox = cos(angle).toFloat()
        val oy = sin(angle).toFloat()
        val tx = -ox
        val ty = -oy
        val px = -ty
        val py = tx
        brush.color = Color.argb(freshOpacity, Color.red(palette.wind),
            Color.green(palette.wind), Color.blue(palette.wind))
        brush.strokeWidth = 4.8f
        brush.strokeCap = Paint.Cap.ROUND
        brush.style = Paint.Style.STROKE
        val originX = C + ox * 147f
        val originY = C + oy * 147f
        canvas.drawLine(originX, originY, originX + tx * 32f,
            originY + ty * 32f, brush)
        val arrowTipX = originX + tx * 33f
        val arrowTipY = originY + ty * 33f
        canvas.drawLine(arrowTipX, arrowTipY,
            arrowTipX - tx * 11f + px * 7f,
            arrowTipY - ty * 11f + py * 7f, brush)
        canvas.drawLine(arrowTipX, arrowTipY,
            arrowTipX - tx * 11f - px * 7f,
            arrowTipY - ty * 11f - py * 7f, brush)

        // Five thick visible streaks. More wind => longer strokes.
        val length = 23f + (speed ?: 0f) * 2.5f
        for (i in -2..2) {
            val side = i * 12f
            val bx = C + ox * 135f + px * side
            val by = C + oy * 135f + py * side
            brush.color = Color.argb((freshOpacity * (1f - kotlin.math.abs(i) * 0.14f)).toInt(),
                Color.red(palette.wind), Color.green(palette.wind), Color.blue(palette.wind))
            brush.strokeWidth = if (i == 0) 4f else 2.6f
            canvas.drawLine(bx + tx * 11f, by + ty * 11f,
                bx + tx * (11f + length), by + ty * (11f + length), brush)
        }
        brush.style = Paint.Style.FILL
    }

    /** One forecast-height-driven line from left ring edge to right ring edge. */
    private fun drawWaves(
        canvas: Canvas, brush: Paint, snap: TileSnapshotStore.Snapshot, palette: Palette
    ) {
        val height = snap.waveHeightM?.takeIf(Double::isFinite)
        val rising = snap.tidePhase.contains("들물")
        val color = if (rising) palette.waveRising else palette.waveFalling
        val opacity = if (height == null) 90 else if (snap.fresh) 238 else 150
        val points = MarineWaveGeometry.sample(
            centerX = C, centerY = C, radius = R - 5f, waveHeightM = height
        )
        val wavePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { point -> lineTo(point.x, point.y) }
        }

        brush.style = Paint.Style.STROKE
        brush.strokeCap = Paint.Cap.ROUND
        brush.strokeJoin = Paint.Join.ROUND
        brush.strokeWidth = 3.7f + (height ?: 0.0).coerceIn(0.0, 3.5).toFloat() * 0.8f
        brush.color = Color.argb(opacity, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawPath(wavePath, brush)
        brush.style = Paint.Style.FILL
    }

    private fun radians(bearingClockwiseFromNorth: Float): Double =
        Math.toRadians((bearingClockwiseFromNorth - 90f).toDouble())
}
