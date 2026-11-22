package com.example.hackapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.scaffold.screens.itinerary.eventStopType
import com.example.hackapp.scaffold.screens.planning.EventAudience
import com.example.hackapp.scaffold.screens.planning.EventDetailsUiState
import com.example.hackapp.scaffold.screens.planning.EventPrice
import com.example.hackapp.scaffold.screens.planning.ShowtimeOptionUi
import com.example.hackapp.scaffold.screens.planning.TicketPriceFieldState
import com.example.hackapp.scaffold.screens.planning.TicketPriceInputError
import com.example.hackapp.scaffold.screens.planning.TicketPriceStatus
import com.example.hackapp.ui.theme.HackappTheme
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val ClockFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Figma "EventDetailsSheet" (141:3622) and "EventDetailsSheet_RepeatedShowtimes" (141:3734).
 * **Add to Itinerary** also saves a pending valid ticket price, so there is no separate save button.
 * [onRetryLoad] calls `load()` again after `LoadFailed`; [onSignIn] starts sign-in when the field is hidden.
 * [onAddWithoutPrice] wires to `addWithoutPrice()` after a failed save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailsSheet(
    state: EventDetailsUiState,
    onSelectShowtime: (eventId: String) -> Unit,
    onTicketPriceChange: (String) -> Unit,
    onRetryLoad: () -> Unit,
    onSignIn: () -> Unit,
    onAddToItinerary: () -> Unit,
    onAddWithoutPrice: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        EventDetailsContent(
            state = state,
            onSelectShowtime = onSelectShowtime,
            onTicketPriceChange = onTicketPriceChange,
            onRetryLoad = onRetryLoad,
            onSignIn = onSignIn,
            onAddToItinerary = onAddToItinerary,
            onAddWithoutPrice = onAddWithoutPrice,
            onClose = onDismiss,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

/** Sheet body without the ModalBottomSheet, for previews. The details scroll; the Add button stays visible. */
@Composable
fun EventDetailsContent(
    state: EventDetailsUiState,
    onSelectShowtime: (eventId: String) -> Unit,
    onTicketPriceChange: (String) -> Unit,
    onRetryLoad: () -> Unit,
    onSignIn: () -> Unit,
    onAddToItinerary: () -> Unit,
    onAddWithoutPrice: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val field = state.ticketPrice
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stopTypeLabel(eventStopType(state.type)),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) {
                HackappIconView(HackappIcon.Close, contentDescription = stringResource(R.string.event_close))
            }
        }

        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WhenRow(state, onSelectShowtime)
                if (state.venueName != null || state.address != null) {
                    InfoRow(HackappIcon.Place, state.venueName ?: state.address.orEmpty(), state.address.takeIf { state.venueName != null })
                }
                PriceRow(state.price)
                state.audience?.let { InfoRow(HackappIcon.Group, audienceLabel(it)) }
            }

            state.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
            }

            state.ticketUrl?.let { url -> TicketLink(url) }

            // A free Event has no price to report.
            if (field.applicable) {
                TicketPriceSection(field, state.hasRepeatedShowtimes, onTicketPriceChange, onRetryLoad, onSignIn)
            }
        }

        HackappPrimaryButton(
            text = stringResource(R.string.event_add),
            onClick = onAddToItinerary,
            enabled = state.canAdd && field.inputError == null && !field.isBusy,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (state.canAddWithoutPrice) {
            HackappSmallTextButton(
                text = stringResource(R.string.event_add_without_price),
                onClick = onAddWithoutPrice,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/**
 * The time (`start – end` when published), or the date with a showtime chooser when the title
 * repeats that day. Selection already shows the date, so a single showtime does not repeat it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhenRow(state: EventDetailsUiState, onSelectShowtime: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale) }
    val time = state.publishedEnd
        ?.let { stringResource(R.string.selection_time_block, state.start.format(ClockFormat), it.format(ClockFormat)) }
        ?: state.start.format(ClockFormat)

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        HackappIconView(HackappIcon.Schedule, tint = scheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (state.hasRepeatedShowtimes) {
                    stringResource(R.string.event_showtimes, state.start.format(dateFormat), state.showtimes.size)
                } else {
                    time
                },
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
            )
            if (state.hasRepeatedShowtimes && state.publishedEnd != null) {
                Text(time, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            if (state.endTimeUnpublished) {
                Text(stringResource(R.string.event_end_unknown), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            if (state.hasRepeatedShowtimes) {
                val group = stringResource(R.string.event_showtimes_group)
                FlowRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .semantics {
                            contentDescription = group
                            selectableGroup()
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.showtimes.forEach { showtime ->
                        val clock = showtime.start.format(ClockFormat)
                        val label = stringResource(R.string.event_showtime_label, clock)
                        // Selection shows by colour only: the chip has no leading checkmark.
                        // One showtime at a time, so TalkBack hears a radio button, not a checkbox.
                        FilterChip(
                            selected = showtime.selected,
                            onClick = { onSelectShowtime(showtime.eventId) },
                            label = { Text(clock, style = MaterialTheme.typography.labelLarge) },
                            modifier = Modifier.semantics {
                                role = Role.RadioButton
                                contentDescription = label
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceRow(price: EventPrice) {
    when (price) {
        EventPrice.Loading -> InfoRow(HackappIcon.Sell, stringResource(R.string.state_loading))
        EventPrice.Free -> InfoRow(HackappIcon.Sell, stringResource(R.string.event_price_free))
        EventPrice.Unknown -> InfoRow(HackappIcon.Sell, stringResource(R.string.event_price_unknown))
        is EventPrice.UserMedian -> InfoRow(HackappIcon.Sell, formatCad(price.medianCad), stringResource(R.string.event_price_median))
        is EventPrice.Published -> {
            val min = price.minCad
            val max = price.maxCad
            val text = when {
                min != null && max != null -> formatCadRange(min, max)
                else -> formatCad(min ?: max ?: 0.0)
            }
            InfoRow(HackappIcon.Sell, text, stringResource(R.string.event_price_published))
        }
    }
}

@Composable
private fun InfoRow(icon: HackappIcon, text: String, caption: String? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        HackappIconView(icon, tint = scheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun TicketLink(url: String) {
    val uriHandler = LocalUriHandler.current
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button) {
                // A device without a browser throws instead of opening the link.
                try {
                    uriHandler.openUri(url)
                } catch (_: IllegalArgumentException) {
                }
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HackappIconView(HackappIcon.OpenInNew, size = 20.dp, tint = scheme.primary)
        Text(stringResource(R.string.event_tickets_link), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
    }
}

/**
 * The optional ticket price. Signed out: a sign-in prompt instead of the field and no shared-price
 * notice, since there is nothing to share. Errors are live regions.
 */
@Composable
private fun ColumnScope.TicketPriceSection(
    field: TicketPriceFieldState,
    repeatedShowtimes: Boolean,
    onTicketPriceChange: (String) -> Unit,
    onRetryLoad: () -> Unit,
    onSignIn: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val min = formatCad(field.minCad)
    val max = formatCad(field.maxCad)

    if (!field.signedIn) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.event_ticket_price_signed_out),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            HackappSmallTextButton(stringResource(R.string.auth_sign_in), onSignIn)
        }
    } else {
        HackappTextField(
            value = field.input,
            onValueChange = onTicketPriceChange,
            label = stringResource(R.string.event_ticket_price_label),
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
            enabled = !field.isBusy,
            errorText = when (field.inputError) {
                null -> null
                TicketPriceInputError.NotANumber -> stringResource(R.string.event_ticket_price_error_format)
                TicketPriceInputError.OutOfRange -> stringResource(R.string.event_ticket_price_error_range, min, max)
            },
            supportingText = stringResource(R.string.event_ticket_price_range, min, max),
        )
    }
    // The shared median loads for everyone, so its failure and Retry show when signed out too.
    TicketPriceStatus(field.status, onRetryLoad)

    if (field.signedIn) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            HackappIconView(HackappIcon.Info, size = 16.dp, tint = scheme.onSurfaceVariant)
            Text(
                stringResource(if (repeatedShowtimes) R.string.event_ticket_price_shared_showtimes else R.string.event_ticket_price_shared),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TicketPriceStatus(status: TicketPriceStatus, onRetryLoad: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when (status) {
        TicketPriceStatus.Idle -> Unit
        TicketPriceStatus.Loading, TicketPriceStatus.Saving -> {
            val description = stringResource(
                if (status == TicketPriceStatus.Saving) R.string.event_price_saving else R.string.state_loading,
            )
            CircularProgressIndicator(
                Modifier
                    .size(20.dp)
                    .semantics {
                        contentDescription = description
                        liveRegion = LiveRegionMode.Polite
                    },
                strokeWidth = 2.dp,
            )
        }
        TicketPriceStatus.Saved -> Text(
            stringResource(R.string.event_ticket_price_saved),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = live,
        )
        TicketPriceStatus.LoadFailed -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.event_load_failed),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.error,
                modifier = live.weight(1f),
            )
            HackappSmallTextButton(stringResource(R.string.action_retry), onRetryLoad)
        }
        TicketPriceStatus.SaveFailed -> Text(
            stringResource(R.string.event_save_failed),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.error,
            modifier = live,
        )
    }
}

