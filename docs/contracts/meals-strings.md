# Meals strings contract (Planning step 4)

Maquettes: `29:687` (plan Meals), `141:3340` (user handles food), `141:3485` (no Meal fits). Team Meals owns `meal_*` except `meal_picker_*` and `meal_tab_*` (team Sheets).

| Element | Key | FR | EN | Notes |
|---|---|---|---|---|
| App bar title | `meal_title` | Repas | Meals | Existing |
| Progress "Étape 4 sur 6" | `onboarding_step` | Étape %1$d sur %2$d | existing | Existing, shared. Args 4, 6 |
| Radio 1 | `meal_plan_yes` | Planifier mes repas | Plan my meals | Existing |
| Radio 2 | `meal_plan_no` | Je m’en occupe | I’ll handle it | Existing. Quoted in `meal_none_fit` |
| Itinerary range line | `meal_itinerary_range` | Itinéraire : %1$s – %2$s | Itinerary: %1$s – %2$s | New. Args: start and end as `11:00`, `23:30` |
| Meal chip, Lunch | `meal_lunch_at` | Dîner · %1$s | Lunch · %1$s | Existing. Arg `12:00` |
| Meal chip, Dinner | `meal_dinner_at` | Souper · %1$s | Dinner · %1$s | Existing |
| Meal chip, Late bite | `meal_late_bite_at` | Bouchée tardive · %1$s | Late bite · %1$s | Existing |
| Meal card title | `meal_lunch` / `meal_dinner` / `meal_late_bite` | Dîner / Souper / Bouchée tardive | Lunch / Dinner / Late bite | New. Do not reuse `budget_*` keys |
| Meal card block length | `meal_block_note` | Bloc de %1$d min | %1$d-min block | Existing. Arg 90, or 45 for Late bite |
| Meal card budget heading | `meal_budget_label` | Budget pour ce repas | Budget for this meal | New |
| Price chips | `budget_under_20`, `budget_under_50`, `budget_under_100`, `budget_100_plus` | < 20 $ ... 100 $ + | same keys | Existing, reused (same meaning) |
| Hour and minute rectangles | `time_hour_value`, `time_minute_value` (and `time_hour`, `time_minute`) | Heure : %1$s | existing | Existing accessibility strings, as in Date and time. No visible text |
| Continue button | `action_continue` | Continuer | Continue | Existing |
| User handles food: callout | `meal_none_reserved` | Aucun bloc repas réservé. | No meal blocks reserved. | New. Shown in `141:3340` |
| No Meal fits: callout | `meal_none_fit` | Aucun repas ne tient dans cette plage. Prolongez-la ou choisissez « Je m’en occupe ». | No meal fits this time slot. Extend it, or choose “I’ll handle it”. | New |
| No Meal fits: button | `meal_change_time` | Modifier l’horaire | Change time | New |
| Meal time error, outside the Itinerary range | `meal_error_outside_window` | Ce repas ne tient pas dans la plage. | This meal doesn’t fit the time slot. | New, approved by the product owner. Show on the Meal card when its time falls outside the range |
| Meal time error, overlaps another Meal | `meal_error_overlaps` | Ce repas chevauche un autre repas. | This meal overlaps another meal. | New, approved by the product owner |
| Meal time errors, summary | `meal_error_summary` | Corrigez l’heure d’un repas. | Fix a meal time. | New, from the UX review. One line when any Meal card shows an error |
| Nothing selected | `meal_none_selected` | Choisissez un repas ou « Je m’en occupe ». | Choose a meal or “I’ll handle it”. | New, from the UX review. Quoted label must match `meal_plan_no` |
| Accessibility label, start time | `meal_start_description` | %1$s, début | %1$s, start | New. Not visible. Arg is the time, such as `12:00` |

## Removed from the maquette copy

- Question above the radios (`meal_question`): the title and radios pose it. The key was already absent.
- "Quand voulez-vous manger?" (`meal_when`): the Meal chips answer it. The key was already absent.
- Block-length callout: `InfoCallout` with `meal_block_note` is replaced by the per-card line.

## Notes for the Feature Engineer

- Itinerary range uses `18:30`-style times in controls. Pass formatted strings.
- Maquette `141:3485` shows the range `14:00 – 17:00` above the callout, then the `meal_change_time` button.
- `meal_block_note` is used only in `@Preview` today (`InfoCallout.kt:37`).
