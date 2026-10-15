@file:OptIn(ExperimentalForeignApi::class)

package com.touristeye.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.touristeye.ar.DeviceOrientation
import com.touristeye.ar.Enu
import com.touristeye.ar.OrientationSmoother
import com.touristeye.geo.GeoLocation
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreMotion.CMAttitudeReferenceFrameXMagneticNorthZVertical
import platform.CoreMotion.CMAttitudeReferenceFrameXTrueNorthZVertical
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSLocale
import platform.Foundation.NSOperationQueue
import platform.Foundation.preferredLanguages

/**
 * Uses CoreMotion's true-north reference frame (X north, Y west, Z up). The rotation matrix rows
 * are the device axes in that frame; the back camera looks along the device's -Z axis.
 */
@Composable
actual fun rememberDeviceOrientation(location: GeoLocation?): State<DeviceOrientation?> {
    val state = remember { mutableStateOf<DeviceOrientation?>(null) }
    DisposableEffect(Unit) {
        val manager = CMMotionManager()
        val smoother = OrientationSmoother()
        manager.deviceMotionUpdateInterval = 1.0 / 60.0
        manager.showsDeviceMovementDisplay = true // iOS shows its own compass calibration prompt.
        val frames = CMMotionManager.availableAttitudeReferenceFrames()
        val frame = if (frames and CMAttitudeReferenceFrameXTrueNorthZVertical != 0uL) {
            CMAttitudeReferenceFrameXTrueNorthZVertical
        } else {
            CMAttitudeReferenceFrameXMagneticNorthZVertical
        }
        manager.startDeviceMotionUpdatesUsingReferenceFrame(frame, NSOperationQueue.mainQueue) { motion, _ ->
            motion?.attitude?.rotationMatrix?.useContents {
                val forward = Enu(e = m32, n = -m31, u = -m33)
                val right = Enu(e = -m12, n = m11, u = m13)
                state.value = smoother.update(forward, right)
            }
        }
        onDispose { manager.stopDeviceMotionUpdates() }
    }
    return state
}

actual fun platformLanguage(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String)?.substringBefore('-')?.ifBlank { null } ?: "en"
