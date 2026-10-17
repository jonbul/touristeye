package com.touristeye.app

import com.touristeye.data.PoiMerge
import com.touristeye.data.PlaceSearch
import com.touristeye.data.PoiSource
import com.touristeye.geo.GeoLocation
import com.touristeye.geo.GeoMath
import com.touristeye.geo.PlacedPoi
import com.touristeye.geo.Poi
import com.touristeye.geo.PoiCategory
import com.touristeye.geo.placedFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

val RadiusOptions = listOf(250, 500, 1_000, 2_000, 5_000, 10_000, 20_000)

data class ArState(
    val location: GeoLocation? = null,
    val radiusMeters: Int = 2_000,
    val enabledCategories: Set<PoiCategory> = PoiCategory.entries.toSet(),
    val allPois: List<Poi> = emptyList(),
    /** POIs inside the radius and enabled categories, nearest first. */
    val visible: List<PlacedPoi> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val selectedId: String? = null,
    /** Places added from the search box, shown on top of the downloaded ones. */
    val searched: List<Poi> = emptyList(),
) {
    val selected: PlacedPoi? get() = selectedId?.let { id -> visible.firstOrNull { it.poi.id == id } }
}

data class SearchState(
    val query: String = "",
    val results: List<Poi> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** True once a search has finished, so "no results" can be told apart from "not searched yet". */
    val searched: Boolean = false,
)

/**
 * Keeps the list of nearby POIs up to date as the user moves or changes the radius.
 * Fetches again only when the user leaves the area of the last download or widens the radius.
 * All sources are asked at once and their results are shown as soon as each one answers.
 */
class ArController(
    private val sources: List<PoiSource>,
    private val scope: CoroutineScope,
    private val placeSearch: PlaceSearch? = null,
    private val radiusDebounce: Duration = 600.milliseconds,
    private val errorBackoff: Duration = 15.seconds,
) {
    private val _state = MutableStateFlow(ArState())
    val state: StateFlow<ArState> = _state.asStateFlow()

    private var fetchedCenter: GeoLocation? = null
    private var fetchedRadius = 0
    private var fetchJob: Job? = null
    private var debounceJob: Job? = null
    private var retryAfter: TimeSource.Monotonic.ValueTimeMark? = null
    private var searchJob: Job? = null

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    fun onLocation(location: GeoLocation) {
        _state.update { it.copy(location = location) }
        recompute()
        maybeFetch()
    }

    fun setRadius(radiusMeters: Int) {
        if (radiusMeters == _state.value.radiusMeters) return
        _state.update { it.copy(radiusMeters = radiusMeters) }
        recompute()
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(radiusDebounce)
            maybeFetch()
        }
    }

    fun toggleCategory(category: PoiCategory) {
        _state.update {
            val set = it.enabledCategories
            it.copy(enabledCategories = if (category in set) set - category else set + category)
        }
        recompute()
    }

    fun select(poiId: String?) = _state.update { it.copy(selectedId = poiId) }

    fun searchPlaces(query: String) {
        val engine = placeSearch ?: return
        searchJob?.cancel()
        if (query.isBlank()) {
            _search.value = SearchState()
            return
        }
        _search.value = SearchState(query = query, loading = true)
        searchJob = scope.launch {
            try {
                val results = engine.search(query, _state.value.location)
                _search.update { it.copy(results = results, loading = false, searched = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _search.update { it.copy(loading = false, searched = true, error = e.message ?: "Search failed") }
            }
        }
    }

    /** Adds places from the search results to the AR view; ones already added are skipped. */
    fun addSearched(pois: List<Poi>) {
        _state.update { s ->
            val known = s.searched.map { it.id }.toSet()
            s.copy(searched = s.searched + pois.filter { it.id !in known })
        }
        recompute()
    }

    fun removeSearched(poiId: String) {
        _state.update { s ->
            s.copy(
                searched = s.searched.filterNot { it.id == poiId },
                selectedId = s.selectedId.takeIf { it != poiId },
            )
        }
        recompute()
    }

    fun retry() {
        retryAfter = null
        fetchedCenter = null
        maybeFetch()
    }

    private fun maybeFetch() {
        val s = _state.value
        val location = s.location ?: return
        if (fetchJob?.isActive == true) return
        retryAfter?.let { if (it.hasNotPassedNow()) return }
        val center = fetchedCenter
        val moved = center?.let { GeoMath.distanceMeters(it, location) } ?: Double.MAX_VALUE
        val needed = center == null || s.radiusMeters > fetchedRadius || moved > max(150.0, fetchedRadius * 0.25)
        if (!needed) return

        val radius = s.radiusMeters
        fetchJob = scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val results = arrayOfNulls<List<Poi>>(sources.size)
            var lastError: Throwable? = null
            coroutineScope {
                sources.forEachIndexed { i, source ->
                    launch {
                        try {
                            results[i] = source.fetch(location, radius)
                            val merged = PoiMerge.merge(results.filterNotNull())
                            _state.update { it.copy(allPois = merged) }
                            recompute()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            lastError = e
                        }
                    }
                }
            }
            if (results.any { it != null }) {
                fetchedCenter = location
                fetchedRadius = radius
                retryAfter = null
                _state.update { it.copy(loading = false) }
            } else {
                retryAfter = TimeSource.Monotonic.markNow() + errorBackoff
                _state.update { it.copy(loading = false, error = lastError?.message ?: "Could not load places") }
            }
        }
    }

    private fun recompute() {
        _state.update { s ->
            val loc = s.location ?: return@update s
            val searchedIds = s.searched.map { it.id }.toSet()
            val nearby = s.allPois
                .asSequence()
                .filter { it.category in s.enabledCategories && it.id !in searchedIds }
                .map { it.placedFrom(loc) }
                .filter { it.distanceMeters <= s.radiusMeters }
            // The user picked these, so they stay visible whatever the radius or category filters.
            val visible = (nearby + s.searched.asSequence().map { it.placedFrom(loc) })
                .sortedBy { it.distanceMeters }
                .toList()
            s.copy(visible = visible)
        }
    }
}
