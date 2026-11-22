# Implementation plan and task board

Owner: team-lead. This file replaces the native shared task list (not available in this Claude Code build).
Teammates report status to team-lead by message; team-lead updates this table.

States: `pending` / `in progress` / `in review` / `completed`. A task is `completed` only after its reviewer gives a label (workflow-auditor W1).
Review labels: APPROVED / CHANGES REQUESTED / BLOCKED / NOT VERIFIED.
Disputes between team-lead and workflow-auditor go to the user (W2).

## Team (2026-11-21)
| Agent | Role | Owns |
|---|---|---|
| team-lead | Architecture, coordination, docs | `CLAUDE.md`, `docs/implementation-plan.md`, `docs/adr/`, `.claude/agents/` |
| feature-engineer | Kotlin implementation | All production Kotlin, resources and manifest in Phase 4 (single implementer, no concurrent edits) ; `docs/engineering/` |
| ux-designer | Interaction / accessibility | `docs/ux/` |
| visual-designer | Visual / design system | `docs/design/` |
| workflow-auditor | Independent QA and process | `docs/qa/`; new unit test files under `app/src/test/` for T13 |
| skill-curator | Skills | `.claude/skills/`, `docs/skills-inventory.md` |

Gradle rule (W4-W6): only one agent runs Gradle at a time. During implementation feature-engineer may run `assembleDebug` / `testDebugUnitTest`; on hand-off to review, workflow-auditor runs the full set. Connected tests need the emulator.

## Phase 1: audits (done 2026-11-21)
| # | Task | Owner | State | Reviewer | Label |
|---|---|---|---|---|---|
| A1 | Technical audit, `docs/engineering/initial-audit.md` (34 findings) | feature-engineer | completed | workflow-auditor | verified by workflow-auditor: 9 confirmed, F15 suspected, F3 narrowed |
| A2 | UX audit, `docs/ux/initial-audit.md` (33 issues) | ux-designer | completed | team-lead | APPROVED |
| A3 | Visual audit, `docs/design/initial-audit.md` (23 items, decisions D1-D5) | visual-designer | completed | team-lead | APPROVED |
| A4 | QA + workflow audit, `docs/qa/initial-audit.md` | workflow-auditor | completed | team-lead | APPROVED (findings W1, W2, W8 accepted) |
| A5 | Skills inventory, `docs/skills-inventory.md` | skill-curator | in review | workflow-auditor | |
| S1 | Skill `places-db` | skill-curator | completed | workflow-auditor | APPROVED (after `--google-key=` fix) |
| S2 | Skill `add-compose-component` | skill-curator | completed | workflow-auditor | APPROVED |
| S3 | Skill `run-android` (emulator, screenshots, font scale, TalkBack, notification checks) | skill-curator | completed | workflow-auditor | APPROVED (small changes applied) |

Baseline (workflow-auditor, 2026-11-21): `assembleDebug` OK; `testDebugUnitTest` 22/22; `connectedDebugAndroidTest` 14/14; **`lintDebug` FAILS** (3 errors `LocalContextConfigurationRead`: `ScreenSettings.kt:26`, `ScreensUnderway.kt:100`, `:211`). Not verified: end-to-end flow, notifications, reboot.

## Hackathon demo stabilization (user directive 2026-11-22, overrides the order below)
Local data only (CLAUDE.md open item 11). Demo order: **T1 (finishing) -> T7b + T18a (default date = today within the data window 2026-11-21..12-06; hide events already started and meal blocks before now) -> T18b (the 2 demo-blocking empty states: DistrictPick all zero, StopSelection nothing reachable; docs/ux/demo-empty-states.md) -> T4 (onboarding gate, Back on step 1) -> T10 (Leg readable) -> T3+T17 (stamps) -> T2 (alarm sync) -> T9 (events parse off Main)**. Deferred until after the demo (scope freeze 2026-11-22): T10b (demo script avoids the Passport/Itinerary tabs), FLAKY-1 (AppNavigationTest teardown crash: pre-existing infra flake, about 1 in 11 connected runs; fix by closing each ActivityScenario explicitly or retrying once), SC3a/SC3, T5, T7, T8, T11, T12, T13b, T18 demo mode, T22. No new features, no refactoring.

