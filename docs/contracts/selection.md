# Contract: Event and Meal selection (Planning step 5)

Owner: Domain Engineer, team Selection. Consumer: Feature Engineer. File:
`scaffold/screens/planning/PlanningStopSelectionViewModel.kt`. The page is Compose only and holds no rules. Pure rules are in `itinerary/StopSelection.kt` and `itinerary/EventSelection.kt`. The sheets (Event details, Planned Departure, Meal picker) are described in `docs/contracts/sheets.md`; this ViewModel builds their holders and applies their outcomes.

## Construction

`PlanningStopSelectionViewModel.factory(itineraryId, auth, itineraries, profiles, events, places, tickets, estimator, zone)`.
`events` is `CityServices.events`, `places` is `CityServices.places`, `estimator` is `CityServices.legs`, `zone` is the City Pack time zone, `tickets` is the app's `TicketPriceRepository`. The ViewModel loads on creation. All rules live in the plain class `StopSelectionSession`, which has the same methods as suspend functions and no Android dependency.

## UiState (`state: StateFlow<PlanningStopSelectionUiState>`)

| Field | Type | Meaning |
|---|---|---|
| `loading` | Boolean | First load or retry. Render a progress state. |
| `loadFailed` | Boolean | Draft or Events could not be read, or the draft has no District. Render the error with Retry. |
| `districtIds` | List<String> | The District ids still in play, in visit order. Names come from the City Pack. |
| `districtId` | String? | The District being planned. |
| `districtNumber` | Int | 1-based position of `districtId` in `districtIds`, for "District 2 of 3". |
| `hasNextDistrict` | Boolean | Another District follows. Show **Next borough** and **Finish here**. When false show only **Finish**. |
| `canFinish` | Boolean | At least one Event Stop exists. Enables **Finish here** and **Finish**, which stay visible but disabled until then. |
| `gapStart` | ZonedDateTime? | Where the open time gap starts: the last Stop's Planned Departure, else the later of the window start and now. |
| `gapEnd` | ZonedDateTime? | Where it ends: the start of the next Meal Block (margins included), else the window end. |
| `groups` | List<EventHourGroup> | Events of this District for this gap, grouped by start hour then type, already ranked. Empty means the "no Events" state. |
| `mealPrompt` | MealPrompt? | The next unresolved Meal Block, or null when none remains. |
| `stops` | List<Stop> | Active Stops in visit order, for the timeline. |
| `budget` | BudgetSummary? | Running total, null before the first load. |
| `sheet` | SelectionSheet? | The open sheet, if any. |
| `saveFailed` | Boolean | The last autosave failed. Show the save error with **Retry** (`retrySave()`). Cleared by the next successful save. |
| `reviewId` | String? | Set after Finish. Navigate to Review with this id, then call `onNavigated()`. |
| `leaveId` | String? | Set by `back()` when planning is at the first District with nothing to undo. Navigate to the previous step (Meal choice) with this id, then call `onNavigated()`. |

`MealPrompt`

| Field | Meaning |
|---|---|
| `blockId`, `kind`, `start`, `end` | The reserved Meal Block (`end` = start + block length). |
| `reached` | No Event remains before the block in this gap. Make **Choose a Meal** the main action. When false, show the prompt below the Events as a secondary row. |

Both actions are always available: `openMealPicker()` and `skipMeal()`.

`EventCard` (from `itinerary/EventSelection.kt`): `title`, `venueName`, `type`, `showtimes` (selectable times in order), `matchesInterest`, `priceRank`, `priceCad` (null = unknown, 0 = free), `travelMinutes`, and `isDropIn`.

`SelectionSheet` (sealed). Each holds the sheet's holder; collect `holder.state` and forward its callbacks through the ViewModel methods below.
- `EventDetails(holder: EventDetailsHolder)`
- `PlannedDeparture(holder: PlannedDepartureHolder, event: Event, ticketPriceCad: Double?)`
- `MealPicker(holder: MealPickerHolder, block: MealBlock)`

## Callbacks

| Callback | Effect |
|---|---|
| `retry()` | Reloads after `loadFailed`. |
| `retrySave()` | Saves the current draft again after `saveFailed`. |
| `openEvent(card)` | Opens Event details for the card and loads its prices. |
| `selectShowtime(eventId)`, `setTicketPriceInput(text)`, `retryEventDetails()` | Forward to the details holder. |
| `addToItinerary()` | From Event details. With a published end the Stop is added at once. Without one the sheet becomes `PlannedDeparture`. Nothing happens while the holder says it cannot add. |
| `setDeparture(time)`, `confirmDeparture()` | From Planned Departure. Confirm adds the Stop. |
| `openMealPicker()` | Opens the Meal picker for `mealPrompt`. Ignored without a prompt. |
| `selectMealTab(tab)`, `chooseMeal(placeId)` | Forward to the picker holder. Choosing adds the Meal Stop; choosing from the next District also moves planning there. |
| `skipMeal()` | Skips the prompt's block (from the picker or from the prompt). Frees the block so its Events reappear. |
| `dismissSheet()` | Closes the open sheet without changes. |
| `nextDistrict()` | Moves to the next District. The one left is dropped from the Itinerary if it has no Stop. Ignored on the last District. |
| `finish()` | Ignored unless `canFinish`. Drops Districts with no Stop, sets `step = Review`, saves and sets `reviewId`. Used by both **Finish here** and **Finish**. |
| `back()` | Undoes a Meal move, else goes to the previous District, else sets `leaveId`. Never removes Stops. |
| `onNavigated()` | Clears `reviewId` and `leaveId`. |