/**
 * The source's audience text mapped to its `event_audience_*` key (`docs/contracts/sheets-strings.md`,
 * "Audience labels"); an unknown text shows as is. Without a text: adults when the minimum age is
 * 18 or more, family when a maximum age exists, else all audiences.
 */
@Composable
private fun audienceLabel(audience: EventAudience): String {
    val description = audience.description?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        description != null -> audienceKey(description)?.let { stringResource(it) } ?: description
        (audience.minAge ?: 0) >= 18 -> stringResource(R.string.event_audience_adults)
        audience.maxAge != null -> stringResource(R.string.event_audience_family)
        else -> stringResource(R.string.event_audience_all)
    }
}

/** Exact source values of `targetAudience.description` in `events.json`. */
private fun audienceKey(description: String): Int? = when (description) {
    "Pour tous" -> R.string.event_audience_all
    "Enfants d'âge préscolaire", "Enfants d’âge préscolaire" -> R.string.event_audience_preschool
    "Famille" -> R.string.event_audience_family
    "Enfants" -> R.string.event_audience_children
    "Adultes" -> R.string.event_audience_adult_public
    "Adolescents" -> R.string.event_audience_teens
    "Personnes aînées" -> R.string.event_audience_seniors
    else -> null
}

// Previews

private val PreviewZone: ZoneId = ZoneId.of("America/Toronto")
private val PreviewStart: ZonedDateTime = ZonedDateTime.of(2026, 11, 28, 20, 0, 0, 0, PreviewZone)
private const val PREVIEW_DESCRIPTION =
    "Un quatuor de jazz revisite les standards de Miles Davis dans l’ambiance intime du Club Soda."

