# Spec: confirm before replacing an active trip (#8 / F13)

Status: approved by team-lead on 2026-11-21, including the finished-but-not-ended case (plan task T3). Build: feature-engineer, Phase 4. Source: `docs/ux/initial-audit.md` #8.

## When it shows
- The user taps **Plan an itinerary** on Home (`AppNavigation.kt:94-97`, `onPlanItinerary`) while `vm.underway.value` is not null and `finished == false`.
- If no trip is active, or the trip is finished, the dialog does not show and planning starts directly, as today.
- Also show it for a trip that is finished but not ended (End of day left by system Back, audit #1), so its stamps are not lost. Rule: show the dialog whenever `vm.underway.value != null`.
  - Body for an unfinished trip: `replace_trip_body`.
  - Body for a finished trip: `replace_trip_body_finished`.

## Component
M3 `AlertDialog` (already used in `ui/components/DateTimeFields.kt:107`). Default M3 styling with theme tokens; no custom layout.

| Part | Content |
|---|---|
| Title | `replace_trip_title` |
| Body | `replace_trip_body`, or `replace_trip_body_finished` when `finished == true` |
| Confirm button (right, `TextButton`) | `replace_trip_confirm` |
| Dismiss button (left, `TextButton`) | `action_cancel` (existing string) |
| Icon | none |

Button order follows M3: dismiss first (left), confirm last (right). The confirm button is not destructive-red: stamps are kept, so nothing is lost.

## Behaviour
- **Cancel**, tap outside, or system Back: closes the dialog and stays on Home. Nothing changes. `onDismissRequest` = Cancel.
- **End and plan new**, in order:
  1. `endDay` behaviour: the confirmed Stops become stamps, the old trip's alarms are cancelled and its notifications removed, then the progress is cleared.
  2. When step 1 has finished, `vm.newDraft()`, then navigate to `PlanningDateTimeRoute`.
- While step 1 runs, disable both buttons so a double tap cannot start two drafts.
- Engineering pairing (feature-engineer): award stamps in the domain when the last Stop finishes, and make `endDay` safe to call more than once, so the dialog, the End of day button and Back can never double-award or lose stamps.
- Do not show a snackbar or toast afterwards. The Planning page opening is the feedback.

## Strings
French in `res/values/strings.xml` (default), English in `res/values-en/strings.xml`. The words follow the existing strings: itinéraire / itinerary, arrêt / stop, timbre / stamp, passeport / passport.

| Key | FR | EN |
|---|---|---|
| `replace_trip_title` | Un itinéraire est en cours | An itinerary is in progress |
| `replace_trip_body` | Pour en planifier un nouveau, terminez d’abord celui-ci. Vos arrêts confirmés deviennent des timbres dans votre passeport et les rappels de cet itinéraire sont annulés. | To plan a new one, end this one first. Your confirmed stops become stamps in your passport, and this itinerary’s reminders are cancelled. |
| `replace_trip_body_finished` | Votre dernier itinéraire n’a pas été clôturé. Il le sera maintenant : vos arrêts confirmés deviennent des timbres dans votre passeport. | Your last itinerary wasn’t closed. It will be closed now, and your confirmed stops become stamps in your passport. |
| `replace_trip_confirm` | Terminer et planifier | End and plan new |
| `action_cancel` | Annuler (exists) | Cancel (exists) |

XML escaping: write the apostrophes as the typographic ’ (as the existing strings do), or escape a straight apostrophe as `\'`.

## Accessibility
- `AlertDialog` gives a dialog role and focus trapping. The title is read first.
- Both buttons use M3 `TextButton`, which already meets the 48 dp minimum touch target.
- The FR confirm label ("Terminer et planifier", 21 characters) fits one line at 100 % font. At 200 % the M3 dialog stacks the buttons, which is acceptable.

## Acceptance
- With an active trip, tapping Plan shows the dialog. Cancel, tap outside and Back leave the trip and its notifications unchanged.
- Confirm: the stamps of the confirmed Stops appear in the stamps store, no alarm of the old trip fires afterwards, and Planning 1 opens with a fresh draft.
- With no active trip, Plan opens Planning 1 directly (no dialog).
- FR and EN text match the table.
