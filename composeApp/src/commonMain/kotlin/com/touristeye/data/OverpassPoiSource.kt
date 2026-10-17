package com.touristeye.data

import com.touristeye.geo.GeoLocation
import com.touristeye.geo.GeoMath
import com.touristeye.geo.Poi
import com.touristeye.geo.PoiCategory
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.cos
import kotlin.math.roundToLong

/**
 * Tourist POIs from OpenStreetMap through the public Overpass API.
 * The public servers are often busy, so if one hasn't answered after [hedgeAfterMillis]
 * (or fails) the next one is asked too, and the first good answer wins.
 */
class OverpassPoiSource(
    private val client: HttpClient,
    private val endpoints: List<String> = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
    ),
    private val maxResults: Int = 500,
    private val language: String = "en",
    private val hedgeAfterMillis: Long = 12_000,
) : PoiSource {
    override val name = "OpenStreetMap"

    override suspend fun fetch(center: GeoLocation, radiusMeters: Int): List<Poi> = coroutineScope {
        val query = buildQuery(center, radiusMeters)
        val results = Channel<Result<List<Poi>>>(Channel.UNLIMITED)
        val jobs = mutableListOf<Job>()
        var started = 0
        var finished = 0
        var lastError: Throwable? = null

        fun startNext() {
            if (started >= endpoints.size) return
            val endpoint = endpoints[started++]
            jobs += launch {
                val result = try {
                    Result.success(request(endpoint, query))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Result.failure(e)
                }
                results.send(result)
            }
        }

        startNext()
        while (finished < started) {
            val result = withTimeoutOrNull(hedgeAfterMillis) { results.receive() }
            if (result == null) {
                startNext()
                continue
            }
            finished++
            result.onSuccess { pois ->
                jobs.forEach { it.cancel() }
                return@coroutineScope pois
            }
            lastError = result.exceptionOrNull()
            startNext()
        }
        throw lastError ?: IllegalStateException("No Overpass endpoint")
    }

    private suspend fun request(endpoint: String, query: String): List<Poi> {
        val response = client.submitForm(endpoint, parameters { append("data", query) }) {
            header(HttpHeaders.UserAgent, USER_AGENT)
        }
        if (!response.status.isSuccess()) error("Overpass ${response.status.value}")
        return parse(response.bodyAsText(), language)
    }

    /**
     * Uses a bounding box instead of `around`: Overpass can then use its spatial index for every
     * tag filter, which is much faster. Results outside the circle are filtered out by distance later.
     */
    fun buildQuery(center: GeoLocation, radiusMeters: Int): String {
        val dLat = radiusMeters / 111_320.0
        val dLon = radiusMeters / (111_320.0 * cos(GeoMath.toRad(center.latitude)).coerceAtLeast(0.01))
        fun f(v: Double) = ((v * 100_000).roundToLong() / 100_000.0).toString()
        val bbox = "${f(center.latitude - dLat)},${f(center.longitude - dLon)},${f(center.latitude + dLat)},${f(center.longitude + dLon)}"
        return """
            [out:json][timeout:50][bbox:$bbox];
            (
              nwr["tourism"~"^(attraction|museum|viewpoint|artwork|gallery|zoo|theme_park|aquarium)$"]["name"];
              nwr["historic"~"^(monument|castle|memorial|ruins|archaeological_site|fort|tower|city_gate|palace|manor|church|monastery|ship|aqueduct|bridge)$"]["name"];
              nwr["amenity"="place_of_worship"]["name"]["wikidata"];
              nwr["building"~"^(cathedral|basilica)$"]["name"];
              node["natural"~"^(peak|volcano)$"]["name"];
              nwr["man_made"~"^(lighthouse|tower)$"]["name"]["wikidata"];
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
                tourism == "artwork" -> PoiCategory.Art
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
            elevation = tags["ele"]?.let(::parseOsmElevation),
            description = tags["description"],
            wikipediaUrl = tags["wikipedia"]?.let(::osmWikipediaUrl),
            website = tags["website"] ?: tags["contact:website"],
            source = "OpenStreetMap",
        )
    }
}

internal fun parseOsmElevation(raw: String): Double? =
    raw.trim().removeSuffix("m").trim().replace(',', '.').toDoubleOrNull()

/** OSM stores "lang:Title"; turn it into a URL. */
internal fun osmWikipediaUrl(tag: String): String? {
    val parts = tag.split(":", limit = 2)
    if (parts.size != 2 || parts[0].length !in 2..12) return null
    return "https://${parts[0]}.wikipedia.org/wiki/${parts[1].trim().replace(' ', '_')}"
}
