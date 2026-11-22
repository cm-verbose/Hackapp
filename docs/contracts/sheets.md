# Planning sheets: logic contract

For the Feature Engineer and team Selection. The state holders are plain Kotlin classes with no Android dependencies, in `app/src/main/java/com/example/hackapp/scaffold/screens/planning/`. Each exposes `state: StateFlow<...UiState>` and methods. Selection builds the inputs, opens the sheet, and applies the outcome. All user-facing text comes from string resources, and times and money are formatted by the UI.

Flow in Event and Meal selection: Event card -> **Event details** -> **Add to Itinerary** -> **Planned Departure** (only when the Event has no published end) -> Stop added. A Meal Block -> **Meal picker** -> a Meal Stop or **Skip this Meal**.

## 1. Meal picker (`MealPickerState.kt`)

Create: `MealPickerHolder(search: MealSearch, estimator: LegEstimator)`. `MealSearch` carries the Itinerary, the block, the Places, the context, the current and next District ids.

`MealPickerUiState`

| Field | Meaning |
|---|---|
| `blockId`, `kind`, `blockStart`, `blockEnd` | The reserved Meal Block |
| `tabs` | `[Here]`, or `[Here, NextDistrict]` when another District follows. Render no second tab when absent |
| `selectedTab`, `nextDistrictId` | Active tab. `nextDistrictId` is the name source for **In [next District]** (look it up in the City Pack) |
| `here`, `nextDistrict` | Rows already filtered (open during the block, fit the Meal budget, bars rules, reachable by block start for the second tab) and ranked |
| `options` | Rows of the selected tab |
| `isEmpty` | Selected tab has no rows: show the empty state for that tab |
| `hasNoOptions` | Both tabs are empty: show the empty state and make **Skip this Meal** the main action |

`MealOptionRow`: `placeId`, `name`, `venueType`, `distanceMeters` (null without a reference point), `walkingMinutes` (null likewise), `price`, `inNextDistrict`, `matchesCuisine`.

`MealPrice`
- `Range(range: PriceRange, countedCad: Int?)`: show `range.label()` as a range (for example $10-20, or $100+). The cost counts `countedCad`, the top of the range. When `countedCad` is null (the "$100+" range) the cost is left out of the total and counted as an unknown price.
- `Unknown`: show "+ unknown price". The row stays visible and ranks below priced rows.

Callbacks
- `selectTab(tab)`: ignored when the tab is not offered.
- `choose(placeId): MealPickerOutcome.Chosen?`: returns `Chosen(stop, moveToDistrictId)`. Selection applies `itinerary.withStop(stop)`; when `moveToDistrictId` is not null, planning moves to that District after the Meal. Null if the row is unknown.
- `skip(): MealPickerOutcome.Skipped(blockId)`: Selection applies `itinerary.withMealSkipped(blockId)`, which frees the block so its Events reappear and removes that block's Meal Stop.
- Choosing a Meal again for the same block replaces the earlier Meal Stop (`withStop` replaces by `mealBlockId`).

Always render **Skip this Meal**, including when rows exist.

## 2. Planned Departure (`PlannedDepartureState.kt`)

Create: `PlannedDepartureHolder.create(event, itinerary, zone, latestDeparture): PlannedDepartureHolder?`. Compute `latestDeparture` with `latestDeparture(event, itinerary, zone, estimator)`: the earliest of the window end, the next active Meal Block start, and the next active Stop start minus the transit Leg to it. Null when the Event has no start time (it is never reachable then). Open the sheet only when `!state.hasPublishedEnd`. The prefill is the typical stay for the Event type, clamped to `latestDeparture`. The source end time stays empty.

`PlannedDepartureUiState`

| Field | Meaning |
|---|---|
| `eventId`, `eventTitle`, `eventType`, `start` | The Event |
| `earliestDeparture`, `latestDeparture` | The valid range: picks must be after the first and no later than the second. Show one range error with both times (replaces `windowEnd`) |
| `typicalStayMinutes` | Typical stay for the Event type, for a "Typical stay" label or reset hint |
| `departure` | Last valid choice |
| `stayMinutes` | Stay duration, from `start` to `departure`, to show next to the time |
| `hasPublishedEnd` | Always false when the sheet is needed |
| `isTypicalStay` | `stayMinutes` equals `typicalStayMinutes` and no end was published. False when clamped to the limit or edited |
| `crossesMidnight` | `departure` is on a later day than `start`: show an after-midnight hint |
| `error`, `rejectedTime` | `OutsideWindow` (outside the range, including a time before the start and a stay of zero or less: one range error) or `NotAfterStart` (no valid time exists at all). The invalid pick leaves `departure` unchanged |
| `canConfirm` | `error == null` |

