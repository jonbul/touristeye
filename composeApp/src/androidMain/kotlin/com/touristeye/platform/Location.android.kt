package com.touristeye.platform

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.touristeye.geo.GeoLocation

@SuppressLint("MissingPermission") // Only composed after the location permission is granted.
@Composable
actual fun rememberLocation(): State<GeoLocation?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<GeoLocation?>(null) }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var best: Location? = null

        fun offer(location: Location) {
            val current = best
            val ageOfCurrentMs = current?.let { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000 }
            val accept = current == null ||
                ageOfCurrentMs!! > 10_000 ||
                location.accuracy <= current.accuracy + 15f
            if (!accept) return
            best = location
            state.value = location.toGeoLocation()
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = offer(location)
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}

            // Still called on Android 9 and below, where it has no default implementation.
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?.let(::offer)
        for (provider in providers) {
            manager.requestLocationUpdates(provider, 1_000L, 2f, listener, Looper.getMainLooper())
        }

        onDispose { manager.removeUpdates(listener) }
    }
    return state
}

private fun Location.toGeoLocation(): GeoLocation {
    val altitude = when {
        Build.VERSION.SDK_INT >= 34 && hasMslAltitude() -> mslAltitudeMeters
        hasAltitude() -> altitude
        else -> null
    }
    return GeoLocation(latitude, longitude, altitude, if (hasAccuracy()) accuracy else null)
}
