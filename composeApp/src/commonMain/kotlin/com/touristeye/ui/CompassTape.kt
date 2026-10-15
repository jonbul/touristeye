package com.touristeye.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touristeye.ar.DeviceOrientation
import com.touristeye.geo.GeoMath
import com.touristeye.geo.PlacedPoi
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val HALF_SPAN_DEGREES = 50.0

/** Horizontal compass strip showing the heading, cardinal points and where places lie. */
@Composable
fun CompassTape(
    orientation: DeviceOrientation?,
    pois: List<PlacedPoi>,
    selectedId: String?,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val tickStyle = TextStyle(color = Color.White, fontSize = 11.sp)
    val cardinalStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val headingStyle = TextStyle(color = Color(0xFFFFC107), fontSize = 13.sp, fontWeight = FontWeight.Bold)

    Canvas(modifier.height(52.dp)) {
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
        val az = orientation?.azimuth ?: return@Canvas
        val pxPerDeg = size.width / (2 * HALF_SPAN_DEGREES).toFloat()
        val cx = size.width / 2
        fun xOf(deg: Double) = cx + (GeoMath.angleDelta(deg, az) * pxPerDeg).toFloat()

        var d = floor((az - HALF_SPAN_DEGREES) / 5) * 5
        val end = ceil(az + HALF_SPAN_DEGREES)
        while (d <= end) {
            val deg = GeoMath.normalize360(d).roundToInt() % 360
            val x = xOf(deg.toDouble())
            val major = deg % 15 == 0
            drawLine(
                Color.White.copy(alpha = if (major) 0.9f else 0.5f),
                Offset(x, 0f),
                Offset(x, (if (major) 10 else 6).dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
            if (deg % 45 == 0 || major) {
                val text = if (deg % 45 == 0) GeoMath.cardinal(deg.toDouble()) else deg.toString()
                val layout = measurer.measure(text, if (deg % 45 == 0) cardinalStyle else tickStyle)
                drawText(layout, topLeft = Offset(x - layout.size.width / 2f, 12.dp.toPx()))
            }
            d += 5
        }

        for (p in pois) {
            val delta = GeoMath.angleDelta(p.bearingDegrees, az)
            if (kotlin.math.abs(delta) > HALF_SPAN_DEGREES) continue
            val selected = p.poi.id == selectedId
            drawCircle(
                if (selected) Color(0xFFFFC107) else Color(0xFF4FC3F7),
                radius = (if (selected) 4f else 2.5f).dp.toPx(),
                center = Offset(xOf(p.bearingDegrees), 32.dp.toPx()),
            )
        }

        // Center marker and numeric heading.
        val marker = Path().apply {
            moveTo(cx - 6.dp.toPx(), 0f)
            lineTo(cx + 6.dp.toPx(), 0f)
            lineTo(cx, 8.dp.toPx())
            close()
        }
        drawPath(marker, Color(0xFFFFC107))
        val heading = measurer.measure("${az.roundToInt() % 360}° ${GeoMath.cardinal(az)}", headingStyle)
        drawText(heading, topLeft = Offset(cx - heading.size.width / 2f, 36.dp.toPx()))
    }
}
