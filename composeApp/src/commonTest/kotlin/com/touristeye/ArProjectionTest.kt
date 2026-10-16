package com.touristeye

import com.touristeye.ar.ArProjector
import com.touristeye.ar.CameraFov
import com.touristeye.ar.DeviceOrientation
import com.touristeye.ar.Enu
import com.touristeye.ar.LabelLayout
import com.touristeye.ar.OrientationMath
import com.touristeye.ar.OrientationSmoother
import com.touristeye.geo.GeoMath
import com.touristeye.geo.PlacedPoi
import com.touristeye.geo.Poi
import com.touristeye.geo.PoiCategory
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArProjectionTest {
    private val fov = CameraFov(50.0, 64.0)
    private val projector = ArProjector(fov, width = 1080.0, height = 2340.0)

    @Test
    fun orientationFromVectors() {
        val north = OrientationMath.fromVectors(Enu(0.0, 1.0, 0.0), Enu(1.0, 0.0, 0.0))
        assertEquals(0.0, north.azimuth, 1e-9)
        assertEquals(0.0, north.pitch, 1e-9)
        assertEquals(0.0, north.roll, 1e-9)

        val east = OrientationMath.fromVectors(Enu(1.0, 0.0, 0.0), Enu(0.0, -1.0, 0.0))
        assertEquals(90.0, east.azimuth, 1e-9)
        assertEquals(0.0, east.roll, 1e-9)

        val up30 = OrientationMath.fromVectors(Enu(0.0, cos(GeoMath.toRad(30.0)), sin(GeoMath.toRad(30.0))), Enu(1.0, 0.0, 0.0))
        assertEquals(30.0, up30.pitch, 1e-9)

        // Phone turned clockwise by 20°: its right edge dips below the horizon.
        val r = GeoMath.toRad(20.0)
        val rolled = OrientationMath.fromVectors(Enu(0.0, 1.0, 0.0), Enu(cos(r), 0.0, -sin(r)))
        assertEquals(20.0, rolled.roll, 1e-9)
    }

    @Test
    fun smootherHandlesNorthWrap() {
        val smoother = OrientationSmoother(alpha = 0.5)
        val a = GeoMath.toRad(350.0)
        val b = GeoMath.toRad(10.0)
        smoother.update(Enu(sin(a), cos(a), 0.0), Enu(cos(a), -sin(a), 0.0))
        val o = smoother.update(Enu(sin(b), cos(b), 0.0), Enu(cos(b), -sin(b), 0.0))
        // Halfway between 350° and 10° is 0°, not 180°.
        assertTrue(o.azimuth < 1e-6 || o.azimuth > 359.999, "was ${o.azimuth}")
    }

    @Test
    fun cropReducesHorizontalFov() {
        // Tall screen: full vertical FOV, horizontal cropped to the screen aspect.
        assertEquals(tan(GeoMath.toRad(32.0)), projector.tanHalfV, 1e-12)
        assertEquals(tan(GeoMath.toRad(32.0)) * 1080 / 2340, projector.tanHalfH, 1e-12)
        assertTrue(projector.visibleHorizontalFov < 50.0)
    }

    @Test
    fun landscapeSwapsFieldOfView() {
        assertEquals(CameraFov(64.0, 50.0), fov.forViewport(2340.0, 1080.0))
        assertEquals(fov, fov.forViewport(1080.0, 2340.0))
        val landscape = ArProjector(fov.forViewport(2340.0, 1080.0), 2340.0, 1080.0)
        // Wide screen: the full 64° is horizontal, the height is cropped.
        assertEquals(tan(GeoMath.toRad(32.0)), landscape.tanHalfH, 1e-12)
    }

    @Test
    fun straightAheadIsCenter() {
        val o = DeviceOrientation(azimuth = 123.0, pitch = 0.0, roll = 0.0)
        val p = assertNotNull(projector.project(123.0, 0.0, o))
        assertEquals(540.0, p.x, 1e-6)
        assertEquals(1170.0, p.y, 1e-6)
    }

    @Test
    fun edgeOfFovIsEdgeOfScreen() {
        val o = DeviceOrientation(azimuth = 10.0, pitch = 0.0, roll = 0.0)
        val half = projector.visibleHorizontalFov / 2
        assertEquals(1080.0, projector.project(10.0 + half, 0.0, o)!!.x, 1e-6)
        assertEquals(0.0, projector.project(10.0 - half, 0.0, o)!!.x, 1e-6)
        // Wraps around north.
        val p = projector.project(GeoMath.normalize360(10.0 - half), 0.0, o)
        assertEquals(0.0, p!!.x, 1e-6)
    }

    @Test
    fun behindCameraIsNull() {
        val o = DeviceOrientation(azimuth = 0.0, pitch = 0.0, roll = 0.0)
        assertNull(projector.project(180.0, 0.0, o))
        assertNull(projector.project(95.0, 0.0, o))
    }

    @Test
    fun tiltingUpMovesHorizonDown() {
        val o = DeviceOrientation(azimuth = 0.0, pitch = 10.0, roll = 0.0)
        val p = projector.project(0.0, 0.0, o)!!
        val expected = 1170.0 + tan(GeoMath.toRad(10.0)) / projector.tanHalfV * 1170.0
        assertEquals(expected, p.y, 1e-6)
    }

    @Test
    fun rollRotatesThePoint() {
        // Phone turned 90° clockwise: its top edge now points right, so a point to the right
        // of where the camera looks appears towards the top of the screen.
        val o = DeviceOrientation(azimuth = 0.0, pitch = 0.0, roll = 90.0)
        val p = projector.project(10.0, 0.0, o)!!
        assertEquals(540.0, p.x, 1e-6)
        assertTrue(p.y < 1170.0)
    }

    @Test
    fun labelsStackNearestLowest() {
        fun placed(id: String, distance: Double, bearing: Double) =
            PlacedPoi(Poi(id, id, 0.0, 0.0, PoiCategory.Attraction, source = "t"), distance, bearing, 0.0)
        val pois = listOf(placed("far", 900.0, 0.5), placed("near", 100.0, 0.0), placed("aside", 300.0, 20.0), placed("behind", 50.0, 180.0))
        val labels = LabelLayout.layout(
            pois, projector, DeviceOrientation(0.0, 0.0, 0.0),
            labelWidth = 400.0, labelHeight = 120.0, gap = 10.0, stemLength = 50.0,
        )
        val byId = labels.associateBy { it.placed.poi.id }
        assertEquals(setOf("near", "far", "aside"), byId.keys)
        assertEquals(0, byId.getValue("near").level)
        assertEquals(1, byId.getValue("far").level)
        assertEquals(0, byId.getValue("aside").level)
        assertTrue(byId.getValue("far").labelTop < byId.getValue("near").labelTop)
    }
}
