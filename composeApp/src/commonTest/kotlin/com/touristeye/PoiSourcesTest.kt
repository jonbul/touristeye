package com.touristeye

import com.touristeye.app.ArController
import com.touristeye.data.NominatimPlaceSearch
import com.touristeye.data.PlaceSearch
import com.touristeye.data.PoiMerge
import com.touristeye.data.OverpassPoiSource
import com.touristeye.data.PoiSource
import com.touristeye.data.WikipediaPoiSource
import com.touristeye.geo.GeoLocation
import com.touristeye.geo.Poi
import com.touristeye.geo.PoiCategory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val OVERPASS_SAMPLE = """
{"version":0.6,"elements":[
 {"type":"node","id":1,"lat":43.3183,"lon":-1.9812,"tags":{"name":"Monte Igueldo","natural":"peak","ele":"181 m"}},
 {"type":"way","id":2,"center":{"lat":43.3237,"lon":-1.9858},"tags":{"name":"Peine del Viento","name:es":"Peine del Viento","tourism":"artwork","wikipedia":"es:Peine del Viento"}},
 {"type":"relation","id":3,"center":{"lat":43.3225,"lon":-1.9840},"tags":{"name":"Catedral del Buen Pastor","amenity":"place_of_worship","wikidata":"Q1"}},
 {"type":"node","id":4,"lat":43.32,"lon":-1.98,"tags":{"tourism":"viewpoint"}},
 {"type":"node","id":5,"lat":43.32,"lon":-1.98,"tags":{"name":"San Telmo Museoa","tourism":"museum","website":"https://www.santelmomuseoa.eus"}},
 {"type":"node","id":6,"lat":43.3240,"lon":-1.9860,"tags":{"name":"Castillo de la Mota","historic":"castle"}}
]}
"""

class PoiSourcesTest {
    @Test
    fun parsesOverpass() {
        val pois = OverpassPoiSource.parse(OVERPASS_SAMPLE, language = "es").associateBy { it.name }
        assertEquals(5, pois.size, "unnamed viewpoint is skipped")
        assertEquals(PoiCategory.Art, pois.getValue("Peine del Viento").category)
        val peak = pois.getValue("Monte Igueldo")
        assertEquals(PoiCategory.Nature, peak.category)
        assertEquals(181.0, peak.elevation)
        val comb = pois.getValue("Peine del Viento")
        assertEquals(43.3237, comb.latitude)
        assertEquals("https://es.wikipedia.org/wiki/Peine_del_Viento", comb.wikipediaUrl)
        assertEquals(PoiCategory.Religious, pois.getValue("Catedral del Buen Pastor").category)
        assertEquals(PoiCategory.Museum, pois.getValue("San Telmo Museoa").category)
        assertEquals("https://www.santelmomuseoa.eus", pois.getValue("San Telmo Museoa").website)
        assertEquals(PoiCategory.Historic, pois.getValue("Castillo de la Mota").category)
    }

    @Test
    fun overpassPostsAroundQueryAndFallsBackToSecondEndpoint() = runTest {
        val requested = mutableListOf<String>()
        var body = ""
        val client = HttpClient(MockEngine { request ->
            requested += request.url.host
            body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            if (request.url.host == "first.example") respondError(HttpStatusCode.TooManyRequests)
            else respond(OVERPASS_SAMPLE)
        })
        val source = OverpassPoiSource(
            client,
            endpoints = listOf("https://first.example/api/interpreter", "https://second.example/api/interpreter"),
        )
        val pois = source.fetch(GeoLocation(43.32, -1.98), 1500)
        assertEquals(listOf("first.example", "second.example"), requested)
        assertEquals(5, pois.size)
        assertTrue("%5Bbbox%3A43.30653%2C-1.99852" in body, body)
    }

