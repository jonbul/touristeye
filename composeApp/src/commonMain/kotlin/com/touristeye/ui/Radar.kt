package com.touristeye.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touristeye.ar.CameraFov
import com.touristeye.ar.DeviceOrientation
import com.touristeye.geo.GeoMath
import com.touristeye.geo.PlacedPoi
import kotlin.math.cos
import kotlin.math.sin

/** Top-down mini map, rotated so the direction you are facing is up. */
@Composable
fun Radar(
    orientation: DeviceOrientation?,
    pois: List<PlacedPoi>,
    radiusMeters: Int,
    fov: CameraFov,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val northStyle = TextStyle(color = Color(0xFFFF5252), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    Canvas(modifier.size(112.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - 2.dp.toPx()
        drawCircle(Color.Black.copy(alpha = 0.55f), r, c)
        drawCircle(Color.White.copy(alpha = 0.25f), r / 2, c, style = Stroke(1.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.6f), r, c, style = Stroke(1.dp.toPx()))
        val az = orientation?.azimuth ?: 0.0

        val fovDeg = fov.horizontalDegrees.toFloat()
        drawArc(
            color = Color(0xFFFFC107).copy(alpha = 0.25f),
            startAngle = -90f - fovDeg / 2,
            sweepAngle = fovDeg,
            useCenter = true,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(2 * r, 2 * r),
        )

        fun pointAt(bearing: Double, fraction: Double): Offset {
            val a = GeoMath.toRad(GeoMath.angleDelta(bearing, az))
            return Offset((c.x + fraction * r * sin(a)).toFloat(), (c.y - fraction * r * cos(a)).toFloat())
        }

        for (p in pois) {
            val f = (p.distanceMeters / radiusMeters).coerceIn(0.0, 1.0)
            drawCircle(Color(0xFF4FC3F7), 2.5.dp.toPx(), pointAt(p.bearingDegrees, f))
        }
        drawCircle(Color.White, 3.dp.toPx(), c)

        val n = measurer.measure("N", northStyle)
        val np = pointAt(0.0, 0.82)
        drawText(n, topLeft = Offset(np.x - n.size.width / 2f, np.y - n.size.height / 2f))
    }
}
