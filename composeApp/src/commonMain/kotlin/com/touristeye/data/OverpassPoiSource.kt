package com.touristeye.data

import com.touristeye.geo.GeoLocation
import com.touristeye.geo.Poi
import com.touristeye.geo.PoiCategory
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Tourist POIs from OpenStreetMap through the Overpass API. */
class OverpassPoiSource(
    private val client: HttpClient,
    private val endpoints: List<String> = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    ),
    private val maxResults: Int = 400,
    private val language: String = "en",
) : PoiSource {
    override val name = "OpenStreetMap"

    override suspend fun fetch(center: GeoLocation, radiusMeters: Int): List<Poi> {
        val query = buildQuery(center, radiusMeters)
        var lastError: Throwable? = null
        for (endpoint in endpoints) {
            try {
                val response = client.submitForm(endpoint, parameters { append("data", query) }) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                }
                if (!response.status.isSuccess()) error("Overpass ${response.status.value}")
                return parse(response.bodyAsText(), language)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No Overpass endpoint")
    }

    fun buildQuery(center: GeoLocation, radiusMeters: Int): String {
        val around = "(around:$radiusMeters,${center.latitude},${center.longitude})"
        return """
            [out:json][timeout:25];
            (
              nwr["tourism"~"^(attraction|museum|viewpoint|artwork|gallery|zoo|theme_park|aquarium)$"]["name"]$around;
              nwr["historic"~"^(monument|castle|memorial|ruins|archaeological_site|fort|tower|city_gate|palace|manor|church|monastery|ship|aqueduct|bridge)$"]["name"]$around;
              nwr["amenity"="place_of_worship"]["name"]["wikidata"]$around;
              nwr["building"~"^(cathedral|basilica)$"]["name"]$around;
              node["natural"~"^(peak|volcano)$"]["name"]$around;
              nwr["man_made"~"^(lighthouse|tower)$"]["name"]["wikidata"]$around;
            );
            out center tags qt $maxResults;
        """.trimIndent()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(body: String, language: String = "en"): List<Poi> =
            json.decodeFromString<OverpassResponse>(body).elements
                .mapNotNull { it.toPoi(language) }
                .distinctBy { it.name.lowercase() to (it.latitude * 1000).toInt() to (it.longitude * 1000).toInt() }

        internal fun categorize(tags: Map<String, String>): PoiCategory? {
            val tourism = tags["tourism"]
            val historic = tags["historic"]
            return when {
                tourism == "museum" || tourism == "gallery" -> PoiCategory.Museum
                tourism == "viewpoint" -> PoiCategory.Viewpoint
                tags["natural"] == "peak" || tags["natural"] == "volcano" -> PoiCategory.Nature
                tags["amenity"] == "place_of_worship" || tags["building"] in setOf("cathedral", "basilica") ||
                    historic == "church" || historic == "monastery" -> PoiCategory.Religious
                historic != null -> PoiCategory.Historic
                tourism != null -> PoiCategory.Attraction
                tags["man_made"] != null -> PoiCategory.Attraction
                else -> null
            }
        }
    }
}

@Serializable
internal data class OverpassResponse(val elements: List<OverpassElement> = emptyList())

@Serializable
internal data class OverpassCenter(val lat: Double, val lon: Double)

@Serializable
internal data class OverpassElement(
    val type: String,
    val id: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val center: OverpassCenter? = null,
    val tags: Map<String, String> = emptyMap(),
) {
    fun toPoi(language: String): Poi? {
        val name = tags["name:$language"] ?: tags["name"] ?: return null
        val latitude = lat ?: center?.lat ?: return null
        val longitude = lon ?: center?.lon ?: return null
        val category = OverpassPoiSource.categorize(tags) ?: return null
        return Poi(
            id = "osm:$type/$id",
            name = name,
            latitude = latitude,
            longitude = longitude,
            category = category,
            elevation = tags["ele"]?.let(::parseElevation),
            description = tags["description"],
            wikipediaUrl = tags["wikipedia"]?.let(::wikipediaUrl),
            website = tags["website"] ?: tags["contact:website"],
            source = "OpenStreetMap",
        )
    }

    private fun parseElevation(raw: String): Double? =
        raw.trim().removeSuffix("m").trim().replace(',', '.').toDoubleOrNull()

    /** OSM stores "lang:Title"; turn it into a URL. */
    private fun wikipediaUrl(tag: String): String? {
        val parts = tag.split(":", limit = 2)
        if (parts.size != 2 || parts[0].length !in 2..12) return null
        return "https://${parts[0]}.wikipedia.org/wiki/${parts[1].trim().replace(' ', '_')}"
    }
}
