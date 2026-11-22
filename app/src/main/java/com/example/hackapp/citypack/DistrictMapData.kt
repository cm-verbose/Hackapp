package com.example.hackapp.citypack

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable

/** A real-world position (Event / Place coordinates). */
data class GeoPoint(val lat: Double, val lng: Double)

/**
 * Optional lon/lat -> map-unit projection written by `tools/geojson_to_districts.py`
 * (equirectangular with cos(lat0) correction). Null for hand-traced maps, in which case Stops are
 * placed at their District's label anchor.
 */
@Serializable
data class MapProjection(val kx: Double, val minX: Double, val minY: Double, val scale: Double) {
    fun project(p: GeoPoint) = Offset(
        ((p.lng * kx - minX) * scale).toFloat(),
        ((-p.lat - minY) * scale).toFloat(),
    )
}

/**
 * One District. [path] is SVG path data in map units, [label] the anchor for badges.
 * Montréal ids are the `area.code` values of places.db (e.g. `ville-marie`); [groupId] is `linked`
 * for the independent linked cities and null for the 19 boroughs.
 */
@Serializable
data class DistrictShape(
    val id: String,
    val name: String,
    val groupId: String? = null,
    val label: List<Float>,
    val path: String,
    /** A 2–3 letter name for the zoomed-out map, such as `VM` for Ville-Marie. */
    val short: String? = null,
)

/** Content of `assets/citypacks/<city>/districts.json`. [viewBox] is `[x, y, width, height]`. */
@Serializable
data class DistrictMapData(
    val cityId: String,
    val viewBox: List<Float>,
    val districts: List<DistrictShape>,
    val projection: MapProjection? = null,
)
