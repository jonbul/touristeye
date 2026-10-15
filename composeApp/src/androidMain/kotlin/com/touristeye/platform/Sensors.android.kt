package com.touristeye.platform

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.touristeye.ar.DeviceOrientation
import com.touristeye.ar.Enu
import com.touristeye.ar.OrientationSmoother
import com.touristeye.geo.GeoLocation
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
actual fun rememberDeviceOrientation(location: GeoLocation?): State<DeviceOrientation?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<DeviceOrientation?>(null) }
    val currentLocation by rememberUpdatedState(location)

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        // Used only when there is no rotation-vector sensor: the gravity sensor (accelerometer
        // cleaned up with the gyroscope) if present, otherwise the raw accelerometer, low-pass filtered.
        val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val smoother = OrientationSmoother()
        val r = FloatArray(9)
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false
        var reliable = true
        var declinationRad = 0.0
        var declinationFor: GeoLocation? = null

        fun emit() {
            val loc = currentLocation
            if (loc != null && loc != declinationFor) {
                val field = GeomagneticField(
                    loc.latitude.toFloat(),
                    loc.longitude.toFloat(),
                    (loc.altitude ?: 0.0).toFloat(),
                    System.currentTimeMillis(),
                )
                declinationRad = Math.toRadians(field.declination.toDouble())
                declinationFor = loc
            }
            // R maps device axes to the world (X east, Y magnetic north, Z up); its columns are the device axes.
            val forward = Enu(-r[2].toDouble(), -r[5].toDouble(), -r[8].toDouble()).toTrueNorth(declinationRad)
            val right = Enu(r[0].toDouble(), r[3].toDouble(), r[6].toDouble()).toTrueNorth(declinationRad)
            state.value = smoother.update(forward, right, reliable)
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(r, event.values)
                        emit()
                    }
                    Sensor.TYPE_GRAVITY -> {
                        event.values.copyInto(gravity, endIndex = 3)
                        hasGravity = true
                        if (hasGeomagnetic && SensorManager.getRotationMatrix(r, null, gravity, geomagnetic)) emit()
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        // Removes hand shake and walking bumps so only gravity remains.
                        if (!hasGravity) {
                            event.values.copyInto(gravity, endIndex = 3)
                        } else {
                            for (i in 0..2) gravity[i] += 0.2f * (event.values[i] - gravity[i])
                        }
                        hasGravity = true
                        if (hasGeomagnetic && SensorManager.getRotationMatrix(r, null, gravity, geomagnetic)) emit()
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        event.values.copyInto(geomagnetic, endIndex = 3)
                        hasGeomagnetic = true
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    reliable = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                }
            }
        }

        // The rotation vector is Android's fusion of accelerometer, gyroscope and magnetometer: the most precise source.
        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        } else if (gravitySensor != null) {
            sensorManager.registerListener(listener, gravitySensor, SensorManager.SENSOR_DELAY_GAME)
        }
        // Always listened to: provides calibration accuracy, and the heading when there is no rotation vector.
        magnetometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }

        onDispose { sensorManager.unregisterListener(listener) }
    }
    return state
}

/** Rotates a magnetic-north ENU vector clockwise (seen from above) by the declination. */
private fun Enu.toTrueNorth(declinationRad: Double): Enu {
    val c = cos(declinationRad)
    val s = sin(declinationRad)
    return Enu(e * c + n * s, -e * s + n * c, u)
}

actual fun platformLanguage(): String = Locale.getDefault().language.ifBlank { "en" }