Callbacks
- `setTime(LocalTime)`: from a time picker. A time at or before the start counts as the next day only when that is inside the range (01:00 after a 23:00 start is after midnight). Otherwise it is the range error. A time after `latestDeparture` sets `OutsideWindow`. A time equal to the one already shown is ignored, so a prefilled time keeps its offset on the night clocks go back.
- `setStayMinutes(Int)`: for steppers.
- `reset()`: back to the typical stay (or `latestDeparture` when that is earlier).
- `confirm(): ZonedDateTime?`: the Planned Departure, or null while there is an error. Selection then calls `eventStop(event, departure, ticketPriceCad)` and `itinerary.withStop(stop)`.

## 3. Event details (`EventDetailsState.kt`)

Create: `EventDetailsHolder(showtimes: List<ReachableShowtime>, initialEventId, zone, uid: String?, repository: TicketPriceRepository)`, where `showtimes` is the tapped `EventCard.showtimes`. `uid` is null when nobody is signed in. Call `suspend load()` when the sheet opens.

`EventDetailsUiState`

| Field | Meaning |
|---|---|
| `eventId`, `title`, `type`, `start` | Selected showtime |
| `showtimes: List<ShowtimeOptionUi(eventId, start, selected)>`, `hasRepeatedShowtimes` | Show the time chooser only when more than one |
| `publishedEnd`, `endTimeUnpublished` | When unpublished, show the end-time-unpublished state instead of an end time |
| `venueName`, `address` | Venue |
| `price: EventPrice` | See below |
| `audience: EventAudience?` | `description`, `minAge`, `maxAge`. Null when none |
| `description`, `ticketUrl` | Both nullable. Hide the row or link when null |
| `ticketPrice: TicketPriceFieldState` | The optional ticket price field |
| `canAdd` | False when the Event cannot become a Stop, and while prices load or save: disable **Add to Itinerary** |
| `canAddWithoutPrice` | The last save or clear failed (`SaveFailed`): offer **Add without price** |

`EventPrice`
- `Loading`: reports have not finished loading (until `load()` ends). Show a loading state, never "unknown price".
- `Free`: counts as $0.
- `UserMedian(medianCad, reportCount)`: label it "median of user-reported prices".
- `Published(minCad, maxCad)`: the source's own price, only when there are no reports.
- `Unknown`: unknown-price state (no reports and no published price).

`TicketPriceFieldState`
- `applicable`: false for Free Events. Hide the whole section: nothing can be entered or saved, and no report goes with the Stop.
- `signedIn`: false means hide the field or show a sign-in prompt. Nobody else is ever shown a name.
- `input`, `inputError` (`NotANumber`, `OutOfRange`), `minCad` 0, `maxCad` 500. Blank is valid and means no report. `$` before or after the amount and a comma decimal are accepted (`25 $`, `12,50 $`), at most 2 decimals, ASCII digits only. A leading minus is `OutOfRange`.
- `ownReportCad`: this person's own saved report, loaded by `load()`; the field is prefilled with it.
- `status`: `Loading`, `Idle`, `Saving`, `Saved`, `LoadFailed` (offer Retry, which calls `load()`; the Event can still be added), `SaveFailed` (input is kept, retry).
- `canSave`: enable the save button. Blank input while `ownReportCad` is set clears the report (`saveTicketPrice()` deletes it and refreshes the median); a failed clear gives `SaveFailed`.
- `isBusy`: loading or saving. Cancelling a load leaves `LoadFailed`; cancelling a save leaves `Idle`.
- Always tell the person the price is shared and applies to all showtimes of this title and venue that day.

Callbacks
- `suspend load()`: median plus own report. Safe to call again.
- `selectShowtime(eventId)`.
- `setTicketPriceInput(String)`: validates as the person types.
- `suspend saveTicketPrice(): Boolean`: one editable report per user and Event. Updates the median.
- `suspend addToItinerary(): AddToItineraryAction?`: saves a pending valid ticket price first. Null while loading or saving, when the input is invalid, the save failed, or the Event cannot be added. Otherwise:
  - `Add(stop)`: published end. Apply `itinerary.withStop(stop)`.
  - `ChooseDeparture(event, ticketPriceCad)`: no published end. Open the Planned Departure sheet, then `eventStop(event, departure, ticketPriceCad)`.
