# A1: Technical implementation audit (initial)

Author: feature-engineer. Date: 2026-11-21. Scope: uncommitted flow on `feat-business` (domain/, services/, onboarding/planning/underway/settings screens, navigation, motion). Read-only review; no build or device run (workflow-auditor owns builds).

Legend: **C** = confirmed by reading the code, **S** = suspected (needs a device or test to prove). P0 = blocks the demo or loses data, P1 = visible defect or real risk, P2 = debt or polish. No P0 found.

## 1. Architecture as built

- **Single activity-scoped `FlowViewModel`** (`scaffold/navigation/AppNavigation.kt:54`) holds the whole planning draft as Compose `mutableStateOf` fields (`domain/FlowViewModel.kt:46-61`), exposes `ProfileStore` and `UnderwayStore` flows via `stateIn(Eagerly, null)` (`:38-43`), and owns underway actions. Screens receive `vm` directly and also reach into `vm.planner`, `vm.services`, `vm.profileStore`, `vm.notifier`. There is no per-screen UI state class; each screen loads its own data in `LaunchedEffect`/`produceState`.
- **`ItineraryPlanner`** (`domain/Planner.kt:45`) is the rules layer and is Android-free (good, unit-tested in `PlannerRulesTest`). It depends on `EventRepository`, a lazy `PlaceRepository` and `LegEstimator`.
- **Persistence**: `ProfileStore` (DataStore `profile`) and `UnderwayStore` (DataStore `trip`, JSON-encoded `UnderwayProgress` + stamps). Both built lazily in `HackappApplication` (`HackappApplication.kt:18-20`). The draft itinerary is not persisted.
- **Services**: `CityServices` container per City Pack (`services/CityServices.kt`), `UnderwayNotifier` + `UnderwayReceiver` (inexact `setAndAllowWhileIdle`), `AppLocale` (SharedPreferences + `attachBaseContext`).
- Boundaries are mostly respected (screens use repository interfaces through the planner). Main leaks: screens call `vm.profileStore.update` directly (onboarding) and `vm.services.legs/transit/rideHail` (underway), so the ViewModel is a service locator more than a state holder.

## 2. Findings

### Planning logic and state

| # | Pri | C/S | Finding | Where |
|---|---|---|---|---|
| F1 | P1 | C | **"Yes, plan a meal" is selected by default but no meal time is set.** `wantsMeal = true`, `meal = null`; `chooseMeal(options.first())` only runs when the person taps the Yes row. Pressing Continue directly gives no Meal prompt at all. | `domain/FlowViewModel.kt:54-57`, `scaffold/screens/planning/ScreensPlanning.kt:237` |
| F2 | P1 | C | **Meal can overlap an event.** Only events *starting* inside the Meal Block are held back; an event 11:00-13:00 can be picked, then lunch 12:00 is added (sorted in by `addStop`) with no overlap or reachability check. Same for a meal added after a later event was already chosen. | `domain/Planner.kt:87-88`, `domain/FlowViewModel.kt:102`, `ScreensPlanning.kt:354` |
| F3 | P1 | C | **With 3 districts selected, the preview still offers "Add as #4"**; `onAdd` silently does nothing (`toggleDistrict` ignores) and closes the sheet. *Corrected after ux-designer review:* unavailable districts (0 events, linked cities) cannot be previewed, `DistrictMap.kt:52` drops their taps. Remaining edge: a selected district whose count drops to 0 after the window changes stays selected. | `ScreensPlanning.kt:198-219`, `domain/FlowViewModel.kt:85-91` |
| F4 | P2 | C | Stale meal after changing the window: `meal` keeps a block from the old window (not in `options`, no radio shows selected) and is still used in StopSelection. | `ScreensPlanning.kt:228,248`, `FlowViewModel.kt:80-83` |
| F5 | P2 | C | No window validation: a Day out with end <= start silently becomes an overnight (or 24 h) window via `PlanWindow.endAt`. | `domain/Itinerary.kt:64`, `ScreensPlanning.kt:132-133` |
| F6 | P2 | C | Planned departure earlier than the event start becomes the *next day* (e.g. 13:00 for a 14:00 event -> 23 h stop). | `domain/Planner.kt:121`, `ScreensPlanning.kt:347` |
| F7 | P2 | C | `removeStop` recomputes `districtIndex` from the last stop's district; a meal stop can carry another borough (`place.borough`, anchor search spans boroughs) -> `indexOf` = -1 -> jumps back to district 1. | `FlowViewModel.kt:107`, `Planner.kt:160,179` |
| F8 | P2 | S | Double tap on an `EventOptionCard` can add the same event twice: the list is only refiltered after the `LaunchedEffect` re-runs. | `ScreensPlanning.kt:270-277,327-329` |
| F9 | P2 | C | `expectedArrival()` KDoc says "previous departure plus the Leg" but returns `stop.start`; unused. | `FlowViewModel.kt:172-173` |
| F10 | P2 | C | `FIRST_DATE`/`LAST_DATE` hard-coded to the snapshot (2026-11-21..12-06) and used as the default date; after that range every trip is in the past and no alarm is scheduled. Fine for the demo, should come from the snapshot. | `FlowViewModel.kt:183-184`, `UnderwayNotifier.kt:51,54` |
| F11 | P2 | C | Draft lives only in ViewModel memory: after process death Navigation restores e.g. StopSelection/Review with empty `districts`/`stops` (empty state, no crash). | `FlowViewModel.kt:46-61` |