    @Test
    fun parsesWikipediaGeosearchWithCategories() {
        val json = """{"batchcomplete":true,"query":{"pages":[
          {"pageid":1,"title":"Catedral del Buen Pastor (San Sebastián)","description":"catedral en San Sebastián, España","coordinates":[{"lat":43.317,"lon":-1.98}]},
          {"pageid":2,"title":"Calle de San Juan (San Sebastián)","description":"calle de San Sebastián","coordinates":[{"lat":43.32,"lon":-1.98}]},
          {"pageid":3,"title":"Boulevard (parada en San Sebastián)","coordinates":[{"lat":43.32,"lon":-1.98}]},
          {"pageid":4,"title":"Teatro Principal (San Sebastián)","description":"teatro en Donostia/San Sebastián","coordinates":[{"lat":43.322,"lon":-1.985}]},
          {"pageid":5,"title":"Monte Urgull","description":"monte en San Sebastián","coordinates":[{"lat":43.325,"lon":-1.986}]},
          {"pageid":6,"title":"San Telmo Museoa","description":"museo en San Sebastián","coordinates":[{"lat":43.324,"lon":-1.984}]},
          {"pageid":7,"title":"Parte Vieja","coordinates":[{"lat":43.323,"lon":-1.985}]},
          {"pageid":8,"title":"Sin coordenadas","description":"museo"}
        ]}}"""
        val pois = WikipediaPoiSource.parse(json, "es").associate { it.name to it.category }
        assertEquals(
            mapOf(
                "Catedral del Buen Pastor" to PoiCategory.Religious,
                "Teatro Principal" to PoiCategory.Historic,
                "Monte Urgull" to PoiCategory.Nature,
                "San Telmo Museoa" to PoiCategory.Museum,
                "Parte Vieja" to PoiCategory.Attraction,
            ),
            pois,
        )
    }

    @Test
    fun wikipediaCategoriesUseTheHeadOfTheDescription() {
        assertEquals(PoiCategory.Religious, WikipediaPoiSource.categorize("Burgos Cathedral", "cathedral in the city of Burgos, Spain"))
        assertEquals(PoiCategory.Art, WikipediaPoiSource.categorize("Peine del Viento", "escultura de Eduardo Chillida"))
        assertNull(WikipediaPoiSource.categorize("Amara", "barrio de San Sebastián"))
        assertEquals(PoiCategory.Historic, WikipediaPoiSource.categorize("Castillo de la Mota", null))
    }

    @Test
    fun mergeDropsDuplicatesFromLaterSources() {
        val osm = listOf(poi("osm", 43.3225, -1.9840).copy(name = "Catedral del Buen Pastor"))
        val wiki = listOf(
            poi("wiki1", 43.3230, -1.9845).copy(name = "Catedral del Buen Pastor de San Sebastián", wikipediaUrl = "https://w/1"),
            poi("wiki2", 43.3300, -1.9900).copy(name = "Monte Ulia"),
        )
        val merged = PoiMerge.merge(listOf(osm, wiki))
        assertEquals(listOf("osm", "wiki2"), merged.map { it.id })
        assertEquals("https://w/1", merged[0].wikipediaUrl)
    }

    @Test
    fun controllerFetchesFiltersAndRefetchesOnlyWhenNeeded() = runTest {
        val source = FakeSource(
            listOf(
                poi("near", 43.3210, -1.98), // ~110 m north
                poi("far", 43.3400, -1.98), // ~2.2 km north
            ),
        )
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val controller = ArController(listOf(source, FakeSource(error = true)), testScope)

        controller.setRadius(1_000)
        controller.onLocation(GeoLocation(43.32, -1.98))
        testScope.advanceUntilIdle()
        assertEquals(1, source.calls)
        assertEquals(listOf("near"), controller.state.value.visible.map { it.poi.id })

        // A few metres away: no new download.
        controller.onLocation(GeoLocation(43.3201, -1.98))
        testScope.advanceUntilIdle()
        assertEquals(1, source.calls)

        // Wider radius: downloads again and shows the far place.
        controller.setRadius(5_000)
        testScope.advanceUntilIdle()
        assertEquals(2, source.calls)
        assertEquals(listOf("near", "far"), controller.state.value.visible.map { it.poi.id })

        // Narrowing filters locally.
        controller.setRadius(1_000)
        testScope.advanceUntilIdle()
        assertEquals(2, source.calls)
        assertEquals(1, controller.state.value.visible.size)

        controller.toggleCategory(PoiCategory.Attraction)
        assertTrue(controller.state.value.visible.isEmpty())
        assertNull(controller.state.value.error, "one source failing is not an error")
    }

