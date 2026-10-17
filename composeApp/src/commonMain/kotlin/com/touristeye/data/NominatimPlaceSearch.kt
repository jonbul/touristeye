package com.touristeye.data

import com.touristeye.geo.GeoLocation
import com.touristeye.geo.GeoMath
import com.touristeye.geo.Poi
import com.touristeye.geo.PoiCategory
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.cos

/** Free-text search for any place: streets, shops, restaurants, addresses, sights… */
interface PlaceSearch {
    suspend fun search(query: String, near: GeoLocation?): List<Poi>
}

/**
 * Place search through OpenStreetMap's public Nominatim server.
 * Looks inside a box of [nearbyMeters] around the user first, nearest first, and only searches
 * everywhere when nothing nearby matches. The usage policy allows about one request per second,
 * so callers search on submit rather than on every keystroke.
 */
class NominatimPlaceSearch(
    private val client: HttpClient,
    private val language: String = "en",
    private val endpoint: String = "https://nominatim.openstreetmap.org/search",
    private val nearbyMeters: Int = 25_000,
    private val limit: Int = 25,
) : PlaceSearch {
    override suspend fun search(query: String, near: GeoLocation?): List<Poi> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        if (near != null) {
            val nearby = request(q, near, bounded = true)
            if (nearby.isNotEmpty()) {
                return nearby.sortedBy { GeoMath.distanceMeters(near.latitude, near.longitude, it.latitude, it.longitude) }
            }
        }
        return request(q, near, bounded = false)
    }

    private suspend fun request(query: String, near: GeoLocation?, bounded: Boolean): List<Poi> {
        val response = client.get(endpoint) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            parameter("q", query)
            parameter("format", "jsonv2")
            parameter("limit", limit)
            parameter("extratags", 1)
            parameter("accept-language", language)
            if (near != null) {
                parameter("viewbox", viewbox(near, nearbyMeters))
                parameter("bounded", if (bounded) 1 else 0)
            }
        }
        if (!response.status.isSuccess()) error("Nominatim ${response.status.value}")
        return parse(response.bodyAsText())
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** "left,top,right,bottom" in degrees, as Nominatim expects. */
        internal fun viewbox(center: GeoLocation, radiusMeters: Int): String {
            val dLat = radiusMeters / 111_320.0
            val dLon = radiusMeters / (111_320.0 * cos(GeoMath.toRad(center.latitude)).coerceAtLeast(0.01))
            return "${center.longitude - dLon},${center.latitude + dLat},${center.longitude + dLon},${center.latitude - dLat}"
        }

        fun parse(body: String): List<Poi> =
            json.decodeFromString<List<NominatimPlace>>(body).mapNotNull { it.toPoi() }.distinctBy { it.id }
    }
}

@Serializable
internal data class NominatimPlace(
    @SerialName("place_id") val placeId: Long = 0,
    @SerialName("osm_type") val osmType: String? = null,
    @SerialName("osm_id") val osmId: Long? = null,
    val lat: String,
    val lon: String,
    val category: String? = null,
    val type: String? = null,
    val name: String? = null,
    @SerialName("display_name") val displayName: String = "",
    val extratags: Map<String, String>? = null,
) {
    fun toPoi(): Poi? {
        val latitude = lat.toDoubleOrNull() ?: return null
        val longitude = lon.toDoubleOrNull() ?: return null
        val parts = displayName.split(", ")
        val title = name?.takeIf { it.isNotBlank() } ?: parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        // The rest of the display name is the address; drop the leading part when it just repeats the name.
        val address = (if (parts.firstOrNull() == title) parts.drop(1) else parts).take(3).joinToString(", ")
        val tags = extratags.orEmpty()
        return Poi(
            id = if (osmType != null && osmId != null) "osm:$osmType/$osmId" else "nominatim:$placeId",
            name = title,
            latitude = latitude,
            longitude = longitude,
            category = PoiCategory.Searched,
            elevation = tags["ele"]?.let(::parseOsmElevation),
            description = listOf(kind(), address).filter { it.isNotBlank() }.joinToString(" · "),
            wikipediaUrl = tags["wikipedia"]?.let(::osmWikipediaUrl),
            website = tags["website"] ?: tags["contact:website"],
            source = "OpenStreetMap (search)",
        )
    }

    /** "Street", "Restaurant", "Bus stop"… from the OSM class and type. */
    fun kind(): String {
        if (category == "highway" && type !in setOf("bus_stop", "traffic_signals", "crossing")) return "Street"
        val raw = type?.takeIf { it != "yes" && it != "unclassified" } ?: category ?: return ""
        return raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
