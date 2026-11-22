# Copy consistency report

Audit of the five writing plans and of the strings each team has applied, read on 2026-11-22. For each key, the value checked is the one in its owner's worktree, because the plans are partly stale (see "Already consistent"). Approved decisions are not reopened.

`⍽` marks U+00A0. Paste the real character. "Line N" is a row in the Planning plan's "Maquette copy to tighten" table.

## Must fix (5)

| # | Keys | Owner | Problem | FR | EN | UI change |
|---|---|---|---|---|---|---|
| M1 | `budget_subtitle` | access | "Par itinéraire" is false for Lunch and Dinner, which the spec budgets **per Meal**. Only `budget_events` is a total, and it already says so. | `Par personne` | `Per person` | No |
| M2 | `end_of_day_finish`, `end_of_day_title` | underway | The approved title says the Itinerary has ended, while the button on the same screen offers to end it. | Keep `Terminer l’itinéraire` for the Underway action, before the warning. New key `end_of_day_done`: `Retour à l’accueil` | Keep `End itinerary` there. `end_of_day_done`: `Back to Home` | Yes: end the Itinerary before this screen opens |
| M3 | `end_of_day_no_stamp` | home, underway | Home shortened it to `Aucun` for the Past "Timbres" section. Underway needs it on End of day Stop rows, beside `Déjà dans votre passeport`, where `Aucun` means nothing. | `end_of_day_no_stamp`: `Aucun timbre`. New key `past_stamps_none`: `Aucun` | `No stamp`. `past_stamps_none`: `None` | Yes: `ItineraryDetailPages.kt:118` uses the new key |
| M4 | `review_remove_note` | planning | "Les arrêts ne se réordonnent pas" contradicts the approved `Réorganiser` action. It also still says "créneau". | `Retirer un arrêt vous ramène à la sélection pour cette plage horaire.` | `Removing a stop takes you back to selection for that time slot.` | No |
| M5 | `leg_fare_required` (absorbed `leg_fare_source`) | underway | Source attribution must stay, but `1 passage · ARTM` reads as the ticket's issuer, not the source of the fare. | `Titre requis⍽: %1$s · tarif %2$s` | `Ticket needed: %1$s · %2$s fare` | No |

## Should fix (10)

| # | Keys | Owner | Problem | FR | EN |
|---|---|---|---|---|---|
| S1 | `late_warning` | shared | The French can mean the Stop itself is delayed. The EN has a different shape, and Underway proposed a third wording. | `Vous risquez d’être en retard⍽: %1$s` | `You may be late: %1$s` |
| S2 | `notification_arrival_title` | underway | The FR pairs with the approved `Je suis sur place`. The EN question does not pair with `I’m here`. | `Êtes-vous sur place?` (no change) | `Are you there?` |
| S3 | Lines 5 and 10 | planning | Alone, "accessible" can read as wheelchair access in a city outing app. | Line 5: `Rien d’accessible à temps depuis votre dernier arrêt.` Line 10: `… Ces arrêts ne seront plus accessibles à temps⍽: replanifiez-les ou réorganisez votre itinéraire.` | Line 5: `Nothing reachable in time from your last stop.` Line 10: `… These stops won’t be reachable in time. Reschedule them or reorganize your itinerary.` |
| S4 | Line 8, Underway's proposed end-early warning | planning, underway | Same outcome, two English forms: `are marked skipped` and `will be marked as skipped`. | `… seront marqués comme sautés.` (no change) | Line 8: `“{name}” will move to Past. Confirmed stops will keep their stamps. The others will be marked as skipped.` |
| S5 | `stop_kind_event` at `PlanningNavigation.kt:76`, proposed `planning_stops_title` | planning | Step 5 is still titled "Événement", and the split has not been applied. Shared's `Choix des arrêts` / `Choose stops` pair a noun with a verb, and other step titles are nouns. | `Arrêts` | `Stops` |
| S6 | `new_stamps_in_passport`, `end_of_day_new_stamps_section` | shared, underway | After shortening, both say "new stamps" on End of day. | Show `Nouveaux timbres` only | Show `New stamps` only |
| S7 | `notification_leave_title`, `notification_arrival_body`, `planning_error_latest_end` | underway, planning | The Underway editor asks for `7:15 p.m.`, but the app formats `HH:mm` and Planning uses `04:00`. | Sentences `19 h 15`, values `19:15` | `19:15` everywhere (see question 1) |
| S8 | All ` · ` strings: `itinerary_title_in`, `itinerary_date_window`, `itineraries_archived`, `meal_*_at`, `at_stop_next`, `leg_fare_required`, `leg_stop_progress`, `notification_arrival_body`, `notification_preview_header`, `district_order_chip`. Code joins: `ItineraryUi.kt:69`, `ItineraryDetailPages.kt:223`, `ScreenSettings.kt:126,136`, `StopOptionCards.kt:75,94`, `DistrictPickMap.kt:114,116` | all | A wrapped line can start with the separator. | `⍽· ` | `⍽· ` |
| S9 | `at_stop_next`, `leg_walk_minutes`, `budget_under_20`, `budget_under_50`, `budget_under_100` | underway, access | The French has U+00A0 and the English does not, so values can split across lines. | No change | `≈⍽%1$d⍽min`, `&lt;⍽$20` (same for 50 and 100) |
| S10 | `interests_subtitle`, lines 2 and 4 | access, planning | Fragments end with a period, while similar ones (`Bars masqués`, `Archivé⍽: date passée`) do not. Rule: sentences and errors take a period, fragments do not. | `Pour classer les résultats, sans rien masquer`; `Aucun bloc repas réservé`; `Événements à heure fixe masqués pendant ce bloc` | `Used to rank results, not hide them`; `No meal blocks reserved`; `Fixed-time events hidden during this block` |

