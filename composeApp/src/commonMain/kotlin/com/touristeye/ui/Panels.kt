package com.touristeye.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.touristeye.app.ArState
import com.touristeye.app.RadiusOptions
import com.touristeye.ar.DeviceOrientation
import com.touristeye.geo.GeoMath
import com.touristeye.geo.PlacedPoi
import com.touristeye.geo.PoiCategory
import kotlin.math.roundToInt

private val PanelColor = Color.Black.copy(alpha = 0.6f)

@Composable
fun StatusLine(state: ArState, orientation: DeviceOrientation?, hasLocation: Boolean, onRetry: () -> Unit) {
    val (text, action) = when {
        !hasLocation -> "📡 Finding your location…" to null
        state.error != null -> "⚠️ Couldn't load places. Tap to retry" to onRetry
        state.loading && state.visible.isEmpty() -> "⏳ Loading places nearby…" to null
        orientation == null -> "🧭 Waiting for the compass…" to null
        !orientation.reliable -> "🧭 Move your phone in a figure 8 to calibrate the compass" to null
        state.loading -> "⏳ Loading more places…" to null
        state.visible.isEmpty() -> "No places within ${GeoMath.formatDistance(state.radiusMeters.toDouble())}" to null
        else -> return
    }
    Surface(
        color = PanelColor,
        contentColor = Color.White,
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(top = 4.dp).let { if (action != null) it.clickable(onClick = action) else it },
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

@Composable
fun Controls(
    state: ArState,
    onRadius: (Int) -> Unit,
    onToggleCategory: (PoiCategory) -> Unit,
    onOpenSearch: () -> Unit,
) {
    Surface(color = PanelColor, contentColor = Color.White, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            var sliderValue by remember(state.radiusMeters) {
                mutableFloatStateOf(RadiusOptions.indexOf(state.radiusMeters).coerceAtLeast(0).toFloat())
            }
            val shownRadius = RadiusOptions[sliderValue.roundToInt().coerceIn(0, RadiusOptions.lastIndex)]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Radius ${GeoMath.formatDistance(shownRadius.toDouble())}", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text("${state.visible.size} places", style = MaterialTheme.typography.labelLarge)
            }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onRadius(shownRadius) },
                valueRange = 0f..RadiusOptions.lastIndex.toFloat(),
                steps = RadiusOptions.size - 2,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = state.searched.isNotEmpty(),
                    onClick = onOpenSearch,
                    label = {
                        val count = state.searched.size
                        Text(if (count > 0) "🔍 Search ($count)" else "🔍 Search")
                    },
                )
                for (category in PoiCategory.entries.filter { it != PoiCategory.Searched }) {
                    FilterChip(
                        selected = category in state.enabledCategories,
                        onClick = { onToggleCategory(category) },
                        label = { Text("${category.emoji} ${category.label}") },
                    )
                }
            }
        }
    }
}

@Composable
fun PoiCard(placed: PlacedPoi, onClose: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val poi = placed.poi
    Surface(color = PanelColor, contentColor = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${poi.category.emoji} ${poi.name}",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose) { Text("✕", color = Color.White) }
            }
            val facts = buildList {
                add(poi.category.label)
                add(GeoMath.formatDistance(placed.distanceMeters))
                add("${placed.bearingDegrees.roundToInt()}° ${GeoMath.cardinal(placed.bearingDegrees)}")
                poi.elevation?.let { add("${it.roundToInt()} m high") }
            }
            Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            poi.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis) }
            Text("Source: ${poi.source}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                poi.wikipediaUrl?.let { url -> FilledTonalButton(onClick = { uriHandler.openUri(url) }) { Text("Wikipedia") } }
                poi.website?.let { url -> FilledTonalButton(onClick = { uriHandler.openUri(url) }) { Text("Website") } }
                FilledTonalButton(onClick = {
                    uriHandler.openUri("https://www.openstreetmap.org/?mlat=${poi.latitude}&mlon=${poi.longitude}#map=17/${poi.latitude}/${poi.longitude}")
                }) { Text("Map") }
            }
        }
    }
}
