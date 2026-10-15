@file:OptIn(ExperimentalForeignApi::class)

package com.touristeye.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.touristeye.geo.GeoLocation
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.darwin.NSObject

internal class LocationDelegate(
    private val onLocation: (GeoLocation) -> Unit = {},
    private val onAuthorizationChanged: () -> Unit = {},
) : NSObject(), CLLocationManagerDelegateProtocol {

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
        val altitude = if (location.verticalAccuracy >= 0) location.altitude else null
        val accuracy = location.horizontalAccuracy.takeIf { it >= 0 }?.toFloat()
        location.coordinate.useContents {
            onLocation(GeoLocation(latitude, longitude, altitude, accuracy))
        }
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) = onAuthorizationChanged()
}

@Composable
actual fun rememberLocation(): State<GeoLocation?> {
    val state = remember { mutableStateOf<GeoLocation?>(null) }
    // The manager only holds its delegate weakly, so both are remembered here.
    val manager = remember { CLLocationManager() }
    val delegate = remember { LocationDelegate(onLocation = { state.value = it }) }
    DisposableEffect(manager) {
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = 2.0
        manager.startUpdatingLocation()
        onDispose { manager.stopUpdatingLocation() }
    }
    return state
}
