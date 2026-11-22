package com.example.hackapp.scaffold.screens.underway

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.scaffold.screens.itinerary.itineraryShortDate
import com.example.hackapp.ui.components.DataAttribution
import com.example.hackapp.ui.components.HackappPrimaryButton
import com.example.hackapp.ui.components.HackappTextActionButton
import com.example.hackapp.ui.components.Stamp
import com.example.hackapp.ui.components.stampInitials
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.ui.theme.StopType
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private const val CONFETTI_COUNT = 90
private const val CONFETTI_MILLIS = 3_200

private class Confetto(
    val x: Float,
    val delay: Float,
    val speed: Float,
    val sway: Float,
    val spin: Float,
    val width: Float,
    val color: Color,
)

/** Confetti in the Stop type colours, falling once across the whole page. */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier) {
    val palette = HackappTheme.stopTypeColors
    val colors = remember(palette) { StopType.entries.map { palette[it] } }
    val pieces = remember(colors) {
        val random = Random(7)
        List(CONFETTI_COUNT) {
            Confetto(
                x = random.nextFloat(),
                delay = random.nextFloat() * 0.35f,
                speed = 0.8f + random.nextFloat() * 0.6f,
                sway = 0.02f + random.nextFloat() * 0.05f,
                spin = random.nextFloat() * 720f - 360f,
                width = 6f + random.nextFloat() * 8f,
                color = colors[random.nextInt(colors.size)],
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(CONFETTI_MILLIS, easing = LinearEasing)) }
    Canvas(modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        pieces.forEach { p ->
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val y = -40f + local * p.speed * (size.height + 80f)
            val x = (p.x + sin(local * 2 * PI * 2).toFloat() * p.sway) * size.width
            val w = p.width * density / 2f
            rotate(p.spin * local, pivot = Offset(x, y)) {
                drawRect(p.color, topLeft = Offset(x - w / 2, y - w), size = Size(w, w * 2), alpha = 1f - local * 0.3f)
            }
        }
    }
}

/**
 * The end of an Itinerary, as a celebration: hero colours, confetti and the new Stamps popping in.
 * The main action opens the Passport.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnderwayEndOfDayPage(
    ui: EndOfDayUi,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPassport: () -> Unit = onDone,
) {
    val app = HackappTheme.appColors
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(app.hero, scheme.primary, app.hero))),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val pop = remember { Animatable(0.4f) }
                LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                Icon(
                    Icons.Filled.Celebration,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(88.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                )
                Text(
                    stringResource(R.string.end_of_day_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = scheme.onPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                val visited = ui.results.count { it.outcome != com.example.hackapp.underway.StopOutcome.Skipped }
                Text(
                    if (ui.newStamps.isEmpty()) {
                        pluralStringResource(R.plurals.end_of_day_visited, visited, visited)
                    } else {
                        pluralStringResource(R.plurals.end_of_day_stamps_earned, ui.newStamps.size, ui.newStamps.size)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = app.onHero,
                    textAlign = TextAlign.Center,
                )
                if (ui.newStamps.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ui.newStamps.forEachIndexed { index, stamp ->
                            val scale = remember { Animatable(0f) }
                            LaunchedEffect(Unit) {
                                delay(500L + index * 180L)
                                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                            }
                            Stamp(
                                initials = stampInitials(stamp.name),
                                stampName = stamp.name,
                                date = itineraryShortDate(stamp.date),
                                district = stamp.districtName,
                                modifier = Modifier
                                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = (index % 3 - 1) * 6f }
                                    .semantics(mergeDescendants = true) {},
                            )
                        }
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(scheme.surface, MaterialTheme.shapes.large)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ui.results.forEach { DayResultRow(it) }
                    DataAttribution()
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HackappPrimaryButton(stringResource(R.string.end_of_day_open_passport), onOpenPassport)
                HackappTextActionButton(stringResource(R.string.end_of_day_done), onDone, contentColor = scheme.onPrimary)
            }
        }
        ConfettiBurst(Modifier.fillMaxSize())
    }
}
