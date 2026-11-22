package com.example.hackapp.scaffold.screens.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hackapp.R
import com.example.hackapp.citypack.DistrictMapData
import com.example.hackapp.citypack.GeoPoint
import com.example.hackapp.citypack.currentRoughLocation
import com.example.hackapp.citypack.hasCoarseLocation
import com.example.hackapp.citypack.rememberDistrictMap
import com.example.hackapp.ui.components.BottomActions
import com.example.hackapp.ui.components.HackappPrimaryButton
import com.example.hackapp.ui.components.HackappTopBar
import com.example.hackapp.ui.components.LoadingState
import com.example.hackapp.ui.components.map.DistrictMarker
import com.example.hackapp.ui.components.map.DistrictPickMap
import com.example.hackapp.ui.components.map.ParsedDistrict
import com.example.hackapp.ui.components.map.rememberMapViewportState
import com.example.hackapp.ui.components.map.rememberParsedDistricts

/** The District containing [point], using the map's projection. Null off the map or without a projection. */
internal fun districtAt(map: DistrictMapData, parsed: List<ParsedDistrict>, point: GeoPoint): String? {
    val projection = map.projection ?: return null
    val p = projection.project(point)
    return parsed.firstOrNull { it.hit.contains(p.x.toInt(), p.y.toInt()) }?.id
}

/**
 * Full-screen map for the District of stay, with the same gestures as the Planning map. Names show
 * once zoomed in, with no Event counts. With permission, the District the person is in is chosen
 * for them on open, and they can still pick another.
 */
@Composable
internal fun DistrictOfStayMapDialog(
    options: List<DistrictOption>,
    selectedId: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val map by rememberDistrictMap()
    var chosen by rememberSaveable { mutableStateOf(selectedId) }
    var located by rememberSaveable { mutableStateOf<String?>(null) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var granted by remember { mutableStateOf(context.hasCoarseLocation()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val pickable = remember(options) { options.map { it.id }.toSet() }

    LaunchedEffect(Unit) {
        if (!granted && !asked) {
            asked = true
            permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            HackappTopBar(stringResource(R.string.profile_district_of_stay), onBack = onDismiss)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val loaded = map) {
                    null -> LoadingState(Modifier.align(Alignment.Center))
                    else -> {
                        val parsed = rememberParsedDistricts(loaded)
                        LaunchedEffect(granted, loaded) {
                            if (!granted || located != null) return@LaunchedEffect
                            val here = context.currentRoughLocation()?.let { districtAt(loaded, parsed, it) }
                                ?.takeIf { it in pickable }
                            located = here
                            if (here != null && chosen == null) chosen = here
                        }
                        val markers = remember(loaded, pickable, chosen) {
                            loaded.districts.associate { d ->
                                d.id to DistrictMarker(
                                    count = 0,
                                    pickable = d.id in pickable,
                                    showLabel = d.id in pickable,
                                    previewed = d.id == chosen,
                                )
                            }
                        }
                        DistrictPickMap(
                            map = loaded,
                            markers = markers,
                            viewportState = rememberMapViewportState(),
                            onDistrictTap = { chosen = it },
                            onEmptyTap = {},
                            showCounts = false,
                        )
                        val located = located
                        if (located != null) {
                            Text(
                                stringResource(R.string.profile_district_located, options.nameOf(located)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }
                }
            }
            BottomActions {
                val current = chosen
                HackappPrimaryButton(
                    text = if (current != null) {
                        stringResource(R.string.profile_district_choose, options.nameOf(current))
                    } else {
                        stringResource(R.string.profile_district_choose_empty)
                    },
                    onClick = { current?.let(onConfirm) },
                    enabled = current != null,
                )
            }
        }
    }
}

private fun List<DistrictOption>.nameOf(id: String) = firstOrNull { it.id == id }?.name ?: id
