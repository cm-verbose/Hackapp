# Planning copy plan

Draft by the Copy Lead for the French and English Editors. Covers the 68 `planning` keys in `docs/copy/key-ownership.md`, in user-flow order. No string or Kotlin file was changed.

## Summary

| Decision | Keys |
|---|---:|
| keep | 44 |
| delete | 8 |
| show-in-UI | 2 |
| merge | 2 |
| shorten | 12 |
| unused | 0 |
| **Total** | **68** |

- **13 keys** need a layout, behaviour or maquette change, so the String Steward cannot apply them alone. 3 of the 10 maquette lines do too.
- No key is marked `unused`. Every key with no live caller belongs to a page not built yet and is tagged `future` in its reason.
- A `merge` row with `→` is absorbed into the named key.

## 1. Date and time

Live. Step 1 of 6.

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `planning_new_itinerary` | keep | Nouvel itinéraire | New itinerary | Nouvel itinéraire | New itinerary | Names the task in the app bar. | No |
| `planning_when_title` | delete | Quand sortez-vous? | When are you going out? | — | — | The Date, Start and End labels already ask this. | Yes: remove the heading in `PlanningDateTimePage.kt:107` |
| `planning_date` | keep | Date | Date | Date | Date | Field label. | No |
| `planning_day_out` | keep | Journée | Day out | Journée | Day out | Segment label. | No |
| `planning_night_out` | keep | Soirée | Night out | Soirée | Night out | Segment label. | No |
| `planning_start` | keep | Début | Start | Début | Start | Field label. | No |
| `planning_end` | keep | Fin | End | Fin | End | Field label. | No |
| `planning_error_min_duration` | shorten | Un itinéraire dure au moins 2 heures. | An itinerary lasts at least 2 hours. | Prévoyez au moins 2 h. | Allow at least 2 hours. | Error stays and now states the next action. | No |
| `planning_error_latest_end` | shorten | Un itinéraire se termine au plus tard à 04:00. | An itinerary ends no later than 04:00. | Terminez au plus tard à 4 h. | End by 04:00. | Error stays. Also shown when End is before Start, and still correct there. French sentence time format. | No |
| `planning_save_error` | keep | Impossible d’enregistrer. Réessayez. | Couldn’t save. Try again. | Impossible d’enregistrer. Réessayez. | Couldn’t save. Try again. | Must stay. Error with next action, already minimal. | No |

## 2. Budget

Live. Step 2 of 6, choice page then optional edit page. `BudgetFields` is also rendered in onboarding, so its label changes appear there too.

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `planning_budget_title` | keep | Budget | Budget | Budget | Budget | Names the step in the app bar. | No |
| `planning_budget_question` | delete | Modifier votre budget pour cette sortie? | Change your budget for this outing? | — | — | The two buttons already pose this choice. | Yes: remove the heading in `PlanningBudgetPages.kt:101` |
| `planning_budget_current` | delete | Voici le budget actuellement prévu pour cette sortie. | This is the budget currently set for this outing. | — | — | The value card under the Budget title shows this. | Yes: remove the text in `PlanningBudgetPages.kt:106` |
| `budget_lunch` | shorten | Dîner (repas 1) | Lunch (Meal 1) | Dîner | Lunch | Settled Meal name. "(repas 1)" adds nothing. | No |
| `budget_dinner` | shorten | Souper (repas 2) | Dinner (Meal 2) | Souper | Dinner | Settled Meal name. "(repas 2)" adds nothing. | No |
| `budget_events` | keep | Événements (total) | Events (total) | Événements (total) | Events (total) | "(total)" matters: this budget covers all Events, unlike a per-Meal budget. | No |
| `budget_late_night` | shorten | Collation de fin de soirée | Late-night snack | Bouchée tardive | Late bite | Settled Meal name, replaces "Collation de fin de soirée". | No |
| `budget_late_night_description` | delete | Facultatif. Ajoutez un budget pour une bouchée tardive. | Optional. Add a budget for a late bite. | — | — | The switch, off by default, shows it is optional. | Yes: remove the text in `BudgetFields.kt:60` (also affects onboarding) |
| `budget_off` | keep | Désactivée | Off | Désactivée | Off | Value in the card. Feminine agreement still works with "Bouchée tardive". | No |
| `budget_not_set` | keep | Non défini | Not set | Non défini | Not set | Value in the card. | No |
| `planning_budget_keep` | keep | Garder ce budget | Keep this budget | Garder ce budget | Keep this budget | Names the action. Used by `AppNavigationTest`. | No |
| `planning_budget_change` | shorten | Modifier pour cette sortie | Change for this outing | Modifier pour cet itinéraire | Change for this itinerary | Glossary term: Itinerary, not outing. Carries the scope once the body text is gone. | No |
| `planning_budget_edit_title` | merge | Budget de cette sortie | Budget for this outing | Pour cet itinéraire seulement | For this itinerary only | App bar already says Budget. Absorbs the scope from the note. | No |
| `planning_budget_edit_note` | merge | Ces changements s’appliquent à cet itinéraire seulement. | These changes apply to this Itinerary only. | → `planning_budget_edit_title` | → `planning_budget_edit_title` | Same message as the merged heading. | Yes: remove the text in `PlanningBudgetPages.kt:217` |

