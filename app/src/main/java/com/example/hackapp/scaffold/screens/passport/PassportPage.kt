package com.example.hackapp.scaffold.screens.passport

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.scaffold.sandbox.PhysicsScreen
import com.example.hackapp.scaffold.sandbox.SandboxItem
import com.example.hackapp.scaffold.sandbox.LocalSandboxItemScale
import com.example.hackapp.scaffold.sandbox.SandboxLayout
import com.example.hackapp.scaffold.sandbox.sandboxLayout
import com.example.hackapp.ui.components.ChoiceOption
import com.example.hackapp.ui.components.ErrorState
import com.example.hackapp.ui.components.HackappSegmentedButton
import com.example.hackapp.ui.components.HackappSmallTextButton
import com.example.hackapp.ui.components.HeroHeader
import com.example.hackapp.ui.components.LoadingState
import com.example.hackapp.ui.components.StampDisc
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.ui.theme.StopType

private val GravityButtonSize = 56.dp
private val GravityButtonInset = 12.dp

/** The solid corner under the gravity button, so a Stamp can never rest beneath it. */
private val GravityKeepOut = GravityButtonSize + GravityButtonInset

/** Below this height the page scrolls and the field keeps [ScrollingFieldHeight]. */
private val MinFullPageHeight = 480.dp
private val ScrollingFieldHeight = 240.dp

private val DetailsShapeSize = 96.dp
private val ListShapeSize = 64.dp

/** Figma PassportScreen: Physics (188:3044), Details (188:3383), Empty (188:3475), Dark (188:3548). */
@Composable
fun PassportPage(
    content: PassportContent,
    cities: List<ChoiceOption<String>>,
    selectedCity: String,
    cityName: String,
    gravityLocked: Boolean,
    onCityChange: (String) -> Unit,
    onGravityLockedChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val stamps = (content as? PassportContent.Ready)?.stamps.orEmpty()

    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val scrolls = maxHeight < MinFullPageHeight
        Column(
            Modifier
                .fillMaxSize()
                .then(if (scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HackappSegmentedButton(
                options = cities,
                selected = selectedCity,
                onSelectedChange = onCityChange,
                maxLines = 1,
            )
            val rest = if (scrolls) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f)
            when (content) {
                PassportContent.Loading -> LoadingState(rest)
                PassportContent.Unavailable -> ErrorState(onRetry = onRetry, modifier = rest)
                is PassportContent.Ready -> if (content.stamps.isEmpty()) {
                    PassportEmpty(rest)
                } else {
                    HeroHeader(
                        title = pluralStringResource(R.plurals.stamps_count, content.stamps.size, content.stamps.size),
                        subtitle = stringResource(
                            R.string.leg_time_district,
                            pluralStringResource(R.plurals.districts_count, content.districtCount, content.districtCount),
                            cityName,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StampField(
                        stamps = content.stamps,
                        gravityLocked = gravityLocked,
                        onGravityLockedChange = onGravityLockedChange,
                        onOpen = { openKey = it },
                        onShowAll = { showAll = true },
                        dragOnSwipe = !scrolls,
                        modifier = if (scrolls) Modifier.fillMaxWidth().height(ScrollingFieldHeight) else rest,
                    )
                }
            }
        }
    }

    if (showAll && stamps.isNotEmpty()) {
        AllStampsSheet(
            stamps = stamps,
            onOpen = {
                showAll = false
                openKey = it
            },
            onDismiss = { showAll = false },
        )
    }
    stamps.firstOrNull { it.key == openKey }?.let { stamp ->
        StampDetailsSheet(stamp, onDismiss = { openKey = null })
    }
}

/** "All Stamps" appears only when some Stamps do not fit the field. */
@Composable
private fun StampField(
    stamps: List<PassportStampUi>,
    gravityLocked: Boolean,
    onGravityLockedChange: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onShowAll: () -> Unit,
    dragOnSwipe: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        // Measured without the link's row, so showing the link cannot flip this decision.
        val overflows = fieldLayout(stamps, maxWidth, maxHeight).count < stamps.size
        Column(Modifier.fillMaxSize()) {
            if (overflows) {
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterEnd) {
                    HackappSmallTextButton(stringResource(R.string.passport_all_stamps), onShowAll)
                }
            }
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(HackappCorners.Large))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                val layout = fieldLayout(stamps, maxWidth, maxHeight)
                // Newest Stamps are kept. Largest first is the order the layout was measured in.
                val shown = remember(stamps, layout.count) {
                    stamps.take(layout.count).sortedByDescending { it.shape.widthDp * it.shape.heightDp }
                }
                val colors = HackappTheme.stopTypeColors
                val outlines = StampShape.entries.associateWith { it.outline() }
                val items = remember(shown, colors) {
                    shown.map { stamp ->
                        val disc = colors[stamp.type]
                        val outline = outlines.getValue(stamp.shape)
                        SandboxItem(
                            key = stamp.key,
                            color = disc,
                            shape = outline,
                            width = stamp.shape.widthDp.dp,
                            height = stamp.shape.heightDp.dp,
                        ) {
                            val itemScale = LocalSandboxItemScale.current
                            StampDisc(
                                initials = stamp.initials,
                                discColor = disc,
                                inkColor = colors.onStopType,
                                shape = outline,
                                initialsScale = { readableInitialsScale(itemScale()) },
                                modifier = Modifier.fillMaxSize().clearAndSetSemantics {
                                    contentDescription = stamp.description
                                    role = Role.Button
                                    onClick {
                                        onOpen(stamp.key)
                                        true
                                    }
                                },
                            )
                        }
                    }
                }
                PhysicsScreen(
                    items = items,
                    gravityLocked = gravityLocked,
                    keepOutBottomEnd = GravityKeepOut,
                    itemScale = layout.scale,
                    dragOnSwipe = dragOnSwipe,
                    onItemClick = { onOpen(it.key as String) },
                )
                GravityLockButton(
                    locked = gravityLocked,
                    onLockedChange = onGravityLockedChange,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(GravityButtonInset),
                )
            }
        }
    }
}

