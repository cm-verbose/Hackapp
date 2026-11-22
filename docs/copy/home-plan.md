# Copy plan: Home and Itineraries

Team **home**. Draft for the French and English Editors. Covers the 34 keys in the `home` section of `key-ownership.md`.

## Summary

| Decision | Count |
|---|---:|
| keep | 21 |
| delete | 1 |
| show-in-UI | 0 |
| merge | 2 |
| shorten | 10 |
| unused | 0 |
| **Total** | **34** |

Nine rows need a layout or behaviour change, grouped into five changes:

- **A. Tab icons:** set the icon `contentDescription` to `null` in `MainBottomAppBar.kt:83`. The Tab's text already names it, so TalkBack announces the name twice.
- **B. Next Stop:** pass only the Stop name to `home_next_stop` (`ScreenHome.kt:136`, preview at `UnderwayItineraryCard.kt:66`). Apply together with the string, because the placeholders change.
- **C. Plan card:** pass `cityName` to `home_plan_title`, and remove the body `Text` and `body` parameter from `PlanItineraryCard.kt`. Apply together with the strings.
- **D. Archived note:** remove the note under the Archived list (`ScreenItinerary.kt:157-161`). Keep it on the Archived page. Apply together with the strings.
- **E. Date line:** remove the weekday from `itineraryFullDate` (`ItineraryUi.kt:41`), because the page title directly above already shows it.

## Bottom navigation

| Key | Decision | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `nav_home` | keep | Accueil | Home | Accueil | Home | Clear tab label. | Yes: A |
| `nav_itineraries` | keep | Itinéraires | Itineraries | Itinéraires | Itineraries | Glossary term; also the page title. | Yes: A |
| `nav_passport` | keep | Passeport | Passport | Passeport | Passport | Glossary term. | Yes: A |

## Home

| Key | Decision | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `home_greeting` | keep | Bonjour %1$s | Hello %1$s | Bonjour %1$s | Hello %1$s | Short page header. | No |
| `home_greeting_anonymous` | keep | Bonjour | Hello | Bonjour | Hello | Fallback when no name is saved. | No |
| `home_underway_label` | shorten | EN COURS | UNDERWAY | En cours | Underway | Sentence case. | No |
| `itinerary_title_in` | shorten | %1$s à %2$s | %1$s in %2$s | %1$s · %2$s | %1$s · %2$s | Fixes "Samedi à Le Plateau-Mont-Royal" and "à Le Sud-Ouest"; no preposition fits every District. | No |
| `home_next_stop` | shorten | Prochain arrêt %1$s · %2$s | Next stop %1$s · %2$s | Prochain arrêt : %1$s | Next stop: %1$s | The strip just below already shows this time on the highlighted Stop. | Yes: B |
| `home_resume_itinerary` | shorten | Reprendre l’itinéraire | Resume itinerary | Reprendre | Resume | The card already names the Itinerary. | No |
| `home_plan_title` | merge | Votre sortie, sur mesure | Your outing, made to measure | Journée ou soirée à %1$s | Day or night out in %1$s | Takes over the body's meaning; removes "sortie"/"outing", which the Glossary avoids. | Yes: C |
| `home_plan_body` | delete | Une journée ou une soirée à %1$s, arrêt par arrêt. | A day or night out in %1$s, stop by stop. | — | — | Merged into the title. Delete from both files once C is applied. | Yes: C |
| `home_plan_action` | keep | Planifier un itinéraire | Plan an itinerary | Planifier un itinéraire | Plan an itinerary | Names the action. | No |
| `home_suggestions` | shorten | SUGGESTIONS | SUGGESTIONS | Suggestions | Suggestions | Sentence case. | No |
| `home_planning_section` | keep | En planification | In planning | En planification | In planning | Section label, also on Itineraries. | No |
| `stops_count` | keep | %d arrêt / %d arrêts | %d stop / %d stops | %d arrêt / %d arrêts | %d stop / %d stops | Compact card value. | No |
| `stamps_count` | keep | %d timbre / %d timbres | %d stamp / %d stamps | %d timbre / %d timbres | %d stamp / %d stamps | Live on Past cards; `HeroHeader` use is preview-only. | No |
| `itinerary_date_pattern` | keep | d MMM | (none: `translatable="false"`) | d MMM | (none: `translatable="false"`) | Intentional single-language format, in use. | No |

## Itineraries