**Device rule (workflow-auditor, 2026-11-22):** `connectedDebugAndroidTest` uninstalls the app and the store tests wipe the trip, which erases the Firebase sign-in, profile and trip on that emulator. The only AVD is `Medium_Phone` (emulator-5554). So: the user signs in only after the checkpoint `installDebug`; from then on nobody runs connected tests on emulator-5554 (no second AVD: only 0.5 GB RAM free. Connected runs for T3/T2 are batched into one run near the end, then the final APK is reinstalled and the user signs in once more); smoke tests use `installDebug` only, which keeps app data.
Demo acceptance: build OK; APK installs and launches; local events load; core screens work offline (after one online sign-in); primary flow (onboard -> plan -> underway -> end of day) completes; progress survives app restart; no Google key or external service needed; verified on the emulator.

## Phase 2: prioritised plan (APPROVED by workflow-auditor and by the user, 2026-11-22)
Reviewed by workflow-auditor: CHANGES REQUESTED (minor), all 8 points applied, then APPROVED (2026-11-21).

**Order (strict, one implementer; updated 2026-11-22 after the user approved T16-T18 and T22):** T0 (done) -> T6 -> T13a failing tests -> T16 -> T1 -> T2 -> T18 -> T3+T17 -> SC3 -> T5 -> T4 -> T8 -> T9 -> T7 -> T7b -> T22 -> T10 -> T11 -> T12 -> T13b.
File-sharing groups run strictly in sequence: {T1, T2, T3, T5, T8} share `UnderwayStore`/`UnderwayNotifier`/`FlowViewModel`; {T7, T11, T12} share `ScreensPlanning.kt`.

Global acceptance for every implementation task: `assembleDebug`, `testDebugUnitTest` and `lintDebug` green (lint at least no new errors until T6 lands), existing connected tests still pass, FR + EN strings for any new text, theme tokens only.
IDs refer to the audit docs: F = engineering, UX# = ux, Q/W = qa, V/D = design.

### M1: trip data integrity (P0/P1, domain first)
| # | Task | Sources | Owner | Depends | Acceptance criteria | Reviewers |
|---|---|---|---|---|---|---|
| T1 | **In progress.** Atomic `UnderwayStore.update` (single DataStore transaction); screens navigate after the write | Q1, F14, F15 | feature-engineer | T6, T13a | Instrumented test (`androidTest`, no new dependency): concurrent receiver + UI updates both land | workflow-auditor |
| T2 | Alarm sync: cancel all alarms of the previous trip on start/schedule; cancel a stop's alarms on Arrived/Skip; reschedule on boot (`BOOT_COMPLETED` receiver + `RECEIVE_BOOT_COMPLETED`) and when notification permission is granted; `finish()` in `finally` in the receiver (Q9); cancel notifications/alarms with `cancelAll` on trip end (Q10) | Q2, Q6, Q9, Q10, F12, UX#29 | feature-engineer | T1 | `dumpsys alarm` shows only current-trip alarms after: new trip, Arrived, Skip, reboot; security review of the exported boot receiver (accepts only `BOOT_COMPLETED`) | workflow-auditor (device) |
| T3 | Stamps awarded and alarms cancelled when the last stop finishes (not on the End of day button); `endDay` idempotent; confirm dialog per `docs/ux/spec-replace-active-trip.md`, also shown for a finished-but-not-ended trip | Q3, F22, F13, UX#1, UX#8 | feature-engineer | T1, T2 | System Back on End of day keeps stamps; new trip never overwrites unsaved progress | ux-designer, workflow-auditor |
| T4 | Onboarding gate reads `profile.onboarded` (all sign-in paths); Back on onboarding step 1 does not empty the back stack; log-off `popUpTo` on the old Planning route fixed (Q13) | Q4, Q13, F23, F24, UX#2 | feature-engineer | none | Quit mid-onboarding -> next launch resumes onboarding; no blank screen | ux-designer, workflow-auditor |
| T5 | Sign-out (Settings and old Planning screen) clears profile, active trip, stamps and alarms; drop the unused FINE location permission (Q11) | F25, Q11, UX#30 | feature-engineer | T2 | Second account sees a clean state | workflow-auditor |
| T16 | **Done**, APPROVED. Age rule: hide an event if `minAge > ageRange.min` (spec example: 21+ hidden for 18-24); flip `PlannerRulesTest` | P1-02 | feature-engineer | T13a | Flipped test passes; 21+ shown for 25-34 | workflow-auditor |
| T18 | Injectable `Clock` in planner, FlowViewModel and alarm scheduling; filter events already started; default date = max(today, first data date); debug-only demo mode (clock override) | P1-04, UX#37 | feature-engineer | T2 | Fixed-Clock planner tests; demo mode absent from release builds; emulator alarm check via run-android | workflow-auditor, ux-designer |
| T17 | Stamp key: event id, else brand key for chains, else place id; add `cityId`; pure `mergeStamps`. Done inside T3 | P1-03 | feature-engineer | T1, with T3 | `mergeStamps` tests: same event on 2 dates = 1; 2 locations of one brand = 1 | workflow-auditor, ux-designer |
| T6 | **Done: `2af2ba2`**, APPROVED (notification language on device NOT VERIFIED, moved to T2). Fix the 3 lint errors; notifications use the in-app language | lint, Q5 | feature-engineer | T0 | `lintDebug` passes; notification text follows Settings language | workflow-auditor |

