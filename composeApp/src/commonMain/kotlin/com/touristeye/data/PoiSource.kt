package com.touristeye.data

import com.touristeye.geo.GeoLocation
import com.touristeye.geo.GeoMath
import com.touristeye.geo.Poi

interface PoiSource {
    val name: String
    suspend fun fetch(center: GeoLocation, radiusMeters: Int): List<Poi>
}

internal const val USER_AGENT = "Touristeye/1.0 (AR sightseeing app)"

object PoiMerge {
    /**
     * Combines results from several sources, earlier sources first. A later POI is dropped when an
     * earlier one with a matching name lies within [sameNameMeters], or any earlier one within
     * [sameSpotMeters]; its Wikipedia link is kept on the earlier POI when that has none.
     */
    fun merge(lists: List<List<Poi>>, sameNameMeters: Double = 150.0, sameSpotMeters: Double = 25.0): List<Poi> {
        val merged = mutableListOf<Poi>()
        for (list in lists) {
            for (poi in list) {
                val index = merged.indexOfFirst { kept ->
                    val d = GeoMath.distanceMeters(kept.latitude, kept.longitude, poi.latitude, poi.longitude)
                    d <= sameSpotMeters || (d <= sameNameMeters && namesMatch(kept.name, poi.name))
                }
                if (index < 0) {
                    merged += poi
                } else if (merged[index].wikipediaUrl == null && poi.wikipediaUrl != null) {
                    merged[index] = merged[index].copy(wikipediaUrl = poi.wikipediaUrl)
                }
            }
        }
        return merged
    }

    private fun normalize(name: String) = name.lowercase().filter { it.isLetterOrDigit() }

    internal fun namesMatch(a: String, b: String): Boolean {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return false
        return x == y || (minOf(x.length, y.length) >= 5 && (x in y || y in x))
    }
}