| Key | Decision | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `itineraries_empty_body` | shorten | Vos itinéraires planifiés et passés apparaîtront ici. | Your planned and past itineraries will appear here. | Aucun itinéraire | No itineraries | The page title and Plan card below already give the context and next action. | No |
| `itineraries_past` | keep | Passés | Past | Passés | Past | Short section label. | No |
| `itineraries_archived` | merge | Archivés | Archived | Archivés · jamais commencés | Archived · never started | Absorbs the list note and separates this section from Past. | Yes: D |
| `itinerary_menu` | keep | Options de l’itinéraire | Itinerary options | Options de l’itinéraire | Itinerary options | Must stay: describes an icon-only control. | No |
| `action_delete` | keep | Supprimer | Delete | Supprimer | Delete | Names the action. | No |
| `itinerary_delete_title` | keep | Supprimer cet itinéraire? | Delete this itinerary? | Supprimer cet itinéraire? | Delete this itinerary? | Must stay: data-loss warning. | No |
| `itinerary_delete_body` | shorten | Ce brouillon sera supprimé définitivement. | This draft will be permanently deleted. | Cette action est irréversible. | This can’t be undone. | Must stay; the title already says what is deleted. | No |
| `itinerary_delete_error` | keep | Impossible de supprimer cet itinéraire. Réessayez. | Could not delete this itinerary. Try again. | Impossible de supprimer cet itinéraire. Réessayez. | Couldn’t delete this itinerary. Try again. | Must stay: error with next action. | No |

## Past Itinerary

| Key | Decision | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `itinerary_date_window` | keep | %1$s · %2$s – %3$s | %1$s · %2$s – %3$s | %1$s · %2$s – %3$s | %1$s · %2$s – %3$s | The string is fine; its date argument repeats the title's weekday. Also on Archived. | Yes: E |
| `stop_kind_meal` | keep | Repas | Meal | Repas | Meal | Glossary term; the dot colour alone can't convey the kind. | No |
| `stop_status_confirmed` | keep | Confirmé | Confirmed | Confirmé | Confirmed | Short status. | No |
| `stop_status_skipped` | keep | Sauté | Skipped | Sauté | Skipped | Matches `action_skip_stop` and `stops_skipped`. | No |
| `past_stamps_earned` | shorten | Timbres obtenus | Stamps earned | Timbres | Stamps | A Stamp on a Past page is already earned. | No |
| `end_of_day_no_stamp` | shorten | aucun timbre | no stamp | Aucun | None | The section label already says Stamps. | No |
| `itinerary_not_found` | keep | Itinéraire introuvable | Itinerary not found | Itinéraire introuvable | Itinerary not found | Must stay: the page's Back button is the next action. Also on Archived. | No |

## Archived Itinerary

| Key | Decision | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `itineraries_archived_note` | shorten | Archivé, car sa date est passée. | Archived because its date has passed. | Archivé : date passée | Archived: date passed | Only marker of this state on the page, and explains the new-date action. | Yes: D |
| `archived_choose_new_date` | keep | Choisir une nouvelle date | Choose a new date | Choisir une nouvelle date | Choose a new date | Names the action. | No |

## Cross-team notes

- **shared:** `stop_status_skipped` (home) follows *sauter*, like `action_skip_stop` and `stops_skipped` (shared). If the Stop wording changes, change all three together. `action_skip` (access, *Passer*) is an onboarding action and is not affected.
- **shared:** `stop_kind_event` pairs with `stop_kind_meal` on Past and Archived Stop rows. Keep both as one-word nouns.
- **shared:** `state_error_title` ("Une erreur est survenue") appears on Home, Itineraries, Past and Archived. It names no failure; "Impossible de charger" would be more specific, with `action_retry` as the next action.
- **Glossary:** "Archived" and "Past" are visible section names but have no Glossary entries. "Draft" appears in code and in the current delete dialog, and this plan removes it from the UI. No term was changed.

### Hardcoded user-facing text

No live hardcoded words were found in this area. These spots build or affect visible text in code:

| Location | Text | Note |
|---|---|---|
| `ItineraryDetailPages.kt:223` | `"$kind · ${stop.districtName}"` | Joins strings in code. Use one string with arguments. |
| `ItineraryUi.kt:69` | `parts.joinToString(" · ")` | Joins date, Stops and Stamps in code. |
| `ScreenHome.kt:138` | `""` | With no next Stop, the Underway card keeps an empty line. Hide the `Text` instead. |
| `HeroHeader.kt:42` | `" · Montréal"` | Preview only. No action. |
| `ScreenHome.kt`, `ScreenItinerary.kt`, `ItineraryDetailPages.kt`, `ItinerarySuggestionsCard.kt`, `UnderwayItineraryCard.kt` | Sample names and Districts | Preview data only. No action. |

## Questions for the product owner

1. **What should tapping a Home suggestion do?** Today it opens a new Itinerary without the suggested Event (`ScreenHome.kt:149`), so the row promises more than it delivers. Either preselect the Event, or keep the behaviour and make the rows non-tappable.

## French Editor notes

