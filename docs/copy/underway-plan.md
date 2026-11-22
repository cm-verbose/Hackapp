# Underway copy plan

Draft for the French and English Editors. Covers the 37 `underway` keys in `docs/copy/key-ownership.md`.

## Summary

| Decision | Keys |
|---|---:|
| keep | 15 |
| delete | 4 |
| show-in-UI | 1 |
| merge | 5 |
| shorten | 10 |
| unused | 2 |
| **Total** | **37** |

19 keys need a layout or behaviour change. The String Steward cannot apply those alone.

French Editor: `strings.xml` has no non-breaking spaces today, and this draft matches it. The Québec rules require one before `:`, so add it to the proposed French.

Each Reason starts with the key's status:

- **live**: shown in the app today, as the title of a placeholder route.
- **preview**: called only from a component that no page uses yet. Tagged `future`.
- **future**: no caller yet, because the page is not built.

For a merge, the surviving key carries the new text and the absorbed key shows `—`.

## Event option card (Planning selection)

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `end_time_unpublished` | show-in-UI | fin non publiée | end time not published | — | — | preview, `future`. A lone start time already contrasts with the `18:00 – 21:00` range, and the Planned Departure sheet handles the missing end. | Yes: remove the text from `EventOptionCard`, then delete the key |

## Leg handoff

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `leg_next_stop_label` | keep | Prochain arrêt | Next stop | Prochain arrêt | Next stop | live. Already minimal. | No |
| `leg_stop_progress` | keep | Prochain arrêt · %1$d sur %2$d | Next stop · %1$d of %2$d | Prochain arrêt · %1$d sur %2$d | Next stop · %1$d of %2$d | preview, `future`. Compact, and the only label on `NextStopCard`. | Yes: use `LegHeader` or `NextStopCard` on a Leg, not both, or "Prochain arrêt" appears twice |
| `leg_start_from_location` | shorten | Début %1$s · départ depuis votre position | Starts %1$s · leaving from your location | Début %1$s | Starts %1$s | preview, `future`. The location clause is false when location is denied, and the travel app shows the start point. | No |
| `leg_depart` | keep | Départ | Depart | Départ | Departure | preview, `future`. Column label. | No |
| `leg_travel` | keep | Trajet | Travel | Trajet | Travel | preview, `future`. Column label. | No |
| `leg_arrive` | keep | Arrivée | Arrive | Arrivée | Arrival | preview, `future`. Column label. | No |
| `leg_now` | keep | Maintenant | Now | Maintenant | Now | preview, `future`. Departure value. | No |
| `leg_walk_minutes` | shorten | ≈ %1$d min à pied | ≈ %1$d min walk | ≈ %1$d min | ≈ %1$d min | preview, `future`. Sits under "Trajet", and the Leg choices are transit or Uber, so "walk" would be wrong. | Yes: pass the estimate for the selected mode. The key name becomes stale |
| `leg_how_to_get_there` | shorten | Comment y allez-vous? | How are you getting there? | Comment y aller? | Getting there | preview, `future`. Names the task in fewer words. | No |
| `leg_mode_transit` | keep | Transport en commun | Public transit | Transport en commun | Public transit | preview, `future`. Standard term. | Yes: the segment is one line with ellipsis and will likely cut the French at 360 dp and 1.3x. Allow two lines |
| `leg_mode_uber` | unused | Uber | (none, `translatable="false"`) | — | — | preview only. Live name comes from City Pack `rideHail.displayName`. | Yes: point the preview at the City Pack, then delete the key |
| `leg_fare_required` | merge | Titre requis : %1$s | Fare needed: %1$s | Titre requis : %1$s · %2$s | Ticket needed: %1$s · %2$s | preview, `future`. Absorbs `leg_fare_source`: one row gives the fare and its source, such as "1 passage · ARTM". | Yes: pass `Fare.agencyName` as `%2$s` in `TransportChoiceCard` and the `TransitFareRow` preview |
| `leg_fare_single_ride` | unused | 1 passage | 1 ride | — | — | preview only. Live label comes from City Pack `fareTable.singleRide`. | Yes: point the previews at the City Pack, then delete the key |
| `leg_fare_source` | merge | Selon la grille tarifaire de %1$s. | Based on the %1$s fare table. | — | — | future. Source attribution must stay, and now lives in `leg_fare_required`. | Yes: delete only after the merge above |
| `leg_open_transit_app` | keep | Ouvrir %1$s | Open %1$s | Ouvrir %1$s | Open %1$s | future. Clear action with the app name. | No |
| `leg_google_maps_directions` | shorten | Itinéraire dans Google Maps | Directions in Google Maps | Ouvrir dans Google Maps | Open in Google Maps | future. "Itinéraire" is our term for the whole outing, so it must not name Google directions. | No |

