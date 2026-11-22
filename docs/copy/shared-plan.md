# Shared copy plan

Draft by the shared Copy Lead, for the French and English Editors. It covers the 31 keys in the `shared` section of `key-ownership.md`.

## Summary

| Decision | Count | Keys |
|---|---:|---|
| keep | 16 | Common actions, attribution, Underway actions, `districts_count`, `price_unknown`, `stop_kind_event`, `app_widget_description` |
| delete | 4 | `action_add`, `action_open`, `late_no_replanning`, `map_district_boundaries` |
| show-in-UI | 3 | `state_loading`, `time_hour`, `time_minute` |
| merge | 0 | |
| shorten | 5 | `action_arrived`, `late_warning`, `price_free`, `state_empty_title`, `state_error_title` |
| unused | 3 | `new_stamps_in_passport`, `stops_confirmed`, `stops_skipped` |
| **Total** | **31** | |

- **`unused`** means no code reads the key today, but a planned page needs it. Keep it.
- **`future`** in a Reason marks a key for a page not built yet. Neither `strings.xml` file has a literal `future` tag, so this comes from the unchecked items in `docs/IMPLEMENTATION_PLAN.md`.
- **Preview only** means the key is reached only from a `@Preview`, although `key-ownership.md` lists a component file for it.
- **Needs UI change:** 4 keys in the tables (`state_loading`, `time_hour`, `time_minute`, `late_no_replanning`), 2 key splits, and `app_widget_description` depending on Q1.

## Common actions

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `action_add` | delete | + Ajouter | + Add | — | — | No code reads it, and no planned page needs a generic Add. | No |
| `action_open` | delete | Ouvrir | Open | — | — | No code reads it. `leg_open_transit_app` covers the planned use. | No |
| `action_back` | keep | Retour | Back | Retour | Back | Accessibility label of the icon-only back arrow. | No |
| `action_cancel` | keep | Annuler | Cancel | Annuler | Cancel | Same meaning in the delete dialog, date and time dialogs, and District replace. | No |
| `action_clear` | keep | Effacer | Clear | Effacer | Clear | Accessibility label. Needs the field name: see split 1. | See split 1 |
| `action_confirm` | keep | Confirmer | Confirm | Confirmer | Confirm | Same meaning in date, time and Planned Departure controls. | No |
| `action_continue` | keep | Continuer | Continue | Continuer | Continue | Correct for every onboarding and Planning step. | No |
| `action_retry` | keep | Réessayer | Try again | Réessayer | Try again | The next action on every error state. | No |
| `settings_sign_out` | keep | Se déconnecter | Sign out | Se déconnecter | Sign out | Same action in Settings, onboarding and the Planning route. | No |
| `settings_title` | keep | Paramètres | Settings | Paramètres | Settings | Screen title and accessibility label of the Home gear icon. | No |

## Loading, empty and error states

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `state_loading` | show-in-UI | Chargement… | Loading… | Chargement… | Loading… | The spinner already shows it. Remove the visible text and keep the key as the spinner's accessibility description. | Yes: `LoadingState` |
| `state_empty_title` | shorten | Rien à afficher pour le moment | Nothing to show yet | Rien à afficher | Nothing to show | Its only live use is a missing Profile in Settings, where "yet" promises content. | No |
| `state_error_title` | shorten | Une erreur est survenue | Something went wrong | Chargement impossible | Couldn’t load | All 11 live uses are load failures. Names the failure, and Retry gives the next action. | No |

## Underway actions and warnings

All keys here are preview only and `future` (Underway, Local notifications).

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `action_arrived` | shorten | Je suis arrivé·e | I’ve arrived | Je suis là | I’m here | Removes the dotted form the Québec rules forbid. Answers "Have you arrived?". | No |
| `action_leaving` | keep | Je pars | I’m leaving | Je pars | I’m leaving | Already short. The infinitive "Partir" would read as an order, not a declaration. | No |
| `action_go_anyway` | keep | Y aller quand même | Go anyway | Y aller quand même | Go anyway | Clear choice on the late warning. | No |
| `action_skip_stop` | keep | Sauter cet arrêt | Skip this stop | Sauter cet arrêt | Skip this stop | Also stands alone on a notification, so it must name the Stop. | No |
| `late_warning` | shorten | Vous risquez d’arriver en retard à %1$s. | You may arrive late at %1$s. | Retard possible : %1$s | You may be late for %1$s | Shorter, and avoids "à Le Bistro" when a Stop name starts with an article. | No |
| `late_no_replanning` | delete | Pas de replanification automatique. | No automatic replanning. | — | — | The two buttons already show the user decides. | Yes: remove `detail` and its `Text` from `LateArrivalWarning` |

