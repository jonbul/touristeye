package com.touristeye.platform

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.display.DisplayManager
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.touristeye.ar.CameraFov
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min

@Composable
actual fun CameraPreview(modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null

        // The activity handles rotation itself, so tell CameraX when the screen turns.
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                previewView.display?.let { preview?.targetRotation = it.rotation }
            }
        }
        displayManager.registerDisplayListener(displayListener, null)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val newPreview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                    previewView.display?.let { display -> it.targetRotation = display.rotation }
                }
                preview = newPreview
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, newPreview)
            } catch (e: Exception) {
                Log.e("Touristeye", "Camera start failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            displayManager.unregisterDisplayListener(displayListener)
            provider?.unbindAll()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
actual fun rememberCameraFov(): CameraFov {
    val context = LocalContext.current
    return remember { backCameraFov(context) ?: CameraFov.Default }
}

/**
 * Field of view from the sensor size and focal length. CameraX previews are 4:3 by default,
 * so a wider sensor is cropped to 4:3 first. In portrait the sensor's short side is horizontal.
 */
private fun backCameraFov(context: Context): CameraFov? = try {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val id = manager.cameraIdList.firstOrNull {
        manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    }
    val chars = id?.let(manager::getCameraCharacteristics)
    val size = chars?.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    val focal = chars?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
    if (size == null || focal == null || focal <= 0f) {
        null
    } else {
        val long = max(size.width, size.height)
        val short = min(size.width, size.height)
        val longUsed = min(long, short * 4f / 3f)
        val shortUsed = min(short, long * 3f / 4f)
        CameraFov(
            horizontalDegrees = Math.toDegrees(2 * atan(shortUsed / (2.0 * focal))),
            verticalDegrees = Math.toDegrees(2 * atan(longUsed / (2.0 * focal))),
        )
    }
} catch (e: Exception) {
    Log.w("Touristeye", "Could not read camera FOV", e)
    null
}
