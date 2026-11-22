# Contract: Meal choice (Planning step 4)

Owner: Domain Engineer, team Meals. Consumer: Feature Engineer. File:
`scaffold/screens/planning/PlanningMealChoiceViewModel.kt`. The page is Compose only and holds no rules.

## Construction

`PlanningMealChoiceViewModel.factory(itineraryId, auth, itineraries, profiles, zone, reconciler = MealStopReconciler)`.
`zone` is `LocalCityPack.current.timeZone`. The ViewModel loads on creation.

## UiState (`state: StateFlow<PlanningMealChoiceUiState>`)

| Field | Type | Meaning |
|---|---|---|
| `loading` | Boolean | First load or retry in progress. Render a progress state. |
| `loadFailed` | Boolean | Draft or Profile could not be read. Render the error with Retry. |
| `planMeals` | Boolean | true = the app plans Meals; false = the person handles food. Defaults to true. |
| `windowStart`, `windowEnd` | LocalTime? | The Itinerary window, set once the draft loads (null before). `windowEnd` may be after midnight. The page needs no second read of the draft. |
| `options` | List<MealChoiceOption> | The Meals offered, in order Lunch, Dinner, Late bite. A Meal is offered when its whole block can fit in the window at some start; its initial `start` is the default time clamped into the window (19:00-21:30 offers Dinner at 19:00). Empty only when the window is too short for every block. Hide the list when `planMeals` is false. |
| `noMealFits` | Boolean | No Meal block fits the Itinerary window. Render the "no Meal fits" state with the Change date and time action. |
| `canContinue` | Boolean | Enables Continue. |
| `saving` | Boolean | Save in progress. Disable Continue. |
| `saveFailed` | Boolean | Last save failed. Show the save error; Continue stays usable to retry. Cleared by any edit. |
| `continueToId` | String? | Set after a successful save. Navigate to the Stops step with this id, then call `onNavigated()`. |
| `changeDateTimeId` | String? | Set by `changeDateAndTime()`. Navigate to Date and time with this id, then call `onNavigated()`. |

`MealChoiceOption`:

| Field | Type | Meaning |
|---|---|---|
| `kind` | MealKind | Lunch (12:00 default, 90 min), Dinner (18:30, 90 min), LateBite (22:00, 45 min). |
| `selected` | Boolean | Chip state. Colour only, no checkmark. |
| `start` | LocalTime | Start time to show in the time field. Default is the kind's default start. |
| `endTime` | LocalTime | `start` + the block length. Show as the reserved range. May be past midnight. |
| `budget` | BudgetBracket? | The bracket chosen for this Meal; null = none chosen (the fallback applies). |
| `effectiveBudget` | BudgetBracket? | What search will use: `budget`, else this Itinerary's override, else the Profile budget. Show it as the unselected default. |
| `error` | MealTimeError? | Only for a selected Meal. `OutsideWindow`: the block leaves the Itinerary window. `Overlaps`: the block overlaps another selected block. |

Offered Meals are those that can fit at some start (default time clamped into the window), plus any Meal restored from the draft. Only Meals whose unclamped default fits are preselected on first open.
Unselected options still carry `start` and `budget`; the page may show the time and budget fields only when `selected`.

## Callbacks

| Callback | Effect |
|---|---|
| `setPlanMeals(Boolean)` | Chooses planned or self-handled. false reserves no Meal Blocks; selections are kept in the state in case the person switches back. |
| `toggleMeal(MealKind)` | Selects or deselects an offered Meal (multi-select, toggles off). Ignored for kinds not offered. |
| `setStart(MealKind, LocalTime)` | Edits a Meal's start time. A bad time is kept and flagged in `error`, never silently rejected. |
| `setBudget(MealKind, BudgetBracket?)` | Chooses a bracket; null deselects it so the Profile budget applies. |
| `changeDateAndTime()` | Sets `changeDateTimeId`. Use from the "no Meal fits" state. |
| `continueToStops()` | Saves when `canContinue`. Sets `planMeals`, `mealBlocks`, and `step = maxOf(latest.step, Stops)`, then sets `continueToId`. |
| `onNavigated()` | Clears `continueToId` and `changeDateTimeId`. Call after navigating. |
| `retry()` | Reloads after `loadFailed`. |

## Rules

- `canContinue` is true when not loading, not saving, and either `planMeals` is false, or `planMeals` is true with at least one selected Meal, no selected Meal has an `error`, and Meals are offered.
- When `noMealFits` is true, only `planMeals = false` can continue. Offer "I handle meals" and Change date and time.
- A Meal is selected on first open (no saved answer) for every Meal that fits, with default times, except Late bite when its add-on is off (no Late-night budget on the Itinerary or Profile): it is offered but not preselected, and can be selected by hand. A reopened draft restores `planMeals`, the saved blocks (kind, start, budget). Saved blocks reopen as selected and keep their skipped flag.
- Blocks are valid when the whole block (start to start + length) is inside the window and half-open overlap with other selected blocks is empty (touching blocks are fine). Windows past midnight are handled: a time before the window start belongs to the next day.
- A missing or unreadable Profile does not block the page: Meals then have no fallback budget (`effectiveBudget` null unless the Itinerary has an override) and Late bite is not preselected.
- Saved blocks keep the id of an existing block of the same kind, otherwise `meal-<kind>`, sorted in window order. If the draft already has Stops, they go through `MealStopReconciler`: a Meal Stop whose block was removed or moved is removed, Event Stops stay.
- The Meal budget stored on a block is the chosen bracket or null. Null means search falls back to the Itinerary or Profile budget.
- Budget summary: a selected Meal whose `effectiveBudget` is null (for example Late bite with its add-on off) adds its actual price to the running total but nothing to the budget limit, so it never makes the Meal or total limit unknown or disables the over-budget warning. The `$100+` bracket still means no upper limit.

## States the page must render

1. Loading.
2. Load error with Retry.
3. Plan choice: planned versus self-handled (self-handled hides the list).
4. Meal list with selected and unselected chips, start time and budget fields for selected ones.
5. Invalid time for a selected Meal: `OutsideWindow` and `Overlaps` messages, Continue disabled.
6. No Meal fits: explanation, Change date and time action, and self-handled still available.
7. Saving, and save error.
