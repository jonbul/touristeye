package com.touristeye.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.touristeye.ar.CameraFov
import com.touristeye.ar.DeviceOrientation
import com.touristeye.geo.GeoLocation

/** Full-screen live preview of the back camera. */
@Composable
expect fun CameraPreview(modifier: Modifier = Modifier)

/** Field of view of the back camera in portrait. */
@Composable
expect fun rememberCameraFov(): CameraFov

/**
 * Smoothed orientation of the back camera relative to true north.
 * [location] is used to correct magnetic declination where the platform reports magnetic north.
 */
@Composable
expect fun rememberDeviceOrientation(location: GeoLocation?): State<DeviceOrientation?>

/** Latest GPS / network position while composed. */
@Composable
expect fun rememberLocation(): State<GeoLocation?>

interface AppPermissions {
    val cameraGranted: Boolean
    val locationGranted: Boolean
    /** True once the user has refused and the system won't show the dialog again. */
    val mustOpenSettings: Boolean
    fun request()
    fun openSettings()
}

@Composable
expect fun rememberAppPermissions(): AppPermissions

/** Two-letter language code of the device, used to pick names and the Wikipedia edition. */
expect fun platformLanguage(): String
