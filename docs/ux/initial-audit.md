# UX audit: initial (task A2)

Author: ux-designer. Date: 2026-11-21. Scope: the uncommitted flow (Login, Onboarding 1-4, Home, Planning 1-5, Underway, Settings, notifications, FR/EN), read from code only. Nothing was run on a device.

Tags: **Confirmed** = the code shows it. **Likely** = depends on runtime behaviour; check on a device. **Suggestion** = a usability improvement, not a bug.
Priority: **P0** = data loss or a dead end in the main path. **P1** = a real user would get stuck or confused. **P2** = polish.

Paths are relative to `app/src/main/java/com/example/hackapp/` unless they start with `app/`.

## 1. Journey as built

```
Login --(already signed in, or email sign-in)--> Home
Login --(Google, new account / Register)--> Onboarding 1 VisitorType -> 2 Profile -> 3 Budget+Audience -> 4 Interests -> Home (back stack cleared)
Home --Plan--> Planning 1 DateTime -> 2 DistrictPick (map + sheet) -> 3 MealChoice -> 4 StopSelection (per borough, meal sheet, departure sheet) -> 5 Review
Review --Start (asks POST_NOTIFICATIONS)--> Underway Leg (asks location) -> Arrival -> AtStop -> Leg (next) ... -> EndOfDay -> Home
Home --gear--> Settings (language, Profile = Onboarding 2-4, sign out)
Home bottom bar --> Itinerary / Passport (placeholders)
Notifications: arrival check (+30 min, Arrived / Skip) and leave reminder (-30 min); tap opens MainActivity
```

## 2. Issues

### P0

| # | Issue | Where | Tag |
|---|---|---|---|
| 1 | **Stamps are lost with the system Back button on End of day.** Stamps are only written in `endDay()`, called by the "End the day" button. System Back pops to Home without calling it. Home then hides the underway card because `finished` is true, so the page cannot be reopened (the `onResume` branch for `finished` cannot be reached). The next Start overwrites the progress and the stamps are gone. Fix idea: award stamps when the last Stop is finished, or handle Back on End of day the same way as the button. | `scaffold/screens/underway/ScreensUnderway.kt:212-216`, `domain/FlowViewModel.kt:155-165`, `scaffold/screens/home/ScreenHome.kt:80`, `scaffold/navigation/AppNavigation.kt:102,197` | Confirmed |

### P1

