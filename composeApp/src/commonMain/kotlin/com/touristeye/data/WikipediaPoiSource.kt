package com.touristeye.data

import com.touristeye.geo.GeoLocation
import com.touristeye.geo.Poi
import com.touristeye.geo.PoiCategory
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Geotagged Wikipedia articles near the user (radius capped at 10 km by the API). */
class WikipediaPoiSource(
    private val client: HttpClient,
    private val language: String = "en",
) : PoiSource {
    override val name = "Wikipedia"

    override suspend fun fetch(center: GeoLocation, radiusMeters: Int): List<Poi> {
        val response = client.get("https://$language.wikipedia.org/w/api.php") {
            header(HttpHeaders.UserAgent, USER_AGENT)
            parameter("action", "query")
            parameter("list", "geosearch")
            parameter("gscoord", "${center.latitude}|${center.longitude}")
            parameter("gsradius", radiusMeters.coerceIn(10, 10_000))
            parameter("gslimit", 200)
            parameter("format", "json")
        }
        if (!response.status.isSuccess()) error("Wikipedia ${response.status.value}")
        return parse(response.bodyAsText(), language)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(body: String, language: String): List<Poi> =
            json.decodeFromString<GeoSearchResponse>(body).query?.geosearch.orEmpty().map {
                Poi(
                    id = "wiki:$language/${it.pageid}",
                    name = it.title,
                    latitude = it.lat,
                    longitude = it.lon,
                    category = PoiCategory.Attraction,
                    wikipediaUrl = "https://$language.wikipedia.org/?curid=${it.pageid}",
                    source = "Wikipedia",
                )
            }
    }
}

@Serializable
internal data class GeoSearchResponse(val query: GeoSearchQuery? = null)

@Serializable
internal data class GeoSearchQuery(val geosearch: List<GeoSearchItem> = emptyList())

@Serializable
internal data class GeoSearchItem(val pageid: Long, val title: String, val lat: Double, val lon: Double)