### Underway, notifications, alarms

| # | Pri | C/S | Finding | Where |
|---|---|---|---|---|
| F12 | P1 | C | **Stale "Are you there?" notifications.** Alarms are scheduled once at start; confirming, skipping or leaving in the app never cancels them, and the receiver posts without checking `UnderwayProgress`. The person gets an arrival check for a stop already confirmed or skipped. | `UnderwayNotifier.kt:45-56,118-129`; `FlowViewModel.kt:141-152` |
| F13 | P1 | C | **Starting a new trip overwrites an active one.** `start()` saves new progress without `endDay` (old stamps lost) and `schedule()` cancels only indices of the *new* itinerary, so extra alarms of a longer old trip still fire with old titles. | `FlowViewModel.kt:125-131`, `UnderwayNotifier.kt:47,58-65` |
| F14 | P1 | C | **Non-atomic read-modify-write** in `UnderwayStore.update`: `current()` and `save()` are two separate DataStore transactions; the notification receiver (Dispatchers.Default) and UI coroutines can interleave and lose an update. Fix: do the change inside one `store.edit {}`. | `domain/UnderwayStore.kt:56-59`, `UnderwayNotifier.kt:134-140` |
| F15 | P1 | S | **Navigation runs before the async store write lands.** Skip / Leaving call `vm.finishStop()` (launches a coroutine) then navigate at once; the new Leg screen first composes with the old progress. With index 0 and no location, `LaunchedEffect(p.index == 0 && p.fromLat == null)` can relaunch the location request. Right after Start, Leg shows `NoItinerary` until the save lands (empty-state flash). | `ScreensUnderway.kt:80,90-92,155-158,185-188`; `FlowViewModel.kt:125-131` |
| F16 | P2 | C | Location permission is re-requested every time Leg for stop 1 is opened while `fromLat` is null (after a denial, or when `getLastKnownLocation` returns null). No rationale. | `ScreensUnderway.kt:87-92,69-74` |
| F17 | P2 | C | Alarms do not survive reboot (no `BOOT_COMPLETED` receiver) and are not scheduled if POST_NOTIFICATIONS is granted after Start. | `AndroidManifest.xml:43-45`, `FlowViewModel.kt:129` |
| F18 | P2 | C | `goAsync()` coroutine has no `try/finally`; an exception skips `pending.finish()`. | `UnderwayNotifier.kt:132-140` |
| F19 | P2 | C | Notification small icon is the adaptive launcher mipmap (renders as a grey/white blob). Needs a monochrome vector. | `UnderwayNotifier.kt:155` |
| F20 | P2 | C | `cancel()` calls `NotificationManagerCompat.cancelAll()`, also removing unrelated app notifications. | `UnderwayNotifier.kt:64` |
| F21 | P2 | C | UI does not react to a notification action: tapping "Arrived" in the shade sets `atStop` but an open Arrival screen stays. Leg uses TRANSIT estimate, AtStop uses WALK (inconsistent leave-by). `late` is computed once per composition, never ticks. | `ScreensUnderway.kt:147-165,176-183` |
| F22 | P2 | C | System Back from EndOfDay returns to AtStop with a finished progress -> `NoItinerary`. EndOfDay has no back handling. | `AppNavigation.kt:196-198`, `ScreensUnderway.kt:207-216` |

### Navigation, auth, data on the device