private fun previewState(
    price: EventPrice = EventPrice.UserMedian(25.0, 12),
    ticketPrice: TicketPriceFieldState = TicketPriceFieldState(signedIn = true, input = "25", ownReportCad = 25.0, status = TicketPriceStatus.Idle),
    showtimes: List<ZonedDateTime> = listOf(PreviewStart),
    publishedEnd: ZonedDateTime? = null,
    description: String? = PREVIEW_DESCRIPTION,
    audience: EventAudience? = EventAudience("Pour tous", null, null),
    canAdd: Boolean = true,
    canAddWithoutPrice: Boolean = false,
) = EventDetailsUiState(
    eventId = "lemay-20",
    title = "Quatuor jazz Lemay",
    type = "music",
    start = PreviewStart,
    showtimes = showtimes.mapIndexed { index, start -> ShowtimeOptionUi("lemay-$index", start, start == PreviewStart) },
    hasRepeatedShowtimes = showtimes.size > 1,
    publishedEnd = publishedEnd,
    endTimeUnpublished = publishedEnd == null,
    venueName = "Club Soda",
    address = "1225, boul. Saint-Laurent · Ville-Marie",
    price = price,
    audience = audience,
    description = description,
    ticketUrl = "https://example.org/billets",
    ticketPrice = ticketPrice,
    canAdd = canAdd,
    canAddWithoutPrice = canAddWithoutPrice,
)

