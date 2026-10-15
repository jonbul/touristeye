package com.touristeye

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.touristeye.platform.AppPermissions
import com.touristeye.platform.rememberAppPermissions
import com.touristeye.ui.ArScreen

@Composable
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val permissions = rememberAppPermissions()
        val granted = permissions.cameraGranted && permissions.locationGranted
        LaunchedEffect(Unit) {
            if (!granted) permissions.request()
        }
        if (granted) ArScreen() else PermissionScreen(permissions)
    }
}

@Composable
private fun PermissionScreen(permissions: AppPermissions) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("👁️ Touristeye", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Text(
            "Point your camera around you to see the sights in that direction. " +
                "Touristeye needs the camera and your location to work.",
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        if (permissions.mustOpenSettings) {
            Button(onClick = permissions::openSettings) { Text("Open settings") }
        } else {
            Button(onClick = permissions::request) { Text("Allow camera and location") }
        }
    }
}
