# Sheets strings contract

Maps each maquette element to its string key. Keys exist in `values/strings.xml` (French) and `values-en/strings.xml`. Existing keys are reused where noted. Maquettes: `141:2520` MealPickerSheet, `141:2614` PlannedDepartureSheet, `141:3622` EventDetailsSheet, `141:3734` EventDetailsSheet_RepeatedShowtimes.

## MealPickerSheet (`141:2520`)

| Element | Key | Arguments / note |
|---|---|---|
| Title, Dîner block | `meal_picker_title_lunch` | |
| Title, Souper block | `meal_picker_title_dinner` | |
| Title, Bouchée tardive block | `meal_picker_title_late_bite` | |
| Subtitle "12:00–13:30 · budget : < 50 $" | `meal_picker_subtitle` | `%1$s` time range, `%2$s` budget, both formatted in code. If the Meal has no budget, the engineer decides whether to omit the budget part; there is no key for that |
| Tab "Ici" | `meal_tab_here` | |
| Tab "Dans Ville-Marie" | `meal_tab_in_district` | `%1$s` borough name. Written as "Dans", never "à", because of names like Le Plateau |
| Row: "120 m · 2 min à pied" | `meal_picker_walk` | `%1$d` metres, `%2$d` minutes. Walking icon is decorative |
| Row price "10–20 $" | none | Formatted in code (en dash, `10–20 $`) |
| Row price, no report | `meal_picker_price_unknown` | "Prix inconnu" / "Price unknown" |
| Row "Bistro · Restaurant" | none | Assembled in code from cuisine and venue type labels. Joining strings is flagged by the plan; use a dedicated key if the Feature Engineer wants one |
| Bottom action "Sauter ce repas" | `action_skip_meal` | Existing key, reused |

## PlannedDepartureSheet (`141:2614`)

| Element | Key | Arguments / note |
|---|---|---|
| Title "Départ prévu" | `planned_departure_title` | Existing |
| Subtitle "Exposition Monet · Musée des beaux-arts · début 14:00" | `planned_departure_subtitle` | `%1$s` Event, `%2$s` venue, `%3$s` start time (`14:00`) |
| Note "Aucune heure de fin publiée. Durée habituelle : 2 h." | `planned_departure_no_end` | `%1$s` duration, e.g. `2 h` |
| Wheel label "Heure" / "Minute" | `time_hour`, `time_minute` | Existing. Wheel descriptions use `time_hour_value`, `time_minute_value` |
| Clock row "Durée : 2 h" | `planned_departure_duration` | `%1$s` duration, updated as the wheels move |
| Primary button | `action_confirm` | Existing |
| Text button | `action_cancel` | Existing |

The maquette's "vous partez à ..." text is dropped: the wheels already show the departure time.

## EventDetailsSheet (`141:3622`)

| Element | Key | Arguments / note |
|---|---|---|
| Close X (accessibility) | `event_close` | |
| Event name, type line "Musique" | none, `stop_type_*` | Name is data; type uses existing `stop_type_*` |
| Date and time "sam. 28 nov. 2026 · 20:00" | none | Formatted in code |
| Caption "Heure de fin non publiée" | `event_end_unknown` | Shown only when no end time exists |
| Venue and address | none | Data |
| Price "25 $" | none | Formatted in code |
| Price, no report | `event_price_unknown` | "Prix inconnu" |
| Price, free | `event_price_free` | |
| Caption under price | `event_price_median` | Must stay. Shown only when the price is the median of user reports |
| Audience "Tous publics" | `event_audience_all` | |
| Audience, adults | `event_audience_adults` | |
| Audience, family | `event_audience_family` | Add only if the data model has these values |
| Description | none | Data |
| Link "Billets sur le site de l’organisateur" | `event_tickets_link` | |
| Field label | `event_ticket_price_label` | Currency moved to the helper below |
| Helper "De 0 $ à 500 $ CAD" | `event_ticket_price_range` | `%1$s` min, `%2$s` max, formatted in code from the real limits ($0 to $500) |
| Notice (info icon) | `event_ticket_price_shared` | Must stay. Price shared, name hidden |
| Button | `event_add` | "Ajouter à l’itinéraire" |

## EventDetailsSheet_RepeatedShowtimes (`141:3734`)

Same as above, plus:

| Element | Key | Arguments / note |
|---|---|---|
| Date line "sam. 28 nov. 2026 · 3 séances" | `event_showtimes` | `%1$s` date, `%2$d` count. Used only for 2 or more showtimes, so it is a plain string, not a plural |
| Time chips "18:00 / 20:00 / 22:00" | none | Formatted in code. Group accessibility description: `event_showtimes_group` |
| Notice | `event_ticket_price_shared_showtimes` | Use instead of `event_ticket_price_shared` when several showtimes exist |

## Removed from the maquette

- "Choisissez l’heure à ajouter à l’itinéraire" under the date: the chips and the Add button make it clear. Delete it.
- "Votre prix du billet, en $ CAD (facultatif)": "en $ CAD" now sits in the range helper.
- "vous partez à ..." on the departure sheet (see above).
- "budget repas" became "budget": the Meal title already names the Meal.

## Notes

- Rows that show "Prix inconnu" use `meal_picker_price_unknown` and `event_price_unknown`: two contexts, two keys. `price_unknown` (lowercase) is unrelated.
- No emoji or checkmark in chips: the selected showtime uses colour only.
