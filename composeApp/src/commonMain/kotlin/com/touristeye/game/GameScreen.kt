package com.touristeye.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val TargetRed = Color(0xFFFF1744)

/**
 * Game mode: aim the crosshair at a person, tree, car… and shoot. Whatever is under the crosshair
 * explodes and adds points. Recognition runs on the phone.
 */
@Composable
fun GameScreen(onExit: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val vision = GameCamera(Modifier.fillMaxSize())
        val detections by vision.detections
        val imageSize by vision.imageSize
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()
        val mapping = imageSize?.let { (w, h) -> FillCenterMapping(w.toFloat(), h.toFloat(), viewW, viewH) }
        val aim = mapping?.toImage(viewW / 2, viewH / 2)
        val target = aim?.let { GameLogic.targetAt(detections, it.x, it.y) }

        var score by rememberSaveable { mutableIntStateOf(0) }
        var hits by rememberSaveable { mutableIntStateOf(0) }
        val explosions = remember { mutableStateListOf<Explosion>() }
        var nextId by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()
        val haptics = LocalHapticFeedback.current

        fun explode(x: Float, y: Float, label: String?) {
            val points = label?.let { GameLogic.points(it) } ?: 0
            score += points
            if (label != null) hits++
            val text = if (label != null) "+$points ${GameLogic.displayName(label)}" else "Boom!"
            explosions += Explosion(nextId++.toLong(), x, y, text)
        }

        fun shoot() {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val hit = target
            if (hit != null && mapping != null) {
                val p = mapping.toView(hit.centerX, hit.centerY)
                explode(p.x, p.y, hit.label)
            } else {
                // Nothing detected under the crosshair: ask the labeller what is there (tree, building…).
                scope.launch {
                    val label = aim?.let { vision.identify(it.x, it.y) }
                    explode(viewW / 2, viewH / 2, label)
                }
            }
        }

        // Tapping anywhere also shoots.
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = ::shoot,
            ),
        )

        // Boxes around recognised things, the one under the crosshair in red.
        if (mapping != null) {
            Canvas(Modifier.fillMaxSize()) {
                for (d in detections) {
                    val tl = mapping.toView(d.left, d.top)
                    val br = mapping.toView(d.right, d.bottom)
                    val isTarget = d == target
                    drawRoundRect(
                        color = if (isTarget) TargetRed else Color.White.copy(alpha = 0.5f),
                        topLeft = Offset(tl.x, tl.y),
                        size = Size(br.x - tl.x, br.y - tl.y),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        style = Stroke(width = (if (isTarget) 3f else 1.5f).dp.toPx()),
                    )
                }
            }
            for (d in detections) {
                val tl = mapping.toView(d.left, d.top)
                Text(
                    "${GameLogic.displayName(d.label)} ${(d.score * 100).roundToInt()}%",
                    color = if (d == target) TargetRed else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .offset { IntOffset(tl.x.roundToInt() + 8, tl.y.roundToInt() + 4) }
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp),
                )
            }
        }

        Crosshair(locked = target != null, modifier = Modifier.align(Alignment.Center))

        for (e in explosions.toList()) {
            key(e.id) { ExplosionView(e, onDone = { explosions.remove(e) }) }
        }

        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White, shape = RoundedCornerShape(50)) {
                TextButton(onClick = onExit) { Text("✕ Sightseeing", color = Color.White) }
            }
            Spacer(Modifier.weight(1f))
            Surface(color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), horizontalAlignment = Alignment.End) {
                    Text("$score", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("$hits hits", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                target?.let { "🎯 ${GameLogic.displayName(it.label)}" } ?: "Aim at something",
                color = if (target != null) TargetRed else Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
            Surface(
                onClick = ::shoot,
                shape = CircleShape,
                color = TargetRed,
                contentColor = Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier.size(84.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { Text("FIRE", fontWeight = FontWeight.Black, fontSize = 18.sp) }
            }
        }
    }
}

@Composable
private fun Crosshair(locked: Boolean, modifier: Modifier = Modifier) {
    val color = if (locked) TargetRed else Color.White
    Canvas(modifier.size(72.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - 4.dp.toPx()
        val stroke = 2.5.dp.toPx()
        drawCircle(Color.Black.copy(alpha = 0.35f), r, c, style = Stroke(stroke + 2.dp.toPx()))
        drawCircle(color, r, c, style = Stroke(stroke))
        val gap = 8.dp.toPx()
        drawLine(color, Offset(c.x - r - 6.dp.toPx(), c.y), Offset(c.x - gap, c.y), stroke)
        drawLine(color, Offset(c.x + gap, c.y), Offset(c.x + r + 6.dp.toPx(), c.y), stroke)
        drawLine(color, Offset(c.x, c.y - r - 6.dp.toPx()), Offset(c.x, c.y - gap), stroke)
        drawLine(color, Offset(c.x, c.y + gap), Offset(c.x, c.y + r + 6.dp.toPx()), stroke)
        drawCircle(color, 2.5.dp.toPx(), c)
    }
}