## 3. District pick

Live. Step 3 of 6. Stop type labels first appear in this sheet and are reused on Stop selection and Review.

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `district_pick_title` | keep | Arrondissements | Boroughs | Arrondissements | Boroughs | Names the step. Also the lowercase fallback in `district_max_title`. | No |
| `district_pick_instructions` | shorten | Choisissez 1 à 3 arrondissements, dans l’ordre de visite. | Choose 1 to 3 boroughs, in the order you’ll visit them. | Choisissez-en 1 à 3, dans l’ordre de visite. | Choose 1 to 3, in the order you’ll visit them. | The title directly above names the District label. Limit and order must stay visible. | No |
| `district_order_chip` | keep | %1$d · %2$s | n/a (`translatable="false"`) | %1$d · %2$s | n/a (`translatable="false"`) | `translatable="false"`, used. | No |
| `district_map_reset` | keep | Réinitialiser la carte | Reset map | Réinitialiser la carte | Reset map | Must stay. Accessibility description of an icon-only button. | No |
| `district_add_first` | keep | Ajouter en 1re | Add as #1 | Ajouter en 1re | Add as #1 | Shows the visit order. French Editor: check "1re" against a masculine District label. | No |
| `district_add_as_position` | keep | Ajouter en %1$de | Add as #%1$d | Ajouter en %1$de | Add as #%1$d | Shows the visit order, so the instruction can stay short. | No |
| `action_remove` | keep | Retirer | Remove | Retirer | Remove | Names the action. Also used by `StopRow`. | No |
| `district_choose_another` | keep | Choisir un autre | Choose another | Choisir un autre | Choose another | Only labelled control that closes this sheet. The handle is drag-only and has no accessibility action. | No |
| `district_max_title` | keep | Maximum de %1$d %2$s | Maximum of %1$d %2$s | Maximum de %1$d %2$s | Maximum of %1$d %2$s | States the limit. | No |
| `district_max_body` | shorten | Pour ajouter %1$s, choisissez lequel remplacer. | To add %1$s, choose which one to replace. | Lequel remplacer par %1$s? | Replace which one with %1$s? | Becomes the question for the radio list. | No |
| `district_replace_label` | delete | Remplacer : | Replace: | — | — | Repeats the body above and the button below. | Yes: remove the label in `DistrictPickPage.kt:367` |
| `district_replace_action` | keep | Remplacer | Replace | Remplacer | Replace | Names the action. | No |
| `stop_type_music` | keep | Musique | Music | Musique | Music | Category label. | No |
| `stop_type_festival` | keep | Festival | Festival | Festival | Festival | Category label. | No |
| `stop_type_theatre` | keep | Théâtre | Theatre | Théâtre | Theatre | Category label. | No |
| `stop_type_comedy` | keep | Humour | Comedy | Humour | Comedy | Category label. | No |
| `stop_type_exhibition` | keep | Exposition | Exhibition | Exposition | Exhibition | Category label. | No |
| `stop_type_sport` | keep | Sport | Sport | Sport | Sport | Category label. | No |
| `stop_type_cinema` | keep | Cinéma | Cinema | Cinéma | Cinema | Category label. | No |
| `stop_type_family` | keep | Famille | Family | Famille | Family | Category label. | No |
| `stop_type_meal` | keep | Repas | Meal | Repas | Meal | Category label. | No |
| `stop_type_bar` | keep | Bar | Bar | Bar | Bar | Category label. | No |