Reviewed all 34 `FR proposed` cells against the screens (`ScreenHome.kt`, `ScreenItinerary.kt`, `ItineraryDetailPages.kt`, `ItineraryUi.kt`, `UnderwayItineraryCard.kt`, `PlanItineraryCard.kt`). Two cells changed, both typography; no wording changed.

- **Changed:** `home_next_stop` and `itineraries_archived_note` now carry a non-breaking space (U+00A0) before the colon: `Prochain arrêt : %1$s`, `Archivé : date passée`. Copy them into `strings.xml` with the U+00A0 kept; `values/strings.xml` has none today (`at_stop_next` has the same gap, shared team). No other proposed string has a colon, a currency `$` or `« »`; the `$` in `%1$s` is placeholder syntax, not currency.
- **Kept as is, checked:** `Supprimer cet itinéraire?` with no space before `?` matches every other question in the app (`auth_forgot_password`, `planning_when_title`, `meal_question`, ...). `%1$s · %2$s – %3$s` keeps the spaced en dash: the OQLF writes time intervals that way (`9 h – 21 h`) and `selection_meal_block_title` already uses it.
- **Agreement with placeholders:** `Archivés · jamais commencés` and `Passés` agree with the implied *itinéraires* (masc. pl.); `Archivé : date passée` with *itinéraire* (masc. sing.) on the single page; `Aucun` under the label *Timbres* with *timbre* (masc.); `Sauté` / `Confirmé` with *arrêt*. `%1$s · %2$s` removes the only broken agreement (`à Le Plateau-Mont-Royal`, `à Le Sud-Ouest`); the District names in `districts.json` need *à*, *au* or *dans le*, so no single preposition works and the dot is the right fix. `Journée ou soirée à %1$s` is correct for every City Pack city without an article (Montréal, Québec, Toronto, Laval); only a city name with an article (`Le Havre` → *au Havre*) would break it.
- **Removed text:** nothing removed harms French comprehension. Dropping *brouillon* from the delete dialog also fixes a wrong word on the Archived page, where the dialog deletes an archived Itinerary, not a draft.
- **Recurring issue (app-wide, outside this column):** no non-breaking spaces anywhere; regular spaces before `·` and `–` can leave the separator at the start of a line when a title wraps at 360 dp / 1.3x. Suggest NBSP before `·` and `–` across the app, including the `" · "` join in `ItineraryUi.kt:69`, as one shared decision rather than in these rows alone. Also `itineraryFullDate` capitalizes the date (`Samedi 28 novembre 2026`); the Québec form is lowercase (`samedi 28 novembre 2026`), acceptable here because it sits in a label position.
- **Human Québec French read requested:** `Archivés · jamais commencés` (label register; *non commencés* is the alternative), `Archivé : date passée` (*date dépassée* is the alternative), `Aucun` standing alone under *Timbres*, `En planification` (kept from the current file; *en cours de planification* is the fuller form), `Journée ou soirée à %1$s` (headlineMedium wraps to two lines at 360 dp / 1.3x).

## English Editor notes

Reviewed all 34 `EN proposed` cells against the final French and the screens. One cell changed; no meaning mismatches.

- **Changed:** `itinerary_delete_error` now reads `Couldn’t delete this itinerary. Try again.` The app's English already uses contractions (`Couldn’t` twice, `can’t` once); `Could not` was the odd one out. Same meaning as the French.
- **`Day or night out in %1$s`:** kept. It matches `Journée ou soirée à %1$s` and the Glossary's "day out or night out", and avoids "outing". It is a noun phrase that reads as a card title. `%1$s` comes from `cityPack.displayName.get(language)`, so check the English City Pack name gives `Montreal` without the accent (Plan Montreal).
- **`Archived · never started`:** kept. It matches `Archivés · jamais commencés`. "Not started" is the alternative; "never" better says the date passed without a start.
- **`This can’t be undone.`:** kept. It matches `Cette action est irréversible.` and is correct once the title names what is deleted. In `strings.xml` the apostrophe needs a backslash (`can\’t`) or the curly ’ typed as is, as the file already does.
- **Kept, checked:** `Archived: date passed` is terse but reads as a status label and matches `Archivé : date passée`. `None` under `Stamps` matches `Aucun`. `Next stop: %1$s` and `Resume` match the French. `In planning` is kept from the current file. `Underway` and `Meal` are Glossary terms; meals elsewhere stay Lunch, Dinner, Late bite.
- **Spelling:** no Canadian-spelling issues in this area.
- **Recurring problem:** inconsistent contractions (`Could not` against `Couldn’t`). If the French Editor’s NBSP suggestion before `·` and `–` is adopted, apply it to the English separators too.
- **No meaning mismatches found.** The French and English proposals agree in every row.
