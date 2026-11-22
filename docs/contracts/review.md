# Contract: Itinerary review (Planning step 6) and Stop edit warnings

Owner: Domain Engineer, team Review. Consumer: Feature Engineer. Files:
`scaffold/screens/planning/ItineraryReviewViewModel.kt` (state and callbacks),
`itinerary/StopRemoval.kt` and `itinerary/PlanningChanges.kt` (pure rules),
`scaffold/screens/planning/StopFitReconciler.kt` (the real `PlanningChangeReconciler`).
The pages are Compose only and hold no rules. All text comes from string resources; times and money are formatted by the UI.

## 1. Itinerary review

### Construction

`ItineraryReviewViewModel.factory(itineraryId, auth, itineraries, estimator, zone, loadContext, loadPrices, loadEvents, demoMode = false, clock = Instant::now)`

| Parameter | Meaning |
|---|---|
| `estimator` | `services.legs` (`LegEstimator`) |
| `zone` | `LocalCityPack.current.timeZone` |
| `loadContext` | `suspend () -> PlanningContext`, for example `PlanningContext.from(profile, now, zone)` |
| `loadPrices` | `suspend (Itinerary) -> BudgetPrices` for the budget summary (Events by id, medians, Meal price ranges) |
| `loadEvents` | `suspend (Itinerary) -> List<Event>`: Events of the window. Called only when a removal needs showtimes |
| `demoMode` | No global demo flag exists yet. Pass the flag when there is one. Default false |

The ViewModel loads on creation. It is a thin wrapper over `ItineraryReviewHolder`, the plain class that holds every rule and is unit tested. Step 6 is reached only from selection with at least one Event (Finish here).

### UiState (`state: StateFlow<ItineraryReviewUiState>`)

| Field | Type | Meaning |
|---|---|---|
| `loading` | Boolean | First load or retry. Render progress. |
| `loadFailed` | Boolean | Itinerary, context or prices could not be read. Render the error with Retry. |
| `itinerary` | Itinerary? | The draft. Null while loading. |
| `stops` | List<ReviewStop> | Active Stops in visit order (`number` is 1-based). Skipped Stops are not listed. |
| `highlightedStopId` | String? | The Stop highlighted in the list and on the map. |
| `budget` | BudgetSummary? | Running total versus the Itinerary budget (Events, Meals, total, unknown prices, `isOverBudget`). Over budget warns only. |
| `start` | ReviewStartState | What the Start button can do. See below. |
| `dialog` | ReviewDialog? | The dialog to show, or null. |
| `reschedule` | RescheduleStep? | The showtime picker for an affected Stop, or null. |
| `busy` | Boolean | A save or start is running. Disable Start, Remove and dialog actions. |
| `actionFailed` | Boolean | The last Start, Remove or confirm failed. Show the save error. Cleared by the next action. |
| `navigation` | ReviewNavigation? | Navigate, then call `onNavigated()`. |

`ReviewStop`: `stop: Stop`, `number: Int`, `highlighted: Boolean`, `leaveBy: OffsetDateTime` (the earlier of the Planned Departure and the time to reach the next Stop; the last Stop leaves at its Planned Departure). Show `stop.start` to `stop.plannedDeparture`. Show **Retirer** on every row.

`ReviewStartState` (sealed)
- `Ready`: enable **Start**. `demoOverride` is true when only demo mode makes it ready (the date is not today); a demo build may label it.
- `NotToday(date)`: the Itinerary is on a later date. Disable Start and show "Start on [date]".
- `WindowEnded(date)`: the date and window have passed. Disable Start and offer Change date and time (opens Date and time).
- `NoEvents`: needs at least one Event. Disable Start.
- `NotPlanning`: the Itinerary is already Underway, Past or Archived. Hide Start.

Start works on the Itinerary's date, including the hours after midnight of its window (`startBlock`). Demo mode skips the date and window check only.

### Dialogs (`ReviewDialog`, sealed)

