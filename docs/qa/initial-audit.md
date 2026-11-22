# A4: Workflow and quality audit (initial)

Auditor: workflow-auditor. Date: 2026-11-21. Branch `feat-business`, 28 uncommitted paths (18 modified, 10 untracked) on top of `d780b18`.
Scope: build/test/lint, code review of the uncommitted flow (domain/, services/UnderwayNotifier, AppLocale, screens, manifest), team workflow.

Overall label: **CHANGES REQUESTED** (build and tests green; lint gate fails; several confirmed state/alarm defects).

## 1. Verification results (all run by workflow-auditor, sequentially)

| Command | Result | Label |
|---|---|---|
| `.\gradlew.bat assembleDebug` | `BUILD SUCCESSFUL in 17s` (38 tasks, 7 executed) | APPROVED |
| `.\gradlew.bat testDebugUnitTest --rerun` | `BUILD SUCCESSFUL in 7s`; `> Task :app:testDebugUnitTest` executed fresh. 22 tests, 0 failures: ExampleUnitTest 1, MontrealEventSnapshotTest 2, SnapshotEventRepositoryTest 5, PlannerRulesTest 10, CityServicesLogicTest 4 | APPROVED |
| `.\gradlew.bat lintDebug` | `BUILD FAILED in 1m 13s`: `Lint found 3 errors, 30 warnings and 1 hint` | CHANGES REQUESTED |
| `adb devices` | `emulator-5554 device` (Medium_Phone AVD, API 37) | |
| `.\gradlew.bat connectedDebugAndroidTest` | `BUILD SUCCESSFUL in 49s`. 14 tests, 0 failures: AppNavigationTest 11, ExampleInstrumentedTest 1, PlacesDatabaseTest 2 | APPROVED (scope limited, see below) |

Note: the first plain `testDebugUnitTest` run reported `UP-TO-DATE` (cached results); the `--rerun` run above is the one that counts.

Lint errors (all `LocalContextConfigurationRead`, new code):
- `scaffold/screens/settings/ScreenSettings.kt:26`
- `scaffold/screens/underway/ScreensUnderway.kt:100`
- `scaffold/screens/underway/ScreensUnderway.kt:211`
Fix: use `LocalConfiguration.current` (or `LocalLocale`) instead of `LocalContext.current.resources.configuration`. Notable warnings: `AuthHandler.kt:42` CredentialManagerMisuse (no `NoCredentialException` handling), `strings.xml:49,192` PluralsCandidate, 23 unused resources (template colours, unused cuisine/action strings).

What is NOT verified: the end-to-end flow on a device (login needs real credentials), notification delivery and the Arrived/Skip buttons, alarms across Doze/reboot, location permission flow. AppNavigationTest opens each page directly, so it does not test real navigation sequences.

## 2. Code-quality findings

Classification: CONFIRMED = evidence in code (path reasoning given); SUSPECTED = likely, not reproduced.

