# Plan Montréal: hackathon demo script

Build: `apk-checkpoints/plan-montreal-debug-demo-0b4ad38.apk` (commit `0b4ad38`). SHA-256 `D41B167B9211E4C168FA1C4DE31EF81FA2751F9FA41635BAF745CB0F5C9C0AE0`; a clean rebuild from `0b4ad38` is byte-identical. Fallback: `plan-montreal-debug-T1-d8cd43f.apk` (first checkpoint, smoke-tested online and offline; it lacks today's-date default, empty states, the onboarding gate and the readable Leg header). Do NOT use `plan-montreal-debug-demo-a32be92-UNVERIFIED.apk`: a concurrent build may have produced it, so its match to commit `a32be92` is unproven, and it was never smoke-tested on its own.

Final acceptance (workflow-auditor, 2026-11-22): the full demo route below passed on the installed `0b4ad38` build (night out, 2 stops, swipe-away + relaunch keeps progress and alarms, End of day stamps show "Rosemont-La Petite-Patrie", End the day). No crashes.
Data: local only (bundled `events.json`, 1,295 events, 2026-11-21 to 2026-12-06; bundled `places.db`). No Google key or external service is needed.

## Before going on stage (5 min)
1. Emulator `Medium_Phone` (emulator-5554) running, app installed, signed in, on Home ("Hello Demo").
   - Fresh device: install with `adb install -r <apk>`, then sign in ONCE while online. Firebase keeps the session offline.
2. Location: `adb -s emulator-5554 emu geo fix -73.5673 45.5017` (Montréal). On this emulator Leg 1 still shows "Travel –" (no GPS last-known fix; expected, not a bug). Leg 2 onwards shows real minutes. (Unverified: opening Google Maps once after the geo fix may give Leg 1 a number.)
3. Warm-up: open Plan an itinerary once and reach the district map, then back out. The first event load can stutter (T9 deferred).
4. Optional offline demo: `adb shell svc wifi disable` and `adb shell svc data disable`. The whole route below works offline. Re-enable afterwards.

## Demo route (about 4 min)
1. Home -> **Plan an itinerary**.
2. Step 1: date = today (default) or a later date; **choose an evening window** (events that already started are hidden today, so an afternoon slot can be thin). Day or Night.
3. Step 2: district map with event counts -> pick a district, preview, **Add**.
4. Meals: pick a meal time explicitly (there is no default meal time).
5. Events: pick events; choose a restaurant from the local database.
6. Review: route map -> **Start**.
7. Leg -> **I've arrived** -> At stop -> **I'm leaving** -> next Leg -> repeat; show **Skip** once.
8. End of day: stamps with the district name -> **End the day** (tap the button).
9. Optional: mid-trip, swipe the app away from Recents and reopen it: Resume opens the right page (progress is saved).

## Do NOT do on stage
- Don't use system Back on End of day; tap "End the day" (Back loses the stamps; T3 deferred).
- Don't force-stop the app from Settings; it wipes the pending reminders (T2 deferred). Swipe away instead.
- Don't start a second trip while one is running (old reminders can fire; T2 deferred).
- Don't open the Passport or Itineraries tabs (placeholders).
- Don't create a new account (it resets the device profile and forces onboarding).
- Don't rely on a live notification: reminders fire 30 min before or after stop times.

## Known limitations (be ready to answer)
- Reminders, and their Arrived/Skip buttons, have not been verified firing on a device.
- Travel time on Leg 1 needs a start location; otherwise "–".
- At very large font sizes some Leg labels truncate.
- Restaurant data contains Google-derived fields; see CLAUDE.md open item 11 (ADR 0002).
