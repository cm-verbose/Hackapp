// Add to app/src/main/java/com/ets/hackapp/citypack/DistrictMapData.kt

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos

/** A real-world position (Event / Place coordinates from Firestore). */
data class GeoPoint(val lat: Double, val lng: Double)

/**
 * Optional lon/lat → map-unit projection written by tools/geojson_to_districts.py
 * (equirectangular with cos(lat0) correction). Null for hand-traced maps (current Montréal),
 * in which case Stops are placed at their District's label anchor.
 */
@Serializable
data class MapProjection(val kx: Double, val minX: Double, val minY: Double, val scale: Double) {
    fun project(p: GeoPoint) = Offset(
        ((p.lng * kx - minX) * scale).toFloat(),
        ((-p.lat - minY) * scale).toFloat(),
    )
}

// …and add this field to DistrictMapData:
//     val projection: MapProjection? = null,