@Composable
private fun EventDetailsPreview(state: EventDetailsUiState) = HackappTheme {
    EventDetailsContent(
        state = state,
        onSelectShowtime = {},
        onTicketPriceChange = {},
        onRetryLoad = {},
        onSignIn = {},
        onAddToItinerary = {},
        onAddWithoutPrice = {},
        onClose = {},
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Preview(name = "EventDetails · median price", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun EventDetailsMedianPreview() = EventDetailsPreview(previewState())

@Preview(name = "EventDetails · repeated showtimes", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun EventDetailsRepeatedShowtimesPreview() = EventDetailsPreview(
    previewState(showtimes = listOf(PreviewStart.minusHours(2), PreviewStart, PreviewStart.plusHours(2))),
)

@Preview(name = "EventDetails · published price, end known", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun EventDetailsPublishedPreview() = EventDetailsPreview(
    previewState(
        price = EventPrice.Published(18.0, 32.0),
        publishedEnd = PreviewStart.plusHours(2),
        ticketPrice = TicketPriceFieldState(signedIn = true, status = TicketPriceStatus.Idle),
        audience = EventAudience(null, 18, null),
    ),
)

@Preview(name = "EventDetails · repeated showtimes, end known", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun EventDetailsRepeatedEndKnownPreview() = EventDetailsPreview(
    previewState(
        showtimes = listOf(PreviewStart.minusHours(2), PreviewStart, PreviewStart.plusHours(2)),
        publishedEnd = PreviewStart.plusMinutes(90),
        audience = EventAudience("Rencontre avec l'artiste", null, null),
    ),
)

@Preview(name = "EventDetails · unknown price, no description", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun EventDetailsUnknownPreview() = EventDetailsPreview(
    previewState(
        price = EventPrice.Unknown,
        description = null,
        audience = EventAudience("Adolescents", null, null),
        ticketPrice = TicketPriceFieldState(signedIn = true, status = TicketPriceStatus.Idle),
    ),
)

@Preview(name = "EventDetails · free, signed out", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun EventDetailsSignedOutPreview() = EventDetailsPreview(
    previewState(
        price = EventPrice.Free,
        ticketPrice = TicketPriceFieldState(signedIn = false, applicable = false, status = TicketPriceStatus.Idle),
        audience = null,
    ),
)

@Preview(name = "EventDetails · unknown price, signed out", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun EventDetailsUnknownSignedOutPreview() = EventDetailsPreview(
    previewState(
        price = EventPrice.Unknown,
        ticketPrice = TicketPriceFieldState(signedIn = false, status = TicketPriceStatus.Idle),
        audience = EventAudience(null, null, 12),
    ),
)

@Preview(name = "EventDetails · loading", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun EventDetailsLoadingPreview() = EventDetailsPreview(
    previewState(price = EventPrice.Loading, ticketPrice = TicketPriceFieldState(signedIn = true)),
)

@Preview(name = "EventDetails · load failed", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun EventDetailsLoadFailedPreview() = EventDetailsPreview(
    previewState(price = EventPrice.Unknown, ticketPrice = TicketPriceFieldState(signedIn = true, status = TicketPriceStatus.LoadFailed)),
)

@Preview(name = "EventDetails · load failed, signed out", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun EventDetailsLoadFailedSignedOutPreview() = EventDetailsPreview(
    previewState(
        price = EventPrice.Unknown,
        audience = EventAudience("Pour les 16 ans et plus", null, null),
        ticketPrice = TicketPriceFieldState(signedIn = false, status = TicketPriceStatus.LoadFailed),
    ),
)

@Preview(name = "EventDetails · save failed", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun EventDetailsSaveFailedPreview() = EventDetailsPreview(
    previewState(
        ticketPrice = TicketPriceFieldState(signedIn = true, input = "30", status = TicketPriceStatus.SaveFailed),
        canAddWithoutPrice = true,
    ),
)

@Preview(name = "EventDetails · saving", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun EventDetailsSavingPreview() = EventDetailsPreview(
    previewState(ticketPrice = TicketPriceFieldState(signedIn = true, input = "30", status = TicketPriceStatus.Saving)),
)

@Preview(name = "EventDetails · saved", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun EventDetailsSavedPreview() = EventDetailsPreview(
    previewState(ticketPrice = TicketPriceFieldState(signedIn = true, input = "30", ownReportCad = 30.0, status = TicketPriceStatus.Saved)),
)

@Preview(name = "EventDetails · input errors", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun EventDetailsInputErrorPreview() = EventDetailsPreview(
    previewState(
        ticketPrice = TicketPriceFieldState(
            signedIn = true,
            input = "650",
            inputError = TicketPriceInputError.OutOfRange,
            status = TicketPriceStatus.Idle,
        ),
    ),
)

@Preview(name = "EventDetails · cannot add", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun EventDetailsCannotAddPreview() = EventDetailsPreview(previewState(canAdd = false))

@Preview(name = "EventDetails · long description, 360 dp, 1.3x", showBackground = true, widthDp = 360, heightDp = 720, fontScale = 1.3f)
@Composable
private fun EventDetailsLargeTextPreview() = EventDetailsPreview(
    previewState(
        showtimes = listOf(PreviewStart.minusHours(2), PreviewStart, PreviewStart.plusHours(2)),
        description = List(6) { PREVIEW_DESCRIPTION }.joinToString(" "),
    ),
)
