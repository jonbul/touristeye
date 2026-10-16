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

/**
 * Geotagged Wikipedia articles near the user (radius capped at 10 km by the API).
 * Each article's short description ("church in San Sebastián", "museo en Bilbao"…) decides its category;
 * streets, stations, schools and similar articles are left out.
 */
class WikipediaPoiSource(
    private val client: HttpClient,
    private val language: String = "en",
) : PoiSource {
    override val name = "Wikipedia"

    override suspend fun fetch(center: GeoLocation, radiusMeters: Int): List<Poi> {
        val response = client.get("https://$language.wikipedia.org/w/api.php") {
            header(HttpHeaders.UserAgent, USER_AGENT)
            parameter("action", "query")
            parameter("format", "json")
            parameter("formatversion", 2)
            parameter("generator", "geosearch")
            parameter("ggscoord", "${center.latitude}|${center.longitude}")
            parameter("ggsradius", radiusMeters.coerceIn(10, 10_000))
            parameter("ggslimit", 200)
            parameter("prop", "description|coordinates")
            parameter("colimit", "max")
        }
        if (!response.status.isSuccess()) error("Wikipedia ${response.status.value}")
        return parse(response.bodyAsText(), language)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(body: String, language: String): List<Poi> =
            json.decodeFromString<WikiResponse>(body).query?.pages.orEmpty().mapNotNull { page ->
                val coordinate = page.coordinates?.firstOrNull() ?: return@mapNotNull null
                val category = categorize(page.title, page.description) ?: return@mapNotNull null
                Poi(
                    id = "wiki:$language/${page.pageid}",
                    name = page.title.substringBefore(" (").ifBlank { page.title },
                    latitude = coordinate.lat,
                    longitude = coordinate.lon,
                    category = category,
                    description = page.description?.replaceFirstChar { it.uppercase() },
                    wikipediaUrl = "https://$language.wikipedia.org/?curid=${page.pageid}",
                    source = "Wikipedia",
                )
            }

        // Word stems in English, Spanish, Basque, French, German, Italian and Portuguese.
        private val excluded = listOf(
            "street", "calle", "kalea", "avenue", "avenida", "rue ", "straße", "strasse", "via ", "rua ", "road",
            "station", "estación", "estacion", "geltoki", "gare", "bahnhof", "stazione", "estação", "parada", "stop",
            "metro", "tram", "bus ", "autobús",
            "school", "escuela", "colegio", "ikastola", "instituto", "école", "schule", "scuola", "university",
            "universidad", "unibertsitate", "université", "hospital", "clínica", "hôpital", "krankenhaus",
            "company", "empresa", "enpresa", "entreprise", "unternehmen", "football", "fútbol", "futbol",
            "club", "team", "equipo", "hotel", "restaurant", "restaurante", "shop", "tienda", "store",
            "neighbourhood", "neighborhood", "barrio", "auzo", "quartier", "district", "distrito",
            "municipality", "municipio", "udalerri", "commune", "gemeinde", "comune", "town", "village",
            "city", "ciudad", "hiri", "pueblo", "localidad", "election", "elecciones", "event", "festival",
            "library", "biblioteca", "liburutegi", "bibliothèque", "bibliothek",
        )

        private val rules: List<Pair<PoiCategory, List<String>>> = listOf(
            PoiCategory.Museum to listOf(
                "museum", "museo", "museoa", "musée", "galler", "galería", "galeria", "pinacoteca", "aquarium", "acuario",
            ),
            PoiCategory.Religious to listOf(
                "church", "iglesia", "eliza", "église", "kirche", "chiesa", "igreja", "cathedral", "catedral",
                "katedral", "cathédrale", "dom ", "duomo", "basilic", "chapel", "capilla", "ermita", "baseliza",
                "chapelle", "kapelle", "cappella", "monaster", "convent", "convento", "abbey", "abadía", "abbaye",
                "sanctuar", "santuario", "temple", "templo", "mosque", "mezquita", "synagogue", "sinagoga", "shrine",
            ),
            PoiCategory.Viewpoint to listOf("viewpoint", "mirador", "begiratoki", "belvedere", "point de vue", "aussichts"),
            PoiCategory.Nature to listOf(
                "mountain", "montaña", "mendi", "montagne", "berg", "montagna", "mount ", "monte ", "peak", "pico",
                "hill", "colina", "colline", "volcano", "volcán", "island", "isla", "uharte", "île", "insel", "isola",
                "beach", "playa", "hondartza", "plage", "strand", "spiaggia", "praia", "park", "parque", "parke",
                "jardín", "garden", "jardin", "garten", "giardino", "river", "río", "ibai", "rivière", "fleuve", "fluss",
                "lake", "lago", "laku", "lac ", "see ", "bay", "bahía", "badia", "baie", "bucht", "cape", "cabo",
                "waterfall", "cascada", "cascade", "wasserfall", "cave", "cueva", "kobazulo", "grotte", "höhle",
                "forest", "bosque", "basoa", "forêt", "wald", "natural", "nature", "naturaleza",
            ),
            PoiCategory.Art to listOf(
                "sculpture", "escultura", "eskultura", "statue", "estatua", "estatua", "fountain", "fuente", "iturri",
                "fontaine", "brunnen", "fontana", "mural", "artwork", "obra de arte", "monumento a", "skulptur",
            ),
            PoiCategory.Historic to listOf(
                "castle", "castillo", "gaztelu", "château", "schloss", "burg", "castello", "castelo", "fort", "fuerte",
                "fortress", "fortaleza", "citadel", "ciudadela", "palace", "palacio", "jauregi", "palais", "palast",
                "palazzo", "tower", "torre", "dorre", "tour ", "turm", "monument", "monumento", "memorial", "ruin",
                "ruina", "archaeolog", "arqueol", "historic", "histórico", "historique", "roman", "romano", "medieval",
                "wall", "muralla", "harresi", "remparts", "stadtmauer", "gate", "puerta", "portal", "bridge", "puente",
                "zubi", "pont", "brücke", "ponte", "aqueduct", "acueducto", "lighthouse", "faro", "itsasargi", "phare",
                "leuchtturm", "heritage", "patrimonio", "ondare", "patrimoine", "square", "plaza", "enparantza",
                "theatre", "theater", "teatro", "antzoki", "théâtre", "town hall", "ayuntamiento", "udaletxe",
                "building", "edificio", "eraikin", "bâtiment", "gebäude", "edificio", "mansion", "villa",
            ),
        )

        private val headSeparators = listOf(" in ", " en ", " de ", " del ", " à ", " dans ", " im ", " in der ", " a ", " em ", " do ", " da ", ",")

        /**
         * Returns null for articles that are not sights (streets, stations, schools…).
         * Only the head of the description counts ("church" in "church in the city of Burgos"),
         * so places merely mentioned in it don't change the category.
         */
        internal fun categorize(title: String, description: String?): PoiCategory? {
            val subject = if (description.isNullOrBlank()) {
                title
            } else {
                val d = description.lowercase()
                headSeparators.mapNotNull { sep -> d.indexOf(sep).takeIf { it > 0 } }.minOrNull()?.let { d.substring(0, it) } ?: d
            }
            val text = " ${subject.lowercase()} "
            if (excluded.any { it in text }) return null
            rules.firstOrNull { (_, words) -> words.any { it in text } }?.let { return it.first }
            val titleText = " ${title.lowercase()} "
            rules.firstOrNull { (_, words) -> words.any { it in titleText } }?.let { return it.first }
            return PoiCategory.Attraction
        }
    }
}

@Serializable
internal data class WikiResponse(val query: WikiQuery? = null)

@Serializable
internal data class WikiQuery(val pages: List<WikiPage> = emptyList())

@Serializable
internal data class WikiPage(
    val pageid: Long,
    val title: String,
    val description: String? = null,
    val coordinates: List<WikiCoordinate>? = null,
)

@Serializable
internal data class WikiCoordinate(val lat: Double, val lon: Double)
