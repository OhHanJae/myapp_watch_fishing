package com.eastsea.badahannun.domain

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.roundToInt

/**
 * Registers only while the compass page is visible. Output is magnetic
 * azimuth, clockwise from north, before geomagnetic declination correction.
 */
class CompassTracker(
    context: Context,
    private val onHeading: (Float?) -> Unit
) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotation = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private var started = false
    private val gravity = FloatArray(3)
    private val magnetic = FloatArray(3)
    private var haveGravity = false
    private var haveMagnetic = false
    private var lastHeading = -1f

    fun start(): Boolean {
        if (started) return true
        val valid = if (rotation != null) {
            manager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI)
        } else {
            val a = accelerometer?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) } ?: false
            val m = magnetometer?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) } ?: false
            a && m
        }
        if (!valid) {
            manager.unregisterListener(this)
            onHeading(null)
        }
        started = valid
        return valid
    }

    fun stop() {
        if (started) manager.unregisterListener(this)
        started = false
        lastHeading = -1f
    }

    override fun onSensorChanged(event: SensorEvent) {
        val rotationMatrix = FloatArray(9)
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3)
                haveGravity = true
                if (!haveMagnetic) return
                if (!SensorManager.getRotationMatrix(rotationMatrix, null, gravity, magnetic)) return
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetic, 0, 3)
                haveMagnetic = true
                if (!haveGravity) return
                if (!SensorManager.getRotationMatrix(rotationMatrix, null, gravity, magnetic)) return
            }
            else -> return
        }
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val azimuth = ((Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f)
        val delta = kotlin.math.abs(azimuth - lastHeading)
        // Debounce sensor noise, but permit immediate wraparound near north.
        if (lastHeading < 0 || minOf(delta, 360f - delta) >= 1f) {
            lastHeading = azimuth
            onHeading(azimuth)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE &&
            (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD ||
                sensor?.type == Sensor.TYPE_ROTATION_VECTOR)) {
            onHeading(null)
        }
    }
}