## 4. Meal choice

`future`: step 4 is a placeholder and only `meal_title` is live. `meal_plan_*` and `meal_block_note` appear only in `@Preview`. Wording checked against the Figma maquette.

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `meal_title` | keep | Repas | Meals | Repas | Meals | Live in the placeholder. Names the step. | No |
| `meal_question` | delete | On planifie vos repas? | Should we plan your meals? | — | — | `future`. The title and the two options already pose the choice. | Maquette: remove the question |
| `meal_plan_yes` | shorten | Oui, planifiez mes repas | Yes, plan my meals | Planifier mes repas | Plan my meals | `future`. Stands alone once the question is gone. | No |
| `meal_plan_no` | keep | Je m’en occupe | I’ll handle it | Je m’en occupe | I’ll handle it | `future`. Short and clear. | No |
| `meal_when` | delete | Quand voulez-vous manger? | When do you want to eat? | — | — | `future`. Meal chips with their times answer this. | Maquette: remove the heading |
| `meal_lunch_at` | keep | Dîner · %1$s | Lunch · %1$s | Dîner · %1$s | Lunch · %1$s | `future`. Chip label. | No |
| `meal_dinner_at` | keep | Souper · %1$s | Dinner · %1$s | Souper · %1$s | Dinner · %1$s | `future`. Chip label. | No |
| `meal_late_bite_at` | keep | Bouchée tardive · %1$s | Late bite · %1$s | Bouchée tardive · %1$s | Late bite · %1$s | `future`. Chip label. | No |
| `meal_block_note` | show-in-UI | Chaque repas réserve un bloc de 90 min : 1 h, plus 15 min avant et après. | Each meal reserves a 90-minute block: 1 h, plus 15 min before and after. | Bloc de %1$d min | %1$d-min block | `future`. Current text is wrong for Late bite (45 min). The maquette already shows the length on each selected Meal, so the callout can go. | Yes: placeholder added, show per Meal, remove the `InfoCallout` |

## 5. Stop selection

`future`: step 5 is a placeholder. `selection_time_block` and `selection_meal_block_*` appear only in `@Preview`. `PlannedDepartureSheet` has no caller yet.

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `selection_district_progress` | shorten | Arrondissement %1$d sur %2$d · %3$s → %4$s | Borough %1$d of %2$d · %3$s → %4$s | Arrondissement %1$d sur %2$d | Borough %1$d of %2$d | `future`. The time slot header on the same screen already shows the times. | Yes: drop the `%3$s` and `%4$s` arguments |
| `selection_time_block` | keep | %1$s – %2$s | n/a (`translatable="false"`) | %1$s – %2$s | n/a (`translatable="false"`) | `future`. `translatable="false"`, used by the components. | No |
| `selection_meal_block_title` | shorten | Bloc repas %1$s – %2$s | Meal block %1$s – %2$s | Bloc repas | Meal block | `future`. The maquette shows the same range in the header directly above. | Yes: drop the `%1$s` and `%2$s` arguments |
| `selection_meal_block_body` | show-in-UI | Choisissez un restaurant ou un bar (dès 17:00). | Choose a restaurant or bar (from 17:00). | — | — | `future`. The button says what to do, and the list already hides bars before 17:00. The fixed "(dès 17:00)" would also mislead on a Lunch block. | Yes: remove the body from `MealBlockPrompt` |
| `selection_choose_meal` | keep | Choisir un repas | Choose a meal | Choisir un repas | Choose a meal | `future`. Names the action. | No |
| `selection_next_district` | keep | Arrondissement suivant | Next borough | Arrondissement suivant | Next borough | `future`. Names the action. | No |
| `selection_finish_here` | keep | Terminer ici | Finish here | Terminer ici | Finish here | `future`. Names the action. | No |
| `planned_departure_title` | shorten | À quelle heure partirez-vous? | When will you leave? | Départ prévu | Planned departure | `future`. Glossary term, matches `review_planned_departure`. | No |

