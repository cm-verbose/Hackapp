package com.example.hackapp.scaffold.screens.planning

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.itinerary.ContinueKind
import com.example.hackapp.itinerary.MealKind
import com.example.hackapp.ui.components.HackappSmallTextButton
import com.example.hackapp.ui.components.HackappSmallTonalButton
import com.example.hackapp.ui.components.formatSentenceTime
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme
import kotlinx.coroutines.delay
import java.time.LocalTime

/** The callbacks of the selection suggestions. Defaults do nothing, for previews. */
data class SelectionGuidanceActions(
    val onAcceptDistrict: () -> Unit = {},
    val onDismissDistrict: () -> Unit = {},
    val onAcceptReorder: () -> Unit = {},
    val onDismissReorder: () -> Unit = {},
    val onChangeMealTime: () -> Unit = {},
    val onKeepMealTime: () -> Unit = {},
    val onPreviewMealTime: (LocalTime) -> Unit = {},
    val onSaveMealTime: () -> Unit = {},
    val onCancelMealTime: () -> Unit = {},
    val onChooseMeal: () -> Unit = {},
    val onFinishDay: () -> Unit = {},
    val onUndoChoice: () -> Unit = {},
)

private const val MEAL_STEP_MINUTES = 15L
private const val INTRO_HOLD_MILLIS = 900L
private const val INTRO_WIPE_MILLIS = 450

/** Offers to visit the Districts in the order their Events happen. */
@Composable
internal fun ReorderCard(
    suggestion: ReorderSuggestion,
    firstDistrictName: String,
    actions: SelectionGuidanceActions,
) {
    val message = suggestion.currentStart
        ?.let { stringResource(R.string.selection_reorder_late, firstDistrictName, formatSentenceTime(it.toLocalTime())) }
        ?: stringResource(R.string.selection_reorder_none, firstDistrictName)
    GuidanceCard(
        message = message,
        primary = stringResource(R.string.selection_reorder_action) to actions.onAcceptReorder,
        secondary = stringResource(R.string.selection_reorder_keep) to actions.onDismissReorder,
    )
}

/** The next Meal hides many Events: offers to move it. */
@Composable
internal fun MealConflictCard(conflict: MealConflict, actions: SelectionGuidanceActions) {
    GuidanceCard(
        message = stringResource(R.string.selection_meal_conflict, conflict.lost, conflict.withoutMeal),
        title = mealKindLabel(conflict.kind),
        primary = stringResource(R.string.selection_meal_change_time) to actions.onChangeMealTime,
        secondary = stringResource(R.string.selection_meal_keep_time) to actions.onKeepMealTime,
    )
}

/**
 * Moves the Meal 15 minutes at a time. The Event list under it follows each step, so the person
 * can see what each time leaves them. Nothing is saved until Save time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MealTimeEditor(edit: MealTimeEdit, actions: SelectionGuidanceActions) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        shape = RoundedCornerShape(HackappCorners.Medium),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalIconButton(onClick = { actions.onPreviewMealTime(edit.start.minusMinutes(MEAL_STEP_MINUTES)) }) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.selection_meal_earlier))
                }
                Column(
                    Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(
                            when (edit.kind) {
                                MealKind.Lunch -> R.string.meal_lunch_at
                                MealKind.Dinner -> R.string.meal_dinner_at
                                MealKind.LateBite -> R.string.meal_late_bite_at
                            },
                            formatSentenceTime(edit.start),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        pluralStringResource(R.plurals.selection_events_left, edit.eventCount, edit.eventCount),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FilledTonalIconButton(onClick = { actions.onPreviewMealTime(edit.start.plusMinutes(MEAL_STEP_MINUTES)) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.selection_meal_later))
                }
            }
            if (edit.blocked) {
                val message = stringResource(R.string.selection_meal_blocked)
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                    modifier = Modifier.semantics {
                        error(message)
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                HackappSmallTextButton(stringResource(R.string.action_cancel), actions.onCancelMealTime)
                HackappSmallTonalButton(stringResource(R.string.selection_meal_save_time), actions.onSaveMealTime)
            }
        }
    }
}

/**
 * Asked after each new Stop while a next District exists: add another Event, or the Meal that is
 * due, here, or move on. Moving on plays the District transition.
 */
@Composable
internal fun DistrictSuggestionDialog(
    suggestion: DistrictSuggestion,
    currentDistrictName: String,
    nextDistrictName: String,
    actions: SelectionGuidanceActions,
) {
    val meal = suggestion.kind == ContinueKind.Meal
    val stay = {
        actions.onDismissDistrict()
        if (meal) actions.onChooseMeal()
    }
    AlertDialog(
        onDismissRequest = stay,
        title = {
            Text(
                stringResource(
                    if (meal) R.string.selection_continue_meal else R.string.selection_continue_event,
                    nextDistrictName,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = actions.onAcceptDistrict) {
                Text(stringResource(R.string.selection_move_on_action, nextDistrictName))
            }
        },
        dismissButton = {
            TextButton(onClick = stay) {
                Text(stringResource(R.string.selection_stay_in, currentDistrictName))
            }
        },
    )
}

/** The last choice left nothing for the rest of the day. Finish needs at least one Event. */
@Composable
internal fun DayCompleteDialog(canFinish: Boolean, actions: SelectionGuidanceActions) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.selection_day_complete_title)) },
        text = { Text(stringResource(R.string.selection_day_complete_body)) },
        confirmButton = {
            if (canFinish) {
                TextButton(onClick = actions.onFinishDay) { Text(stringResource(R.string.selection_day_complete_finish)) }
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onUndoChoice) { Text(stringResource(R.string.selection_day_complete_change)) }
        },
    )
}

