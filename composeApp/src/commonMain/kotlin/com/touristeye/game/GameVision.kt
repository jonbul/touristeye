package com.touristeye.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier

/** Live camera recognition for the game. */
interface GameVision {
    /** Objects found in the latest analysed frame. */
    val detections: State<List<Detection>>

    /** Size of the analysed (upright) camera image, or null before the first frame. */
    val imageSize: State<Pair<Int, Int>?>

    /** Names what is at a point of the latest frame (normalised 0..1) when no detection covers it; null if unknown. */
    suspend fun identify(x: Float, y: Float): String?
}

/** Whether this platform has the game's on-device recognition. */
expect val gameModeAvailable: Boolean

/** Full-screen camera preview that also analyses frames; returns the analysis results. */
@Composable
expect fun GameCamera(modifier: Modifier = Modifier): GameVision
