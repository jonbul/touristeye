package com.touristeye.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberAppPermissions(): AppPermissions = remember { IosPermissions() }

private class IosPermissions : AppPermissions {
    private val cameraStatus = mutableStateOf(currentCameraStatus())
    private val locationManager = CLLocationManager()
    private val locationStatus = mutableStateOf(locationManager.authorizationStatus)
    private val delegate = LocationDelegate(onAuthorizationChanged = {
        locationStatus.value = locationManager.authorizationStatus
    })

    init {
        locationManager.delegate = delegate
    }

    override val cameraGranted get() = cameraStatus.value == AVAuthorizationStatusAuthorized
    override val locationGranted
        get() = locationStatus.value == kCLAuthorizationStatusAuthorizedWhenInUse ||
            locationStatus.value == kCLAuthorizationStatusAuthorizedAlways

    override val mustOpenSettings
        get() = cameraStatus.value == AVAuthorizationStatusDenied ||
            cameraStatus.value == AVAuthorizationStatusRestricted ||
            locationStatus.value == kCLAuthorizationStatusDenied ||
            locationStatus.value == kCLAuthorizationStatusRestricted

    override fun request() {
        // One system dialog at a time: camera first, then location.
        if (cameraStatus.value == AVAuthorizationStatusNotDetermined) {
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
                dispatch_async(dispatch_get_main_queue()) {
                    cameraStatus.value = currentCameraStatus()
                    requestLocation()
                }
            }
        } else {
            requestLocation()
        }
    }

    private fun requestLocation() {
        if (locationStatus.value == kCLAuthorizationStatusNotDetermined) {
            locationManager.requestWhenInUseAuthorization()
        }
    }

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null)
    }
}

private fun currentCameraStatus() = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