## Attribution, map and counts

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `data_attribution` | keep | Données : Has Things (hasthings.com), Ville de Montréal, © contributeurs OpenStreetMap | Data: Has Things (hasthings.com), Ville de Montréal, © OpenStreetMap contributors | Données : Has Things (hasthings.com), Ville de Montréal, © les contributeurs d’OpenStreetMap | Data: Has Things (hasthings.com), Ville de Montréal, © OpenStreetMap contributors | Must stay. Has Things sets this credit wording under ODbL. Only fix: non-breaking space before the colon. | No |
| `map_district_boundaries` | delete | Carte · limites des arrondissements | Map · borough boundaries | — | — | Caption of the removed placeholder map. No code reads it. | No |
| `districts_count` | keep | one: %d arrondissement / other: %d arrondissements | one: %d borough / other: %d boroughs | one: %d arrondissement / other: %d arrondissements | one: %d borough / other: %d boroughs | Preview only, `future` (Passport). Short and clear. | No |

## Prices

Both keys are preview only and `future` (Event and Meal selection).

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `price_free` | shorten | Gratuit | Free | gratuit | free | Case only: it sits mid-line, as in `18:00 · gratuit · fin non publiée`. | No |
| `price_unknown` | keep | prix inconnu | price unknown | prix inconnu | price unknown | Fits the same line. Absence alone could read as free. | No |

## Stops and Stamps

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `stop_kind_event` | keep | Événement | Event | Événement | Event | Correct as a Stop kind. Wrong as the Planning step 5 title: see split 2. | See split 2 |
| `stops_confirmed` | unused | one: %d arrêt confirmé / other: %d arrêts confirmés | one: %d stop confirmed / other: %d stops confirmed | one: %d arrêt confirmé / other: %d arrêts confirmés | one: %d stop confirmed / other: %d stops confirmed | `future` (End of day). Already short. | No |
| `stops_skipped` | unused | one: %d arrêt sauté / other: %d arrêts sautés | one: %d stop skipped / other: %d stops skipped | one: %d arrêt sauté / other: %d arrêts sautés | one: %d stop skipped / other: %d stops skipped | `future` (End of day). Already short. | No |
| `new_stamps_in_passport` | unused | one: %d nouveau timbre dans votre passeport. / other: %d nouveaux timbres dans votre passeport. | one: %d new stamp in your passport. / other: %d new stamps in your passport. | one: %d nouveau timbre / other: %d nouveaux timbres | one: %d new stamp / other: %d new stamps | `future` (End of day). Stamps always go in the Passport. | No |

## Time controls and system

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `time_hour` | show-in-UI | Heure | Hour | Heure | Hour | Two boxes around a colon read as a time. Remove the visible caption and keep the key for accessibility. | Yes: `TimeUnitWheel` |
| `time_minute` | show-in-UI | Minute | Minute | Minute | Minute | Same as `time_hour`. | Yes: `TimeUnitWheel` |
| `app_widget_description` | keep | Un widget simple | A simple widget | Un widget simple | A simple widget | Live in the system widget picker, though listed as unused. Template text, blocked by Q1. | Depends on Q1 |

## Cross-team notes

### Proposed key splits

| # | Existing key | Context needing its own words | New key | FR | EN | Owner | UI change |
|---|---|---|---|---|---|---|---|
| 1 | `action_clear` | Login and register have several fields, so TalkBack hears the same "Clear" on each | `action_clear_field` | Effacer le champ %1$s | Clear %1$s field | shared | Pass the field label in `HackappTextField` |
| 2 | `stop_kind_event` | Planning step 5 selects Events and Meals but is titled "Événement" | `planning_stops_title` | Choix des arrêts | Choose stops | planning | Use it in `PlanningNavigation.kt:76` |

### Notes for other teams

- **access:** `onboarding_step` is `Étape %1$d de %2$d`, but the settled wording is "Étape N sur 6". It is also the accessibility text of `StepProgress` in Planning, so it should not carry an onboarding name.
- **underway:** `notification_arrival_title` (`Êtes-vous arrivé·e?`) uses the forbidden dotted form. "Êtes-vous là?" pairs with `action_arrived`.
- **planning:** the plan shows unknown Meal prices in the running total as "+ unknown price". Give that its own key rather than reusing `price_unknown`.
- **planning:** when Review enables `StopRow.onRemove`, each "Retirer" needs the Stop name for TalkBack: `action_remove_stop` = `Retirer %1$s` / `Remove %1$s`.
- **City Pack:** `data_attribution` and `districts_count` contain Montréal-specific sources and terms, so they must move to the City Pack before another city ships.
- **Attribution:** recheck the sources when Meal selection ships, since curated restaurant data (ADR 0002) is not credited yet.
- **Docs:** `CLAUDE.md` section 6.3 and open item 8 still mention `map_district_boundaries`. Update them if the key is deleted.
- **French Editor:** `action_arrived` and `action_leaving` deliberately break the infinitive button rule, because they answer a notification in the first person.