- `addWithoutPrice(): AddToItineraryAction?`: only when `canAddWithoutPrice`. Adds without saving the typed price and keeps the last saved report; same `Add` or `ChooseDeparture` outcome. A failed optional price never blocks adding.

The Stop's `ticketPriceCad` is only this person's own report. The running total uses the own report, then the median, then the published price (`knownEventPrice`).

## 4. Compose API (`ui/components/`)

Each sheet is a `ModalBottomSheet` wrapper around a stateless `*Content` composable that takes the `UiState` and callbacks. Selection collects the holder's `state` and forwards the callbacks; the `*Content` composables are what the `@Preview`s render. Money, ranges and durations are formatted by `MoneyFormat.kt` (`formatCad`, `formatCadRange`, `formatDurationMinutes`), sentence times by `formatSentenceTime`.

### `MealPickerSheet.kt`

```kotlin
MealPickerSheet(state: MealPickerUiState, onSelectTab: (MealTab) -> Unit, onChoose: (placeId: Long) -> Unit,
    onSkip: () -> Unit, onDismiss: () -> Unit, budgetLabel: String? = null)
MealPickerContent(state, nextDistrictName: String?, budgetLabel: String?, onSelectTab, onChoose, onSkip, modifier)
```

- The sheet looks up `nextDistrictName` from the City Pack (`rememberDistrictMap()`) by `state.nextDistrictId`; while it is unknown the tab says `meal_tab_next`.
- Each row shows the venue type (`venueTypeLabel`, strings `venue_type_*`) under the name.
- `budgetLabel` is the Meal budget bracket label Selection already shows (for example `budget_under_50`); null drops the budget part of the subtitle.
- Tabs render only when `state.tabs.size > 1`. **Skip this Meal** is a text button, or the primary button when `hasNoOptions`.
- `onChoose` receives the row's `placeId`; Selection calls `holder.choose(placeId)` and applies the outcome.

### `PlannedDepartureSheet.kt`

```kotlin
PlannedDepartureSheet(state: PlannedDepartureUiState, venueName: String?, onTimeChange: (LocalTime) -> Unit,
    onConfirm: () -> Unit, onDismiss: () -> Unit)
PlannedDepartureContent(state, venueName, onTimeChange, onConfirm, onCancel, modifier)
```

- Uses `HackappTimeWheels`; every wheel move calls `onTimeChange` (wire it to `holder.setTime`). The wheels show `rejectedTime` while there is an error, else `departure`.
- `venueName` fills the subtitle's middle part; the Event type label stands in when null. The state has no venue field.
- The note uses `state.typicalStayMinutes`; the duration row adds `planned_departure_next_day` when `crossesMidnight`.
- **Confirm** is disabled while `!canConfirm`. **Cancel** dismisses.

### `EventDetailsSheet.kt`

```kotlin
EventDetailsSheet(state: EventDetailsUiState, onSelectShowtime: (eventId: String) -> Unit, onTicketPriceChange: (String) -> Unit,
    onRetryLoad: () -> Unit, onSignIn: () -> Unit, onAddToItinerary: () -> Unit, onDismiss: () -> Unit)
EventDetailsContent(state, onSelectShowtime, onTicketPriceChange, onRetryLoad, onSignIn, onAddToItinerary, onClose, modifier)
```

- The ticket link opens `ticketUrl` with `LocalUriHandler` (an `IllegalArgumentException` from a device with no browser is swallowed). The ticket field uses a decimal keyboard and `HackappTextField`'s new `supportingText` for the range helper, and is disabled while `ticketPrice.isBusy`.
- No save button: **Add to Itinerary** calls `onAddToItinerary`, which Selection wires to `holder.addToItinerary()`. It is disabled while `!canAdd`, while the input is invalid, or while `ticketPrice.isBusy`.
- `onRetryLoad` wires to `holder.load()`; the `LoadFailed` text and Retry show for signed-out people too. `onSignIn` is shown only when `ticketPrice.signedIn` is false.
- Audience label: `audience.description` when non-blank; otherwise `minAge >= 18` is adults, a `maxAge` is family, else all audiences.