@Composable
private fun fieldLayout(stamps: List<PassportStampUi>, width: Dp, height: Dp): SandboxLayout {
    val density = LocalDensity.current
    return remember(stamps, width, height, density) {
        with(density) {
            sandboxLayout(
                stamps.map { it.shape.widthDp.dp.toPx() to it.shape.heightDp.dp.toPx() },
                width.toPx(),
                height.toPx(),
                this.density,
                GravityKeepOut.toPx(),
            )
        }
    }
}

/** The Material shape. [scale] keeps the rounded squares' corners in proportion when smaller. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StampShape.outline(scale: Float = 1f): Shape = when (this) {
    StampShape.VerySunny -> MaterialShapes.VerySunny.toShape()
    StampShape.Cookie9Sided -> MaterialShapes.Cookie9Sided.toShape()
    StampShape.Cookie7Sided -> MaterialShapes.Cookie7Sided.toShape()
    StampShape.RoundedLarge -> RoundedCornerShape(24.dp * scale)
    StampShape.RoundedMedium -> RoundedCornerShape(16.dp * scale)
    StampShape.Circle -> CircleShape
}

/** A Stamp in its own shape and proportions, with its longest side at [longestSide]. */
@Composable
private fun StampBadge(
    stamp: PassportStampUi,
    longestSide: Dp,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val colors = HackappTheme.stopTypeColors
    val scale = longestSide.value / maxOf(stamp.shape.widthDp, stamp.shape.heightDp)
    StampDisc(
        initials = stamp.initials,
        discColor = colors[stamp.type],
        inkColor = colors.onStopType,
        shape = stamp.shape.outline(scale),
        textStyle = textStyle,
        modifier = modifier.size(stamp.shape.widthDp.dp * scale, stamp.shape.heightDp.dp * scale),
    )
}

@Composable
private fun GravityLockButton(locked: Boolean, onLockedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = { onLockedChange(!locked) },
        modifier = modifier.size(GravityButtonSize),
    ) {
        Icon(
            imageVector = if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = stringResource(
                if (locked) R.string.passport_gravity_unlock else R.string.passport_gravity_lock,
            ),
        )
    }
}