## Arrival

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `arrival_en_route` | keep | En route | On the way | En route | On the way | live. Status label. | No |
| `arrival_expected` | keep | Arrivée prévue %1$s | Expected arrival %1$s | Arrivée prévue %1$s | Expected arrival %1$s | preview, `future`. Label and value, so use `18:22`. | No |
| `arrival_not_detected_note` | delete | L’arrivée n’est pas détectée par GPS : confirmez-la. | Arrival isn’t detected by GPS, so please confirm it. | — | — | preview, `future`. The arrival button asks for the action, and a reminder follows if it is missed. | Yes: remove `hint` from `ArrivalStatusCard` and keep the arrival button prominent |
| `arrival_reminder_note` | delete | Sans confirmation 30 min après l’heure prévue : | If not confirmed 30 min after the expected time: | — | — | future. Explains app behaviour the user does not act on. | Yes: build Arrival without this caption or a notification mock |

## At the Stop

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `at_stop_leave_by` | keep | Partez avant | Leave by | Partez avant | Leave by | live. Labels the time. | No |
| `at_stop_next` | shorten | Prochain arrêt : %1$s · %2$d min à pied | Next stop: %1$s · %2$d min walk | Prochain arrêt : %1$s · ≈ %2$d min | Next stop: %1$s · ≈ %2$d min | preview, `future`. Leave By uses the transit estimate, so "walk" is wrong. | Yes: pass the transit minutes used for Leave By |
| `at_stop_alert_note` | delete | Alerte prioritaire 30 min avant : | High-priority alert 30 min before: | — | — | future. Explains app behaviour the user does not act on. | Yes: build At the Stop without this caption or a notification mock |

## Notifications

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `notification_arrival_title` | shorten | Êtes-vous arrivé·e? | Have you arrived? | Êtes-vous sur place? | Have you arrived? | preview, `future`. Removes the dotted form, which the French rules forbid. | No |
| `notification_arrival_body` | shorten | %1$s devait commencer à %2$s. | %1$s was due to start at %2$s. | %1$s · %2$s | %1$s · %2$s | preview, `future`. Stop name and time are enough on a lock screen, and this avoids agreement with the name. | No |
| `notification_leave_title` | shorten | Départ dans 30 min | Leaving in 30 min | Partez avant %1$s | Leave by %1$s | preview, `future`. A countdown goes stale on a lock screen, and a clock time stays true. | Yes: pass the Leave By time, formatted `19 h 15` in French |
| `notification_leave_body` | shorten | Quittez %1$s avant %2$s pour arriver à l’heure. | Leave %1$s by %2$s to arrive on time. | Prochain arrêt : %1$s | Next stop: %1$s | preview, `future`. The time moves to the title, and the next Stop says why to leave. The last Stop has no alert. | Yes: one argument, the next Stop's name |
| `notification_channel_underway` | keep | Itinéraire en cours | Underway itinerary | Itinéraire en cours | Underway itinerary | future. Android settings label. | No |
| `notification_preview_header` | keep | %1$s · maintenant | %1$s · now | %1$s · maintenant | %1$s · now | preview, `future`. In-app mock only. | No |

## End of day

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `end_of_day_title` | merge | Fin de la journée | End of day | Itinéraire terminé | Itinerary ended | live. Absorbs both heroes. True for a day, a night, an early end or no confirmed Stops. See question 1. | Yes: one title, no hero line |
| `end_of_day_hero_day` | merge | Belle journée! | What a day! | — | — | future. Merged into `end_of_day_title`. Also breaks the no-exclamation rule. | Yes: see `end_of_day_title` |
| `end_of_day_hero_night` | merge | Belle soirée! | What a night! | — | — | future. Merged into `end_of_day_title`. Also breaks the no-exclamation rule. | Yes: see `end_of_day_title` |
| `end_of_day_new_stamps_section` | keep | Nouveaux timbres | New stamps | Nouveaux timbres | New stamps | future. Section heading. | No |
| `end_of_day_confirmed_only` | delete | Seuls les arrêts confirmés donnent un timbre. | Only confirmed stops earn a stamp. | — | — | future. Each Stop's status and Stamp result show this. | Yes: show status and Stamp or "aucun timbre" per Stop |
| `end_of_day_finish` | shorten | Terminer la journée | End the day | Terminer l’itinéraire | End itinerary | future. Uses the Glossary term and stays true at night. | No |

## Passport

Owned by teammate `cmvb`. Wording proposal only.

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `passport_title` | keep | Passeport | Passport | Passeport | Passport | future. **Passport key: `cmvb`.** Glossary term. | Yes: use it in `ScreenPassport.kt` (see hardcoded text) |

