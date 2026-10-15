package com.touristeye.ar

import com.touristeye.geo.PlacedPoi
import kotlin.math.abs

/** A label placed on screen. [anchor] is the exact point of the POI; the label sits above it at [labelTop]. */
data class PlacedLabel(
    val placed: PlacedPoi,
    val anchor: ScreenPoint,
    val labelCenterX: Double,
    val labelTop: Double,
    val level: Int,
)

/**
 * Stacks labels so they don't overlap. Nearer POIs get the lowest rows (closest to their anchor);
 * farther ones are pushed up. Labels that would need more than [maxLevels] rows are dropped.
 */
object LabelLayout {
    fun layout(
        pois: List<PlacedPoi>,
        projector: ArProjector,
        orientation: DeviceOrientation,
        labelWidth: Double,
        labelHeight: Double,
        gap: Double,
        stemLength: Double,
        maxLevels: Int = 6,
        maxLabels: Int = 40,
    ): List<PlacedLabel> {
        val rows = Array(maxLevels) { mutableListOf<Double>() }
        val result = mutableListOf<PlacedLabel>()
        for (placed in pois.sortedBy { it.distanceMeters }) {
            if (result.size >= maxLabels) break
            val anchor = projector.project(placed.bearingDegrees, placed.elevationDegrees, orientation) ?: continue
            // Keep labels whose anchor is horizontally visible; vertically they are clamped below.
            if (anchor.x < -labelWidth / 2 || anchor.x > projector.width + labelWidth / 2) continue
            val level = rows.indexOfFirst { row -> row.none { abs(it - anchor.x) < labelWidth + gap } }
            if (level < 0) continue
            rows[level].add(anchor.x)
            val bottom = anchor.y - stemLength - level * (labelHeight + gap)
            val top = (bottom - labelHeight).coerceIn(0.0, (projector.height - labelHeight).coerceAtLeast(0.0))
            result += PlacedLabel(placed, anchor, anchor.x, top, level)
        }
        return result
    }
}