    @Test
    fun controllerReportsErrorWhenAllSourcesFail() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val controller = ArController(listOf(FakeSource(error = true), FakeSource(error = true)), testScope)
        controller.onLocation(GeoLocation(43.32, -1.98))
        testScope.advanceUntilIdle()
        assertEquals("boom", controller.state.value.error)
        assertTrue(!controller.state.value.loading)
    }

    @Test
    fun parsesNominatimResults() {
        val json = """[
          {"place_id":1,"osm_type":"way","osm_id":10,"lat":"43.3215","lon":"-1.9850","category":"highway","type":"pedestrian","name":"Calle Mayor","display_name":"Calle Mayor, Parte Vieja, Donostia/San Sebastián, Gipuzkoa, España"},
          {"place_id":2,"osm_type":"node","osm_id":20,"lat":"43.3230","lon":"-1.9860","category":"amenity","type":"restaurant","name":"Bar Txepetxa","display_name":"Bar Txepetxa, 5, Pescadería kalea, Parte Vieja, Donostia","extratags":{"website":"https://txepetxa.example","wikipedia":"es:Txepetxa"}},
          {"place_id":3,"lat":"43.31","lon":"-1.97","category":"place","type":"house","name":"","display_name":"12, Avenida de la Libertad, Centro"},
          {"place_id":4,"osm_type":"node","osm_id":20,"lat":"43.3230","lon":"-1.9860","category":"amenity","type":"restaurant","name":"Bar Txepetxa","display_name":"duplicate"}
        ]"""
        val pois = NominatimPlaceSearch.parse(json)
        assertEquals(listOf("osm:way/10", "osm:node/20", "nominatim:3"), pois.map { it.id })
        assertTrue(pois.all { it.category == PoiCategory.Searched })
        assertEquals("Street · Parte Vieja, Donostia/San Sebastián, Gipuzkoa", pois[0].description)
        assertEquals("https://txepetxa.example", pois[1].website)
        assertEquals("https://es.wikipedia.org/wiki/Txepetxa", pois[1].wikipediaUrl)
        assertEquals("Restaurant · 5, Pescadería kalea, Parte Vieja", pois[1].description)
        assertEquals("12", pois[2].name)
    }

    @Test
    fun nominatimSearchesNearbyFirstThenEverywhere() = runTest {
        val bounded = mutableListOf<String?>()
        val client = HttpClient(MockEngine { request ->
            bounded += request.url.parameters["bounded"]
            assertTrue(request.url.parameters["viewbox"] != null)
            if (request.url.parameters["bounded"] == "1") respond("[]")
            else respond("""[{"place_id":5,"osm_type":"node","osm_id":5,"lat":"40.4","lon":"-3.7","category":"shop","type":"bakery","name":"Far bakery","display_name":"Far bakery, Madrid"}]""")
        })
        val results = NominatimPlaceSearch(client).search("bakery", GeoLocation(43.32, -1.98))
        assertEquals(listOf<String?>("1", "0"), bounded.toList())
        assertEquals(listOf("Far bakery"), results.map { it.name })
    }

    @Test
    fun controllerAddsSearchedPlacesWhateverTheRadiusAndFilters() = runTest {
        val far = poi("osm:node/9", 43.40, -1.98).copy(category = PoiCategory.Searched) // ~9 km
        val search = object : PlaceSearch {
            override suspend fun search(query: String, near: GeoLocation?) = listOf(far)
        }
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val controller = ArController(listOf(FakeSource(listOf(poi("near", 43.3210, -1.98)))), testScope, placeSearch = search)
        controller.setRadius(1_000)
        controller.onLocation(GeoLocation(43.32, -1.98))
        testScope.advanceUntilIdle()

        controller.searchPlaces("faro")
        testScope.advanceUntilIdle()
        assertEquals(listOf(far), controller.search.value.results)

        controller.addSearched(controller.search.value.results)
        controller.addSearched(controller.search.value.results)
        assertEquals(listOf("near", "osm:node/9"), controller.state.value.visible.map { it.poi.id })

        controller.toggleCategory(PoiCategory.Attraction)
        assertEquals(listOf("osm:node/9"), controller.state.value.visible.map { it.poi.id })

        controller.select("osm:node/9")
        controller.removeSearched("osm:node/9")
        assertTrue(controller.state.value.visible.isEmpty())
        assertNull(controller.state.value.selectedId)
    }

    private fun poi(id: String, lat: Double, lon: Double) =
        Poi(id, id, lat, lon, PoiCategory.Attraction, source = "fake")

    private class FakeSource(val pois: List<Poi> = emptyList(), val error: Boolean = false) : PoiSource {
        override val name = "fake"
        var calls = 0
        override suspend fun fetch(center: GeoLocation, radiusMeters: Int): List<Poi> {
            calls++
            if (error) error("boom")
            return pois
        }
    }
}
