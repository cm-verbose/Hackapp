package com.example.hackapp.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappCorners
import com.example.hackapp.ui.theme.HackappTheme

private const val HOURS = 24
private const val MINUTES = 60
private const val WHEEL_LOOPS = 2_000
private val WheelWidth = 112.dp
private val WheelHeight = 96.dp

/** Figma "TimeWheelDialog": flick an hour or minute box, or tap it to type a value. */
@Composable
fun HackappTimeWheels(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
    ) {
        TimeUnitWheel(hour, HOURS, stringResource(R.string.time_hour), R.string.time_hour_value, onHourChange)
        Box(Modifier.height(WheelHeight), contentAlignment = Alignment.Center) {
            Text(":", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        TimeUnitWheel(minute, MINUTES, stringResource(R.string.time_minute), R.string.time_minute_value, onMinuteChange)
    }
}

/**
 * [label] names the unit; [valueTemplate] formats unit and value together for TalkBack.
 * Flicking stays a touch gesture: TalkBack gets one node per wheel that opens the typed input on tap.
 */
@Composable
private fun TimeUnitWheel(
    value: Int,
    count: Int,
    label: String,
    @StringRes valueTemplate: Int,
    onValueChange: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val pageCount = count * WHEEL_LOOPS
    val pagerState = rememberPagerState(initialPage = wheelPageFor(value, count, pageCount / 2)) { pageCount }
    val latestChange by rememberUpdatedState(onValueChange)
    var editing by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(valueTemplate, twoDigits(value))

    LaunchedEffect(pagerState, count) {
        snapshotFlow { pagerState.currentPage }.collect { page -> latestChange(Math.floorMod(page, count)) }
    }
    // A typed value moves the wheel, so flicking continues from it.
    LaunchedEffect(value, editing) {
        if (!editing && !pagerState.isScrollInProgress && Math.floorMod(pagerState.currentPage, count) != value) {
            pagerState.scrollToPage(wheelPageFor(value, count, pagerState.currentPage))
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(WheelWidth, WheelHeight)
                .clip(RoundedCornerShape(HackappCorners.Medium))
                .background(if (editing) scheme.primaryContainer else scheme.surfaceContainerHighest)
                .then(
                    if (editing) {
                        Modifier
                    } else {
                        Modifier.clearAndSetSemantics {
                            contentDescription = description
                            onClick {
                                editing = true
                                true
                            }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (editing) {
                TimeUnitInput(value, count, label, onValueChange = { latestChange(it) }, onDone = { editing = false })
            } else {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        pagerSnapDistance = PagerSnapDistance.atMost(count),
                    ),
                ) { page ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(role = Role.Button) { editing = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            twoDigits(Math.floorMod(page, count)),
                            style = MaterialTheme.typography.displayMedium,
                            color = scheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** Starts with the value selected, so the first digit replaces it. [label] is the unit read by TalkBack. */
@Composable
private fun TimeUnitInput(value: Int, count: Int, label: String, onValueChange: (Int) -> Unit, onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    var text by remember {
        val initial = twoDigits(value)
        mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length)))
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    BasicTextField(
        value = text,
        onValueChange = { next ->
            val digits = next.text.filter(Char::isDigit).take(2)
            text = if (digits == next.text) next else TextFieldValue(digits, TextRange(digits.length))
            parseTimeUnit(digits, count)?.let(onValueChange)
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.displayMedium.copy(
            color = scheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label }
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (hadFocus && !it.isFocused) onDone()
                hadFocus = it.isFocused
            },
    )
}

@Preview(name = "Time wheels", showBackground = true, widthDp = 320)
@Composable
private fun HackappTimeWheelsPreview() = HackappTheme {
    HackappTimeWheels(18, 0, {}, {})
}
