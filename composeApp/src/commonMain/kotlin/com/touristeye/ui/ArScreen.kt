package com.touristeye.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.touristeye.app.ArController
import com.touristeye.data.FallbackPoiSource
import com.touristeye.data.OverpassPoiSource
import com.touristeye.data.WikipediaPoiSource
import com.touristeye.platform.CameraPreview
import com.touristeye.platform.platformLanguage
import com.touristeye.platform.rememberCameraFov
import com.touristeye.platform.rememberDeviceOrientation
import com.touristeye.platform.rememberLocation
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout

@Composable
fun ArScreen() {
    val scope = rememberCoroutineScope()
    val client = remember {
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 35_000
                connectTimeoutMillis = 10_000
            }
        }
    }
    DisposableEffect(client) { onDispose { client.close() } }
    val controller = remember {
        val language = platformLanguage()
        ArController(
            source = FallbackPoiSource(
                listOf(
                    OverpassPoiSource(client, language = language),
                    WikipediaPoiSource(client, language = language),
                ),
            ),
            scope = scope,
        )
    }

    val state by controller.state.collectAsState()
    val location by rememberLocation()
    val orientation by rememberDeviceOrientation(location)
    val fov = rememberCameraFov()

    LaunchedEffect(location) { location?.let(controller::onLocation) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(Modifier.fillMaxSize())

        ArOverlay(
            state = state,
            orientation = orientation,
            fov = fov,
            onSelect = controller::select,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompassTape(orientation, state.visible, state.selectedId, Modifier.fillMaxWidth())
            StatusLine(state, orientation, hasLocation = location != null, onRetry = controller::retry)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val selected = state.selected
            if (selected != null) {
                PoiCard(selected, onClose = { controller.select(null) })
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Radar(
                        orientation = orientation,
                        pois = state.visible,
                        radiusMeters = state.radiusMeters,
                        fov = fov,
                        modifier = Modifier,
                    )
                }
                Controls(
                    state = state,
                    onRadius = controller::setRadius,
                    onToggleCategory = controller::toggleCategory,
                )
            }
        }
    }
}