| # | Pri | C/S | Finding | Where |
|---|---|---|---|---|
| F23 | P1 | C | **Onboarding gate uses Firebase `isNewUser`, never `profile.onboarded`.** Auto-login always goes Home; a user who quit mid-onboarding, or signs in on a new device/after reinstall, never sees onboarding and plans with an empty profile. | `AppNavigation.kt:216-222`, `ScreenLogin.kt:61-64`; `onboarded` is only written (`ScreensOnboarding.kt:209,221`) |
| F24 | P1 | C | **Back on Onboarding step 1 empties the back stack** (blank screen): `finishSignIn` pops Login inclusive, so `OnboardingVisitorTypeRoute` is the root and its `onBack = popBackStack()`. | `AppNavigation.kt:132,218-221` |
| F25 | P1 | C | **Sign-out keeps the previous user's data**: profile, active trip, stamps and alarms stay in DataStore; the next account on the device inherits them. | `GoogleLogoffButton.kt:25-35`, `AppNavigation.kt:122-127` |
| F26 | P2 | C | Legacy `PlanningScreenDestination` log-off navigates to Login without `popUpTo` (Back returns signed-out user to the app). Route looks unreachable; delete or fix. | `AppNavigation.kt:80-84` |
| F27 | P2 | C | Onboarding fields use `remember`, not `rememberSaveable`: rotation reloads from the stored profile and drops typed values. Continue buttons can be double-tapped (two navigations). | `ScreensOnboarding.kt:104-107,77,176` |

### Threading, performance, locale

| # | Pri | C/S | Finding | Where |
|---|---|---|---|---|
| F28 | P1 | C | **850 KB `events.json` is decoded on the main thread** on first use: only the file read is on `Dispatchers.IO`; `decodeFromString` + parse/sort run on the caller (a `LaunchedEffect`, i.e. Main) in DistrictPick. | `data/repository/EventRepository.kt:64-77,97-103` |
| F29 | P2 | C | `districtCenter` loads all 5,694 places (`allPlaces()`), maps rows to `Place` on Main, every time the meal sheet opens without an anchor. Room result mapping in `search` also runs on Main. | `domain/Planner.kt:168-171`, `PlaceRepository.kt:76-82,85` |
| F30 | P2 | C | `rememberDistrictMap()` re-reads and re-parses `districts.json` on every screen that uses it (Profile, DistrictPick, StopSelection, Review); no cache. | `citypack/CityPack.kt:91-101` |
| F31 | P2 | C | `ItineraryRouteMap` runs an infinite 4.2 s transition that redraws the whole map Canvas every frame while Review is visible (battery). | `ui/components/map/ItineraryRouteMap.kt:79-80,100-104` |
| F32 | P2 | C | Locale applies only to `MainActivity`; notifications and the channel name are built from the application/receiver context and use the system language. `Locale.setDefault` is a global side effect. No `locales_config` / per-app language on API 33+. | `services/AppLocale.kt:20-26`, `UnderwayNotifier.kt:104,120-127` |
| F33 | P2 | C | No error handling around data loads in `LaunchedEffect`/`produceState` (Room open, JSON parse): an exception crashes the app instead of showing the error state. | `ScreensPlanning.kt:107,157-160,270-277,369-371`; `ScreensOnboarding.kt:62,197` |
| F34 | P2 | C | `Stop.type` is a serialized `StopType` enum inside the persisted progress; renaming an enum entry makes `decodeFromString` fail and `runCatching` silently drops the active trip. | `domain/Itinerary.kt:33`, `UnderwayStore.kt:29` |

## 3. Technical debt to fix before more features (suggested order)

1. Make `UnderwayStore.update` atomic (F14) and have screens navigate from the updated progress, not before it lands (F15).
2. Re-sync alarms with progress: cancel/reschedule on every status change, check progress in the receiver, cancel by a stored id range, block or confirm starting over an active trip (F12, F13).
3. Route by `profile.onboarded` after sign-in; fix the root Back (F23, F24). Clear stores and alarms on sign-out (F25).
4. Move event snapshot parsing and place mapping to `Dispatchers.Default` (F28, F29); cache the district map (F30).
5. Planning rules: default meal time, overlap check for meals, unavailable districts not addable, window and departure validation (F1-F6).
6. Split `FlowViewModel` (planning draft vs underway) and stop exposing stores/services to screens, so state can be saved in `SavedStateHandle` (F11).

