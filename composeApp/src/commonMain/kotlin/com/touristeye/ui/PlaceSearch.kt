package com.touristeye.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.touristeye.app.SearchState
import com.touristeye.geo.GeoLocation
import com.touristeye.geo.GeoMath
import com.touristeye.geo.Poi

/**
 * Search box for any place (streets, shops, addresses…). The ticked results are added to the AR view;
 * places added before are listed on top so they can be removed again.
 */
@Composable
fun PlaceSearchDialog(
    search: SearchState,
    added: List<Poi>,
    location: GeoLocation?,
    onSearch: (String) -> Unit,
    onAdd: (List<Poi>) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(search.query) }
    var picked by remember(search.results) { mutableStateOf(emptySet<String>()) }
    val addedIds = added.map { it.id }.toSet()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (search.query.isEmpty()) focus.requestFocus() }

    fun submit() {
        keyboard?.hide()
        onSearch(query)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Search places", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Street, shop, restaurant, address…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    trailingIcon = { TextButton(onClick = ::submit, enabled = query.isNotBlank()) { Text("🔍") } },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                val status = when {
                    search.loading -> "⏳ Searching…"
                    search.error != null -> "⚠️ Search failed. Try again"
                    search.searched && search.results.isEmpty() -> "Nothing found for \"${search.query}\""
                    else -> null
                }
                status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

                Column(Modifier.weight(1f, fill = false).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (added.isNotEmpty()) {
                        Text("On the view", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 4.dp))
                        for (poi in added) {
                            PlaceRow(poi, location) {
                                TextButton(onClick = { onRemove(poi.id) }) { Text("✕") }
                            }
                        }
                        if (search.results.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    }
                    for (poi in search.results) {
                        val already = poi.id in addedIds
                        val toggle = { picked = if (poi.id in picked) picked - poi.id else picked + poi.id }
                        PlaceRow(poi, location, onClick = if (already) null else toggle) {
                            Checkbox(
                                checked = already || poi.id in picked,
                                onCheckedChange = { toggle() },
                                enabled = !already,
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    TextButton(
                        onClick = {
                            onAdd(search.results.filter { it.id in picked })
                            onDismiss()
                        },
                        enabled = picked.isNotEmpty(),
                    ) { Text(if (picked.isEmpty()) "Add" else "Add (${picked.size})") }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(
    poi: Poi,
    location: GeoLocation?,
    onClick: (() -> Unit)? = null,
    action: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(poi.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val distance = location?.let {
                GeoMath.formatDistance(GeoMath.distanceMeters(it.latitude, it.longitude, poi.latitude, poi.longitude))
            }
            val details = listOfNotNull(distance, poi.description).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        action()
    }
}