| # | Issue | Where | Tag |
|---|---|---|---|
| 2 | **Signed-in users skip unfinished onboarding.** Login sends any signed-in user to Home (`onSignedIn(false)`), and email sign-in always passes `false`. `profile.onboarded` is never checked. Someone who closes the app during onboarding never sees it again and plans with an empty profile. | `scaffold/screens/login/ScreenLogin.kt:61-65,169`, `scaffold/navigation/AppNavigation.kt:216-222` | Confirmed |
| 3 | **Back arrow on Onboarding step 1 leaves an empty back stack.** `finishSignIn` removes Login (`popUpTo(Login){inclusive}`), so VisitorType is the only entry and its back arrow calls `popBackStack()` on it. Expect a blank screen; the arrow should be hidden. | `scaffold/navigation/AppNavigation.kt:132,218-219` | Likely |
| 4 | **Editing the profile from Settings runs the onboarding tail.** Profile opens Onboarding step 2 with "2 of 4". Continue goes through Budget and Interests, and Finish clears the stack and lands on Home, not Settings. A one-field edit takes 3 pages. | `scaffold/navigation/AppNavigation.kt:121,134-146` | Confirmed |
| 5 | **Going back from Stop selection and continuing deletes every chosen Stop without warning.** MealChoice's Continue always calls `resetSelection()`. The top-bar Back on StopSelection also goes to MealChoice even on borough 2 or 3, because there is no "previous borough". | `scaffold/navigation/AppNavigation.kt:156-158`, `domain/FlowViewModel.kt:97-100`, `scaffold/screens/planning/ScreensPlanning.kt:285,288` | Confirmed |
| 6 | **Choosing an event gives no feedback and no undo.** Tapping a card adds the Stop at once. The card then disappears from the list (chosen ids are excluded). The page shows only "N stops", not which ones; the only way to see or undo a pick is the Review page. `EventOptionCard(selected=...)` exists but is not used. | `scaffold/screens/planning/ScreensPlanning.kt:303-305,320-331` | Confirmed |
| 7 | **Empty and error states give no reason and no way out.** Stop selection, the meal sheet and the Underway `NoItinerary` page all show "Nothing to show yet" with no body and no action, for example "Next borough" or "Change time window". DistrictPick shows a spinner forever if counting fails. `ErrorState` exists but no screen uses it. If every borough has 0 events, the map is all grey with no message. | `scaffold/screens/planning/ScreensPlanning.kt:183-184,293,338,378`, `scaffold/screens/underway/ScreensUnderway.kt:61-64`, `ui/components/StateViews.kt:57` | Confirmed |
| 8 | **Starting a new Itinerary silently replaces the one in progress.** Home's Plan button is always shown. Start overwrites the progress (losing unearned stamps, see #1). Alarms of the old trip are only replaced where the indexes overlap, so leftover alarms can fire for the old trip. | `scaffold/navigation/AppNavigation.kt:94-97`, `domain/FlowViewModel.kt:125-131`, `services/UnderwayNotifier.kt:47-55,58-65` | Confirmed (alarm leftovers are Likely) |
| 9 | **Tapping a notification does not open the trip.** The content intent starts MainActivity with no destination. On a cold start that means Login, then Home; the user has to find the card and tap Resume. The notification should open the current Underway page. | `services/UnderwayNotifier.kt:150-153` | Confirmed |
| 10 | **Notification actions do not update the open screen.** Arrived or Skip from the notification changes the store, but Arrival and AtStop do not react to `atStop`/`index` changes. After Skip, the open Arrival page shows the next Stop as "en route" without its Leg page. | `services/UnderwayNotifier.kt:130-141`, `scaffold/screens/underway/ScreensUnderway.kt:147-166` | Likely |
| 11 | **Picking a borough on the map has no accessible path.** Districts are drawn on a Canvas and handled only by tap position. TalkBack and switch users have no node to focus, and small boroughs are well under 48 dp at phone width. The event-count badges have no semantics either. Suggestion: an accessible list of boroughs with counts, below or in place of the map. | `ui/components/map/CityMap.kt:97-108`, `ui/components/map/DistrictMap.kt:52-61` | Confirmed |
| 12 | **The location prompt has no explanation and can repeat.** Leg launches `ACCESS_COARSE_LOCATION` in a `LaunchedEffect` as soon as the first Leg opens. It runs again on every visit while `fromLat` is null. If permission is denied, or granted with no last known fix, travel time shows "–" with no reason. | `scaffold/screens/underway/ScreensUnderway.kt:87-92,124` | Confirmed |
| 28 | **"Yes, plan a meal" is preselected but no meal time is.** `chooseMeal` runs only when Yes is tapped. If the user keeps the default and presses Continue, no meal prompt appears later (feature-engineer F1). | `domain/FlowViewModel.kt:54-56`, `scaffold/screens/planning/ScreensPlanning.kt:237` | Confirmed |
| 29 | **"Are you there?" keeps coming for Stops already confirmed.** Arrived in the app does not cancel that Stop's alarm (feature-engineer F12). | `domain/FlowViewModel.kt:141`, `services/UnderwayNotifier.kt:50-51` | Confirmed (F12) |
| 30 | **Sign-out keeps the previous user's profile and trip.** On a shared phone the next account sees the last person's name, trip and stamps (feature-engineer F25). | `scaffold/navigation/AppNavigation.kt:122-127` | Confirmed (F25) |
| 13 | **The 4th borough is "added" but nothing happens.** With 3 boroughs chosen, tapping another one opens the sheet with "Add as #4". `toggleDistrict` ignores it and the sheet closes. | `scaffold/screens/planning/ScreensPlanning.kt:199,217-218`, `domain/FlowViewModel.kt:85-91` | Confirmed |

### P2

| # | Issue | Where | Tag |
|---|---|---|---|
| 14 | Two notes end in ":" with nothing after them ("If not confirmed 30 min after the expected time:", "High-priority alert 30 min before:"). The Figma notification preview that followed them is missing. | `scaffold/screens/underway/ScreensUnderway.kt:164,200`; `res/values*/strings.xml` `arrival_reminder_note`, `at_stop_alert_note` | Confirmed |
| 15 | Notifications ignore the in-app language. The receiver uses the application context, while `AppLocale.wrap` only wraps the Activity, so notification text and buttons follow the system language. | `services/UnderwayNotifier.kt:113-128`, `services/AppLocale.kt:20`, `MainActivity.kt:23` | Confirmed |
| 16 | If notification permission is denied, the trip starts silently with no reminders and no message. "We'll ask you to allow notifications" shows even when permission is already granted or not needed (API < 33). | `scaffold/screens/planning/ScreensPlanning.kt:402-405,412` | Confirmed |
| 17 | System Back from AtStop goes back to Arrival for a Stop already marked arrived, where "Skip this stop" is still offered and would mark it skipped. | `scaffold/navigation/AppNavigation.kt:181,189,194` | Confirmed |
| 18 | "Finish here" is disabled until an event is chosen, with no hint why. A meal-only plan cannot be finished. | `scaffold/screens/planning/ScreensPlanning.kt:289` | Confirmed |
| 19 | Leg's primary button says only "Continue", but it means "I'm on my way". The ride-hail button shows only the app name ("Uber"). | `scaffold/screens/underway/ScreensUnderway.kt:106,109` | Suggestion |
| 20 | If the end time is earlier than the start time, the window silently becomes overnight. A Day out set to 10:00-09:00 is 23 h with no hint. | `domain/Itinerary.kt:64`, `scaffold/screens/planning/ScreensPlanning.kt:132-133` | Suggestion |
| 21 | Vague or truncated titles. StopSelection's title is "Event"/"Événement". AtStop uses the full event name as a one-line app-bar title, which is cut off. The Leg page shows "How to get there" twice (screen heading and inside `TransportChoiceCard`). | `scaffold/screens/planning/ScreensPlanning.kt:283`, `scaffold/screens/underway/ScreensUnderway.kt:130,191`, `ui/components/AppChrome.kt:55`, `ui/components/TransportChoiceCard.kt:46-50` | Confirmed |
| 22 | Touch target: "Retirer"/"Remove" on Review is a clickable `Text` (about 20 dp high, under 48 dp). | `ui/components/StopRow.kt:63-69` | Confirmed |
| 23 | Meaning carried only by colour, with no semantics. `ItineraryStrip` shows stop type by dot colour only. `HorizontalInfoCard` shows `selected` by colour and border only (Visitor card, option cards); `selected` is not exposed to accessibility services. | `ui/components/ItineraryStrip.kt:45-66`, `ui/components/HorizontalInfoCard.kt:53-55,88-89` | Confirmed |
| 24 | Font scaling. `StopRow` name and details, `LegTimes` and the app-bar title are `maxLines = 1`. Leg pins up to 3 buttons and Review pins a note plus a button, so at 200 % font on a small phone the scroll area gets very short. | `ui/components/StopRow.kt:60-61`, `ui/components/LegTimes.kt:43-48`, `scaffold/screens/underway/ScreensUnderway.kt:105-115` | Likely |
| 25 | The late warning on AtStop is computed once from `now()` and never refreshes while the page stays open. | `scaffold/screens/underway/ScreensUnderway.kt:183` | Confirmed |
| 26 | End of day says "N new stamps in your passport", but the Passport tab shows a bare "Passport" text. | `scaffold/screens/passport/ScreenPassport.kt:18-21`, `scaffold/screens/underway/ScreensUnderway.kt:236` | Confirmed (known placeholder) |
| 27 | The Google button gets no busy state while Credential Manager runs, so users can tap it twice. Email and password fields stay enabled during Google sign-in. | `scaffold/screens/login/ScreenLogin.kt:117-121`, `scaffold/screens/login/GoogleLoginButton.kt:44` | Suggestion |

