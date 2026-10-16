package com.touristeye.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.touristeye.platform.CameraPreview

/** On-device recognition isn't wired up on iOS yet, so the game is Android-only for now. */
actual val gameModeAvailable: Boolean = false

@Composable
actual fun GameCamera(modifier: Modifier): GameVision {
    CameraPreview(modifier)
    return remember {
        object : GameVision {
            override val detections: State<List<Detection>> = mutableStateOf(emptyList())
            override val imageSize: State<Pair<Int, Int>?> = mutableStateOf(null)
            override suspend fun identify(x: Float, y: Float): String? = null
        }
    }
}