| # | Pri | Class | Finding | Evidence | Recommendation |
|---|---|---|---|---|---|
| Q1 | High | CONFIRMED | `UnderwayStore.update` is a non-atomic read-modify-write across two DataStore transactions: `current()` then `save()`. The notification receiver (`UnderwayReceiver`, Dispatchers.Default) and UI actions (`FlowViewModel.arrived/finishStop/...`) can interleave and lose an update (e.g. Skip from the notification while the user taps Continue). | `domain/UnderwayStore.kt` `update()`; callers `services/UnderwayNotifier.kt` (ARRIVED/SKIP branch), `domain/FlowViewModel.kt` underway section | Do the transform inside a single `store.edit { }` (decode, change, encode). |
| Q2 | High | CONFIRMED | Stale alarms. `schedule(it)` calls `cancel(it)` with the *new* itinerary, so only the new trip's indexes are cancelled. If a previous trip had more stops, its higher-index alarms still fire with the old stop's name. Arrived/Skip only checks `p.index == index`, so an old notification can advance the new trip. (Also reported by ux-designer; verified independently.) | `services/UnderwayNotifier.kt` `schedule()` -> `cancel(itinerary)`; request codes `index*10+1/2` | Cancel against the stored previous progress before saving the new one, or cancel a fixed range; put a trip id in the intent and check it in the receiver. |
| Q3 | High | CONFIRMED | Stamps lost when a new trip starts without End of Day. `start()` overwrites `PROGRESS` without calling `addStamps`; Back from Leg returns Home with progress kept, and "Plan" -> Start replaces it silently. | `domain/FlowViewModel.kt` `start()` vs `endDay()`; `AppNavigation.kt` UnderwayLegRoute `onBack` | In `start()`, if a progress exists, bank its confirmed stamps and cancel its alarms first (or ask the user). |
| Q4 | Med | CONFIRMED | Onboarding gating uses Firebase `isNewUser`, not `UserProfile.onboarded`. `onboarded` is written but never read. A user who abandons onboarding, or reinstalls / signs in on a new device, lands on Home with an empty profile. | `AppNavigation.kt:216-222` `finishSignIn`; `onboarded` only written in `ScreensOnboarding.kt:209,221` | Route on `profile.onboarded` after sign-in. |
| Q5 | Med | CONFIRMED | Notifications ignore the in-app language: the receiver uses the un-wrapped receiver/app context; `AppLocale.wrap` is only applied in `MainActivity.attachBaseContext`. (Also reported by ux-designer.) | `services/AppLocale.kt`, `MainActivity.kt`, `UnderwayReceiver.post()` | Wrap the context in the receiver: `AppLocale.wrap(context).getString(...)`. |
| Q6 | Med | CONFIRMED | No reschedule after reboot (no `BOOT_COMPLETED` receiver) and no reschedule when notification permission is granted later (`start()` schedules only if `hasPermission()` at that moment). Alarms are also lost on app update / force-stop. | Manifest has no boot receiver; `FlowViewModel.start()` | Reschedule from stored progress on boot and when the app opens with an active trip. |
| Q7 | Med | SUSPECTED | `start()` launches the save asynchronously and the caller navigates immediately (`vm.start(); onStarted()`), so the Leg screen can briefly read the *previous* progress or null (NoItinerary flash). | `ScreensPlanning.kt:402-418`, `FlowViewModel.start()` | Make `start` suspend or navigate after the save completes. |
| Q8 | Med | SUSPECTED | When Arrived/Skip is used from the notification, the Arrival/AtStop screens re-render against the new `p.current` without navigating, so the screen can show the next stop under the wrong page. (ux-designer item, plausible from code; not driven on device.) | `ScreensUnderway.kt:147-195` observe `vm.underway` but only Leg reacts to `finished` | Derive the route from progress state (atStop / index) with a LaunchedEffect. |
| Q9 | Low | CONFIRMED | `UnderwayReceiver` ARRIVED/SKIP: `pending.finish()` is not in `finally`; an exception in `update` leaves the broadcast unfinished. Also a new unscoped `CoroutineScope` per broadcast; `HackappApplication.applicationScope` exists. | `UnderwayNotifier.kt` receiver branch | `try { ... } finally { pending.finish() }`. |
| Q10 | Low | CONFIRMED | `cancel()` calls `NotificationManagerCompat.cancelAll()`, removing every app notification, not just trip ones. | `UnderwayNotifier.cancel()` | Cancel the specific ids. |
| Q11 | Low | CONFIRMED | `ACCESS_FINE_LOCATION` declared but only COARSE is requested/needed. Location prompt re-fires every time Leg 1 is opened when denied or `lastKnown` is null. | Manifest; `ScreensUnderway.kt:87-92` | Drop FINE; remember a denial. |
| Q12 | Low | CONFIRMED | `expectedArrival()` KDoc says "previous departure plus the Leg", code returns `stop.start`. `countsByDistrict` uses `districtId!!` (safe only while the repository guarantees non-null when filtering by district). `FIRST_DATE`/`LAST_DATE` hard-coded to the snapshot (2026-11-21..12-06). | `FlowViewModel.kt`, `Planner.kt` | Fix doc or code; replace `!!` with `mapNotNull`. |
| Q13 | Low | CONFIRMED | Pre-existing, old `PlanningScreenDestination` route only (Settings sign-out is correct): `ScreenPlanning` log-off navigates to Login without `popUpTo`, so Back returns to a signed-in screen. | `AppNavigation.kt:82` | `popUpTo(0)`. |

Security:
- PendingIntents: all explicit and `FLAG_IMMUTABLE`; `UnderwayReceiver` is `exported="false"`. **APPROVED.**
- `app/google-services.json` is tracked (since `8b899e8`). Firebase Android keys are not secret by design, but the key must be restricted (Android app + SHA-1) in Google Cloud and Firebase App Check considered. Still open item 10. **NOT VERIFIED** (key restrictions not visible from the repo).
- `.claude/settings.local.json` is untracked and contains a broad `Bash(curl *)` allow. It must not be committed (CLAUDE.md rule on local settings); consider adding `.claude/settings.local.json` to `.gitignore`.
- No secrets found in the new Kotlin code; Google Maps key is env-only.

