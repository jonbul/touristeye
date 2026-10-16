package com.touristeye.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touristeye.app.ArState
import com.touristeye.ar.ArProjector
import com.touristeye.ar.CameraFov
import com.touristeye.ar.DeviceOrientation
import com.touristeye.ar.LabelLayout
import com.touristeye.ar.PlacedLabel
import com.touristeye.geo.GeoMath
import kotlin.math.abs
import kotlin.math.roundToInt

private val LabelWidth = 156.dp
private val LabelHeight = 46.dp
private val LabelGap = 6.dp
private val StemLength = 28.dp

/** Draws a label over the camera image for every POI in the camera's field of view. */
@Composable
fun ArOverlay(
    state: ArState,
    orientation: DeviceOrientation?,
    fov: CameraFov,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        if (orientation == null) return@BoxWithConstraints
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toDouble()
        val heightPx = constraints.maxHeight.toDouble()
        val projector = remember(fov, widthPx, heightPx) { ArProjector(fov.forViewport(widthPx, heightPx), widthPx, heightPx) }
        val labelW = with(density) { LabelWidth.toPx() }.toDouble()
        val labelH = with(density) { LabelHeight.toPx() }.toDouble()
        val gap = with(density) { LabelGap.toPx() }.toDouble()
        val stem = with(density) { StemLength.toPx() }.toDouble()

        val labels = LabelLayout.layout(
            pois = state.visible,
            projector = projector,
            orientation = orientation,
            labelWidth = labelW,
            labelHeight = labelH,
            gap = gap,
            stemLength = stem,
        )

        // Horizon line, stems and anchor dots.
        Canvas(Modifier.fillMaxSize()) {
            val h1 = projector.project(orientation.azimuth - 80, 0.0, orientation)
            val h2 = projector.project(orientation.azimuth + 80, 0.0, orientation)
            if (h1 != null && h2 != null) {
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(h1.x.toFloat(), h1.y.toFloat()),
                    end = Offset(h2.x.toFloat(), h2.y.toFloat()),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                )
            }
            for (label in labels) {
                val selected = label.placed.poi.id == state.selectedId
                val color = if (selected) Color(0xFFFFC107) else Color.White
                val anchor = Offset(label.anchor.x.toFloat(), label.anchor.y.toFloat())
                val labelBottom = Offset(label.labelCenterX.toFloat(), (label.labelTop + labelH).toFloat())
                drawLine(color.copy(alpha = 0.8f), labelBottom, anchor, strokeWidth = 1.5.dp.toPx())
                drawCircle(color, radius = 4.dp.toPx(), center = anchor)
                drawCircle(Color.Black.copy(alpha = 0.5f), radius = 4.dp.toPx(), center = anchor, style = Stroke(1.dp.toPx()))
            }
        }

        for (label in labels) {
            key(label.placed.poi.id) {
                PoiLabel(
                    label = label,
                    radiusMeters = state.radiusMeters,
                    selected = label.placed.poi.id == state.selectedId,
                    onClick = { onSelect(label.placed.poi.id) },
                    modifier = Modifier.offset {
                        IntOffset(
                            (label.labelCenterX - labelW / 2).roundToInt(),
                            label.labelTop.roundToInt(),
                        )
                    },
                )
            }
        }

        // Points to the selected place when it is out of view.
        state.selected?.let { sel ->
            val delta = GeoMath.angleDelta(sel.bearingDegrees, orientation.azimuth)
            if (abs(delta) > projector.visibleHorizontalFov / 2) {
                val text = if (delta < 0) "◀ ${abs(delta).roundToInt()}°" else "${delta.roundToInt()}° ▶"
                Surface(
                    color = Color(0xFFFFC107),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(if (delta < 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(8.dp),
                ) {
                    Text(
                        "$text  ${sel.poi.name}",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).widthIn(max = 220.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PoiLabel(
    label: PlacedLabel,
    radiusMeters: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val placed = label.placed
    // Far places fade a little so near ones stand out.
    val fade = 1f - 0.45f * (placed.distanceMeters / radiusMeters).toFloat().coerceIn(0f, 1f)
    Surface(
        modifier = modifier
            .size(LabelWidth, LabelHeight)
            .alpha(if (selected) 1f else fade)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFFFFC107) else Color.Black.copy(alpha = 0.62f),
        contentColor = if (selected) Color.Black else Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(placed.poi.category.emoji, fontSize = 18.sp)
            Column {
                Text(
                    placed.poi.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${GeoMath.formatDistance(placed.distanceMeters)} · ${GeoMath.cardinal(placed.bearingDegrees)}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}