### Hardcoded user-facing text

Other literals in `ui/components/` are inside `@Preview` code.

| Location | Text | Live | Proposal |
|---|---|---|---|
| `widgets/AppWidget.kt:46` | `Hello from widget !` | Yes | Remove with the widget, or replace (Q1) |
| `ui/components/TimeWheels.kt:106` | `"$label ${twoDigits(value)}"` | Yes | Accessibility join. Reads correctly in both languages, low priority |
| `ui/components/StopOptionCards.kt:75,94` | ` · ` separators | Preview only | Value list, not a sentence. No change |
| `components/DistrictEventCount.kt:44` | `99+` | Yes | Numeric. No change, but see the District map gap below |

### Accessibility gaps in `ui/components/`

| Component | Live | Gap | Proposal |
|---|---|---|---|
| `map/DistrictPickMap.kt:197` | Yes | TalkBack hears only the District name. The Event count and pick order are cleared | Plural `map_district_description`: `%1$s, %2$d événement(s)` / `%1$s, %2$d event(s)`, as real plural items. `map_district_selected`: `%1$s, position %2$d` in both languages |
| `map/ItineraryRouteMap.kt:93` | Yes | No map description. Markers read as bare numbers | Plural `map_route_description`: `Carte de l’itinéraire, %d arrêt(s)` / `Itinerary map, %d stop(s)`, as real plural items, and hide the markers from TalkBack |
| `ItineraryStrip.kt` | Yes (Home) | Stop type and current Stop are shown only by colour and size | `strip_stop`: `%1$s, %2$s`. `strip_stop_current`: `%1$s, %2$s, arrêt actuel` / `%1$s, %2$s, current stop` |
| `HorizontalInfoCard.kt` | Yes (onboarding) | `selected` is visual only | Set the selected semantics. No new string |
| `TimeWheels.kt:124`, `HackappTextField.kt:94` | Yes | `onClickLabel` is a noun, so TalkBack says "double-tap to Date" | Remove `onClickLabel`. No new string |

## Questions for the product owner

1. **Should the home-screen widget ship?** It is still the template and shows "Hello from widget !". I recommend removing it, which also deletes `app_widget_description`. If it stays, it needs real content, and the description could be `Ouvrir l’application` / `Open the app`.

## French Editor notes

Review of every `FR proposed` cell against each Kotlin usage, the glossary and the Québec French rules. One cell changed; everything else confirmed as written.

### Changed

- `data_attribution`: `© contributeurs OpenStreetMap` → `© les contributeurs d’OpenStreetMap`. Noun-on-noun apposition is an English construction; French needs the article and `de`. The openstreetmap.org copyright page no longer prescribes a fixed sentence (it asks for a credit to OpenStreetMap plus a visible ODbL mention), so this is the usual French form, not a licence quotation. Has Things asks for `Data: Has Things (hasthings.com)` under ODbL 1.0; `Données : Has Things (hasthings.com)` renders it faithfully. `Ville de Montréal` satisfies the City's CC BY 4.0 source credit. The same French text is hardcoded in `app/src/main/java/com/example/hackapp/citypack/CityPack.kt:81` and must follow the string, or the two will diverge.

### Confirmed