| 34 | A borough that is already selected stays selected after a window change gives it 0 events (only reachable by going back to DateTime and forward again). StopSelection then shows an empty borough (feature-engineer, F3 follow-up). | `scaffold/screens/planning/ScreensPlanning.kt:190-193` | Confirmed (F3) |
| 31 | Home shows the raw ISO date ("2026-11-21") as the itinerary name. Use a localized date, for example "sam. 21 nov." / "Sat, Nov 21". | `scaffold/screens/home/ScreenHome.kt:83` | Confirmed |
| 32 | AtStop shows `NextStopCard` for the *current* Stop, so its "next stop" wording is wrong on this page (from visual-designer). | `scaffold/screens/underway/ScreensUnderway.kt:197` | Confirmed |
| 33 | Password fields have a clear (X) button but no show/hide toggle; on mobile, show/hide matters more than clear (from visual-designer). | `ui/components/HackappTextField.kt:62` | Suggestion |

**Decision for #8 (requested by feature-engineer, F13).** Recommendation: when a trip is in progress, tapping Plan on Home opens a confirm dialog. Text: "A trip is in progress. End it and keep its stamps?" Buttons: **End and plan new** (runs `endDay`, then the new draft) and **Cancel**. Blocking new plans outright is too strict, and silently replacing the trip is the bug. The team lead should confirm.

**F3 settled** with feature-engineer on code evidence. Unavailable boroughs cannot be added: `DistrictMap.kt:52` drops taps on them, the selected chips can only remove, and counts cannot go stale while the page is open. Only #13 (the silent "Add as #4") and #34 remain.

**#8 approved** by team-lead. Spec and FR/EN strings: `docs/ux/spec-replace-active-trip.md`.

**Ownership with visual-designer** (their doc: `docs/design/initial-audit.md`):
- This doc owns #21 (titles), #22 (Remove touch target), #24 (maxLines and font scale), #32 and #33.
- visual-designer owns #23 (colour-only meaning), the duplicate "How to get there" heading, BottomActions stacking, heading styles and the bottom-nav TalkBack double read.
- visual-designer already sent feature-engineer the fixes for the duplicate heading and the StopRow touch target (#22); count them as done once they land.

## 3. Checked and fine
- FR/EN coverage: the 10 keys missing from `values-en` are all `translatable="false"` (age ranges, language names, "Uber", format strings).
- The step bar has a content description; the back arrow and Settings gear have labels; `RadioOptionRow` uses a 48 dp control; M3 small buttons get the minimum interactive size from M3.
- The notification permission is requested in context (on Start), not at launch.
- Back from Review returns to selection with the Stops kept; Remove trims later Stops, as the note explains.

## 4. Handoffs
- feature-engineer: #1, #2, #3, #5, #9, #13 (small code changes; see the table).
- workflow-auditor: #1 (data loss), #8 (stale alarms), #10, #15, plus no `BOOT_COMPLETED` receiver, so reminders are lost after a reboot (`app/src/main/AndroidManifest.xml`).
- visual-designer: #21, #22, #23, #24 overlap visual work; tell me which you take.
