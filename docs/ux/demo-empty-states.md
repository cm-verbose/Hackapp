# Demo: loading and empty states in Planning (local data)

Author: ux-designer, 2026-11-22. Scope set by the user directive: hackathon demo, local data, stabilization only, no new features. These are empty or loading states and copy on existing screens, read from code (`scaffold/screens/planning/ScreensPlanning.kt`, `domain/Planner.kt`) and checked against T18a (hide events already started, default date = today).

**Why this matters for the demo:** with the date defaulting to today and started events hidden, a demo in the afternoon with the default Day out (10:00-18:00) filters most or all events out. Today several of those paths dead-end with no explanation.

**Plan status (team-lead, 2026-11-22):** sections 1 and 2 are task **T18b** (after T7b+T18a; ux-designer reviews). Dropping past meal blocks (section 3, first bullet) joins **T18a**. The other D-P1 items (the section 3 callout, 4 and 5) wait until after the demo. The T9 risk in section 6 is covered by workflow-auditor's rehearsal.

Priority: **D-P0** = the demo can get stuck. **D-P1** = confusing on stage but still navigable.

## 1. DistrictPick: every borough has 0 events (D-P0)
- Today (`ScreensPlanning.kt:183-202`): the map is all grey, taps are ignored, and Continue is disabled (`vm.districts` is empty). There is no message, so the only way out is the back arrow, and nothing tells the user to change the time. A2 #7.
- Ask: when `counts` has loaded and every borough count is 0, show `EmptyState` above the map:
  - title `planning_no_events_title`
  - body `planning_no_events_body`
  - `actionLabel = planning_change_time`, `onAction = onBack` (back to Planning 1)
- Keep the grey map below it so the user still sees the city.

## 2. StopSelection: no reachable event in this borough (D-P0 on the last borough)
- Today (`ScreensPlanning.kt:338`): the generic "Rien à afficher pour le moment", with no body and no action. On the last borough with no event chosen yet, "Finish here" is disabled and "Next borough" is hidden, so this is a dead end apart from Back. Back to MealChoice and Continue then wipe the selection (A2 #5).
- Ask: replace the generic empty state with:
  - title `selection_no_events_title`, with the borough name
  - body `selection_no_events_body_next` when a next borough exists, else `selection_no_events_body_last`
  - no new buttons. "Next borough" is already in the bottom actions; on the last borough the body tells the user to go back.
- The same body also covers the case where events exist but were all chosen or are no longer reachable after the previous stop.

## 3. MealChoice: no meal time fits, or the time has passed (D-P1)
- `ItineraryPlanner.mealTimes(window)` offers 12:00, 18:30 and 22:00 if a 90-minute block fits in the window. It does not drop times already past on today's date. A demo at 17:00 can pick "Lunch at 12:00" and get a meal prompt for the past. Ask T18a to apply the same "already started" rule to meal times (drop a block whose `mealStart` is before now).
- If no option is left, the page today shows "Yes" selected with no time under it, and Continue silently plans no meal (also A2 #28). Ask: when `options` is empty, show `InfoCallout(meal_no_time_fits)` under "Yes", and keep Continue enabled (no meal will be planned).

## 4. Meal sheet: no place open nearby (D-P1)
- Today (`ScreensPlanning.kt:378`): the generic empty state. `mealOptions` filters by open hours at the meal time, the budget, and 1.5 km. Late slots and tight budgets hit this.
- Ask: title `meal_no_places_title`, body `meal_no_places_body`. No new action is needed: closing the sheet keeps the meal prompt and the user can finish without a meal.

## 5. Planning 1: tell the user started events are hidden (D-P1, copy only)
- When `vm.window.date` is today, add one line under the existing time-window callout: `planning_started_hidden_note`. Without it, a 0-event result in step 2 looks like a bug.

## 6. Loading
- DistrictPick and StopSelection already show `LoadingState` with a label. That's fine.
- Risk: events are parsed on the main thread until T9. The first open of DistrictPick can freeze the spinner, or trigger an ANR on a slow emulator. If T9 slips past the demo, rehearse with DistrictPick opened once before going on stage.
- If `countsByDistrict` throws, the spinner never stops. Local data makes this unlikely; no change asked for the demo.

## Strings (FR default / EN)
| Key | FR | EN |
|---|---|---|
| `planning_no_events_title` | Aucun événement dans ce créneau | No events in this time window |
| `planning_no_events_body` | Les événements déjà commencés sont masqués. Essayez une heure plus tardive, la soirée, une autre date ou un autre budget. | Events that have already started are hidden. Try a later time, a night out, another date or another budget. |
| `planning_change_time` | Modifier la date et l’heure | Change date and time |
| `selection_no_events_title` | Aucun événement possible à %1$s | No events left in %1$s |
| `selection_no_events_body_next` | Rien n’est atteignable ici après votre arrêt précédent. Passez à l’arrondissement suivant. | Nothing here can be reached after your previous stop. Go to the next borough. |
| `selection_no_events_body_last` | Rien n’est atteignable ici après votre arrêt précédent. Revenez en arrière pour changer d’arrondissements ou d’horaire. | Nothing here can be reached after your previous stop. Go back to change boroughs or times. |
| `meal_no_time_fits` | Aucune heure de repas ne tient dans ce créneau. Aucun repas ne sera planifié. | No meal time fits in this window. No meal will be planned. |
| `meal_no_places_title` | Aucun restaurant ouvert à proximité | No restaurants open nearby |
| `meal_no_places_body` | Aucun lieu ouvert à cette heure-là ne correspond à votre budget. Fermez pour continuer sans repas. | No place open at that time fits your budget. Close to continue without a meal. |
| `planning_started_hidden_note` | Aujourd’hui, les événements déjà commencés ne sont pas proposés. | Today, events that have already started are not shown. |

Notes: `%1$s` is the borough name from the City Pack (`names[district]`). Use typographic apostrophes (’), as the existing strings do. Every message uses the existing `EmptyState` / `InfoCallout` components; nothing new is built.

## Post-demo copy fix (found in the T18b review)
`selection_no_events_body_next` / `_last` say "after your previous stop", which is wrong when no stop has been chosen yet (the first borough, or Next borough tapped with nothing chosen). Add a neutral variant for when `previous == null`:
- FR: "Aucun événement n’est encore possible ici dans ce créneau."
- EN: "No event in this time window can still be reached here."

Keep the existing second sentence of each body ("Passez à l’arrondissement suivant." / "Go to the next borough." or "Go back to change boroughs or times.").

## Demo test path (when the emulator is signed in)
1. At about 17:00 today: Plan, keep Day out, Continue. Expect state 1, then "Change date and time" goes back.
2. Switch to Night out, then pick 2 boroughs. On the last one, choose an event late enough that nothing after it is reachable. Expect state 2 with the "last" body.
3. Pick dinner at 18:30 with the budget under $20 in a quiet borough. Expect state 4.
