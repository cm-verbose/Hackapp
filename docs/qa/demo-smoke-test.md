# Demo smoke test (workflow-auditor, 2026-11-22 01:28-01:39)

Build: checkpoint APK `apk-checkpoints/plan-montreal-debug-T1-d8cd43f.apk` (commit d8cd43f), installed by team-lead on emulator-5554 (Medium_Phone API 37, system language English). Driven with adb only. No Gradle, no connected tests, no uninstall, no pm clear. Screenshots: `C:\Users\29401\AppData\Local\Temp\claude\edf5abfe0732945a\b9ae26e6-dc25-4ee5-a475-41f711260abf\scratchpad\smoke\NN-*.png`.
Firebase session already present, so the app opens on Home. Onboarding was not exercised (account not new; Q4/T4).

## Online pass
| # | Step | Result | Evidence |
|---|---|---|---|
| 1 | Launch | VERIFIED: Home "Hello" (no name: empty profile, Q4) | 00-home |
| 2 | Planning 1: date/time | VERIFIED. Default date Sat 21 Nov (yesterday, known UX#37/T7b); picker allows 21 Nov to 6 Dec; picked Sun 22 Nov, Night out | 01, 02, 03 |
| 3 | Planning 2: boroughs | VERIFIED: map, count badges (11/9/8/1), sheet with types, "Add as #1/#2", order list, attribution. The first "Add as #1" tap during the sheet animation was ignored (re-tap worked; LOW) | 04-07 |
| 4 | Planning 3: meals | VERIFIED with a known defect: "Yes" selected but no meal time selected (F1/T7b); chose Dinner 18:30 | 08, 08b |
| 5 | Planning 4: selection | VERIFIED: events by hour/type, meal-block prompt; meal list from places.db (Beaubien Est restaurants with prices); departure sheet for unpublished end time (Confirm only reachable after expanding the sheet, LOW); "N stops". Known: an 18:00-21:00 event is offered inside the 18:15 meal block (F2/T7b); the second borough had 0 reachable events although its badge said 9 (badge counts the whole window) | 09-15 |
| 6 | Planning 5: review | VERIFIED: route map, stops with planned departures, Remove, notification note | 16 |
| 7 | Start | VERIFIED: alarms scheduled exactly as designed: ARRIVAL_CHECK 19:00, LEAVE_REMINDER 19:00, ARRIVAL_CHECK 20:30 (`dumpsys alarm`) | 17 |
| 8 | Leg 1 | **FAILED (demo blocker)**: "Travel 17661 min, Arrive 07:54". The emulator's last known location is its default (California) and is used without a sanity check. Header text very faint (T10) | 17-leg1 |
| 9 | Arrival -> I've arrived -> At stop | VERIFIED: Leave by 19:30, next stop with walk time | 18, 19 |
| 10 | force-stop + relaunch mid-trip | VERIFIED for progress: Home shows UNDERWAY card (raw date "2026-11-22", UX localized date); Resume opens At stop (atStop kept). **FAILED for reminders**: all 3 alarms were gone after force-stop and the app does not reschedule on launch (0 pending alarms) | 20, 21 |
| 11 | Leave -> Leg 2 -> Skip | VERIFIED: Leg 2 13 min; Skip on the last stop went to End of day | 21, 22 |
| 12 | End of day | VERIFIED: "1 stop confirmed, 1 skipped", 1 stamp. Defect: the stamp shows the raw district id "rosemont-petite-patrie" instead of the borough name (LOW-MEDIUM, visible) | 22 |
| 13 | End the day -> Home, relaunch | VERIFIED: back to Home, no underway card, state survives relaunch | 23 |

## Offline pass (svc wifi/data disabled; "Active default network: none")
| # | Step | Result |
|---|---|---|
| O1 | force-stop + launch offline | VERIFIED: straight to Home (cached Firebase session), no error |
| O2 | Plan -> boroughs (badges) -> meal list -> event + departure -> review -> Start | VERIFIED: bundled events and places.db load offline (31-33) |
| O3 | force-stop + relaunch offline mid-trip | VERIFIED: UNDERWAY card and Resume (35) |
| O4 | Skip through to End of day -> End the day | VERIFIED ("no stamp") |
| O5 | Crashes | None (`logcat -s AndroidRuntime:E` empty) |
Network restored afterwards (`svc wifi enable; svc data enable`; active network 102).

## Not verified
Sign-in itself (session pre-existing), onboarding, notifications firing (alarms are hours away; inexact), notification buttons, FR locale, TalkBack, font scale.

## Findings for the demo (new, confirmed on device)
1. **BLOCKER-VISIBLE: Leg 1 travel estimate from a far location** (17661 min). Demo workaround: `adb emu geo fix -73.5673 45.5017` before the demo. Code fix: ignore fixes more than about 50 km from the city.
2. **force-stop removes all reminders; no reschedule on launch** (Android cancels an app's alarms on force-stop). Demo workaround: do not force-stop between Start and showing a reminder. Code: reschedule from stored progress on app start (extend T2).
3. Stamp shows the raw district id on End of day (use the borough name).
4. Passport and Itineraries tabs: placeholder text drawn under the status bar (37-passport-tab); known visual P1, very visible.
5. Minor: "Add as #1" ignored during the sheet animation; the departure sheet needs expanding to reach Confirm.

## Demo tasks QA (2026-11-22 01:40-01:50): build with T7b+T18a, T18b, T4 (+changes), T10, T10c on top of d8cd43f
- `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --rerun-tasks --continue`: 56 tests, 2 failed (expected T22, F2); lint 0 errors. `installDebug`: Installed on 1 device (app data kept). Emulator location set: `adb emu geo fix -73.5673 45.5017` (demo setup step).

| Task | Label | Evidence |
|---|---|---|
| T7b+T18a (default date today, past dates blocked, started events and past meals hidden) | **APPROVED** | Code review OK (Clock injected into planner and VM; `eligible()` drops started events; `mealTimes(window, now)`). PlanClockTest 4/4. Device: default date Sun 22 Nov; picker shows 20/21 Nov disabled (49-picker-T7b) |
| T18b (DistrictPick "no events in window" and StopSelection empty states) | **APPROVED** (StopSelection seen on device; DistrictPick all-zero state reviewed in code only, it needs a window with no events left) | 50-selection-empty-T18b: "No events left in Rosemont-La Petite-Patrie" plus next-borough body |
| T4 (+changes: route from profile.onboarded or isNewUser; no back arrow at root; Q13) | **APPROVED** | AfterSignInRouteTest 4/4. Device: relaunch with a non-onboarded profile opened onboarding step 1 with **no Back** (40-launch-T4); completing onboarding (Demo, 25-34, Rosemont, < $100) lands on "Hello Demo"; relaunch then goes straight to Home. Not run: the new-account path (it would wipe the demo profile) |
| T10 (Leg header on heroCard, duplicate heading removed) | **APPROVED** | 51-leg-light: readable dark card, "How are you getting there?" once; 53-leg-dark readable. At font 2.0 (52-leg-font200) the transport chip truncates "Public tran..." (known post-demo item). Correction from visual-designer: the pinned buttons do not cover NextStopCard; the content column scrolls above them. T10 visually approved by visual-designer |
| T10c (ignore a start fix more than 50 km from the city) | **APPROVED** | StartLocationTest. Device: Travel shows "–" instead of 17661 min. Even after the geo fix the first Leg shows no estimate (the GPS last-known is apparently null and the stale CA network fix is rejected), so the demo shows "–" on Leg 1, not a time. Acceptable |

- Note for the demo device: T4 sent the signed-in emulator through onboarding once, because its profile had never been onboarded. It is now onboarded as "Demo". The test trip was ended; the app is on Home. Font scale reset to 1.0, night mode off. No crash in logcat.
