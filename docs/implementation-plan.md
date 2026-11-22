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

## Phase 2: prioritised plan (APPROVED by workflow-auditor; awaiting user go-ahead)
Reviewed by workflow-auditor: CHANGES REQUESTED (minor), all 8 points applied, then APPROVED (2026-11-21).

**Order (strict, one implementer):** T0 baseline commit (user) -> T6 (lint gate) -> T13a failing tests -> T1 -> T2 -> T3 -> T5 -> T4 -> T8 -> T9 -> T7 -> T10 -> T11 -> T12 -> T13b.
File-sharing groups run strictly in sequence: {T1, T2, T3, T5, T8} share `UnderwayStore`/`UnderwayNotifier`/`FlowViewModel`; {T7, T11, T12} share `ScreensPlanning.kt`.

Global acceptance for every implementation task: `assembleDebug`, `testDebugUnitTest` and `lintDebug` green (lint at least no new errors until T6 lands), existing connected tests still pass, FR + EN strings for any new text, theme tokens only.
IDs refer to the audit docs: F = engineering, UX# = ux, Q/W = qa, V/D = design.

### M1: trip data integrity (P0/P1, domain first)
| # | Task | Sources | Owner | Depends | Acceptance criteria | Reviewers |
|---|---|---|---|---|---|---|
| T1 | Atomic `UnderwayStore.update` (single DataStore transaction); screens navigate after the write | Q1, F14, F15 | feature-engineer | T6, T13a | Instrumented test (`androidTest`, no new dependency): concurrent receiver + UI updates both land | workflow-auditor |
| T2 | Alarm sync: cancel all alarms of the previous trip on start/schedule; cancel a stop's alarms on Arrived/Skip; reschedule on boot (`BOOT_COMPLETED` receiver + `RECEIVE_BOOT_COMPLETED`) and when notification permission is granted; `finish()` in `finally` in the receiver (Q9); cancel notifications/alarms with `cancelAll` on trip end (Q10) | Q2, Q6, Q9, Q10, F12, UX#29 | feature-engineer | T1 | `dumpsys alarm` shows only current-trip alarms after: new trip, Arrived, Skip, reboot; security review of the exported boot receiver (accepts only `BOOT_COMPLETED`) | workflow-auditor (device) |
| T3 | Stamps awarded and alarms cancelled when the last stop finishes (not on the End of day button); `endDay` idempotent; confirm dialog per `docs/ux/spec-replace-active-trip.md`, also shown for a finished-but-not-ended trip | Q3, F22, F13, UX#1, UX#8 | feature-engineer | T1, T2 | System Back on End of day keeps stamps; new trip never overwrites unsaved progress | ux-designer, workflow-auditor |
| T4 | Onboarding gate reads `profile.onboarded` (all sign-in paths); Back on onboarding step 1 does not empty the back stack; log-off `popUpTo` on the old Planning route fixed (Q13) | Q4, Q13, F23, F24, UX#2 | feature-engineer | none | Quit mid-onboarding -> next launch resumes onboarding; no blank screen | ux-designer, workflow-auditor |
| T5 | Sign-out (Settings and old Planning screen) clears profile, active trip, stamps and alarms; drop the unused FINE location permission (Q11) | F25, Q11, UX#30 | feature-engineer | T2 | Second account sees a clean state | workflow-auditor |
| T6 | **First.** Fix the 3 lint errors; notifications use the in-app language | lint, Q5 | feature-engineer | T0 | `lintDebug` passes; notification text follows Settings language | workflow-auditor |

### M2: planning flow and navigation (P1)
| # | Task | Sources | Owner | Depends | Acceptance criteria | Reviewers |
|---|---|---|---|---|---|---|
| T7 | Selection keeps stops on Back/Continue; feedback + undo when picking an event; show chosen stops; meal default sets a time (or defaults to No); meals never overlap events; feedback for "Add as #4" (F3 narrowed: unavailable districts cannot be added, verified); drop a selected district that has 0 events after a window change | UX#3, UX#5, F1, F2, F3, UX#13, UX#28, UX#34 | feature-engineer | none | Each case covered by a unit test on the planner/ViewModel | ux-designer, workflow-auditor |
| T8 | Notification tap opens the current Underway page; Arrived/Skip from a notification updates the open screen | UX#4 | feature-engineer | T1, T2 | Verified on emulator via `run-android` | ux-designer, workflow-auditor |
| T9 | Decode `events.json` off the main thread | F28 | feature-engineer | none | Parse runs inside `withContext(Dispatchers.Default)` in the repository | workflow-auditor |

### M3: visual and accessibility (P0 visual, then P1)
| # | Task | Sources | Owner | Depends | Acceptance criteria | Reviewers |
|---|---|---|---|---|---|---|
| T10 | Leg screen: header on heroCard (contrast >= 4.5:1), remove the duplicate "Comment s'y rendre" | V P0, UX#21 | feature-engineer | none | Contrast checked | visual-designer |
| T11 | Apply design decisions D1-D5: explicit roles where M3 uses `secondaryContainer` (progress track, tonal buttons, map Previewed); full-width page buttons, `labelLarge`; screens on canvas with cards; corners 32/24/16; dark card `#251E1B` (assumed) | `docs/design/initial-audit.md` Decisions (incl. ux constraints on buttons: wrap, min height, max 2 pinned) | feature-engineer | T10 | Light + dark previews reviewed; team-lead updates CLAUDE.md 6.1 (dark card) when merged | visual-designer, ux-designer |
| T12 | Accessibility: district list alternative for TalkBack on the map; touch targets >= 48 dp ("Retirer"); meaning not by colour only; empty/error states with reason + next step; localized date on Home; loading state after Start; reduced-motion respect | UX#5(map), UX#22-24, UX#31, V items | feature-engineer | T11 | TalkBack can complete planning (emulator) | ux-designer, visual-designer |

### M4: tests and hygiene
| # | Task | Sources | Owner | Depends | Acceptance criteria | Reviewers |
|---|---|---|---|---|---|---|
| T13a | Failing tests first: selection, `mealOptions`, progress transitions, serialization, alarm set per trip (pure-logic parts as unit tests) | QA coverage gaps | workflow-auditor | T6 | Tests reproduce the confirmed bugs and fail on the current code | feature-engineer |
| T13b | Re-run T13a after M1-M2; all pass | | workflow-auditor | T1-T9 | All green | team-lead |
| T0 | **Before M1:** user commits the current baseline (flow screens + audits) so fixes are reviewable diffs; team-lead prepares the slice list | W7, review point 8 | user | none | Baseline committed; `.claude/settings.local.json` and `.idea` selector excluded | workflow-auditor |
| T14 | Commit in slices after M1 (user commits); keep `.claude/settings.local.json` and `.idea` selector out of commits | W7 | user (team-lead prepares the slice list) | M1 | Clean, reviewable commits | workflow-auditor |

### Deferred / needs user decision
- Preview screenshot testing (Roborazzi / Paparazzi / AGP screenshot): new test dependency, user approval needed.
- Split `FlowViewModel` (service-locator role, draft lost on process death): after M1-M2.
- kotlin-lsp plugin: deferred (Gradle daemon contention, unverified on AGP 9).
- Ask the Figma owner whether `secondaryContainer` should become a light colour (removes the T11 overrides).
- `app/google-services.json` key restriction: not verified (CLAUDE.md open item 10).
