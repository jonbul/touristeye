package com.touristeye.geo

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A WGS84 position. [altitude] is metres above sea level when known. */
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracyMeters: Float? = null,
)

object GeoMath {
    const val EARTH_RADIUS_M = 6_371_008.8

    fun toRad(deg: Double) = deg * PI / 180.0
    fun toDeg(rad: Double) = rad * 180.0 / PI

    /** Great-circle distance in metres (haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = toRad(lat2 - lat1)
        val dLon = toRad(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(toRad(lat1)) * cos(toRad(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    fun distanceMeters(a: GeoLocation, b: GeoLocation) =
        distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    /** Initial great-circle bearing from point 1 to point 2, degrees clockwise from true north in [0, 360). */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = toRad(lat1)
        val phi2 = toRad(lat2)
        val dLon = toRad(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return normalize360(toDeg(atan2(y, x)))
    }

    /**
     * Angle above (positive) or below (negative) the horizon at which a target is seen,
     * including the drop caused by Earth's curvature and standard refraction (k = 0.13).
     */
    fun elevationAngleDegrees(observerAltitude: Double, targetAltitude: Double, distanceMeters: Double): Double {
        if (distanceMeters <= 1.0) return 0.0
        val effectiveRadius = EARTH_RADIUS_M / (1 - 0.13)
        val curvatureDrop = distanceMeters * distanceMeters / (2 * effectiveRadius)
        return toDeg(atan((targetAltitude - observerAltitude - curvatureDrop) / distanceMeters))
    }

    fun normalize360(deg: Double): Double {
        val r = deg % 360.0
        return if (r < 0) r + 360.0 else r
    }

    /** Signed smallest difference [target] - [reference] in (-180, 180]. */
    fun angleDelta(target: Double, reference: Double): Double {
        var d = normalize360(target - reference)
        if (d > 180.0) d -= 360.0
        return d
    }

    private val CARDINALS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    fun cardinal(bearing: Double): String = CARDINALS[((normalize360(bearing) + 22.5) / 45.0).toInt() % 8]

    fun formatDistance(meters: Double): String = when {
        meters < 1000 -> "${meters.toInt()} m"
        meters < 10_000 -> "${(meters / 100).toInt() / 10.0} km"
        else -> "${(meters / 1000).toInt()} km"
    }
}