## 6. Review

`future`: step 6 is a placeholder and only `review_title` is live.

| Key | Decision (keep/delete/show-in-UI/merge/shorten/unused) | FR now | EN now | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|---|---|
| `review_title` | keep | Révision | Review | Révision | Review | Live in the placeholder. Names the step. | No |
| `review_planned_departure` | keep | départ prévu %1$s | planned departure %1$s | départ prévu %1$s | planned departure %1$s | `future`. Glossary term, shown on the Stop row. | No |
| `review_remove_note` | delete | Retirer un arrêt vous ramène à la sélection pour ce créneau. Les arrêts ne se réordonnent pas. | Removing a stop takes you back to selection for that time slot. Stops can’t be reordered. | — | — | `future`. The remove dialog gives this warning at the moment it matters. Delete only once that dialog ships. | Yes: depends on `RemoveStopWarningDialog` |
| `review_start` | keep | Démarrer l’itinéraire | Start itinerary | Démarrer l’itinéraire | Start itinerary | `future`. Names the action. | No |
| `review_notification_note` | keep | On vous demandera d’autoriser les notifications. | We’ll ask you to allow notifications. | On vous demandera d’autoriser les notifications. | We’ll ask you to allow notifications. | `future`. Must stay as consent context before the system prompt. | No |

## Maquette copy to tighten

French uses non-breaking spaces where required. Placeholders keep their drafted form.

