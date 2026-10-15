package com.touristeye

import com.touristeye.geo.GeoLocation
import com.touristeye.geo.GeoMath
import com.touristeye.geo.Poi
import com.touristeye.geo.PoiCategory
import com.touristeye.geo.placedFrom
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoMathTest {
    @Test
    fun distanceParisLondon() {
        val d = GeoMath.distanceMeters(48.8566, 2.3522, 51.5074, -0.1278)
        assertTrue(abs(d - 343_500) < 1_500, "was $d")
    }

    @Test
    fun bearingsToCardinalDirections() {
        assertEquals(0.0, GeoMath.bearingDegrees(0.0, 0.0, 1.0, 0.0), 1e-6)
        assertEquals(90.0, GeoMath.bearingDegrees(0.0, 0.0, 0.0, 1.0), 1e-6)
        assertEquals(180.0, GeoMath.bearingDegrees(1.0, 0.0, 0.0, 0.0), 1e-6)
        assertEquals(270.0, GeoMath.bearingDegrees(0.0, 1.0, 0.0, 0.0), 1e-6)
    }

    @Test
    fun angleDeltaWraps() {
        assertEquals(20.0, GeoMath.angleDelta(10.0, 350.0), 1e-9)
        assertEquals(-20.0, GeoMath.angleDelta(350.0, 10.0), 1e-9)
        assertEquals(180.0, GeoMath.angleDelta(180.0, 0.0), 1e-9)
    }

    @Test
    fun cardinalNames() {
        assertEquals("N", GeoMath.cardinal(359.0))
        assertEquals("NE", GeoMath.cardinal(40.0))
        assertEquals("W", GeoMath.cardinal(268.0))
    }

    @Test
    fun formatsDistances() {
        assertEquals("850 m", GeoMath.formatDistance(850.4))
        assertEquals("2.4 km", GeoMath.formatDistance(2_430.0))
        assertEquals("15 km", GeoMath.formatDistance(15_200.0))
    }

    @Test
    fun mountainElevationAngle() {
        // A 1,000 m peak 10 km away seen from sea level is about 5.7° up, minus a little curvature.
        val user = GeoLocation(43.0, -2.0, altitude = 0.0)
        val peak = Poi("p", "Peak", 43.0 + 10_000 / 111_195.0, -2.0, PoiCategory.Nature, elevation = 1_000.0, source = "test")
        val placed = peak.placedFrom(user)
        assertEquals(10_000.0, placed.distanceMeters, 30.0)
        assertEquals(0.0, placed.bearingDegrees, 0.01)
        assertTrue(placed.elevationDegrees in 5.6..5.71, "was ${placed.elevationDegrees}")
    }

    @Test
    fun noElevationWithoutAltitude() {
        val peak = Poi("p", "Peak", 43.1, -2.0, PoiCategory.Nature, elevation = 1_000.0, source = "test")
        assertEquals(0.0, peak.placedFrom(GeoLocation(43.0, -2.0)).elevationDegrees)
    }
}