## Cross-team notes

**Other teams' strings on these screens**

- `action_arrived` (shared): "Je suis arrivé·e" uses the forbidden dotted form. Suggest "Je suis sur place", which pairs with the arrival notification.
- `late_no_replanning` (shared): the two buttons already show the user's options. Suggest deleting it.
- `late_warning` (shared): "à %1$s" breaks on names starting with an article ("à Le Bistro"). Suggest "Retard possible : %1$s" / "You may be late: %1$s".
- `new_stamps_in_passport` (shared): repeats `end_of_day_new_stamps_section` if both appear on End of day. Use one.
- `end_of_day_no_stamp` (home): End of day needs it once `end_of_day_confirmed_only` is deleted. Please keep it.
- `data_attribution` (shared): must appear on Underway screens that show Event data.

**Hardcoded user-facing text**

- `scaffold/screens/passport/ScreenPassport.kt:20`: `Text("Passport")` shows English in the French app. It should use `passport_title` (`cmvb`).
- No other hardcoded user-facing text in the Underway components. Their literals are `@Preview` samples.

**Required strings with no key yet**

- Warning before ending an Itinerary early (must stay): "Terminer l’itinéraire? Les arrêts restants seront marqués comme sautés." / "End itinerary? Remaining stops will be marked as skipped."
- Error when no travel app opens (`HandoffResult.UNAVAILABLE`): "Impossible d’ouvrir %1$s. Essayez une autre option." / "Couldn’t open %1$s. Try another option."

## Questions for the product owner

1. **End screen title:** should one neutral title, "Itinéraire terminé" / "Itinerary ended", replace "Belle journée!" and "Belle soirée!"? It is shorter and true when someone ends early or confirms no Stops, but it drops the celebratory line. If you want to keep that line, the fallback is "Quelle journée" / "Quelle soirée" and "What a day" / "What a night", without exclamation marks.
2. **Repeat visits:** a confirmed Stop earns no new Stamp if the person already has it, so "aucun timbre" could look like a bug. Should that row say "Déjà dans votre passeport" / "Already in your passport"?

## French Editor notes

Reviewed every `FR proposed` cell against the `quebec-french-ui` and `plan-writing` rules, read in its component (`ui/components/`) and in `prep/design/screen-list.md`. Only the `FR proposed` column was edited. 4 cells changed, all typography; no wording changed.

**Changed (U+00A0 is the real character in the cell, shown here as `⍽`)**

- `leg_fare_required`: `Titre requis⍽: %1$s · %2$s`. Non-breaking space before the colon (OQLF). "Titre" and "1 passage" are the ARTM/STM terms ("Titres et tarifs", "1 passage, tous modes A"), so `Titre requis : 1 passage · ARTM` reads as standard transit vocabulary.
- `at_stop_next`: `Prochain arrêt⍽: %1$s · ≈⍽%2$d⍽min`. Non-breaking space before the colon and between the number and the unit symbol `min`, as the OQLF requires for `14 h`, `58 kg`; this also stops `≈`, the number and `min` from splitting across lines when a long Stop name wraps the card.
- `leg_walk_minutes`: `≈⍽%1$d⍽min`, same unit rule. The column is single-line, so this is for consistency with `at_stop_next`.
- `notification_leave_body`: `Prochain arrêt⍽: %1$s`, non-breaking space before the colon.

No `keep` row contains `:`, `$` or `« »`, so none needed the corrected form.

**Checked and kept as proposed**