| # | Draft FR | FR proposed | EN proposed | Reason | Needs UI change |
|---|---|---|---|---|---|
| 1 | Votre sortie : %1$s – %2$s. Seuls les repas dans cette plage sont proposés. | Itinéraire : %1$s – %2$s | Itinerary: %1$s – %2$s | Only Meals that fit are offered, so the rule is visible. | No |
| 2 | Vous gérez vos repas : aucun bloc repas n’est réservé et aucune invite de repas n’apparaît pendant la planification. | Aucun bloc repas réservé. | No meal blocks reserved. | The selected option already says the user handles Meals. | No |
| 3 | Aucun repas ne tient dans cette plage. Allongez la sortie ou choisissez « Je m’en occupe ». | Aucun repas ne tient dans cette plage. Prolongez-la ou choisissez « Je m’en occupe ». | No meal fits this time slot. Extend it, or choose “I’ll handle it”. | Error with both next actions. Quoted label must match `meal_plan_no`. | No |
| 4 | Les événements à heure fixe pendant ce bloc sont masqués. Les longues expositions restent visibles. | Événements à heure fixe masqués pendant ce bloc. | Fixed-time events are hidden during this block. | The "En continu" group shows what stays visible, and it is not limited to exhibitions. | No |
| 5 | Rien n’est accessible de 18:00 à 19:00 depuis votre dernier arrêt. Passez à l’arrondissement suivant ou terminez ici. | Rien d’accessible depuis votre dernier arrêt. | Nothing reachable from your last stop. | The header shows the time slot, and the buttons directly below are the next actions. | Yes: keep the next-step actions visible in this state (the last District shows only Finish) |
| 6 | Aucune heure de fin n’est publiée pour cet événement. Nous proposons un séjour typique de %s pour une exposition. Vous pouvez changer l’heure à laquelle vous prévoyez partir. | Aucune heure de fin publiée. Durée habituelle : %s. | No end time published. Typical duration: %s. | The prefilled time wheels show it can be changed. The Event is named above. | No |
| 7 | Séjour de %1$s · vous partez à %2$s. L’heure de fin de l’événement reste vide. | Durée : %1$s | Duration: %1$s | The wheels already show the departure time. | Yes: drop the `%2$s` argument, update as the wheels move |
| 8 | « {nom} » est en cours. Si vous démarrez cet itinéraire, il se terminera : il passera dans Passés, gardera les timbres des arrêts confirmés et marquera les autres arrêts comme ignorés. | « {nom} » passera dans Passés. Les arrêts confirmés garderont leur timbre, les autres seront marqués comme sautés. | “{name}” moves to Past. Confirmed stops keep their stamps. The others are marked skipped. | Must stay. The dialog title already says it ends. "Sautés" matches `stop_status_skipped`; the draft said "ignorés". | No |
| 9 | Ce changement retire les arrêts qui ne conviennent plus. Les autres arrêts sont conservés. | Certains arrêts ne conviennent plus. | Some stops no longer fit. | Must stay. The "Seront retirés" and "Conservés" lists give the detail. | No |
| 10 | Vous reviendrez à la sélection pour le créneau de {heure}. Les arrêts suivants ne seraient plus atteignables et seront aussi retirés. | Retour à la plage horaire de {heure}. Ces arrêts ne seront plus accessibles : replanifiez-les ou réorganisez votre itinéraire. | Back to the time slot at {time}. These stops won’t be reachable anymore. Reschedule them or reorganize your itinerary. | Must stay. The "Seront aussi retirés" list carries the removal. Adds the approved actions: **Replanifier / Reschedule** picks another showtime for the affected Stops, and **Réorganiser / Reorganize** returns to selection from that gap to rebuild the rest. Pass `{heure}` as `19 h 30`. | Yes: the dialog has only Annuler and Retirer, so it needs Replanifier and Réorganiser (approved) |

Other maquette text to align when these screens are built:

- Replace "Dîner (repas 1)", "Souper (repas 2)" and "Collation de fin de soirée" on the Budget and Review frames.
- Use "Sauter ce repas" on the Meal picker, which says "Passer ce repas". The Meal Block frame and `action_skip_stop` already use "Sauter".
- Replace "Budget de la sortie" on Review with "Budget".
- The Date and time frames still show the helper removed from the app ("Journée : 10:00–18:00…"). Remove it.

## Cross-team notes

**Access**

- `onboarding_step` reads "Étape %1$d de %2$d". The settled form is "Étape N sur 6". `StepProgress` uses this key as the Planning progress description.
- `budget_subtitle` is the only place that says budgets are per person. Planning Budget never says it. Keep it if that key is edited.
- `budget_late_night_description` is removed from `BudgetFields`, which onboarding also renders.

**Home**

- `home_plan_title` uses "sortie" where the glossary says Itinerary (see question 2).

**Shared**

- `stop_kind_event` ("Événement") is the temporary Stop selection title in `PlanningNavigation.kt:76`. Step 5 needs its own title key rather than reusing this one.
- `price_unknown` is lowercase and `price_free` is capitalized, and both appear in the same card subtitle.
- `action_add` is unused and has a literal "+". Do not reuse it for District add.

**All teams, French typography**

- `values/strings.xml` has no non-breaking spaces. Nine strings have a plain space before `:`. Proposals here use U+00A0, and the French Editor should settle this across the app.

**Glossary flag**

- "Créneau" (the time gap on Stop selection) is used on screen but has no glossary entry. Not changed.

### Hardcoded or assembled user-facing text

