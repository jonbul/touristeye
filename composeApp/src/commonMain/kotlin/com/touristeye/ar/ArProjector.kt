package com.touristeye.ar

import com.touristeye.geo.GeoMath
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Field of view of the full camera image, as seen with the phone upright (portrait). */
data class CameraFov(val horizontalDegrees: Double, val verticalDegrees: Double) {
    /** The same camera as seen on a screen of this shape: in landscape the long side is horizontal. */
    fun forViewport(width: Double, height: Double): CameraFov =
        if (width > height) CameraFov(horizontalDegrees = verticalDegrees, verticalDegrees = horizontalDegrees) else this

    companion object {
        /** Typical phone main camera in portrait; used until the real value is known. */
        val Default = CameraFov(horizontalDegrees = 50.0, verticalDegrees = 64.0)
    }
}

data class ScreenPoint(val x: Double, val y: Double)

/**
 * Projects world directions onto the screen with a pinhole camera model.
 * The camera image fills the viewport (center-crop), so the field of view is reduced on the cropped axis.
 */
class ArProjector(
    fov: CameraFov,
    val width: Double,
    val height: Double,
) {
    val tanHalfH: Double
    val tanHalfV: Double

    init {
        val th = tan(GeoMath.toRad(fov.horizontalDegrees / 2))
        val tv = tan(GeoMath.toRad(fov.verticalDegrees / 2))
        val viewportAspect = width / height
        if (viewportAspect < th / tv) {
            // Viewport narrower than the image: full height shown, sides cropped.
            tanHalfV = tv
            tanHalfH = tv * viewportAspect
        } else {
            tanHalfH = th
            tanHalfV = th / viewportAspect
        }
    }

    /** Effective horizontal field of view of the visible viewport, degrees. */
    val visibleHorizontalFov: Double get() = 2 * GeoMath.toDeg(kotlin.math.atan(tanHalfH))

    /**
     * Returns where a direction (bearing/elevation in degrees) appears on screen,
     * or null when it is behind the camera. The point may lie outside the viewport.
     */
    fun project(bearing: Double, elevation: Double, orientation: DeviceOrientation): ScreenPoint? {
        val az = GeoMath.toRad(orientation.azimuth)
        val p = GeoMath.toRad(orientation.pitch)
        val b = GeoMath.toRad(bearing)
        val e = GeoMath.toRad(elevation)

        val d = Enu(sin(b) * cos(e), cos(b) * cos(e), sin(e))
        val forward = Enu(sin(az) * cos(p), cos(az) * cos(p), sin(p))
        val right = Enu(cos(az), -sin(az), 0.0)
        val up = Enu(-sin(az) * sin(p), -cos(az) * sin(p), cos(p))

        val z = d.dot(forward)
        if (z <= 1e-3) return null
        val cx = d.dot(right) / z
        val cy = d.dot(up) / z

        // Undo the device roll so points stay fixed to the world when the phone tilts sideways.
        val r = GeoMath.toRad(orientation.roll)
        val sx = cx * cos(r) - cy * sin(r)
        val sy = cx * sin(r) + cy * cos(r)

        return ScreenPoint(
            x = width / 2 + sx / tanHalfH * width / 2,
            y = height / 2 - sy / tanHalfV * height / 2,
        )
    }

    fun isOnScreen(p: ScreenPoint, margin: Double = 0.0) =
        p.x >= -margin && p.x <= width + margin && p.y >= -margin && p.y <= height + margin
}
