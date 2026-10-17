package com.touristeye.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.touristeye.app.ArController
import com.touristeye.data.NominatimPlaceSearch
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
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 10_000
            }
        }
    }
    DisposableEffect(client) { onDispose { client.close() } }
    val controller = remember {
        val language = platformLanguage()
        ArController(
            sources = listOf(
                OverpassPoiSource(client, language = language),
                WikipediaPoiSource(client, language = language),
            ),
            scope = scope,
            placeSearch = NominatimPlaceSearch(client, language = language),
        )
    }

    val state by controller.state.collectAsState()
    val search by controller.search.collectAsState()
    var searchOpen by remember { mutableStateOf(false) }
    val location by rememberLocation()
    val orientation by rememberDeviceOrientation(location)
    val fov = rememberCameraFov()

    LaunchedEffect(location) { location?.let(controller::onLocation) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val landscape = maxWidth > maxHeight
        val screenFov = fov.forViewport(maxWidth.value.toDouble(), maxHeight.value.toDouble())

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

        val bottomModifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)
        val selected = state.selected
        if (landscape) {
            // Landscape: radar on the left, panel on the right, so the middle of the view stays clear.
            Row(
                bottomModifier,
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Radar(orientation, state.visible, state.radiusMeters, screenFov)
                Box(Modifier.widthIn(max = 420.dp)) {
                    if (selected != null) {
                        PoiCard(selected, onClose = { controller.select(null) })
                    } else {
                        Controls(
                            state,
                            onRadius = controller::setRadius,
                            onToggleCategory = controller::toggleCategory,
                            onOpenSearch = { searchOpen = true },
                        )
                    }
                }
            }
        } else {
            Column(bottomModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selected != null) {
                    PoiCard(selected, onClose = { controller.select(null) })
                } else {
                    Radar(orientation, state.visible, state.radiusMeters, screenFov)
                    Controls(
                        state,
                        onRadius = controller::setRadius,
                        onToggleCategory = controller::toggleCategory,
                        onOpenSearch = { searchOpen = true },
                    )
                }
            }
        }

        if (searchOpen) {
            PlaceSearchDialog(
                search = search,
                added = state.searched,
                location = location,
                onSearch = controller::searchPlaces,
                onAdd = controller::addSearched,
                onRemove = controller::removeSearched,
                onDismiss = { searchOpen = false },
            )
        }
    }
}