| Location | Text | Issue |
|---|---|---|
| `ui/components/StopOptionCards.kt:68-73` | `parts.joinToString(" · ")` | Builds the Event subtitle by joining strings |
| `ui/components/StopOptionCards.kt:95` | `"$details · ${price.text()}"` | Builds the Meal subtitle by joining strings |
| `ui/components/TimeWheels.kt:106` | `"$label ${twoDigits(value)}"` | Accessibility description assembled in code |
| `ui/components/map/DistrictPickMap.kt:113-117` | `order · name · count` | Visible map label assembled in code, and the count has no unit |
| `ui/components/map/DistrictPickMap.kt:197` | `contentDescription = name` | TalkBack hears only the name: no visit order, Event count or unavailable state. Needs a dedicated string |
| `scaffold/screens/planning/DistrictPickPage.kt:355` | `district_pick_title` `.lowercase()` | Derives a plural from a title as a fallback |

Sample text in `@Preview` functions (such as "Marché" and "Jazz sur le toit") is not user-facing and is not listed.

## Questions for the product owner

1. **What should "reschedule" and "reorganize" do in the Remove Stop warning?** The maquette offers only Annuler and Retirer, and the current plan says Stops cannot be reordered. Line 10 uses your wording, but its buttons cannot be named until these two actions are defined.
2. **Should "sortie" stay in any visible text?** The glossary says to avoid "outing" for an Itinerary, so this plan uses "itinéraire". Home and Welcome still say "Votre sortie, sur mesure", which other teams own.

## French Editor notes

Review of every `FR proposed` cell and every kept French string against the OQLF usage rules in `quebec-french-ui`, read in its Kotlin screen context. 5 strings changed, all in `Maquette copy to tighten`; the 68 key rows stand as proposed.

**Changed**

- Line 3: "Allongez-la" → "Prolongez-la". One prolongs a period of time; "allonger" is for physical length.
- Line 6: "Aucune fin publiée. Séjour typique" → "Aucune heure de fin publiée. Durée habituelle". "Fin publiée" read as truncated; "séjour" means a stay of days, not two hours at an Event; "typique" in this sense is a calque of "typical".
- Line 7: "Séjour de %1$s" → "Durée : %1$s" (same term as line 6).
- Line 8: "gardent leur timbre, … marqués sautés" → "garderont leur timbre, … marqués comme sautés". One tense for both clauses; "marqué comme" is the required construction.
- Line 10: "créneau" → "plage horaire", "atteignables" → "accessibles", "journée" → "itinéraire". "Plage horaire" is the OQLF-preferred term and line 3 already says "plage"; "accessible" matches line 5 so "reachable" has one French term; "journée" is wrong for a Soirée Itinerary.

**Checked and kept**

- Typography: the Copy Lead's U+00A0 is already correct before `:`, inside `« »` and between number and unit (`2 h`, `4 h`, `%1$d min`). No kept key row in this plan contains `:`, `$` or `« »`, so no `keep` row needed a corrected form. The two current planning strings with a plain space before `:` (`district_replace_label`, `meal_block_note`) are deleted or replaced by this plan.
- `Pour cet itinéraire seulement`: natural as a heading under the app bar "Budget"; "seulement" at the end is the usual Québec order.
- `district_add_first` "Ajouter en 1re": the ordinal agrees with the implied noun "position" (f.), not with the District label, so it stays correct for a masculine "arrondissement" and for any City Pack. `1re` is the OQLF abbreviation.
- `district_max_body` "Lequel remplacer par %1$s?": "par" takes the borough name without contraction, so "par Le Plateau-Mont-Royal" and "par Le Sud-Ouest" are correct. "Lequel" agrees with "arrondissements".
- `district_choose_another` "Choisir un autre": "un autre" agrees with "arrondissement". Another City Pack with a feminine District label would need its own form.
- Times: `4 h` and `19 h 30` in sentences, `18:30` in chips and headers, as settled.
- Placeholders: every `%1$s`, `%1$d`, `%s`, `{nom}` and `{heure}` is in the position the Copy Lead drafted.

