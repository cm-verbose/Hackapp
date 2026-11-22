package com.example.hackapp.citypack

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.example.hackapp.data.model.LocalizedText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Optional tint for the Districts of one group (e.g. Toronto divisions). [colors] are ARGB values. */
data class DistrictGroup(val id: String, val colors: List<Long>)

/** City-specific wording that overrides the default string resources. */
enum class LabelKey { DISTRICT, DISTRICT_PLURAL }

/** A city's own Transit App. A null [packageName] goes straight to the directions fallback. */
data class TransitAppConfig(val displayName: String, val packageName: String?)

data class FareTable(val agencyName: String, val singleRide: LocalizedText)

data class RideHailConfig(val displayName: String, val packageName: String)

/** Inputs for distance-based Leg estimates. Speeds are door-to-door averages. */
data class LegEstimatorConfig(
    val routeDetourFactor: Double = 1.3,
    val walkMetersPerMinute: Double = 80.0,
    val transitMetersPerMinute: Double = 300.0,
    val rideHailMetersPerMinute: Double = 400.0,
    val walkBufferMinutes: Int = 5,
    val transitBufferMinutes: Int = 10,
    val rideHailBufferMinutes: Int = 8,
)

/** The inclusive range of dates covered by the pack's Event snapshot. */
data class EventCoverage(val firstDate: LocalDate, val lastDate: LocalDate)

/**
 * The complete set of city-specific content and options. It is data: services are built from it
 * by [com.example.hackapp.services.CityServicesFactory], so screens never name a city or provider.
 */
data class CityPack(
    val id: String,
    val displayName: LocalizedText,
    val timeZone: ZoneId,
    val defaultLocale: Locale,
    val districtMapAsset: String,
    val eventSnapshotAsset: String,
    val eventCoverage: EventCoverage,
    val transitApp: TransitAppConfig,
    val fareTable: FareTable,
    val rideHail: RideHailConfig,
    val attribution: LocalizedText,
    val labels: Map<LabelKey, LocalizedText> = emptyMap(),
    val legEstimator: LegEstimatorConfig = LegEstimatorConfig(),
    val districtGroups: List<DistrictGroup> = emptyList(),
)

val MontrealCityPack = CityPack(
    id = "mtl",
    displayName = LocalizedText(fr = "Montréal", en = "Montreal"),
    timeZone = ZoneId.of("America/Toronto"),
    defaultLocale = Locale.CANADA_FRENCH,
    districtMapAsset = "citypacks/mtl/districts.json",
    eventSnapshotAsset = "citypacks/mtl/events.json",
    eventCoverage = EventCoverage(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 12, 6)),
    transitApp = TransitAppConfig(displayName = "Chrono", packageName = "quebec.artm.chrono"),
    fareTable = FareTable(
        agencyName = "ARTM",
        singleRide = LocalizedText(fr = "1 passage", en = "1 ride"),
    ),
    rideHail = RideHailConfig(displayName = "Uber", packageName = "com.ubercab"),
    attribution = LocalizedText(
        fr = "Données\u00A0: Has Things (hasthings.com), Ville de Montréal, © les contributeurs d’OpenStreetMap",
        en = "Data: Has Things (hasthings.com), Ville de Montréal, © OpenStreetMap contributors",
    ),
    labels = mapOf(
        LabelKey.DISTRICT to LocalizedText(fr = "arrondissement", en = "borough"),
        LabelKey.DISTRICT_PLURAL to LocalizedText(fr = "arrondissements", en = "boroughs"),
    ),
)

/** The City Pack of this build (ADR 0001). Build flavors will select it later. */
val ActiveCityPack: CityPack = MontrealCityPack

val LocalCityPack = staticCompositionLocalOf { ActiveCityPack }

private val DistrictJson = Json { ignoreUnknownKeys = true }

/** Reads the pack's districts.json off the main thread. Throws if it cannot be read. */
suspend fun loadDistrictMap(context: Context, cityPack: CityPack): DistrictMapData =
    withContext(Dispatchers.IO) {
        context.assets.open(cityPack.districtMapAsset).bufferedReader(Charsets.UTF_8).use {
            DistrictJson.decodeFromString<DistrictMapData>(it.readText())
        }
    }

/** Loads the active pack's districts.json from assets (null while loading or if it cannot be read). */
@Composable
fun rememberDistrictMap(cityPack: CityPack = LocalCityPack.current): State<DistrictMapData?> {
    val context = LocalContext.current
    return produceState<DistrictMapData?>(initialValue = null, cityPack.districtMapAsset) {
        value = try {
            loadDistrictMap(context, cityPack)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
