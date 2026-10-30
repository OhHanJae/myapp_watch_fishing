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

    fun create(snapshot: TileSnapshotStore.Snapshot): ResourceBuilders.ImageResource {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream(48_000)
        try {
            draw(Canvas(bitmap), snapshot)
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

    private fun draw(canvas: Canvas, snap: TileSnapshotStore.Snapshot) {
        val brush = Paint(Paint.ANTI_ALIAS_FLAG)
        // Opaque ocean background: no dependence on a renderer's tile background color.
        brush.shader = LinearGradient(0f, 0f, 0f, SIZE.toFloat(),
            Color.rgb(5, 19, 32), Color.rgb(7, 40, 57), Shader.TileMode.CLAMP)
        canvas.drawColor(Color.rgb(5, 19, 32))
        canvas.drawCircle(C, C, 178f, brush)
        brush.shader = null

        // Actual heading is only valid when recently cached from the visible app.
        // Otherwise the compass is north-up with a clearly labeled fallback.
        val heading = snap.headingDegrees?.takeIf { snap.headingRecent } ?: 0f
        drawCompass(canvas, brush, heading, snap.headingRecent)

        // Wind and waves are DRAWN EVEN IF old, but dimmed. No model value means
        // a neutral decorative symbol and a "--" numerical text in the tile.
        drawWind(canvas, brush, snap, heading)
        drawWaves(canvas, brush, snap)

        // Dark translucent center ensures white text remains legible over graphics.
        brush.style = Paint.Style.FILL
        brush.color = Color.argb(198, 5, 18, 30)
        canvas.drawRoundRect(RectF(90f, 74f, 270f, 272f), 38f, 38f, brush)
        brush.color = Color.argb(148, 62, 135, 155)
        brush.style = Paint.Style.STROKE
        brush.strokeWidth = 1.4f
        canvas.drawRoundRect(RectF(90f, 74f, 270f, 272f), 38f, 38f, brush)
        brush.style = Paint.Style.FILL
    }

    private fun drawCompass(
        canvas: Canvas, brush: Paint, heading: Float, hasHeading: Boolean
    ) {
        brush.style = Paint.Style.STROKE
        brush.strokeWidth = 3f
        brush.color = Color.rgb(63, 126, 153)
        canvas.drawCircle(C, C, R - 5f, brush)

        for (index in 0 until 36) {
            val angle = radians(index * 10f - heading)
            val major = index % 9 == 0
            val inner = if (major) R - 28f else R - 15f
            val outer = R - 4f
            brush.strokeWidth = if (major) 4.2f else 1.8f
            brush.color = if (index == 0) Color.rgb(75, 238, 216)
                else Color.argb(majorAlpha(hasHeading, major), 148, 196, 215)
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
            val angle = radians(bearing - heading)
            val x = C + cos(angle).toFloat() * (R - 48f)
            val y = C + sin(angle).toFloat() * (R - 48f) + 7f
            // Only show compass letters near the outer ring: center text overlays them
            // when they are behind the dashboard, but the north tick stays visible.
            brush.color = if (label == "N") Color.rgb(75, 238, 216)
                else Color.argb(185, 167, 211, 224)
            canvas.drawText(label, x, y, brush)
        }
        brush.isFakeBoldText = false
    }

    private fun drawWind(
        canvas: Canvas, brush: Paint, snap: TileSnapshotStore.Snapshot, heading: Float
    ) {
        val from = snap.windDegrees?.toDouble()
            ?.let { WatchVisualMath.relativeWind(it, heading) }
        val freshOpacity = if (snap.fresh) 240 else 125
        val speed = snap.windSpeedMs?.coerceIn(0.0, 20.0)?.toFloat()
        if (from == null) {
            // No wind model: show a neutral, subdued streak - not a direction arrow.
            brush.color = Color.argb(90, 113, 175, 197)
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
        brush.color = Color.argb(freshOpacity, 113, 220, 255)
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
                82, 193, 235)
            brush.strokeWidth = if (i == 0) 4f else 2.6f
            canvas.drawLine(bx + tx * 11f, by + ty * 11f,
                bx + tx * (11f + length), by + ty * (11f + length), brush)
        }
        brush.style = Paint.Style.FILL
    }

    private fun drawWaves(canvas: Canvas, brush: Paint, snap: TileSnapshotStore.Snapshot) {
        val hasWave = snap.waveHeightM != null
        val height = snap.waveHeightM?.coerceIn(0.0, 3.5)?.toFloat() ?: 0f
        val rising = snap.tidePhase.contains("들물")
        val baseAlpha = if (!hasWave) 84 else if (snap.fresh) 230 else 120
        val amplitude = if (!hasWave) 4.5f else 6f + height * 11f
        val tint = if (rising) intArrayOf(68, 234, 209) else intArrayOf(91, 173, 254)

        // Three full-width lower arcs: placed outside the center reading panel.
        // Wave height determines visible amplitude and stroke thickness.
        for (row in 0..2) {
            val yBase = 288f + row * 18f
            val path = Path()
            for (sample in 0..64) {
                val x = 23f + sample * (314f / 64f)
                val phase = sample / 64f * Math.PI * 4.0 + row * 0.85
                val y = yBase + (sin(phase).toFloat() * amplitude)
                if (sample == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            brush.style = Paint.Style.STROKE
            brush.strokeCap = Paint.Cap.ROUND
            brush.strokeJoin = Paint.Join.ROUND
            brush.strokeWidth = 3.2f + height * 0.8f
            brush.color = Color.argb((baseAlpha - row * 37).coerceAtLeast(50),
                tint[0], tint[1], tint[2])
            canvas.drawPath(path, brush)
        }
        brush.style = Paint.Style.FILL
    }

    private fun majorAlpha(headingKnown: Boolean, major: Boolean): Int =
        if (!headingKnown) 110 else if (major) 225 else 122

    private fun radians(bearingClockwiseFromNorth: Float): Double =
        Math.toRadians((bearingClockwiseFromNorth - 90f).toDouble())
}