### M2: planning flow and navigation (P1)
| # | Task | Sources | Owner | Depends | Acceptance criteria | Reviewers |
|---|---|---|---|---|---|---|
| T7 | Selection keeps stops on Back/Continue; feedback + undo when picking an event; show chosen stops; meal default sets a time (or defaults to No); meals never overlap events; feedback for "Add as #4" (F3 narrowed: unavailable districts cannot be added, verified); drop a selected district that has 0 events after a window change; Remove on Review deletes only that stop (UX#36) | UX#3, UX#36, UX#5, F1, F2, F3, UX#13, UX#28, UX#34 | feature-engineer | none | Each case covered by a unit test on the planner/ViewModel | ux-designer, workflow-auditor |
| T7b | Default plan date is today (not the hard-coded 2026-11-21); the date picker blocks past dates | UX#37 | feature-engineer | T7 | Unit test on the default date; picker rejects past dates | ux-designer, workflow-auditor |
| T22 | Bars offered as meals from 17:00, including dinner, for adults-only audiences only (never FAMILY / NO_ALCOHOL); user decision 2026-11-22, ADR 0003. While `places.db` is frozen (T15), apply it in the app's slot logic; update `reference.yaml` for the next rebuild | P1-10 | feature-engineer | T7 | `mealOptions` test: 18:30 adults-only dinner includes a bar; family/no-alcohol never | workflow-auditor |
| T8 | Notification tap opens the current Underway page; Arrived/Skip from a notification updates the open screen | UX#4 | feature-engineer | T1, T2 | Verified on emulator via `run-android` | ux-designer, workflow-auditor |
| T9 | Decode `events.json` off the main thread | F28 | feature-engineer | none | Parse runs inside `withContext(Dispatchers.Default)` in the repository | workflow-auditor |

### M3: visual and accessibility (P0 visual, then P1)
Definition of done for T10-T12 (user, 2026-11-22): screenshot verify passes for the affected pilot subjects AND visual-designer approves the image diffs; per `docs/engineering/screenshot-testing.md` 4a. No reference image before SC3a.
| # | Task | Sources | Owner | Depends | Acceptance criteria | Reviewers |
|---|---|---|---|---|---|---|
| T10 | Leg screen: header on heroCard (contrast >= 4.5:1), remove the duplicate "Comment s'y rendre" | V P0, UX#21 | feature-engineer | none | Contrast checked | visual-designer |
| T11 | Apply design decisions D1-D5: explicit roles where M3 uses `secondaryContainer` (progress track, tonal buttons, map Previewed); full-width page buttons, `labelLarge`; screens on canvas with cards; corners 32/24/16; dark card `#251E1B` (assumed) | `docs/design/initial-audit.md` Decisions (incl. ux constraints on buttons: wrap, min height, max 2 pinned) | feature-engineer | T10 | Light + dark previews reviewed; red text >= 4.5:1 on its real background (D6: red text on white may use primary; elsewhere onPrimaryContainer #930018; never on primaryContainer; incl. LateArrivalWarning -> onErrorContainer, DistrictPreviewSheet, AlertDialog buttons, focused text-field labels; fills keep #E4002B); team-lead updates CLAUDE.md 6.1 (dark card) when merged | visual-designer, ux-designer |
| T12 | Accessibility: district list alternative for TalkBack on the map; touch targets >= 48 dp ("Retirer"); meaning not by colour only; empty/error states with reason + next step; localized date on Home; loading state after Start; reduced-motion respect | UX#5(map), UX#22-24, UX#31, V items | feature-engineer | T11 | TalkBack can complete planning (emulator) | ux-designer, visual-designer |

### M4: tests and hygiene
| # | Task | Sources | Owner | Depends | Acceptance criteria | Reviewers |
|---|---|---|---|---|---|---|
| T13a | **Done: `542c2d2`**, APPROVED as reproduction suite (6 expected failures, 20 characterisation tests pass; docs/qa/t13a-regression-tests.md). Failing tests first: selection, `mealOptions`, progress transitions, serialization, alarm set per trip (pure-logic parts as unit tests) | QA coverage gaps | workflow-auditor | T6 | Tests reproduce the confirmed bugs and fail on the current code | feature-engineer |
| T13b | Re-run T13a after M1-M2; all pass | | workflow-auditor | T1-T9 | All green | team-lead |
| T0 | **Done: `fa32fda`** (local). Baseline commit of flow screens + audits so fixes are reviewable diffs | W7, review point 8 | team-lead (user-authorised) | none | Baseline committed; `.claude/settings.local.json` and `.idea` selector excluded | workflow-auditor |
| T14 | Commit in slices after M1 (user commits); keep `.claude/settings.local.json` and `.idea` selector out of commits | W7 | user (team-lead prepares the slice list) | M1 | Clean, reviewable commits | workflow-auditor |

### Added by the user (2026-11-22)
| # | Task | Owner | State | Depends | Acceptance criteria | Reviewers |
|---|---|---|---|---|---|---|
| P1 | Prep workflow audit: `docs/prep-workflow-audit.md` (13 sections), read-only on production code, runs in parallel with M1 | workflow-auditor (+ ux, visual, feature-engineer for feasibility) | in review | none | All prep workflows mapped vs code; findings with severity; new tasks proposed, not silently added | team-lead |
| SC1 | Screenshot-testing research (`docs/engineering/screenshot-testing.md`): AGP screenshot test suites, half-day time box, Roborazzi fallback | skill-curator | in review | none | Verified compatibility, one recommendation | workflow-auditor, team-lead |
| SC2 | Screenshot pilot scope: S1 LegHeader, S2 EventOptionCard, S3 step page; 20 images | visual-designer (+ ux-designer states) | completed | none | `docs/design/screenshot-pilot.md` | team-lead |
| SC3a | `Modifier.entrance()` skips the animation under LocalInspectionMode (previews and screenshots render visible) | feature-engineer | pending | none (before SC3) | Previews render at full alpha | visual-designer |
| SC3 | Implement pilot setup (AGP test suites, engine 0.0.1-alpha16 pinned; deps and flags covered by the user's pilot approval of 2026-11-22) | feature-engineer | pending | SC1, SC2, after M1 critical fixes (scheduled after T3) | Deterministic record/verify on this machine; `Modifier.entrance()` guarded by LocalInspectionMode; fallback to Roborazzi if the gate fails | workflow-auditor, visual-designer |

### From P1 (docs/prep-workflow-audit.md section 13)
Approved by the user 2026-11-22: T16, T17, T18, T22 (scheduled above). T15: user decides with the team and the ADR owner; agents do not touch `places.db`; no distribution outside the team meanwhile. Still proposed: T19 (= UX#36, already in T7) · T20 Leave By / late warning in the domain · T21 ticket link · T23 Firestore event cache lockdown · T24 scope: Itineraries/Passport/Firestore (M3+).

### Deferred / needs user decision
- **CRITICAL (P1 audit): ADR 0002 says do not store Google Places content, yet `places.db` (53fad78) ships Google-derived content for 4,761 places in the APK.** Options: keep after checking the current terms / rebuild keeping only `google_place_id` (places-db skill) / follow ADR 0002. User + ADR owner decide. Until then: no distribution outside the team (recommendation).
- Split `FlowViewModel` (service-locator role, draft lost on process death): after M1-M2.
- kotlin-lsp plugin: deferred (Gradle daemon contention, unverified on AGP 9).
- Ask the Figma owner whether `secondaryContainer` should become a light colour (removes the T11 overrides).
- `app/google-services.json` key restriction: not verified (CLAUDE.md open item 10).
