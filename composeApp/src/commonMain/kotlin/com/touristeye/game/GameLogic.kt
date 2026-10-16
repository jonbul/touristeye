package com.touristeye.game

import kotlin.math.max

/** Something the camera recognised, with its box in the upright camera image, normalised to 0..1. */
data class Detection(
    val label: String,
    val score: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
    val area get() = (right - left) * (bottom - top)
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

data class Point(val x: Float, val y: Float)

/**
 * Maps between the camera image and a view showing it center-cropped (fill, keep aspect ratio),
 * the way the camera preview is drawn. Image coordinates are normalised to 0..1.
 */
class FillCenterMapping(imageWidth: Float, imageHeight: Float, private val viewWidth: Float, private val viewHeight: Float) {
    private val scale = max(viewWidth / imageWidth, viewHeight / imageHeight)
    private val shownWidth = imageWidth * scale
    private val shownHeight = imageHeight * scale
    private val offsetX = (viewWidth - shownWidth) / 2
    private val offsetY = (viewHeight - shownHeight) / 2

    fun toView(nx: Float, ny: Float) = Point(offsetX + nx * shownWidth, offsetY + ny * shownHeight)
    fun toImage(vx: Float, vy: Float) = Point((vx - offsetX) / shownWidth, (vy - offsetY) / shownHeight)
}

object GameLogic {
    /** The detection under the crosshair: the smallest box containing the point, so a dog in front of a car wins. */
    fun targetAt(detections: List<Detection>, x: Float, y: Float): Detection? =
        detections.filter { it.contains(x, y) }.minByOrNull { it.area }

    private val pointsByLabel = mapOf(
        "person" to 10,
        "car" to 25, "truck" to 30, "bus" to 30, "train" to 40, "airplane" to 50, "boat" to 25,
        "bicycle" to 20, "motorcycle" to 20,
        "dog" to 15, "cat" to 15, "bird" to 30, "horse" to 25,
        "tree" to 15, "bench" to 10, "potted plant" to 5,
    )

    fun points(label: String): Int = pointsByLabel[label.lowercase()] ?: 5

    fun displayName(label: String): String = label.replaceFirstChar { it.uppercase() }
}