## 4. Cross-audit items (from ux-designer and visual-designer, queued; not implemented in A1)

Engineering view of teammates' findings. Implementation waits for the Phase 2 plan (team-lead).

### From ux-designer (`docs/ux/initial-audit.md`)
| Ref | Pri | Engineering note |
|---|---|---|
| UX#1 | raise F22 to P0 | Agreed. Stamps are only awarded by the End-of-day button (`ScreensUnderway.kt:215`). System Back skips it, Home hides a finished trip, and the next Start overwrites the progress (F13), so the stamps are lost. Fix in the domain: award stamps and cancel alarms when the last Stop finishes (`finishCurrent` reaches `finished`), so the End-of-day page only displays them. |
| UX#2 | P1 | Same as F23 (also email sign-in, `ScreenLogin.kt:169`). |
| UX#3 | P1 | Same as F24. |
| UX#5 | P1 | New, confirmed: MealChoice Continue always calls `vm.resetSelection()` (`AppNavigation.kt:156-158`), so Back from StopSelection then Continue wipes chosen stops. Reset only when districts/window/meal actually changed (keep a fingerprint of the inputs). |
| UX#9 | P1 | Agreed. The content intent opens `MainActivity` at Login -> Home (`UnderwayNotifier.kt:150`). Pass an extra and route to the Underway page derived from progress (same `when` as Home `onResume`). |
| UX#13 | P1 | Same as F3 (full selection). |
| UX#4 | P2 | Settings > Profile reuses onboarding steps 2-4 and ends at Home with the stack cleared (`AppNavigation.kt:121,134-146`). Needs an "edit mode" flag so the last step pops back to Settings. |

### From visual-designer (`docs/design/initial-audit.md`)
| Ref | Pri | Engineering note |
|---|---|---|
| V-P0 | P0 | Leg text unreadable: `LegHeader`/`LegTimes` use onHero/onHeroCard colours but sit on the light surface (`ScreensUnderway.kt:117`). Give `LegHeader` its own heroCard background (corner 32 per team-lead's rule). |
| V1 | P1 | Duplicate "How to get there" heading: remove `ScreensUnderway.kt:130` (`TransportChoiceCard.kt:44` shows it). |
| V2 | P1 | StepProgress track `secondaryContainer` -> `surfaceContainerHighest` (`AppChrome.kt:100`); team-lead keeps the token in `Color.kt`, fixes per component. |
| V3 | P1 | `trailing = null` for `HorizontalInfoCard` in `StopOptionCards.kt:73,92` (empty 80 dp block). |
| V4 | P1 | `DistrictPreviewSheet.kt:53-57`: drop clip + background inside the ModalBottomSheet. |
| V5 | P1 | `HackappButtons.kt`: one label style (labelLarge, pending ux-designer) for Primary/Outline/Text; `pressScale` on `HackappTextActionButton`. |
| V6 | P1 | `GoogleLogoffButton.kt:24`: use `HackappOutlineButton`. |
| V7 | P1 | `TransportChoiceCard.kt:40`: `surfaceContainerLowest` -> `surfaceContainer`. |
| V8 | P1 | `StopRow.kt:65`: "Retirer" needs a 48 dp target (`HackappSmallTextButton` or `minimumInteractiveComponentSize()`). |
| V9 | P1 | `DistrictMap.kt:46-48,67`: Unavailable = `surfaceContainerLow`, no badge (taps are already ignored, `DistrictMap.kt:52`). |
| V10 | P2 | F19: 24 dp monochrome vector (simplified Star), `setColor(primary)`. F31: traveller loops at most 3 times; static route when ANIMATOR_DURATION_SCALE == 0; same rule for PlanItineraryCard shimmer/orbs and Login star pulse. |
| V11 | P1 | F15: show LoadingState (never the empty state) until progress loads; fix the stale-stop flash. |
| V12 | P2 | Home itinerary name: localized medium date + first district (e.g. "Sat, Dec 5 · Ville-Marie"), replacing the raw ISO date. |
| V13 | P2 | `entrance()`: run once per back-stack entry (key on entry id), not on each new Leg instance. |
| UX-F13 | P1 | ux-designer proposal (pending team-lead): Plan on Home with a trip in progress opens a confirm dialog "End and plan new" (runs `endDay` then `newDraft`) / "Cancel". Engineering: fine; combine with domain-side stamp award (UX#1) so `endDay` is idempotent. |
