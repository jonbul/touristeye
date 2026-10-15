package com.touristeye.ar

import com.touristeye.geo.GeoMath
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A 3-D vector in the local East-North-Up frame. */
data class Enu(val e: Double, val n: Double, val u: Double) {
    fun dot(o: Enu) = e * o.e + n * o.n + u * o.u
    fun norm() = sqrt(dot(this))
    fun normalized(): Enu = norm().let { if (it == 0.0) this else Enu(e / it, n / it, u / it) }
    operator fun plus(o: Enu) = Enu(e + o.e, n + o.n, u + o.u)
    operator fun times(s: Double) = Enu(e * s, n * s, u * s)
}

/**
 * Where the back camera points.
 * [azimuth]: degrees clockwise from true north. [pitch]: degrees above the horizon.
 * [roll]: degrees the screen is rotated clockwise around the camera axis (0 = upright portrait).
 */
data class DeviceOrientation(
    val azimuth: Double,
    val pitch: Double,
    val roll: Double,
    /** False when the platform reports that the compass needs calibration. */
    val reliable: Boolean = true,
)

object OrientationMath {
    /**
     * Builds the camera orientation from two device axes expressed in world ENU coordinates:
     * [cameraForward] is the direction the back camera looks (the device's -Z axis) and
     * [deviceRight] is the device's +X axis (the right edge of the screen in portrait).
     */
    fun fromVectors(cameraForward: Enu, deviceRight: Enu, reliable: Boolean = true): DeviceOrientation {
        val f = cameraForward.normalized()
        val azimuth = GeoMath.normalize360(GeoMath.toDeg(atan2(f.e, f.n)))
        val pitch = GeoMath.toDeg(asin(f.u.coerceIn(-1.0, 1.0)))
        val az = GeoMath.toRad(azimuth)
        val p = GeoMath.toRad(pitch)
        // Camera basis with no roll: right is horizontal, up is perpendicular to forward.
        val right = Enu(cos(az), -sin(az), 0.0)
        val up = Enu(-sin(az) * sin(p), -cos(az) * sin(p), cos(p))
        val x = deviceRight.normalized()
        // Positive when the device's right edge has been lifted above the horizon (counter-clockwise turn).
        val roll = -GeoMath.toDeg(atan2(x.dot(up), x.dot(right)))
        return DeviceOrientation(azimuth, pitch, roll, reliable)
    }
}

/**
 * Low-pass filter for orientation. Filters the axis vectors, not the angles, so it never
 * jumps when the heading wraps from 359° to 0°.
 */
class OrientationSmoother(private val alpha: Double = 0.15) {
    private var forward: Enu? = null
    private var right: Enu? = null

    fun update(cameraForward: Enu, deviceRight: Enu, reliable: Boolean = true): DeviceOrientation {
        val f = forward?.let { (it * (1 - alpha) + cameraForward.normalized() * alpha).normalized() }
            ?: cameraForward.normalized()
        val r = right?.let { (it * (1 - alpha) + deviceRight.normalized() * alpha).normalized() }
            ?: deviceRight.normalized()
        forward = f
        right = r
        return OrientationMath.fromVectors(f, r, reliable)
    }

    fun reset() {
        forward = null
        right = null
    }
}