- Non-breaking spaces: `late_warning` (`Retard possible : %1$s`) and `data_attribution` (`Données : …`) already carry U+00A0 before the colon in `FR proposed`. No other proposed French string contains `:`, `$` or `« »`. `values/strings.xml` still has zero U+00A0, so whoever applies the plan must paste these two cells as they are, not retype them.
- `action_arrived` = `Je suis là`: confirmed. Épicène, natural Québec French, and it reads as an answer because the notification body names the Stop. First person is right here: the string answers a question, so the infinitive rule for buttons does not apply (same logic as `J’accepte`). It requires the underway team to change `notification_arrival_title` to `Êtes-vous là?`, as already noted. If a human reader finds `là` too vague on a phone screen, the fallback is `Je suis sur place`, also épicène.
- `action_leaving` = `Je pars`: confirmed. It answers `Départ dans 30 min` and `Quittez %1$s avant %2$s…`; `Partir` would read as an instruction.
- `state_error_title` = `Chargement impossible`: confirmed. Noun phrase, matches the house pattern (`Connexion impossible`, `Impossible d’enregistrer`), and `Réessayer` gives the next action. Note for later: it is only correct while every `ErrorState` is a load failure; a save failure must not reuse it.
- `late_warning` = `Retard possible : %1$s`: confirmed. The label-value form works with any Stop name, including names starting with an article (`Le Bistro`, `L’Escalier`), and the placeholder changes no agreement. Removing `late_no_replanning` does not hurt comprehension: the two buttons carry the choice.
- `state_empty_title` = `Rien à afficher`: confirmed for its one live use (missing Profile in Settings).
- `price_free` = `gratuit` lowercase: confirmed. Both call sites (`StopOptionCards.kt:75`, `:94`) place it mid-line after ` · `.
- Plurals (`districts_count`, `stops_confirmed`, `stops_skipped`, `new_stamps_in_passport`): agreements correct. French `one` covers 0 and 1, so `0 nouveau timbre` and `0 arrêt sauté` are right.
- `action_clear_field` (`Effacer le champ %1$s`) and `planning_stops_title` (`Choix des arrêts`) in the key splits: correct as proposed. The accessibility proposals (`%1$s, %2$d événement(s)`, `Carte de l’itinéraire, %d arrêt(s)`, `arrêt actuel`, `position %2$d`) are correct once written as real plural items.
- All other `keep` rows (`Retour`, `Annuler`, `Effacer`, `Confirmer`, `Continuer`, `Réessayer`, `Se déconnecter`, `Paramètres`, `Chargement…`, `Y aller quand même`, `Sauter cet arrêt`, `prix inconnu`, `Événement`, `Heure`, `Minute`): infinitives on actions, accented capital on `Événement`, no anglicisms, consistent with `stop_status_skipped` / `stops_skipped` for "sauter".

### Needs a human Québec French reader

- `action_arrived` / `action_leaving`: deliberate first-person labels on notification actions. Confirm they feel natural on a real Android notification, where the two actions sit side by side (`Je suis là` · `Sauter cet arrêt`).
- `late_warning`: `Retard possible` could be read as the Stop itself being delayed rather than the person arriving late. The Underway context makes it clear to me, but a reader should check it on the card.
- `data_attribution`: confirm the French OSM credit form (`© les contributeurs d’OpenStreetMap`), and confirm with Has Things that a French rendering of their English credit line is acceptable. Separately, OSM requires the ODbL to be named or linked somewhere in the app (licence page), which is a UI item, not a string change here.
- `app_widget_description`: `widget` is accepted usage but the string is template text; blocked by Q1, so no change proposed.

## English Editor notes

Review of every `EN proposed` cell against the final French, each Kotlin usage, `GLOSSARY.md` and the plan-writing rules. No cell changed; all were confirmed as written. Canadian spelling is not at issue (no affected words). Contractions are consistent (`I’m`, `Couldn’t`, curly apostrophes).

### Confirmed

- `action_arrived` = `I’m here`: matches `Je suis là`. First person is right because it answers the notification question (`Have you arrived?`, to be shortened by the underway team). `I’m here` reads naturally and is shorter than `I’ve arrived`.
- `action_leaving` = `I’m leaving`: matches `Je pars`; it answers `Leaving in 30 min`.
- `state_error_title` = `Couldn’t load`: matches `Chargement impossible`. Short and plain, and `Try again` gives the next step. Valid only while every `ErrorState` is a load failure (same caveat as the French).
- `late_warning` = `You may be late for %1$s`: matches `Retard possible : %1$s`. The English addresses the person (as the old French `Vous risquez d’arriver…` did), which is clearer than a label form in English, and `for %1$s` works with any Stop name. Placeholder preserved.
- `data_attribution`: unchanged and accurate. `Data: Has Things (hasthings.com)` is the wording Has Things asks for, and `Ville de Montréal` and `© OpenStreetMap contributors` are both credited. Keep the same text in `citypack/CityPack.kt:81` (`en`), which currently matches.
- `state_empty_title` = `Nothing to show`, `price_free` = `free`, `price_unknown` = `price unknown`: lowercase prices sit mid-line after ` · `, as in French.
- Common actions, plurals, `Clear %1$s field`, `Choose stops`, `Remove %1$s`: natural, consistent with the glossary, placeholders intact.

### Recurring problems

None in the proposals. The strings being replaced (`Something went wrong`, `Nothing to show yet`, `I’ve arrived`) had the problems the Copy Lead already fixed: vague error cause, a false promise (`yet`), and wordiness.

### Meaning mismatches

None. One nuance only: `late_warning` says the person may be late, while the French label says a delay is possible. Both mean the same on the Underway card.

### Check

U+00A0 count in this file is unchanged (2, both in the French column).
