package com.example.hackapp.citypack

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Optional tint for the Districts of one group (e.g. Toronto divisions). [colors] are ARGB values. */
data class DistrictGroup(val id: String, val colors: List<Long>)

/**
 * Minimal City Pack: everything city-specific that the map components need.
 * Only Montréal exists for now; more cities mean more packs, not more code.
 */
data class CityPack(
    val id: String,
    val districtMapAsset: String,
    val districtGroups: List<DistrictGroup> = emptyList(),
)

val MontrealCityPack = CityPack(id = "mtl", districtMapAsset = "citypacks/mtl/districts.json")

val LocalCityPack = staticCompositionLocalOf { MontrealCityPack }

private val DistrictJson = Json { ignoreUnknownKeys = true }

/** Loads the active pack's districts.json from assets (null while loading or if it cannot be read). */
@Composable
fun rememberDistrictMap(cityPack: CityPack = LocalCityPack.current): State<DistrictMapData?> {
    val context = LocalContext.current
    return produceState<DistrictMapData?>(initialValue = null, cityPack.districtMapAsset) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(cityPack.districtMapAsset).bufferedReader(Charsets.UTF_8).use {
                    DistrictJson.decodeFromString<DistrictMapData>(it.readText())
                }
            }.getOrNull()
        }
    }
}
