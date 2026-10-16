package com.touristeye.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

data class Explosion(val id: Long, val x: Float, val y: Float, val text: String)

private class Particle(val angle: Double, val speed: Float, val size: Float, val color: Color)

private val FireColors = listOf(Color(0xFFFFF59D), Color(0xFFFFCA28), Color(0xFFFF9800), Color(0xFFFF5722), Color(0xFFD84315))

/** A cartoon explosion at ([Explosion.x], [Explosion.y]) in px, with the score text floating up. Calls [onDone] when finished. */
@Composable
fun ExplosionView(explosion: Explosion, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val particles = remember {
        val random = Random(explosion.id)
        List(26) {
            Particle(
                angle = random.nextDouble(0.0, 2 * PI),
                speed = random.nextFloat() * 0.7f + 0.3f,
                size = random.nextFloat() * 6f + 3f,
                color = FireColors[random.nextInt(FireColors.size)],
            )
        }
    }
    LaunchedEffect(explosion.id) {
        progress.animateTo(1f, tween(durationMillis = 1100, easing = LinearEasing))
        onDone()
    }
    val t = progress.value
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(explosion.x, explosion.y)
        val maxRadius = 110.dp.toPx()
        val fire = FastOutSlowInEasing.transform((t / 0.45f).coerceAtMost(1f))
        val fade = (1f - t).coerceIn(0f, 1f)

        // Smoke that lingers after the fireball.
        drawCircle(Color.DarkGray.copy(alpha = 0.45f * fade), radius = maxRadius * (0.5f + 0.6f * t), center = center)
        // Fireball.
        if (t < 0.7f) {
            val r = maxRadius * fire
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White, Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFE64A19).copy(alpha = 0f)),
                    center = center,
                    radius = r.coerceAtLeast(1f),
                ),
                radius = r,
                center = center,
                alpha = (1f - t / 0.7f),
            )
        }
        // Shock wave.
        drawCircle(
            Color.White.copy(alpha = 0.6f * fade),
            radius = maxRadius * 1.4f * fire,
            center = center,
            style = Stroke(width = 4.dp.toPx() * fade),
        )
        // Sparks flying out and falling.
        for (p in particles) {
            val distance = maxRadius * 1.6f * p.speed * FastOutSlowInEasing.transform(t)
            val gravity = 120.dp.toPx() * t * t
            val pos = Offset(
                center.x + (cos(p.angle) * distance).toFloat(),
                center.y + (sin(p.angle) * distance).toFloat() + gravity,
            )
            drawCircle(p.color.copy(alpha = fade), radius = p.size.dp.toPx() * (1f - 0.5f * t), center = pos)
        }
    }
    Text(
        explosion.text,
        color = Color(0xFFFFEB3B),
        fontSize = 22.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .alpha((1.6f - t * 1.6f).coerceIn(0f, 1f))
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .offset {
                IntOffset(
                    explosion.x.roundToInt() - 60.dp.roundToPx(),
                    (explosion.y - 60.dp.toPx() - 80.dp.toPx() * t).roundToInt(),
                )
            },
    )
}