1. `EndCurrent(current, confirmedStops, skippedStops)`: another Itinerary is Underway. Message: starting this one ends the current one. The old one moves to Past, keeps Stamps for its `confirmedStops`, and its `skippedStops` (every Stop not confirmed) become skipped. Actions: **Confirm** (`confirmDialog()`), **Cancel** (`dismissDialog()`).
2. `RemoveAffects(removed, affected, canReschedule)`: removing `removed` leaves `affected` Stops (in visit order) that transit from the previous remaining Stop cannot reach by their start. List them. Three actions:
   - **Replanifier / Reschedule** (`reschedule()`): enabled only when `canReschedule`. It is false when any affected Stop is a Meal (a Meal's time is its Meal Block) or has no other reachable showtime of the same title and venue. Opens the picker (`reschedule` state).
   - **Réorganiser / Reorganize** (`reorganize()`): removes `removed` and the listed `affected` Stops, keeps every later Stop that is still reachable, and returns to selection from that gap to rebuild.
   - **Cancel** (`dismissDialog()`): nothing is saved.

Nothing is saved while a dialog is open. The affected Stops are never removed silently.

### Reschedule picker (`RescheduleStep`)

`stop` (the affected Stop), `options: List<RescheduleOption>`, `index`, `total` ("2 of 3"). `RescheduleOption`: `eventId`, `start`, `plannedDeparture`, `hasPublishedEnd`. Options are other showtimes of the same title and venue that are reachable from the previous remaining Stop and leave time to reach the next one. `pickShowtime(eventId)` takes the option (its suggested departure) and moves to the next affected Stop; the last pick saves everything at once and navigates back to Review (state reloads). If a later Stop has no option left, show the empty state with **Réorganiser** (`reorganize()`) and **Cancel** (`cancelReschedule()`: nothing saved, dialog returns).

### Callbacks

| Callback | Effect |
|---|---|
| `retry()` | Reload after `loadFailed`. |
| `selectStop(id: String?)` | Highlights a Stop (marker tap or row tap). Tapping the highlighted Stop again, or null, clears it. Unknown ids are ignored. |
| `start()` | Starts the Itinerary when `start` is `Ready`. Another Itinerary Underway opens `EndCurrent`. On success `navigation = Started(id)`. |
| `remove(stopId)` | Removes the Stop. With no affected Stops it saves and sets `navigation = BackToSelection`. Otherwise it opens `RemoveAffects` and saves nothing. |
| `reschedule()` | From `RemoveAffects` with `canReschedule`. Opens the picker. |
| `pickShowtime(eventId)` | Takes an option in the picker. |
| `reorganize()` | From `RemoveAffects` or the picker. Saves and sets `navigation = BackToSelection`. |
| `confirmDialog()` | Confirms `EndCurrent`. |
| `dismissDialog()` | Cancels the open dialog. |
| `cancelReschedule()` | Leaves the picker, nothing saved. |
| `onNavigated()` | Clears `navigation`. |

`ReviewNavigation`
- `Started(itineraryId)`: open Underway for this id.
- `BackToSelection(itineraryId, afterStopId, districtId)`: open Event and Meal selection for the gap that follows `afterStopId` (null = before the first Stop, `Itinerary.gapAfter(afterStopId)`), in `districtId` (the removed Stop's District). The saved Itinerary has `step = Stops`, so Back from selection and resuming the draft land on selection, not Review.

### Rules

- Removing a Stop: a later Stop is affected only when `previous.plannedDeparture` plus the transit Leg to it is after its start, where previous is the last remaining Stop before it that is itself reachable. Stops after an affected one are checked against the last reachable Stop, not the affected one.
- Removing a Meal Stop keeps its Meal Block, so the block's prompt returns in selection.
- Only `Planning` Itineraries can start. At most one Itinerary is Underway.

## 2. Stop edit warnings for Date and time, Budget and District

`PlanningChangeReconciler` now has a second method, `suspend preview(previous, updated): PlanningChangePreview`, whose default says nothing changes (so `MealStopReconciler` is unchanged and keeps its own silent Meal Stop rules). `KeepSavedStops` is deleted. The real reconciler is `StopFitReconciler`, built with `services.stopFitReconciler()` (it is also the default of the Date and time and District factories from the City Pack; the Budget factory takes it as a parameter).

`PlanningChangePreview`: `previous`, `proposed` (what would be saved), `kept: List<Stop>`, `removed: List<RemovedStop>` (`stop`, `reason`), `needsConfirmation` (any removal).

Reasons (`StopRemovalReason`): `DateChanged` (Event Stops belong to their date), `OutsideWindow`, `DistrictRemoved`, `MealBlockRemoved`, `MealClosed` (the Meal is closed at the new date), `NoLongerReachable` (transit from the previous kept Stop is too slow), `MealOverBudget` (new Meal budget below the Meal's price). Event Stops are never removed by Budget (over budget only warns).

The rules are state based, so they are idempotent and run on every save that has Stops: moved Meal Stops keep their clock time on a new date; a Stop is checked against the new window, the new District list, the Meal Blocks that still fit, Meal opening hours, and the reachability chain.

### What each page does

Each of the three ViewModels gains `changeWarning: PlanningChangePreview?` in its state and the callbacks `confirmChange()` and `cancelChange()`.

- **Date and time** (`PlanningDateTimeViewModel.save()`): when the date or window changed and Stops would be removed, it saves nothing, sets `changeWarning`, and `saving` is false. Same for an Archived draft with **Choose a new date**.
- **District pick** (`DistrictPickViewModel.continueToMeals()`): the tap autosave still stores only the selection. The warning appears on Continue. Cancel keeps the selection and stays on the page.
- **Budget** (`PlanningBudgetViewModel.keep()` / `saveEdits()`): the warning appears when a Meal Stop no longer fits its new bracket. Cancel keeps the edited values on screen.

Dialog (shared by the three pages): title says some Stops no longer fit; list `removed` with the reason text (one string per reason), list `kept` Stops as staying; actions **Confirm** (`confirmChange()`: saves `proposed` and navigates as Continue normally does) and **Cancel** (`cancelChange()`: nothing saved). Show nothing when `changeWarning` is null. Removed Stops are listed by name and time; never remove them without this dialog (Meal Stops of a removed or moved Meal Block on the Meal choice page are still removed silently, per the Meals contract).

## 3. States and dialogs the UI must render

1. Loading, and load error with Retry.
2. Stop list with times, Leave By, the highlighted Stop and the budget summary (including over budget and "+ unknown price").
3. Start: Ready, not today (with date), window ended, no Events.
4. End-current confirmation.
5. Remove with no effect on later Stops: just return to selection.
6. Remove warning: affected list, Reschedule (maybe disabled), Reorganize, Cancel.
7. Reschedule picker, its empty state, and progress.
8. Save error (`actionFailed`).
9. On Date and time, Budget and District pick: the shared change-warning dialog with removed and kept Stops.
