# Underway strings contract

Maps each Underway state and notification to string keys. Owner: Copy Editor. French is `values/`, English is `values-en/`.

Time arguments: sentences and notification titles use `19 h 15` in French and `19:15` in English; value lines and labels use `19:15`. The caller formats the time. English times stay 24-hour.

## Existing keys reused

| Key | FR | EN | Used for |
|---|---|---|---|
| `action_skip_stop` | Sauter cet arrêt | Skip this stop | Leg, Arrival, late warning, notification action |
| `action_skip_meal` | Sauter ce repas | Skip this meal | Same, for a Meal |
| `action_go_anyway` | Y aller quand même | Go anyway | Late warning |
| `late_warning` | Vous risquez d’être en retard : %1$s | You may be late: %1$s | Late warning at a Stop (shared key) |
| `action_arrived` | Je suis sur place | I’m here | Arrival button, arrival notification action |
| `action_leaving` | Je pars | I’m leaving | At the Stop, Leave By notification action |
| `action_cancel` | Annuler | Cancel | Dismiss button of both dialogs |
| `stop_status_skipped` | Sauté | Skipped | End of day row (home-owned key) |
| `end_of_day_finish` | Terminer l’itinéraire | End itinerary | Last Stop button, end-early confirm button |
| `end_of_day_done` | Retour à l’accueil | Back to Home | End of day |
| `end_of_day_no_stamp` | Aucun timbre | No stamp | Stamp result, skipped Stop |
| `end_of_day_already_in_passport` | Déjà dans votre passeport | Already in your passport | Stamp result, repeat Stop |
| `end_of_day_new_stamps_section` | Nouveaux timbres | New stamps | End of day section heading |
| `notification_channel_underway` | Itinéraire en cours | Underway itinerary | Ongoing channel |

## New keys

| Key | FR | EN | State |
|---|---|---|---|
| `underway_skip_title` | Sauter %1$s? | Skip %1$s? | Skip confirmation title (`%1$s` = Stop name) |
| `underway_skip_confirm` | Sauter | Skip | Skip confirmation button |
| `underway_end_early_title` | Terminer l’itinéraire? | End itinerary? | End-early dialog title |
| `underway_end_early_body` | Les arrêts restants seront marqués comme sautés. | Remaining stops will be marked as skipped. | End-early dialog body |
| `end_of_day_status_visited` | Visité | Visited | Result label, Stop attended with a Stamp or not |
| `end_of_day_too_brief` | Trop bref pour un timbre | Too brief for a stamp | Result label, visited under 20% |
| `end_of_day_stamp_new` | Nouveau timbre | New stamp | Stamp result, Stamp earned |
| `arrival_change_transport` | Changer de transport | Change transport | Arrival action, returns to the Leg |
| `underway_notifications_off` | Notifications désactivées. Vous manquerez vos rappels. | Notifications are off. You will miss your reminders. | Banner, once per Itinerary |
| `underway_notifications_open_settings` | Ouvrir les paramètres | Open settings | Banner action |
| `underway_banner_dismiss` | Fermer | Dismiss | Banner close icon description |
| `underway_exact_alarm_rationale` | Autorisez les alarmes précises pour recevoir vos rappels à l’heure. | Allow exact alarms so reminders arrive on time. | Exact-alarm request at Start |
| `underway_exact_alarm_allow` | Autoriser | Allow | Exact-alarm request, accept |
| `underway_exact_alarm_later` | Pas maintenant | Not now | Exact-alarm request, decline (falls back to normal alerts) |
| `notification_channel_arrival` | Rappels d’arrivée | Arrival reminders | Channel name |
| `notification_channel_leave` | Heures de départ | Leave by alerts | Channel name |
| `notification_channel_late` | Retards possibles | Possible delays | Channel name |
| `notification_late_title` | Vous risquez d’être en retard | You may be late | Late alert title |
| `notification_late_body` | %1$s commence à %2$s | %1$s starts at %2$s | Late alert body (Stop name, start time) |

## States

| State | Keys |
|---|---|
| Leg, with Leave By | `leg_next_stop_label`, `at_stop_leave_by`, `leg_depart`, `leg_travel`, `leg_arrive`, `leg_now`, `leg_fare_required`; skip: `action_skip_stop` |
| Arrival (after handoff) | `arrival_en_route`, `arrival_expected`, `action_arrived`, `arrival_change_transport`, `action_skip_stop` |
| Skip confirmation | `underway_skip_title`, `underway_skip_confirm`, `action_cancel` |
| At the Stop | `at_stop_leave_by`, `at_stop_next`, `action_leaving` (last Stop: `end_of_day_finish`) |
| Late warning | `late_warning`, `action_go_anyway`, `action_skip_stop` |
| End-early dialog | `underway_end_early_title`, `underway_end_early_body`, `end_of_day_finish`, `action_cancel` |
| Notifications-off banner | `underway_notifications_off`, `underway_notifications_open_settings`, `underway_banner_dismiss` |
| Exact-alarm request | `underway_exact_alarm_rationale`, `underway_exact_alarm_allow`, `underway_exact_alarm_later` |
| End of day | `end_of_day_title`; per Stop: `end_of_day_status_visited` or `stop_status_skipped`, then `end_of_day_new_stamps_section` / `end_of_day_stamp_new`, `end_of_day_already_in_passport`, `end_of_day_too_brief` or `end_of_day_no_stamp`; `end_of_day_done` |

Stamp result per Stop: new Stamp = `end_of_day_stamp_new`; repeat = `end_of_day_already_in_passport`; visited under 20% = `end_of_day_too_brief`; skipped = `end_of_day_no_stamp` (or no line).

## Notifications

Each must read on a lock screen with no app context. Android shows the app name above.

| Notification | Channel | Title | Body | Actions |
|---|---|---|---|---|
| Arrival reminder | `notification_channel_arrival` | `notification_arrival_title`: Êtes-vous sur place? / Have you arrived? | `notification_arrival_body`: Stop name · time | `action_arrived`, `action_skip_stop` |
| Leave By alert | `notification_channel_leave` | `notification_leave_title`: Partez avant 19 h 15 / Leave by 19:15 | `notification_leave_body`: Prochain arrêt : name / Next stop: name | `action_leaving`, `action_skip_stop` |
| Late alert | `notification_channel_late` | `notification_late_title` | `notification_late_body` | `action_skip_stop` |

## Overlaps and notes

- `end_itinerary_warning` (existing, one string) overlaps `underway_end_early_title` + `underway_end_early_body`. Use the two new keys in the dialog and delete `end_itinerary_warning` if the other branch has no caller.
- `late_warning`, `action_go_anyway`, `action_arrived` are shared keys; not changed.
- The arrival notification body is `name · time` with no label, so the time's meaning (planned start) is only implied. Kept because `notification_arrival_body` is approved; flag for review.
- `notification_preview_header` is the in-app mock only.