@Composable
private fun PassportEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        StampDisc(
            initials = "",
            discColor = Color.Transparent,
            inkColor = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(96.dp),
        )
        Text(
            stringResource(R.string.passport_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.passport_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StampDetailsSheet(stamp: PassportStampUi, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        StampDetails(stamp, Modifier.navigationBarsPadding())
    }
}

@Composable
private fun StampDetails(stamp: PassportStampUi, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(DetailsShapeSize), contentAlignment = Alignment.Center) {
            StampBadge(stamp, DetailsShapeSize)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stamp.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(stamp.district, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stamp.date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllStampsSheet(stamps: List<PassportStampUi>, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        AllStampsList(stamps, onOpen)
    }
}

@Composable
private fun AllStampsList(stamps: List<PassportStampUi>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.passport_all_stamps),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            items(stamps, key = { it.key }) { stamp ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable(role = Role.Button) { onOpen(stamp.key) }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(ListShapeSize), contentAlignment = Alignment.Center) {
                        StampBadge(stamp, ListShapeSize, textStyle = MaterialTheme.typography.labelLarge)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stamp.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            stringResource(R.string.leg_time_district, stamp.district, stamp.date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private val PreviewTypes = listOf(StopType.Music, StopType.Meal, StopType.Bar, StopType.Exhibition, StopType.Festival, StopType.Theatre)

private fun previewStamps(count: Int) = List(count) { index ->
    PassportStampUi(
        key = "stamp-$index",
        initials = "S${index % 10}",
        name = "Jazz sur le toit $index",
        district = "Le Plateau",
        date = "28 nov. 2026",
        type = PreviewTypes[index % PreviewTypes.size],
        shape = StampShape.entries[index % StampShape.entries.size],
    )
}

@Composable
private fun PassportPreview(content: PassportContent, darkTheme: Boolean = false) = HackappTheme(darkTheme = darkTheme) {
    PassportPage(
        content = content,
        cities = listOf(ChoiceOption("mtl", "Montréal")),
        selectedCity = "mtl",
        cityName = "Montréal",
        gravityLocked = true,
        onCityChange = {},
        onGravityLockedChange = {},
        onRetry = {},
    )
}

@Preview(name = "Passport · physics", showBackground = true, widthDp = 412, heightDp = 760)
@Composable
private fun PassportPhysicsPreview() = PassportPreview(PassportContent.Ready(previewStamps(12), 4))

@Preview(name = "Passport · dark", showBackground = true, widthDp = 412, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PassportDarkPreview() = PassportPreview(PassportContent.Ready(previewStamps(12), 4), darkTheme = true)

@Preview(name = "Passport · all Stamps link", showBackground = true, widthDp = 412, heightDp = 760)
@Composable
private fun PassportOverflowPreview() = PassportPreview(PassportContent.Ready(previewStamps(60), 12))

@Preview(name = "Passport · 360 dp, 1.3x", showBackground = true, widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun PassportLargeTextPreview() = PassportPreview(PassportContent.Ready(previewStamps(40), 8))

@Preview(name = "Passport · empty", showBackground = true, widthDp = 412, heightDp = 760)
@Composable
private fun PassportEmptyPreview() = PassportPreview(PassportContent.Ready(emptyList(), 0))

@Preview(name = "Passport · empty dark", showBackground = true, widthDp = 360, heightDp = 640, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PassportEmptyDarkPreview() = PassportPreview(PassportContent.Ready(emptyList(), 0), darkTheme = true)

@Preview(name = "Passport · loading", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun PassportLoadingPreview() = PassportPreview(PassportContent.Loading)

@Preview(name = "Passport · error", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun PassportErrorPreview() = PassportPreview(PassportContent.Unavailable)

@Preview(name = "Stamp details", showBackground = true, widthDp = 360, fontScale = 1.3f)
@Composable
private fun StampDetailsPreview() = HackappTheme { StampDetails(previewStamps(1).first()) }

@Preview(name = "All Stamps", showBackground = true, widthDp = 360, heightDp = 480)
@Composable
private fun AllStampsPreview() = HackappTheme { AllStampsList(previewStamps(6), {}) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllShapes(darkTheme: Boolean) = HackappTheme(darkTheme = darkTheme) {
    FlowRow(
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        previewStamps(StampShape.entries.size).forEach { stamp ->
            StampBadge(stamp, maxOf(stamp.shape.widthDp, stamp.shape.heightDp).dp)
        }
    }
}

@Preview(name = "Stamp shapes", showBackground = true, widthDp = 360)
@Composable
private fun StampShapesPreview() = AllShapes(darkTheme = false)

@Preview(name = "Stamp shapes · dark", showBackground = true, widthDp = 360, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StampShapesDarkPreview() = AllShapes(darkTheme = true)
