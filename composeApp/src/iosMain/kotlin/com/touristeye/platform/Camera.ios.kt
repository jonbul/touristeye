@file:OptIn(ExperimentalForeignApi::class)

package com.touristeye.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.touristeye.ar.CameraFov
import com.touristeye.geo.GeoMath
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.activeFormat
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import kotlin.math.atan
import kotlin.math.tan

private class CameraSession {
    val session = AVCaptureSession()
    private val queue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)

    init {
        session.sessionPreset = AVCaptureSessionPresetPhoto // 4:3, matches rememberCameraFov()
        AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)?.let { device ->
            val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) as? AVCaptureDeviceInput
            if (input != null && session.canAddInput(input)) session.addInput(input)
        }
    }

    fun start() = dispatch_async(queue) { session.startRunning() }
    fun stop() = dispatch_async(queue) { session.stopRunning() }
}

private class CameraPreviewView(session: AVCaptureSession) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.frame = bounds
        CATransaction.commit()
    }
}

@Composable
actual fun CameraPreview(modifier: Modifier) {
    val camera = remember { CameraSession() }
    DisposableEffect(camera) {
        camera.start()
        onDispose { camera.stop() }
    }
    UIKitView(factory = { CameraPreviewView(camera.session) }, modifier = modifier)
}

/**
 * videoFieldOfView is the horizontal FOV of the landscape image, which is the vertical FOV in portrait.
 * The photo preset delivers 4:3 frames.
 */
@Composable
actual fun rememberCameraFov(): CameraFov = remember {
    val landscapeHorizontal = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        ?.activeFormat?.videoFieldOfView?.toDouble()
    if (landscapeHorizontal == null || landscapeHorizontal <= 0.0) {
        CameraFov.Default
    } else {
        val halfV = GeoMath.toRad(landscapeHorizontal / 2)
        CameraFov(
            horizontalDegrees = GeoMath.toDeg(2 * atan(tan(halfV) * 3.0 / 4.0)),
            verticalDegrees = landscapeHorizontal,
        )
    }
}