/** What a transition panel announces. */
internal sealed interface SelectionTransition {
    data object Intro : SelectionTransition
    data class District(val name: String) : SelectionTransition
    data class Meal(val kind: MealKind) : SelectionTransition
}

/** Plays [transition] once, then calls [onFinished]. */
@Composable
internal fun SelectionTransitionOverlay(transition: SelectionTransition, onFinished: () -> Unit) {
    when (transition) {
        SelectionTransition.Intro -> SelectionIntroOverlay(onFinished)
        is SelectionTransition.District -> SelectionIntroOverlay(
            onFinished = onFinished,
            title = transition.name,
            caption = stringResource(R.string.selection_transition_district),
            icon = Icons.Filled.Place,
        )
        is SelectionTransition.Meal -> SelectionIntroOverlay(
            onFinished = onFinished,
            title = mealKindLabel(transition.kind),
            caption = stringResource(R.string.selection_transition_meal),
            icon = Icons.Filled.Restaurant,
        )
    }
}

/** Where the borough panel is while the move-on question is open. */
internal enum class PeekMode { Peek, Continue, Reverse }

/** The panel shows this much of itself while it waits for the answer. */
private const val PEEK_FRACTION = 0.28f
private const val PEEK_IN_MILLIS = 500
private const val PEEK_CONTINUE_MILLIS = 450
private const val PEEK_REVERSE_MILLIS = 400

/**
 * The next borough's panel, paused partway in while the move-on question is open. [PeekMode.Continue]
 * finishes the slide, holds, then wipes away like the other panels. [PeekMode.Reverse] slides it
 * back out. [onFinished] runs once it has left the screen either way.
 */
@Composable
internal fun BoroughPeekOverlay(name: String, mode: PeekMode, onFinished: () -> Unit) {
    val colors = HackappTheme.appColors
    val scheme = MaterialTheme.colorScheme
    // 0 is off screen to the end side, 1 covers the screen, 2 has wiped off to the start side.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(mode) {
        when (mode) {
            PeekMode.Peek -> progress.animateTo(PEEK_FRACTION, tween(PEEK_IN_MILLIS))
            PeekMode.Continue -> {
                progress.animateTo(1f, tween(PEEK_CONTINUE_MILLIS))
                delay(INTRO_HOLD_MILLIS)
                progress.animateTo(2f, tween(INTRO_WIPE_MILLIS))
                onFinished()
            }
            PeekMode.Reverse -> {
                progress.animateTo(0f, tween(PEEK_REVERSE_MILLIS))
                onFinished()
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val shift = width * (1f - progress.value)
                    translationX = if (ltr) shift else -shift
                }
                .background(Brush.linearGradient(listOf(colors.hero, scheme.primary))),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(112.dp))
                Text(
                    stringResource(R.string.selection_transition_district),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    name,
                    style = MaterialTheme.typography.displaySmall,
                    color = scheme.onPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
    }
}

/**
 * A full-screen colour panel that marks the move from setting up the Itinerary to choosing its
 * Stops. It arrives with the page's slide, holds briefly, then wipes away to the start side.
 * A tap skips it.
 */
@Composable
internal fun SelectionIntroOverlay(
    onFinished: () -> Unit,
    title: String = stringResource(R.string.selection_intro_title),
    caption: String? = null,
    icon: ImageVector = Icons.Filled.Explore,
) {
    val colors = HackappTheme.appColors
    val scheme = MaterialTheme.colorScheme
    val visible = remember { MutableTransitionState(true) }
    LaunchedEffect(Unit) {
        delay(INTRO_HOLD_MILLIS)
        visible.targetState = false
    }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState) onFinished()
    }
    AnimatedVisibility(
        visibleState = visible,
        exit = slideOutHorizontally(tween(INTRO_WIPE_MILLIS)) { width -> -width },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(colors.hero, scheme.primary)))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    visible.targetState = false
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
                enter = scaleIn(tween(400), initialScale = 0.6f) + fadeIn(tween(400)),
            ) {
                Column(
                    Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(112.dp))
                    caption?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium, color = scheme.onPrimary, textAlign = TextAlign.Center)
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.displaySmall,
                        color = scheme.onPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuidanceCard(
    message: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>,
    title: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.tertiaryContainer,
        contentColor = scheme.onTertiaryContainer,
        shape = RoundedCornerShape(HackappCorners.Medium),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                HackappSmallTextButton(secondary.first, secondary.second)
                HackappSmallTonalButton(primary.first, primary.second)
            }
        }
    }
}

@Composable
private fun mealKindLabel(kind: MealKind): String = stringResource(
    when (kind) {
        MealKind.Lunch -> R.string.meal_lunch
        MealKind.Dinner -> R.string.meal_dinner
        MealKind.LateBite -> R.string.meal_late_bite
    },
)

/* Previews */

@Preview(name = "Selection guidance", showBackground = true, widthDp = 360, fontScale = 1.3f)
@Composable
private fun SelectionGuidancePreview() = HackappTheme {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MealConflictCard(MealConflict("d", MealKind.Dinner, lost = 5, withoutMeal = 8), SelectionGuidanceActions())
        MealTimeEditor(MealTimeEdit("d", MealKind.Dinner, LocalTime.of(17, 30), eventCount = 6, blocked = true), SelectionGuidanceActions())
    }
}

@Preview(name = "Selection intro", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun SelectionIntroPreview() = HackappTheme { SelectionIntroOverlay(onFinished = {}) }
