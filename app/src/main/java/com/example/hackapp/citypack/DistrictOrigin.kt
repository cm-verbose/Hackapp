package com.example.hackapp.citypack

import com.example.hackapp.data.model.LatLng
import kotlinx.coroutines.CancellationException

/**
 * A representative point for a District: its label anchor, converted back to coordinates with the
 * pack's own projection. Null when the id, the District, its anchor or the projection is missing.
 */
fun DistrictMapData.districtPoint(districtId: String?): LatLng? {
    if (districtId.isNullOrBlank()) return null
    val map = projection ?: return null
    val anchor = districts.firstOrNull { it.id == districtId }?.label ?: return null
    if (anchor.size < 2 || map.scale == 0.0 || map.kx == 0.0) return null
    val lng = (anchor[0] / map.scale + map.minX) / map.kx
    val lat = -(anchor[1] / map.scale + map.minY)
    return LatLng(lat, lng).takeIf { lat.isFinite() && lng.isFinite() }
}

/**
 * Where the first Leg starts: the Profile's District of stay. Any missing or unreadable input gives
 * null, because an unknown origin must never block Start.
 */
suspend fun resolveStayOrigin(
    loadDistrictId: suspend () -> String?,
    loadMap: suspend () -> DistrictMapData,
): LatLng? = try {
    loadDistrictId()?.takeIf { it.isNotBlank() }?.let { loadMap().districtPoint(it) }
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}
