package com.touristeye.geo

enum class PoiCategory(val emoji: String, val label: String) {
    Attraction("⭐", "Sights"),
    Museum("🏛️", "Museums"),
    Historic("🏰", "Historic"),
    Religious("⛪", "Religious"),
    Viewpoint("🔭", "Viewpoints"),
    Nature("⛰️", "Nature"),
}

data class Poi(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: PoiCategory,
    /** Metres above sea level, when the source knows it (e.g. mountain peaks). */
    val elevation: Double? = null,
    val description: String? = null,
    val wikipediaUrl: String? = null,
    val website: String? = null,
    val source: String,
)

/** A POI seen from the user's current location. */
data class PlacedPoi(
    val poi: Poi,
    val distanceMeters: Double,
    val bearingDegrees: Double,
    val elevationDegrees: Double,
)

fun Poi.placedFrom(user: GeoLocation): PlacedPoi {
    val d = GeoMath.distanceMeters(user.latitude, user.longitude, latitude, longitude)
    val elevationAngle = if (elevation != null && user.altitude != null) {
        GeoMath.elevationAngleDegrees(user.altitude, elevation, d)
    } else 0.0
    return PlacedPoi(
        poi = this,
        distanceMeters = d,
        bearingDegrees = GeoMath.bearingDegrees(user.latitude, user.longitude, latitude, longitude),
        elevationDegrees = elevationAngle,
    )
}