**Needs a human Québec French reader**

- `review_title` "Révision": correct, but "Récapitulatif" is the more common label for a summary-before-start step. The step also lets the user remove Stops, which favours "Révision". A reader should settle it.
- `meal_plan_yes` / `meal_plan_no`: the radio pair now mixes an infinitive ("Planifier mes repas") with a first-person sentence ("Je m’en occupe"). Radio labels are not buttons, so the first person is allowed, but a parallel pair such as "Planifier mes repas" / "Gérer mes repas moi-même" may read better. Line 3 quotes the chosen label, so change both together.
- Line 10 "replanifiez-les": "replanifier" is understood but rare; "reprogrammez-les" is the established verb for rescheduling. Left as is because the buttons await the product owner's decision on these actions.
- Line 8 "« {nom} » passera dans Passés": the echo passera/Passés is audible. "sera classé dans Passés" avoids it but would need agreement with the Itinerary name's gender. A reader should pick one.
- `review_planned_departure` "départ prévu %1$s": telegraphic by design on a Stop row. If a reader finds "départ prévu à 21:30" needed, the EN should follow.

**Data, not strings**

- `assets/citypacks/mtl/districts.json` names use a hyphen where the City's official names use an en dash between the two parts ("Côte-des-Neiges–Notre-Dame-de-Grâce", "Rivière-des-Prairies–Pointe-aux-Trembles", "L’Île-Bizard–Sainte-Geneviève"), and "L'Île-Bizard" has a straight apostrophe. These names appear on the map and in `district_max_body`.

## English Editor notes

Review of every `EN proposed` cell, the kept English strings and the English in `Maquette copy to tighten`, each read against the final French and its Kotlin screen. 7 strings changed; the other key rows stand.

**Changed**

- `district_pick_instructions`: "in visiting order" is not natural English. Now "in the order you’ll visit them", same meaning as the French.
- Line 3: "time window" to "time slot" for "plage horaire", so the term is the same across lines 3, 5 and 10. Comma added before the second action.
- Line 4: added "are" so the sentence is not a headline fragment.
- Line 6: "Typical stay" to "Typical duration", matching "Durée habituelle". "Stay" implied days.
- Line 7: "%1$s stay" to "Duration: %1$s", matching "Durée : %1$s" and line 6.
- Line 8: the semicolon is now a full stop, one idea per sentence.
- Line 10: "slot" to "time slot at {time}", "day" to "itinerary" (an Itinerary may be a night out), "will no longer be" to "won’t be ... anymore" for contraction consistency. "Reachable" matches "accessibles" and line 5.

**Recurring problems**

- Calques from French word order or terms ("visiting order", "stay", "time window").
- One French term rendered by two English terms across lines (window, slot).
- Semicolons and headline-style fragments with no verb.
- "day" used for an Itinerary that may be a night out.

**Meaning mismatches**

- None left. Tense only: French lines 8 and 10 use the future ("passera", "seront"); the English line 8 uses the present ("moves", "keep"). The meaning is the same in a confirmation dialog, so it stands.
- Line 10 action labels are not written. "Reschedule them or reorganize your itinerary" follows "replanifiez-les ou réorganisez votre itinéraire", and the buttons wait for product owner question 1.

**Checked and kept**

- Meals: Lunch, Dinner, Late bite. "Late bite" matches "Bouchée tardive".
- "Day out" and "Night out" are segment labels for "Journée" and "Soirée", not names for an Itinerary. They stay.
- Time format: "04:00" in `planning_error_latest_end` follows the current English string; the French "4 h" is its sentence form. Line 10 should receive `{time}` in the app's English time format.
- Canadian spelling: no word needed a change ("Theatre", "Cinema", "reorganize", "Exhibition"). Contractions are used throughout ("Couldn’t", "I’ll", "We’ll", "won’t").
- Placeholders unchanged and in the same order as the French.
- `values-en/strings.xml` was not edited.
