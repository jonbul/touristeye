package com.touristeye.data

import com.touristeye.geo.GeoLocation
import com.touristeye.geo.Poi

interface PoiSource {
    val name: String
    suspend fun fetch(center: GeoLocation, radiusMeters: Int): List<Poi>
}

/** Tries each source in order and returns the first one that succeeds with results. */
class FallbackPoiSource(private val sources: List<PoiSource>) : PoiSource {
    override val name: String = sources.joinToString(" / ") { it.name }

    override suspend fun fetch(center: GeoLocation, radiusMeters: Int): List<Poi> {
        var lastError: Throwable? = null
        for (source in sources) {
            try {
                val pois = source.fetch(center, radiusMeters)
                if (pois.isNotEmpty()) return pois
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
            }
        }
        lastError?.let { throw it }
        return emptyList()
    }
}

internal const val USER_AGENT = "Touristeye/1.0 (AR sightseeing app)"