## Optional (4)

| # | Keys | Owner | Note | FR | EN |
|---|---|---|---|---|---|
| O1 | `audience_adults_only` | access | Differs from the approved `18 ans et plus`. The Glossary uses "18+ only", so update its example if adopted. | `18 ans et plus` | `18 and over` |
| O2 | `password_reset_title` | access | The FR names a state and the EN names a task. | `Mot de passe oublié` (no change) | `Forgot password` |
| O3 | `leg_how_to_get_there` | underway | The FR is a question and the EN a noun phrase. | `Comment y aller?` (no change) | `How to get there` |
| O4 | `settings_events_budget`, `budget_events` | access, planning | Same budget, labelled with and without "(total)". | `Événements (total)⍽: %1$s` | `Events (total): %1$s` |

## Glossary additions

Proposed only. `GLOSSARY.md` was not edited.

| Term | French | English | Definition or rule |
|---|---|---|---|
| Time Slot | plage horaire | time slot | The gap being filled on Stop selection. Avoid "créneau". Different from Meal Block. |
| Reachable | accessible à temps | reachable in time | A Stop that can be reached before it starts. Never "accessible" alone. |
| Skip a Stop | sauter | skip | Avoid "ignorer" and "passer" for a Stop or Meal. |
| Skip a step | passer | skip | Onboarding steps only (`action_skip`). |
| Meals | Dîner, Souper, Bouchée tardive | Lunch, Dinner, Late bite | Avoid "Collation de fin de soirée" and "Late-night snack". |
| Leave By | Partez avant | Leave by | Label form of the existing term. |
| Planned Departure | Départ prévu | Planned departure | French form of the existing term. |
| Duration | durée | duration | Time spent at a Stop. Avoid "séjour", which stays only in `profile_district_of_stay`. |
| Past, Archived | Passés, Archivés | Past, Archived | Past was started. Archived was never started and its date has passed. |

## Borough names (report only)

`app/src/main/assets/citypacks/mtl/districts.json` differs from the project's `reference.yaml`, which carries the official en dash forms. Ids are unaffected.

| `districts.json` | Official form |
|---|---|
| `Côte-des-Neiges-Notre-Dame-de-Grâce` | `Côte-des-Neiges–Notre-Dame-de-Grâce` |
| `L'Île-Bizard-Sainte-Geneviève` | `L’Île-Bizard–Sainte-Geneviève` |
| `Mercier-Hochelaga-Maisonneuve` | `Mercier–Hochelaga-Maisonneuve` |
| `Rivière-des-Prairies-Pointe-aux-Trembles` | `Rivière-des-Prairies–Pointe-aux-Trembles` |
| `Rosemont-La Petite-Patrie` | `Rosemont–La Petite-Patrie` |
| `Villeray-Saint-Michel-Parc-Extension` | `Villeray–Saint-Michel–Parc-Extension` |
| `L'Île-Dorval` | `L’Île-Dorval` (apostrophe only) |
| `Baie-D'Urfé` | `Baie-D’Urfé` (apostrophe only) |

Severity: should fix, in the City Pack data. `reference.yaml` also uses straight apostrophes. `Dollard-des-Ormeaux` is left as is: secondary sources report a 2022 return to this spelling, but the Commission de toponymie's own record was not checked.

## Already consistent

- "Sortie" and "outing" remain only in `welcome_title` among owned strings. `DesignSystemPreview.kt:97` still has "sortie", in preview text only.
- The approved strings that have keys are applied in their owner's worktree. `Replanifier` / `Reschedule` and `Réorganiser` / `Reorganize` have no keys yet, because the Remove Stop dialog is not built. Planning should add them with that dialog. The access, shared, Planning and Underway plans still show some superseded text (`Visitor`, `Je suis là`, open questions on Reschedule and End of day). Follow the approved wording.
- Meal names, `sauter` for Stops, `Étape %1$d sur %2$d`, typographic apostrophes and U+00A0 before French `:` and `$` are consistent.
- `data_attribution` matches `CityPack.kt:81` in both languages.
- Every deleted key is gone from its owner's Kotlin. Other worktrees still reference them only because those branches have not merged yet.

## Questions for the product owner

1. **English time format (S7):** keep 24-hour `19:15`, as the app's time controls do, or format English as `7:15 p.m.`? The second needs a locale-aware formatter.
2. **End of day (M2):** confirm the Itinerary ends before this screen opens, so its button can be `Retour à l’accueil` / `Back to Home`.

**Must-fix count: 5**