## Rules

**Gap.** The page works through one time gap at a time in `districtId`. The gap starts at the last active Stop's Planned Departure and ends at the next unresolved Meal Block start (margins are inside the block length). An unresolved block is active (not skipped, Meal planning on) with no Meal Stop, and ends after the gap start. Only showtimes that start before `gapEnd` are listed, so nothing after a Meal can be added before the Meal is chosen or skipped. Every change (adding a Stop, a Meal, or skipping) recomputes the list from the new departure.

**Eligible Events.** `isEligible` (age, audience, cancelled, sold out, past), in `districtId`, starting inside the window and not before now, reachable by public transit from the last Stop (plus buffer), able to leave in time for a later Stop. An Event already in the Itinerary, or another showtime of the same title, venue and day, is hidden. Events are never hidden by budget. Events of type "other" are hidden (demo rule).

**Grouping and ranking.** Hour groups in time order, types inside an hour ordered by their best card. Cards rank by: matches an Interest, then price (known within budget, unknown, known over budget), then shortest travel time, then start, then title. The price used is the free price, else the shared median of user reports, else the published price. Repeated showtimes of one title at one venue on one day are one card with several `showtimes`.

**Meal Blocks and Events.** A fixed-showtime Event whose stay overlaps an active Meal Block is hidden. A drop-in Event stays visible even when its stay would cross a block. **Drop-in rule:** the Event published an end time and `end - start >= 4 hours` (`DROP_IN_MIN_MINUTES = 240`). An Event with no published end is never drop-in, because its stay is a guess. For a drop-in that crosses a block the suggested departure is shortened to `latestDeparture`: the earliest of the window end, the next Meal Block start and the next Stop minus the Leg to it. Details therefore adds the Stop with that earlier departure, and the Stop keeps `hasPublishedEnd = true`. **In-progress drop-in:** a drop-in that began before the person can arrive is still offered when they can arrive before its end with at least `MIN_DROP_IN_STAY_MINUTES` (60) left, after any Meal Block cut. Arrival is the latest of the window start, now, and the previous Stop's departure plus the transit Leg (the gap start for the first Stop). It must be before the next Meal Block start. `ReachableShowtime.start` and the Event keep the real published times for display; `ReachableShowtime.arrivalAt` is the earliest reachable arrival, and the created Stop has `start = arrivalAt` (`eventStop(..., arrival)`, wired in Event details). A drop-in that starts inside the gap has `arrivalAt = start`. Cards are grouped and sorted by `arrivalAt`, so an exhibition from 10:00 sits in the 18:00 hour of an 18:00 gap. Everything keyed on the Event still uses the Event: duplicate detection (`sourceId` and `showtimeKey`), ticket prices (`showtimeKey`) and the running total. District counts include a running drop-in with enough time left. The Events query has no lower start bound, so Events that began before the window are loaded. Fixed showtimes must still start at or after the earliest arrival. `latestDeparture` still assumes the Event start, which is fine because drop-ins always have a published end and never open Planned Departure.

**Meal prompt.** The prompt is exposed while a block is unresolved. Choose opens the picker (tabs **Here** and **In [next District]**, per sheets.md). Skip applies `withMealSkipped(blockId)`, which also removes that block's Meal Stop.

**Next District Meal.** When the chosen Meal is in the next District, the Stop is added and planning moves there. The move is undoable: while nothing else has changed, `back()` removes that Meal Stop and returns to the earlier District with the prompt open again. After any other change, Back goes to the previous District and keeps every Stop. The undo is lost if the app is closed; Back then keeps the Stop.

**Advancing.** `nextDistrict()` works at any time, even with a Meal unresolved (the prompt then follows to the next District). A District left with no active Stop is removed from `Itinerary.districtIds`, when leaving it forward, back to the previous District, or at Finish (unvisited Districts then go too). Back from the first District to the previous step never drops it. The last District shows only **Finish**. Both finish buttons need `canFinish` (an Event Stop exists).

**Running total.** `budget` is `PlanningEngine.budget`: chosen Event prices (own report, else median, else published, free = 0) plus the top of each chosen restaurant's range, compared with `totalBudgetCad` (Event budget plus selected Meal budgets). A Meal with no budget adds its price and nothing to the limit. A range with no top ("$100+"), and any Stop with no known price, is not added and counts in `unknownPriceCount`; show "+ prix inconnu" when `hasUnknownPrices`. `isOverBudget` warns only and never blocks.

**Autosave and resume.** Every change saves the Itinerary (`stops`, `mealBlocks` skip flags, `districtIds`, `planningDistrictId`, `step = max(step, Stops)`). A reopened draft resumes in `planningDistrictId` (else the District of the last Stop, else the first) with all Stops, skipped blocks and removed Districts. Finish sets `step = Review`.

## States the page must render

1. Loading.
2. Load error with Retry.
3. Event list: hour groups with type groups and cards; a card with several showtimes shows its times; a drop-in card is marked (for example "open long hours") using `isDropIn`.
4. No Events in this gap (`groups` empty) with the Meal prompt or the Next borough action still available.
5. Meal prompt (`reached` false: secondary row; `reached` true: main action), with Choose and Skip this Meal.
6. Event details sheet, then the Planned Departure sheet when the Event has no published end.
7. Meal picker sheet with its empty state (Skip this Meal main action).
8. Running total: normal, "+ prix inconnu", and over budget warning.
9. Footer: **Next borough** and **Finish here** (disabled until `canFinish`), or only **Finish** on the last District.
10. Save error with Retry.
11. Undone Meal move: the page simply shows the earlier District with the Meal prompt.