Test coverage of domain/:
- Covered (PlannerRulesTest, 10 tests): type mapping, eligibility (status, age, children-only), ranking, time conversion, meal block, night window, meal times, unpublished end.
- Not covered: `ItineraryPlanner.selection` (reachability, meal-block hold-back, grouping), `mealOptions` (bars after 17:00 / adults only, district filter), `finishCurrent`/`arrived`/`leaveAndSkipNext`, `UnderwayProgress` serialization round-trip, `FlowViewModel.removeStop/toggleDistrict/newStamps`, `ProfileStore` mapping. These are pure or fake-able (repositories are interfaces). Recommend adding them before Phase 2 changes touch this code.

## 3. Workflow review

| # | Class | Concern | Recommendation |
|---|---|---|---|
| W1 | CONFIRMED | Review stage skipped: A5 is marked `completed` and S1/S2 started "depends on A5" while the A5 reviewer is workflow-auditor and no review was requested or given (I reviewed only the kotlin-lsp candidate: NOT VERIFIED, defer). | Add an explicit "in review" state; a task is `completed` only after its reviewer's label. |
| W2 | CONFIRMED | A2 and A3 are reviewed by team-lead, who also authored the plan and owns architecture; A4 (this audit, which reviews team-lead's process) is also reviewed by team-lead. No independent check of the lead. | Accept: one reviewer per task is enough for audits, but route disagreements about team-lead decisions to the user. |
| W3 | CONFIRMED | No native task list; the markdown board relies on team-lead updating it from messages, so states drift (see W1). | Each owner sends a one-line state change; team-lead updates within the same turn. Keep the table, it works. |
| W4 | CONFIRMED | Gradle restricted to workflow-auditor during audits is good, but feature-engineer's agent definition says it runs Gradle; in Phase 2 two agents may run Gradle concurrently. | State in the plan who may run Gradle per phase; feature-engineer builds, auditor re-verifies afterwards. |
| W5 | CONFIRMED | No owner for test files in Phase 2 and no "tests required" acceptance criterion. | Add to every implementation task: unit test for changed domain logic, `testDebugUnitTest` + `lintDebug` green. |
| W6 | CONFIRMED | Lint is not a gate today (it fails on new code). | Fix the 3 errors, then make `lintDebug` part of the definition of done (or add a baseline for old warnings only). |
| W7 | CONFIRMED | Repo hygiene: about 1,300 lines of new feature code uncommitted; one large change will be hard to review or revert. `.idea/deploymentTargetSelector.xml` is a tracked IDE file with a local-only diff. | Commit in slices (domain + tests, services, screens, navigation) once Q1-Q3 are fixed; revert or untrack the `.idea` selector file. User commits. |
| W8 | CONFIRMED | CLAUDE.md drift: section 1 still says start = Login -> PlanningScreenDestination (now Login -> Onboarding/Home via `finishSignIn`); section 2 repo map lacks `citypack/`, `data/remote`, `data/Event.kt`, `services/`, `viewmodel/`, `widgets/`, `docs/adr/`, `domain/`; work log still says PlacesScreen "now the start destination". | team-lead updates sections 1-2. |

## 4. Third-party skill review
- kotlin-lsp plugin (anthropics/claude-plugins-official): read all files (plugin.json, LICENSE Apache-2.0, README). Only registers `kotlin-lsp --stdio`; no scripts or hooks. Risk lies in the external JetBrains binary (pre-release, no Windows install doc, imports and runs the Gradle build). **NOT VERIFIED**; recommendation: defer.

## 5. Independent check of feature-engineer's A1 defects (docs/engineering/initial-audit.md)
Checked by reading the code. None of these was driven on a device.

| A1 id | Result | Note |
|---|---|---|
| F24 | CONFIRMED (code) | `finishSignIn` pops Login inclusive (`AppNavigation.kt:218-221`), so Onboarding step 1 is the root, and its `onBack = popBackStack()` (`:132`) leaves an empty back stack. Needs a device repro |
| F1 | CONFIRMED | `wantsMeal = true` but `meal = null` by default; only tapping Yes picks the first meal time (`ScreensPlanning.kt:237`) |
| F12 | CONFIRMED | The receiver posts ARRIVAL_CHECK without reading progress, so a stop already confirmed or skipped still gets "Are you there?" |
| F13 / F14 / F23 | CONFIRMED | Same as Q2+Q3 / Q1 / Q4 |
| F25 | CONFIRMED | Both sign-out paths (Settings `ScreenSettings.kt:47` and the old PlanningScreen) use `GoogleLogoffButton.kt:26`, which signs out of Firebase only; local profile, trip, stamps and alarms stay. (Corrected: Settings does have a sign-out, and its `onLogOff` at `AppNavigation.kt:122-127` pops the whole graph correctly.) |
| F28 | CONFIRMED (code) | The snapshot is read on IO, but `parse()`, the filtering and the sorting run in the caller's context (LaunchedEffect at `ScreensPlanning.kt:157`, Main). Not measured |
| F2 | CONFIRMED | The meal-block hold-back only tests the event's start, so an event that starts before the block and runs into it is kept |
| F15 | SUSPECTED | Same cause as Q7/Q8 |

## 6. Project skill reviews (S1, S2)
- `places-db`: **CHANGES REQUESTED**. CONFIRMED blocker: Windows PowerShell 5.1 drops `""` arguments to native programs. Tested here: `python -c "import sys;print(sys.argv[1:])" --google-key "" --out out\` printed `['--google-key', '--out', 'out\']`, so argparse aborts ("expected one argument"). It fails safe, but the procedure cannot run. Use `--google-key=` instead (tested: arrives as `['--google-key=', ...]`). CLAUDE.md 5.x has the same `--google-key ""` advice. All other facts check out (`pipeline.py:38,364,378`, empty key = cache only at `google_enrich.py:117,133,135`, `VERSION = 4`, `${TABLE_NAME}` in the schema JSON). No scripts bundled.
- `add-compose-component`: **APPROVED**. The facts match `PreviewSupport.kt:9-14` and `InfoCallout.kt`; `:app:compileDebugKotlin` resolves (`--dry-run` BUILD SUCCESSFUL). No scripts bundled.

## 7. Phase 2 plan review (T1-T14): CHANGES REQUESTED (minor)
- Q1-Q6 map correctly: Q1->T1, Q2->T2, Q3->T3, Q4->T4, Q5->T6, Q6->T2. Missing: Q9 and Q10 (add to T2), Q11 and Q13 (add to T5/T4).
- T1: DataStore needs a Context, so testing concurrent updates needs Robolectric (a new dependency) or an androidTest. The plan should name which.
- T2: a `BOOT_COMPLETED` receiver must be exported and needs `RECEIVE_BOOT_COMPLETED`; add a security review to its acceptance.
- T9 needs a testable criterion: decoding inside `withContext(Dispatchers.Default)` in the repository.
- Run T6 first so the lint gate holds; T13 tests should be written failing before T1/T2/T7.
- File overlap: T1-T3/T5/T8 (UnderwayStore, UnderwayNotifier, FlowViewModel) and T7/T11/T12 (ScreensPlanning) must run strictly in sequence.
- Commit the current baseline before M1 so the fixes are reviewable as diffs.

## 8. run-android skill: APPROVED with small changes
Read in full; only read-only checks were run. The `$adb` prefix resolves (Test-Path True). screencap+pull is correct for PS 5.1. The `exported=false` / no-root limit is correct. The no-login-bypass rule is right.
Changes requested:
- Section 7: `--locales ""` has the same PS 5.1 empty-argument bug as places-db; use `--locales '""'`.
- Re-run `installDebug` after connected tests: the app is currently uninstalled ("Unknown package").
- Add `ACCESS_COARSE_LOCATION` to section 9.
- Note that `pm clear` keeps scheduled alarms.
places-db is still CHANGES REQUESTED: SKILL.md lines 19 and 30 still say `--google-key ""` (CLAUDE.md:173 is fixed).

## 9. Re-reviews
- `places-db` (after fix): **APPROVED**. Every pipeline command uses `--google-key=`, with the key guard in the same command line. There is a note that `""` fails in PS 5.1, `--no-google` is forbidden, the cache is protected, and Gradle steps return to the repo root. Nothing was executed.
- `run-android` (after fix): **APPROVED**. The locale reset omits `--locales`; the device help confirms "Empty locale list is used when unspecified" (`cmd locale help` on emulator-5554). Open nits from section 8 (re-install after connected tests, COARSE location, `pm clear` keeps alarms) are optional.
- `add-compose-component`: **APPROVED**.
- Phase 2 plan (revised): **APPROVED**. All 8 points were applied (order T0 -> T6 -> T13a -> T1 ... -> T13b; T1 as an androidTest; T2 includes Q9/Q10 and a security review of the boot receiver; T9 criterion is testable). Implementation waits for the user's go-ahead.
- run-android (second fix): **APPROVED**; all 4 nits applied. places-db re-checked: line 19 is a deliberate warning and the pipeline command uses `--google-key=`, so it stays **APPROVED**.
