# Selection strings contract (Planning step 5)

Maquettes: `29:754` (Event selection), `141:3868` (Meal Block reached), `141:3910` (over budget), `141:3952` (empty gap), `141:3994` (last borough). Team Selection owns `selection_*` and `running_total_*`.

Amounts are formatted by code as `20 $` (U+00A0 before `$`) and passed as strings. Times are `17:00` and passed as strings. The app bar title is the borough name (data), so no key is needed.

## Screen

| Element | Key | FR | EN | Notes |
|---|---|---|---|---|
| Progress line "Étape 5 sur 6 · Arrondissement 1 sur 2" | `selection_progress` | Étape %1$d sur %2$d · Arrondissement %3$d sur %4$d | Step %1$d of %2$d · Borough %3$d of %4$d | New. One string, never join `onboarding_step`. Args 5, 6, borough index, borough count. Replaces `selection_district_progress` (deleted, no caller) |
| Time slot heading | `selection_slot_title` | Plage horaire %1$s – %2$s | Time slot %1$s – %2$s | New. Args start and end |
| Earliest start line "Dès 17:00" | `selection_slot_from` | Dès %1$s | From %1$s | New. Arg: first reachable start |
| Hour group header "17:00" and type tag | data, `stop_type_*` | n/a | n/a | Existing. `selection_time_block` stays for ranges (`translatable="false"`) |
| Group header "En continu" | `selection_ongoing` | En continu | Ongoing | New. Holds Events with no fixed time |
| Ongoing chip "Sans heure fixe · 10:00 – 21:00" | `selection_no_fixed_time` | Sans heure fixe · %1$s | No fixed time · %1$s | New. Arg: `10:00 – 21:00` from `selection_time_block` |
| Event card details "17:30 · Salle · 20 $ · fin non publiée" | data, `price_free`, `price_unknown`, `selection_end_unknown` | gratuit, prix inconnu, fin non publiée | free, price unknown, end time not published | Price keys existing (lowercase on purpose, mid-line). `selection_end_unknown` new. The long `event_end_unknown` stays for the Event sheet |
| Showtime chips "17:15 17:45 18:15" | data | n/a | n/a | Selected state by colour only |
| Showtime chip group accessibility label | `selection_start_times` | Heures de début | Start times | New. Content description only, no visible text |
| Meal Block card title | `selection_meal_block_title` | Bloc repas | Meal block | Existing |
| Meal Block button | `selection_choose_meal` | Choisir un repas | Choose a meal | Existing |
| Skip text button | `action_skip_meal` | Sauter ce repas | Skip this meal | Existing, not mine |
| Fixed-time events hidden callout | `selection_fixed_hidden` | Événements à heure fixe masqués pendant ce bloc. | Fixed-time events are hidden during this block. | New. Meal Block state only |
| Empty gap title (with calendar icon) | `selection_empty_title` | Aucun événement dans cette plage horaire | No events in this time slot | New |
| Empty gap body | `selection_empty_body` | Rien d’accessible depuis votre dernier arrêt. | Nothing reachable from your last stop. | New. Next actions stay visible below |
| Left button | `selection_finish_here` | Terminer ici | Finish here | Existing. Disabled state in `141:3952` has no extra text |
| Right button | `selection_next_district` | Arrondissement suivant | Next borough | Existing |
| Last borough button (full width) | `action_finish` | Terminer | Finish | Existing, not mine. Replaces both buttons in `141:3994` |

## Added by the copy pass

| Element | Key | FR | EN | Notes |
|---|---|---|---|---|
| Median price label | `selection_price_median` | %1$s (médiane déclarée) | %1$s (reported median) | Arg: the formatted price. Keeps the "median of user-reported prices" label |
| Save failure | `selection_save_error` | Enregistrement impossible | Couldn’t save | Own key. `planning_save_error` is shared and unchanged |

Non-breaking spaces (U+00A0) keep `selection_end_unknown` and every `running_total_*` string together, so an amount never splits from its label or currency.

## Running total card

| Element | Key | FR | EN | Notes |
|---|---|---|---|---|
| Label | `running_total_label` | Total | Total | New |
| Amount, some prices unknown ("70 $ + prix inconnu") | `running_total_amount` | %1$s + prix inconnu | %1$s + unknown price | New. Arg: known sum. One string, not joined |
| Amount, nothing known | `running_total_unknown_only` | Prix inconnu | Unknown price | New. Use when the known sum is 0 and a price is unknown. Not in a maquette |
| Amount, all known ("50 $") | none | n/a | n/a | Pass the formatted amount alone |
| Budget "Budget 100 $" | `running_total_budget` | Budget %1$s | Budget %1$s | New |
| Breakdown, Events | `running_total_events` | Événements %1$s | Events %1$s | New. Shown even at `0 $` (`141:3952`) |
| Breakdown, Lunch | `running_total_lunch` | Dîner %1$s | Lunch %1$s | New |
| Breakdown, Dinner | `running_total_dinner` | Souper %1$s | Dinner %1$s | New |
| Breakdown, Late bite | `running_total_late_bite` | Bouchée tardive %1$s | Late bite %1$s | New |
| Breakdown separator "·" | none | n/a | n/a | Code joins the parts with U+00A0 + `· `, as `EventOptionCard` does |
| Over budget heading ("Budget dépassé de 70 $") | `running_total_over` | Budget dépassé de %1$s | Over budget by %1$s | New. Arg: the excess. Card turns error colour with a warning icon |
| Over budget hint | `running_total_over_hint` | Vous pouvez quand même continuer | You can still continue | New. Warns without blocking. Must stay: it says the buttons still work |

## Removed or not needed

- `selection_district_progress` (old, with `%3$s → %4$s` in the plan): deleted, replaced by `selection_progress`.
- `selection_meal_block_body`: already absent, per the plan.
- Screen title: the borough name, not `stop_kind_event`.

## Notes for the Feature Engineer

- In `141:3868` the Meal Block card and the "En continu" group both show. The hidden-events callout sits below.
- Over budget: the Total label and breakdown are replaced by the over heading and hint (`141:3910`). The unknown price still shows in the amount.
- Maquette text to recommend removing: none. "Dès 17:00" could go if the heading already conveyed it, but it carries the earliest reachable start, so it stays.

## For a Québec French reader

- `selection_progress`, `selection_slot_from` ("Dès"), `running_total_over` ("Budget dépassé de"), `running_total_amount`.
