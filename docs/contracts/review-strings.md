# Review strings contract

Maps each maquette element to its string key. Keys exist in `values/strings.xml` (French) and `values-en/strings.xml`. Maquettes: `29:853` ItineraryReviewScreen, `141:2692` marker selected, `141:2822` not today, `141:2958` Start warning, `141:2993` Stop edit warning, `141:3031` unreachable Stops.

## ItineraryReviewScreen (`29:853`, `141:2692`)

| Element | Key | Arguments / note |
|---|---|---|
| App bar "Révision" | `review_title` | Existing |
| "Étape 6 sur 6" | `onboarding_step` | Existing. `%1$d` 6, `%2$d` 6 |
| Map | `map_route_description` | Existing plural, set by `ItineraryRouteMap` |
| Stop time "19:30" | none | Formatted in code, `18:30` form |
| Stop name | none | Data |
| Stop details "Événement · Le Plateau · départ prévu 19:15" | `stop_kind_event` or `stop_kind_meal`, District name, `review_planned_departure` | Joined with " · " in code. `review_planned_departure` takes `%1$s` time, shown only when the Event has a Planned Departure |
| Row action "Retirer" | `action_remove` | Existing |
| Budget card title | `planning_budget_title` | Existing "Budget" |
| Budget rows | `budget_lunch`, `budget_dinner`, `budget_late_night`, `budget_events` | Existing. Show only the Meals planned. Values (`< 50 $`) come from `budget_under_*` |
| Primary button | `review_start` | Existing |
| Note under button | `review_notification_note` | Existing |

States:

- **Marker selected (`141:2692`)**: no text. The marker and its row are highlighted by colour only.
- **Not today (`141:2822`)**: the button is disabled and `review_start_only_on` replaces the notification note, in the error colour. `%1$s` is the date formatted as `sam. 28 nov. 2026`.

## StartItineraryWarningDialog (`141:2958`)

| Element | Key | Arguments / note |
|---|---|---|
| Title | `review_start_warning_title` | |
| Body | `review_start_warning_body` | `%1$s` name of the current Itinerary. The key holds the non-breaking spaces inside the quotes |
| Text button | `action_cancel` | Existing |
| Confirm button | `review_start_warning_confirm` | "Terminer et démarrer" |

The body keeps the full consequence: the current Itinerary ends and moves to Past, confirmed Stops keep their Stamps, the other Stops are marked skipped. `stop_status_skipped` ("Sauté") is the matching Stop status.

## StopEditWarningDialog (`141:2993`)

Shown on Step 1 when a date or time change makes Stops no longer fit.

| Element | Key | Arguments / note |
|---|---|---|
| Title | `stop_edit_title` | |
| Body | `stop_edit_body` | The two lists carry the detail |
| List label, error colour | `stop_edit_removed` | Rows are time and Stop name from data |
| List label | `stop_edit_kept` | Also used by the unreachable dialog |
| Text button | `action_cancel` | Existing |
| Confirm button | `stop_edit_confirm` | "Modifier" |

## RemoveStopWarningDialog, unreachable Stops (`141:3031`)

| Element | Key | Arguments / note |
|---|---|---|
| Title | `review_unreachable_title` | "Arrêts inaccessibles" |
| Body | `review_unreachable_body` | `%1$s` time of the time slot as a sentence time: `19 h 30` in French, in the app's English time format in English |
| List label, error colour | `review_unreachable_affected` | Rows are time and Stop name from data |
| List label | `stop_edit_kept` | Reused, same meaning |
| Text button | `action_cancel` | Existing |
| Action | `action_reschedule` | Existing. Picks another showtime for the affected Stops |
| Action, error colour | `action_reorganize` | Existing. Returns to selection from that gap |

## Removed

- `review_remove_note`: no code used it, and the unreachable dialog now gives the warning at the moment it matters.

## Notes for the engineers

- Times in the dialogs' Stop rows use `18:30`. The sentence time in `review_unreachable_body` uses `19 h 30` (non-breaking spaces from the formatter).
- Maquette text left out: "Dîner (repas 1)" and similar labels are replaced by the `budget_*` keys. Maquette "Seront aussi retirés" is now "Arrêts touchés"; the frame's layer name is not text.