- `notification_leave_title` `Partez avant %1$s`: correct on a lock screen once the caller formats the time as `19 h 15` (a sentence, not a control). It matches the live `at_stop_leave_by` label. Note the current `LocalNotificationPreview` sample passes `"19:15"`; the preview must pass the sentence form.
- `notification_arrival_title` `Êtes-vous sur place?`: épicène, no dotted form, natural Québec usage. No space before `?`, consistent with `Mot de passe oublié?` elsewhere in the app.
- `notification_arrival_body` `%1$s · %2$s`: a value line, so `18:30` is allowed by the time rule; see the human-review list for the lock-screen consistency question.
- `leg_how_to_get_there` `Comment y aller?`: correct and shorter.
- `leg_mode_transit` `Transport en commun`: standard Québec term (ARTM's own "transport collectif" is no shorter). The two-line layout fix in the plan still applies.
- `leg_google_maps_directions` `Ouvrir dans Google Maps`, `leg_open_transit_app` `Ouvrir %1$s`, `end_of_day_finish` `Terminer l’itinéraire`: infinitive for actions, Glossary term for Itinerary.
- `end_of_day_title` `Itinéraire terminé`: agreement is right (`itinéraire` is masculine) and the title stays true at night.
- `leg_start_from_location` `Début %1$s`, `arrival_expected` `Arrivée prévue %1$s`, `leg_stop_progress`: compact label-and-value lines, `18:30` form is right.

**Removed text: no harm to French comprehension**

`arrival_not_detected_note`, `arrival_reminder_note`, `at_stop_alert_note`, `end_of_day_confirmed_only`, the two heroes and `leg_fare_source` can go. Their meaning survives in the arrival button, the merged fare row and the per-Stop Stamp status. Nothing to restore.

**Cross-team French, not edited here (outside the column)**

- `late_warning` suggestion `Retard possible : %1$s` needs U+00A0 before the colon when it is proposed to the shared team.
- `action_arrived` suggestion `Je suis sur place` is right and pairs with the arrival notification.
- The two required strings with no key yet read correctly: `Terminer l’itinéraire? Les arrêts restants seront marqués comme sautés.` and `Impossible d’ouvrir %1$s. Essayez une autre option.`

**Needs a human Québec French reader**

- `passport_title` and `end_of_day_new_stamps_section` (`cmvb` owns the Passport keys): "timbre" is the app-wide word for Stamp (`stamps_count`, `welcome_tagline`, `new_stamps_in_passport`). For a mark in a passport, "tampon" or "estampille" is what a reader may expect, while "timbre" also covers a stamped mark (timbre humide). Keep "timbre" for consistency unless a native reader objects; this is a glossary-level decision, not a row fix.
- `notification_leave_title` versus `notification_arrival_body`: when both notifications stack on a lock screen, one shows `19 h 15` (sentence) and the other `18:30` (value). Both follow the rule; confirm that the mix reads fine to a Québec reader, or ask for `18 h 30` in the arrival body too.
- `at_stop_leave_by` and `notification_leave_title` `Partez avant`: "Leave By" means the latest moment to leave, which "avant" renders a little loosely; `Partez d’ici 19 h 15` is the closer Québec phrasing. Kept "avant" because it is live and consistent, but worth a native ear.
- `leg_fare_required` `Titre requis`: "titre" alone (without "de transport") is how the STM and ARTM write it next to a fare, and the ticket icon carries the context; confirm it does not read as "title" to a first-time reader.
- `leg_start_from_location` `Début %1$s` on a Meal Stop ("Début 18:30" for a restaurant): acceptable as the Meal Block start, but confirm it does not read as the restaurant's opening time.

## English Editor notes

Reviewed every `EN proposed` cell against the final French, the Glossary and `plan-writing`, in its component (`ui/components/`) and in `prep/design/screen-list.md`. Only the `EN proposed` column was edited. The U+00A0 count is unchanged. 3 strings changed.

**Changed**

- `leg_depart` "Depart" to "Departure" and `leg_arrive` "Arrive" to "Arrival". The French labels (Départ, Arrivée) are nouns, and "Depart / Arrive" read as commands next to "Travel".
- `leg_fare_required` "Fare needed" to "Ticket needed". "Titre" is a ticket (the ticket icon sits beside it), and "fare" means the price, which the row does not show. It now reads "Ticket needed: 1 ride · ARTM".

**Recurring problems**

- Column labels written as verbs where the French uses nouns.
- Fare and ticket vocabulary mixed.
- Time format in notification arguments: the caller must format `%1$s` for English (`7:15 p.m.`, not `19 h 15`).

**Meaning mismatches to flag**

- `leg_fare_required`: the French and English drop "Selon la grille tarifaire de ARTM". A bare "· ARTM" reads as the ticket's issuer, not as the data source. Attribution must stay, so the Copy Lead should confirm this is enough (same in French).
- `notification_arrival_body` `%1$s · %2$s`: on a lock screen, "Le Bistro · 6:30 p.m." does not say the time is the start time. Same ambiguity in French. The title "Have you arrived?" carries the context, so it is kept.
- `notification_leave_title` "Leave by %1$s" matches the Glossary term Leave By. French "Partez avant" is looser; no English change.
- `end_of_day_title` "Itinerary ended" versus French "Itinéraire terminé" (finished). "Ended" is truer when stopped early and matches the "End itinerary" button. Kept.
- `notification_channel_underway` "Underway itinerary" uses the Glossary term as an adjective. It is stiff, but "in progress" would swap the term. Kept; flagged for the product owner.

**Passport keys (`cmvb`)**: `passport_title` "Passport" and `end_of_day_new_stamps_section` "New stamps" are proposals only and match the Glossary.

**Checked and kept**: the contractions in the cross-team lines ("Couldn’t open", "You may be late") are consistent. There is no Canadian spelling issue in this file, and Lunch, Dinner and Late bite do not appear in these strings.
