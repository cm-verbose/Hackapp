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
- `skip(): MealPickerOutcome.Skipped(blockId)`: Selection applies `itinerary.withMealSkipped(blockId)`, which frees the block so its Events reappear.

Always render **Skip this Meal**, including when rows exist.

## 2. Planned Departure (`PlannedDepartureState.kt`)

Create: `PlannedDepartureHolder.create(event, itinerary, zone): PlannedDepartureHolder?`. Null when the Event has no start time (it is never reachable then). Open the sheet only when `!state.hasPublishedEnd`. The prefill is the typical stay for the Event type (`typicalStayMinutes`), clamped to the Itinerary window end. The source end time stays empty.

`PlannedDepartureUiState`

| Field | Meaning |
|---|---|
| `eventId`, `eventTitle`, `eventType`, `start` | The Event |
| `windowEnd` | End of the Itinerary window, after midnight on Night Itineraries |
| `departure` | Last valid choice |
| `stayMinutes` | Stay duration, from `start` to `departure`, to show next to the time |
| `hasPublishedEnd` | Always false when the sheet is needed |
| `isTypicalStay` | Still the typical stay: label it as typical, not as chosen |
| `crossesMidnight` | `departure` is on a later day than `start`: show an after-midnight hint |
| `error`, `rejectedTime` | `OutsideWindow` (after the window end) or `NotAfterStart` (stay of zero or less). The invalid pick leaves `departure` unchanged |
| `canConfirm` | `error == null` |

Callbacks
- `setTime(LocalTime)`: from a time picker. A time at or before the start rolls to the next day, so 01:00 after a 23:00 start means after midnight. A time after the window end sets `OutsideWindow`.
- `setStayMinutes(Int)`: for steppers.
- `reset()`: back to the typical stay.
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
| `canAdd` | False when the Event cannot become a Stop: disable **Add to Itinerary** |

`EventPrice`
- `Free`: counts as $0.
- `UserMedian(medianCad, reportCount)`: label it "median of user-reported prices".
- `Published(minCad, maxCad)`: the source's own price, only when there are no reports.
- `Unknown`: unknown-price state (no reports and no published price).

`TicketPriceFieldState`
- `signedIn`: false means hide the field or show a sign-in prompt. Nobody else is ever shown a name.
- `input`, `inputError` (`NotANumber`, `OutOfRange`), `minCad` 0, `maxCad` 500. Blank is valid and means no report. `$` and a comma decimal are accepted, at most 2 decimals.
- `ownReportCad`: this person's own saved report, loaded by `load()`; the field is prefilled with it.
- `status`: `Loading`, `Idle`, `Saving`, `Saved`, `LoadFailed` (offer Retry, which calls `load()`; the Event can still be added), `SaveFailed` (input is kept, retry).
- `canSave`: enable the save button.
- Always tell the person the price is shared and applies to all showtimes of this title and venue that day.

Callbacks
- `suspend load()`: median plus own report. Safe to call again.
- `selectShowtime(eventId)`.
- `setTicketPriceInput(String)`: validates as the person types.
- `suspend saveTicketPrice(): Boolean`: one editable report per user and Event. Updates the median.
- `suspend addToItinerary(): AddToItineraryAction?`: saves a pending valid ticket price first. Null when the input is invalid, the save failed, or the Event cannot be added. Otherwise:
  - `Add(stop)`: published end. Apply `itinerary.withStop(stop)`.
  - `ChooseDeparture(event, ticketPriceCad)`: no published end. Open the Planned Departure sheet, then `eventStop(event, departure, ticketPriceCad)`.

The Stop's `ticketPriceCad` is only this person's own report. The running total uses the own report, then the median, then the published price (`knownEventPrice`).
