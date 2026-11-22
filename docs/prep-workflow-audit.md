# P1: Prep workflow audit

Auditor: workflow-auditor. Date: 2026-11-22. Read-only on production code; nothing executed except read-only shell checks.
Initial audits are referenced by id instead of repeated: F# = `docs/engineering/initial-audit.md`, UX# = `docs/ux/initial-audit.md`, V# = `docs/design/initial-audit.md`, Q#/W# = `docs/qa/initial-audit.md`, T# = `docs/implementation-plan.md`.
Every finding is marked **C** (confirmed from code or git evidence) or **S** (suspected; needs a device, a test or an outside fact).

## 1. Executive summary

`prep/` (repo root; it is the only directory named prep) is **not code that runs**. It holds the product's specification and research: the screen/flow spec (`design/screen-list.md`), the final restaurant data design, event API research, a draft event struct, a UI research brief, and reference copies of the map code. The spec it defines is the yardstick for the app.

Main results:
1. **CRITICAL (C conflict, S legal):** ADR `docs/adr/0002` (2026-11-21 17:29) says *not* to store Google Places content because of Google's caching terms. Two hours later, `places.db` shipped with Google-derived prices, ratings, hours, photo refs and descriptions for 4,761 places, bundled in the APK. Escalated to team-lead (P1-01).
2. **HIGH, spec contradicted by code and by a test:** an 18-24 user is shown 21+ Events. The spec uses exactly this case as its example of what to hide, and `PlannerRulesTest` asserts the opposite (P1-02).
3. **HIGH:** the Passport/Stamp rules ("one Stamp ever", chain = one restaurant) are not implemented. Stamps dedupe by stop id and date (P1-03).
4. **HIGH:** past Events are never hidden, and there is no demo mode. The default plan date (2026-11-21) is already in the past (P1-04).
5. **MEDIUM:** removing a Stop deletes every later Stop; the spec says to return to that time gap (P1-05). Leave By ignores the Leg time (P1-06). The late warning uses walking time and never refreshes (P1-07). Ticket links are never opened (P1-08). The Firestore event cache lets any signed-in user rewrite cached events (P1-09, S). Bars: the spec contradicts the data design (P1-10).
6. **Not built (scope gaps, not defects):** Itineraries tab (In planning / Past), several plans in Planning, Passport screen, Firestore-backed Profile/Itinerary/Passport, and demo mode. These need user prioritisation, not silent scope growth.

M1 (T1-T6) covers none of P1-01..P1-10 directly. It fixes the trip-state integrity those workflows sit on. Section 13 proposes tasks T15-T24, all for user approval.

## 2. Directory overview

| Path | Kind | Used by code? | Notes |
|---|---|---|---|
| `prep/design/screen-list.md` | **Product spec** (flows, rules) | Implemented in part (sections 3, 5) | References `GLOSSARY.md` (repo root, exists, tracked) |
| `prep/api-business/restaurant-data-design-final.md` | Data design v3.1, "final" | ETL + Room layer | CLAUDE.md: wins over code on conflict. Google venue rules not yet written in (CLAUDE.md open item 2) |
| `prep/api-business/mtl_food_core_v2.csv` (726 KB) | Earlier restaurant dataset | **No** (ETL reads `etl/data/businesses.csv`) | Superseded; only referenced by docs |
| `prep/api-business/ui_design_research_brief_*.md` | Research (Hochelaga, Chrono/Maps/Waze, swipe onboarding) | Ideas only | Colours superseded by Figma (CLAUDE.md 6) |
| `prep/api-event/data struct.txt` | Draft TypeScript `Event` interface | Basis of `data/Event.kt` | Code added `districtId`, `status` |
| `prep/api-event/montreal api/*.md`, `toronto api/*` (+ 386 KB CSV) | API research | `data/remote/{hasthings,montreal}`, `TorontoCityCsvEventSource` | Eventbrite/Ticketmaster/PredictHQ researched, not used |
| `prep/api-event/thing.md` | A prompt that was used to generate the API docs | No | Noise |
| `prep/design/{CityMap,DistrictMap,ItineraryRouteMap}.kt`, `DistrictMapData_additions.kt`, `ItineraryReview_usage.kt` | Reference copies of the map code | Already ported to `ui/components/map/` | Stale package `com.ets.hackapp`; code has grown (ItineraryRouteMap 96 -> 137 lines); mentions Firestore and runtime `switchCity` |
| `prep/design/geojson_to_districts(1).py` | Old copy of `tools/geojson_to_districts.py` | No | Lacks the UTF-8 fix (diff: `open(..., encoding='utf-8')`); running it re-introduces the accent corruption CLAUDE.md 6.2 describes |
| `prep/*/test.txt` | Empty files | No | Noise |

## 3. Workflows defined or implied by prep, and implementation status

| W | Workflow (spec source) | Implemented in | Status |
|---|---|---|---|
| W1 | Auth: Welcome / Register / reset; new account -> onboarding, returning user with Profile -> Home (screen-list "Authentication") | `scaffold/screens/login/*`, `AppNavigation.finishSignIn` | Partial: gate uses Firebase `isNewUser` (Q4/F23, in T4) |
| W2 | Onboarding 1-4 (Resident disabled, profile prefilled from Google, budget/audience, interests and cuisines rank but never hide) | `ScreensOnboarding.kt`, `ProfileStore` | Implemented (Resident disabled `:91-92`, prefill `:110-111`); cuisine "bars" chip not used in meal ranking (P1-12) |
| W3 | Planning 1: date within the seed range, day/night windows, budget override, date owns hours after midnight | `ScreensPlanning.kt:119-122`, `PlanWindow` | Implemented; past dates selectable (P1-04) |
| W4 | Planning 2: District pick, 1-3 districts in order, count badge, 0 events = disabled, preview sheet | `ScreensPlanning.kt:157`, `Planner.countsByDistrict` | Implemented (F3 narrowed, verified); events parsed on Main (F28, T9) |
| W5 | Planning 3: Meal choice, 90-min Meal Block | `ScreenPlanningMealChoice`, `MealBlock` | Block = 15+60+15 min, matches. Default-Yes defect F1 (T7) |
| W6 | Planning 4: selection; reachability = previous Planned Departure + Leg + buffer; eligibility rules; bars from 17:00 | `Planner.selection`, `EventRules.isEligible` | Reachability matches (LegEstimate includes buffer). Defects P1-02, P1-04, P1-10, F2 (T7) |
| W7 | Planning 5: review map, remove Stop -> back to that time gap, no reorder, Start asks for notification permission, one trip Underway | `ScreenItineraryReview` | P1-05; one-Underway rule Q3/UX#8 (T3) |
| W8 | Underway Leg: start from location, transit fare + Chrono with Google Maps fallback, Uber | `ScreenUnderwayLeg`, `TripHandoff`, `CityServices.fares` | Implemented; on-device handoff not verified |
| W9 | Arrival: manual "I've arrived"; notify 30 min after *expected arrival* | `UnderwayNotifier` (+30 min after `stop.start`) | Minor: uses start, not expected arrival (`expectedArrival` returns start, Q12) |
| W10 | At Stop: alert 30 min before **Leave By**; late warning with Go anyway / Skip | `UnderwayNotifier`, `ScreenUnderwayAtStop` | P1-06, P1-07 |
| W11 | End of day: end any time; Itinerary logged; Stamps for confirmed Stops only | `FlowViewModel.endDay` | Stamps OK; "Itinerary logged" not done (no history; P1-11); Back loses stamps (UX#1, T3) |
| W12 | Passport: by city then district; one Stamp per Event / restaurant ever; chain = one | `UnderwayStore.addStamps`, `ScreenPassport` (placeholder) | P1-03 |
| W13 | Itineraries tab: In planning (several) / Past (read-only) | `ScreenItinerary.kt` placeholder; draft only in ViewModel memory | Not built (P1-11) |
| W14 | Data storage: Events/Places bundled behind repository interfaces; Profile/Itineraries/Passport in **Firestore** | Events/Places as specified; Profile/trip/stamps in **local DataStore** | Divergence (P1-13); causes F25 on shared devices and no cross-device sync |
| W15 | Attribution "Data: Has Things, Ville de Montréal, © OSM" wherever Event data is shown | `DataAttribution` on DistrictPick, Selection, Review | Implemented on planning; absent from Underway/Passport if they show event data (S) |
| W16 | Demo mode: hidden date pin, skip to the next moment, trigger alerts | none | Not built (P1-04) |
| W17 | Restaurant ETL rules 1-5 + price/cuisine/dietary semantics (data design) | `etl/`, `data/local`, `PlaceRepository` | Implemented and tested (CLAUDE.md 4-5). Gaps: price `NONE` ranking penalty not applied in `mealOptions` (P1-12); Google venue rules not in the doc (open item 2) |
| W18 | Event ingestion (API research + struct) | bundled `citypacks/mtl/events.json` (built by `tools/build_event_snapshot.py`); live `HasThingsApi` + `FirestoreEventCache` in `viewmodel/{montreal,toronto}` | Two paths; the live one is used by no screen (C: no reference outside `viewmodel/`). P1-09 |
| W19 | City Pack / maps (design .kt copies) | `citypack/`, `ui/components/map/` | Ported. ADR 0001 (build-time packs, no in-app picker) contradicts `prep/design/ItineraryReview_usage.kt` (`switchCity`): the prep file is stale |
| W20 | Google data use (data design section 1, ETL) vs ADR 0002 | `places.db` | P1-01 |

## 4. Dependencies

- Spec chain: `GLOSSARY.md` -> `prep/design/screen-list.md` -> screens/domain. Data chain: `restaurant-data-design-final.md` -> `etl/config/reference.yaml` -> `places.db` -> Room -> `PlaceRepository` -> `ItineraryPlanner.mealOptions`.
- Events: `tools/build_event_snapshot.py` -> `assets/citypacks/mtl/events.json` -> `SnapshotEventRepository` -> `ItineraryPlanner` -> Planning screens.
- Underway: `FlowViewModel` -> `UnderwayStore` (DataStore) <-> `UnderwayReceiver` (alarms) -> screens. **Every W9-W12 fix depends on T1 (atomic store) and T2 (alarm sync).**
- Passport (W12) depends on a Stamp model that carries a city and a venue/chain key. `Stop` has neither a brand nor a chain id; `Place.brand`/`is_chain` exist in the DB.
- Firestore (W14) would require auth-scoped rules (`firestore.rules` has only `eventSources`) and makes sign-out semantics (F25/T5) per account.
- Decision documents: ADR 0001 (build-time city packs), ADR 0002 (no stored Google content), and CLAUDE.md all claim authority; nothing states which one wins over the other (P1-01).

## 5. Confirmed defects

### P1-01: Shipped Google Places content contradicts ADR 0002
- WORKFLOW: W20 / W17. RELATED FILES: `docs/adr/0002-hand-curated-restaurant-data.md`, `app/src/main/assets/databases/places.db`, `etl/mtl_food_etl/google_enrich.py`, CLAUDE.md 5.1.
- CURRENT: `places.db` (commit `53fad78`, 2026-11-21 19:18) contains Google-derived price, rating, opening hours, `photo_ref` and descriptions for 4,761 places, bundled in the APK.
- EXPECTED: ADR 0002 (commit `931e318`, 17:29, same day): hand-curate about 60 places; do not store Google Places content; do not restore the Google step without first checking the terms.
- PROBLEM: two team decisions conflict. Google Maps Platform terms restrict caching of Places content other than place IDs.
- EVIDENCE: git log of both files; CLAUDE.md never mentions ADR 0002. **C** (conflict) / **S** (ToS breach: terms not verified, not legal advice).
- SEVERITY: **CRITICAL** (compliance; City-sponsored public demo). Escalated to team-lead on 2026-11-22.
- RECOMMENDATION: user decides: (a) keep after checking current terms, (b) rebuild `places.db` keeping only `google_place_id` (places-db skill, zero API calls), or (c) follow ADR 0002. Record the outcome as an ADR and in CLAUDE.md. Do not distribute builds until then.
- RESPONSIBLE AGENT: user + team-lead (decision); feature-engineer (rebuild if chosen). REGRESSION TEST: if (b), a `PlacesDatabaseTest` assertion that the Google content columns are empty. RELATED M1 TASK: none (new T15).

### P1-02: 21+ Events shown to 18-24 users
- WORKFLOW: W6. RELATED FILES: `domain/EventRules.kt` `isEligible`, `app/src/test/.../PlannerRulesTest.kt:52-56`.
- CURRENT: hidden only if `minAge > ageRange.max`. For 18-24 the max is 24, so a 21+ Event is **shown**. The test asserts `assertTrue(... minAge = 21 ...)` for `A18_24`.
- EXPECTED: screen-list, "Event eligibility": "Hide Events whose minimum age is above the person's age range, **such as 21+ Events for someone aged 18-24**."
- PROBLEM: the code and its test encode the opposite of the spec's own example.
- EVIDENCE: code plus test lines. **C**.
- SEVERITY: HIGH (age-restricted content offered to underage users).
- RECOMMENDATION: compare with `ageRange.min` (hide if `minAge > min`); flip the test.
- RESPONSIBLE AGENT: feature-engineer. REGRESSION TEST: `PlannerRulesTest`: 21+ hidden for A18_24 and shown for A25_34. RELATED M1 TASK: none (new T16; can join T7's planner work).

### P1-03: Stamp rules not implemented
- WORKFLOW: W12. RELATED FILES: `domain/UnderwayStore.kt` `addStamps`, `EarnedStamp`, `FlowViewModel.newStamps`, `scaffold/screens/passport/ScreenPassport.kt`.
- CURRENT: dedupe key is `stopId + date`, so the same Event on another day earns a second Stamp. Chains are not merged. `EarnedStamp` has no city. The Passport screen shows only the text "Passport" (UX#26).
- EXPECTED: GLOSSARY "Stamp: single, unrepeatable mark ... all locations of a chain counting as one restaurant". screen-list Passport: organised by city then District; one Stamp per Event ever and per restaurant/bar ever; one Passport across cities.
- EVIDENCE: `old.none { it.stopId == n.stopId && it.date == n.date }`. **C**.
- SEVERITY: HIGH (core gamification rule; data written now carries the wrong keys and will need migration).
- RECOMMENDATION: key the Stamp by event id, or by brand key for chain places (`Place.brand`), else place id; add `cityId`; decide now, before T3 writes more stamps.
- RESPONSIBLE AGENT: feature-engineer. REGRESSION TEST: unit test on a pure `mergeStamps(old, new)`: same event on two dates gives 1; two locations of one brand give 1. RELATED M1 TASK: T3 (stamps awarded at the last stop) touches the same code; add the key change there or right after (T17).

### P1-04: Past Events offered; no demo mode
- WORKFLOW: W3, W6, W16. RELATED FILES: `domain/EventRules.kt`, `domain/Planner.kt` `eligible`, `FlowViewModel.FIRST_DATE/LAST_DATE`, `ScreensPlanning.kt:119-122`.
- CURRENT: eligibility and `EventQuery` filter by the window only, never by now. The default date is `FIRST_DATE = 2026-11-21`, already in the past (today 2026-11-22). Alarms for past times are skipped silently by `schedule()`.
- EXPECTED: "Hide cancelled, sold-out and past Events". Demo mode pins the date and triggers alerts.
- EVIDENCE: no `now` in `isEligible` / `selection`. F10 covers only the hard-coded dates. **C**.
- SEVERITY: HIGH (a plan for today can include Events that already started; underway reminders never fire).
- RECOMMENDATION: inject a `Clock` into `ItineraryPlanner` and `FlowViewModel`; filter `start >= now` (or `end > now`); default the date to `max(today, FIRST_DATE)`. Demo mode = a debug-only Clock override, which also makes alarms testable on the emulator (cf. run-android limits).
- RESPONSIBLE AGENT: feature-engineer. REGRESSION TEST: planner test with a fixed Clock: an Event that started 1 h ago is excluded. RELATED M1 TASK: T2 (alarm scheduling uses `LocalDateTime.now`; a Clock there too). New T18.

### P1-05: Removing a Stop deletes all later Stops
- WORKFLOW: W7. RELATED FILES: `FlowViewModel.removeStop`, `ScreensPlanning.kt:438`.
- CURRENT: `while (stops.size > index) stops.removeAt(lastIndex)`: every later Stop is deleted, then the user is sent back to selection.
- EXPECTED: "Remove a Stop, which returns the user to selection for that time gap".
- EVIDENCE: code and KDoc ("drops the later ones too"). **C** (behaviour matches its KDoc, but not the spec). F7 covers the `districtIndex` side effect only.
- SEVERITY: MEDIUM (lost choices; the user rebuilds the rest of the day).
- RECOMMENDATION: remove only that Stop and open selection bounded by the previous departure and the next Stop's start (selection needs a `latestStart`).
- RESPONSIBLE AGENT: feature-engineer; ux-designer to confirm. REGRESSION TEST: ViewModel test: remove stop 2 of 4, then 3 remain, and selection offers only Events fitting the gap. RELATED M1 TASK: T7 (M2) is the natural home; a spec decision is needed first (T19).

### P1-06: Leave reminder uses Planned Departure, not Leave By
- WORKFLOW: W10. RELATED FILES: `services/UnderwayNotifier.schedule`, `ScreensUnderway.kt:182` (`leaveBy = stop.departure`).
- CURRENT: reminder at `departure - 30 min`.
- EXPECTED: GLOSSARY "Leave By: the earlier of its Planned Departure and the time needed to reach the next Stop". Alert 30 min before Leave By.
- EVIDENCE: code. **C**.
- SEVERITY: MEDIUM (the reminder comes too late whenever the Leg is longer than the slack).
- RECOMMENDATION: `leaveBy = min(departure, next.start - leg.totalMinutes)`, computed once in the domain and used by both the notifier and the screen.
- RESPONSIBLE AGENT: feature-engineer. REGRESSION TEST: pure `leaveBy(stop, next, legs)` unit test. RELATED M1 TASK: T2 (same scheduling code).

### P1-07: Late warning uses walking time and never refreshes
- WORKFLOW: W10. RELATED FILES: `ScreensUnderway.kt:177-184`.
- CURRENT: `LegMode.WALK` regardless of transit/Uber choice; `LocalDateTime.now()` is read once at composition.
- EXPECTED: warn when the next Stop can no longer be reached on time.
- EVIDENCE: code. UX#25 already covers the refresh part. **C**.
- SEVERITY: MEDIUM.
- RECOMMENDATION: use `p.useRideHail` to pick the mode; tick every minute (`produceState` with `delay(60_000)`) from the same Clock as P1-04.
- RESPONSIBLE AGENT: feature-engineer. REGRESSION TEST: pure `isLate(now, stop, next, mode)` test. RELATED M1 TASK: T8 is adjacent; proposed T20.

### P1-08: Ticket link never opened
- WORKFLOW: W6. RELATED FILES: `data/Event.kt:12` (`url`), planning/underway screens.
- CURRENT: `Event.url` is parsed but no screen uses it (grep: the only `ACTION_VIEW` is in `TripHandoff`). `Stop` does not carry a url.
- EXPECTED: "Ticket purchases open the Event's own link".
- EVIDENCE: grep. **C**.
- SEVERITY: MEDIUM.
- RECOMMENDATION: add `url` to `Stop`; an "Open tickets" action on the event row / AtStop, opening only `https` URLs, via `startSafely`.
- RESPONSIBLE AGENT: feature-engineer. REGRESSION TEST: `Stop` serialization round-trip keeps `url`; Compose test that the action is present when url != null. RELATED M1 TASK: none (T21).

### P1-10: Bars: the spec contradicts the data design
- WORKFLOW: W6. RELATED FILES: `reference.yaml` `venue_type_slots.BAR: [EVENING]`, `Planner.mealOptions` (`BAR_FROM = 17:00`), `Planner.mealTimes` (EVENING = 22:00).
- CURRENT: bars only fill the EVENING slot (22:00), so a day out (10-18) or a dinner at 18:30 never offers bars; the 17:00 check almost never applies.
- EXPECTED: screen-list: "Bars count as Meals and are available from 17:00, on day and night Itineraries". Data design decision 2: "included, but limited to evening slots".
- EVIDENCE: config plus code. **C** (conflict between two prep documents).
- SEVERITY: MEDIUM (spec ambiguity; the code follows the data doc).
- RECOMMENDATION: user picks one rule; if the screen-list rule wins, add BAR to the DINNER slot in `reference.yaml` (reference data; check whether that needs a DB rebuild) and keep the 17:00 / audience check.
- RESPONSIBLE AGENT: user (decision), feature-engineer. REGRESSION TEST: `MealOptionsTest.barsAreOfferedAtDinnerFrom17hForAdults` (written in T13a, fails today). RELATED M1 TASK: T22.
- **UPDATE 2026-11-22: decided by the user** (ADR 0003, CLAUDE.md section 3): bars from 17:00 including dinner, adults-only audiences only. The conflict is resolved; the code change is T22.

### Shorter confirmed items
- **P1-11 (C, scope gap, HIGH for the spec, not a defect in shipped behaviour):** the Itineraries tab (`ScreenItinerary.kt` shows the text "itinerary"), several plans in Planning, and the "Itinerary logged" history at End of day do not exist. The draft is ViewModel memory only, so process death loses it. Owner: user (scope, T24). Test: none until built.
- **P1-12 (C, LOW):** `mealOptions` ranks only by cuisine match and distance. It does not apply the `price_source = NONE` penalty (data design 7.2), and the "bars" chip (matches `venue_type = bar`) is ignored because the code compares cuisines only. Owner: feature-engineer. Test: `mealOptions` ranking test (T13a).
- **P1-13 (C, MEDIUM, architecture):** the spec stores Profile/Itineraries/Passport in Firestore; the code uses device DataStore. There is no ADR for the change. Owner: user/team-lead (T24). Related: T5.
- **P1-14 (C, LOW):** Review's Start is enabled by `stops.isNotEmpty()` (`ScreensPlanning.kt:420`), so after removals a meal-only trip can start; the spec needs at least one Event. Owner: feature-engineer (with T19). Test: ViewModel/Compose test.

## 6. Risks (suspected)

### P1-09: The Firestore event cache can be rewritten by any signed-in user (S)
- WORKFLOW: W18. RELATED FILES: `firestore.rules`, `data/repository/FirestoreEventCache.kt`, `viewmodel/{montreal,toronto}/*EventsViewModel.kt`.
- CURRENT: `events/{eventId}` allows `create, update` for any authenticated user, forever (the comment says "only needed during the first fill"). Fields are type-checked only. The marker doc is create-only, so the first writer's data is final and is never refreshed.
- EXPECTED: client-side caches should not let one user change what all users see; event data should be refreshable.
- PROBLEM: a signed-in user could change any cached event's title or url (phishing through "tickets", P1-08). Data also goes stale forever. Today no screen uses this path (C), so the impact is latent.
- EVIDENCE: rules text. Whether these rules are deployed is **NOT VERIFIED** (no `firebase.json`).
- SEVERITY: MEDIUM (HIGH once the live path or ticket links are used).
- RECOMMENDATION: either delete the unused live path, or make `events` writes create-only and server-side (Cloud Function / admin script), with a TTL/refresh. Keep the planner on the bundled snapshot (CLAUDE.md 7: no live Google; data in assets).
- RESPONSIBLE AGENT: team-lead (decide), feature-engineer. REGRESSION TEST: Firestore emulator rules test (needs tooling approval). RELATED M1 TASK: none (T23).

Other risks:
- R1 (S): stale prep code copies (`prep/design/*.kt` with package `com.ets.hackapp`, `geojson_to_districts(1).py` without the UTF-8 fix) can be copied back over working code by a teammate or an agent. LOW. Mark prep/design as reference-only or delete the copies (user decision; prep is the team's folder).
- R2 (C): `screen-list.md` says Profile/Itineraries/Passport live in Firestore; code uses device DataStore (P1-13). Users who switch phone lose everything; two accounts on one phone share data (F25/T5).
- R3 (C): `GLOSSARY.md` terms (Leave By, Stamp) are stricter than the code; no test pins them.
- R4 (S): Attribution may be missing on screens that show event data outside Planning (Underway, Passport). LOW.

## 7. UX (referencing ux-designer)

Already covered by ux-designer: UX#1, UX#8 (one-Underway rule, T3), UX#25 (late warning refresh, part of P1-07), UX#26 (Passport placeholder, part of P1-03), UX#30 (F25).
New from the spec comparison: P1-05 (remove Stop loses later choices), P1-08 (no ticket path), P1-11 (no Itineraries tab, no saved plans; "Itineraries in Planning (several)" on Home is impossible while the draft lives only in memory, so process death loses the draft), P1-04 (past events in a plan).
ux-designer input (2026-11-22, their ids; not repeated in full here):
- P1-05 is now **UX#36 (P1)**: the on-screen note `review_remove_note` promises "back to selection for that time slot", so users lose stops without warning. Fix: remove only that stop, or confirm ("also removes the N stops after it").
- P1-11: **P1**. The spec's "plan now, go later" journey does not exist. ux note P1-2: the draft is also lost by tapping Plan again (`newDraft`).
- P1-03: dedupe rule **P1**; the Passport placeholder stays **P2, UX#26**.
- P1-08: **P2** (ux note P2-5). Event cards show only title, time and price (`ScreensPlanning.kt:322-331`), with no venue, description or link.
- P1-07: **P2, UX#25**, widened to cover the walk-only estimate.
- P1-04: past default date is **P1, new UX#37** (default to today, clamped to the snapshot range). Demo mode: **P2 for users, P1 for the hackathon demo**, because alarms are 30 min out and inexact.
New divergences from ux-designer, accepted into this audit:
- **P1-15 (C, HIGH): no "end the day early".** The spec says "after the last Stop, or at any time to end early". No Underway page has an End action; EndOfDay is reached only when `finished` (`ScreensUnderway.kt:84`, `AppNavigation.kt:182`). Owner: feature-engineer; relates to T3. Test: navigation/ViewModel test that End from Leg/AtStop awards the confirmed stamps.
- **P1-16 (C, MEDIUM): District preview tags show raw enum names** (`ScreensPlanning.kt:209` maps `it.key.name`), so "Music"/"Comedy" appear untranslated in French; the spec also asks for a short description. Fix: use `stopTypeLabel(type)`. Owner: feature-engineer (fits T12).
- P2-7 confirms R4: attribution is missing on Leg, Arrival, AtStop and EndOfDay, which show event titles. Severity LOW (C).
- P2-10: selection does not show how much of the window is left or where the meal block falls. Suggestion.
- Research brief vs built: onboarding step 2 packs 4 questions; there is no "you can change this later" reassurance (untrue until Settings editing works, UX#4); step 1 has no real choice while Resident is disabled (suggest auto-skip). Swipe cards and the full-bleed map were reasonably not adopted.

## 8. UI (referencing visual-designer)

The spec implies states with no design yet: Passport (organised by city/district, empty state), Itineraries tab (In planning / Past / empty with "plan" button), replace-active-trip dialog (`docs/ux/spec-replace-active-trip.md`), loading while `events.json` parses (F28), and a ticket action. visual-designer input (2026-11-22; A3-n / D1-D6 ids from `docs/design/initial-audit.md`):
- **ErrorState exists (`StateViews.kt`) but no screen uses it: P1.** No error is surfaced anywhere in the planning flow.
- **No events / no meals use EmptyState with only the generic title** (`ScreensPlanning.kt:293,338,378`): no cause, no action. P1 (visual and copy): add a body ("no events in this window", "no restaurants open at this time") and an action (change time, next district, skip meal).
- **Passport and Itineraries tabs: raw hard-coded English `Text`, no padding: P1 for a demo**; they should use EmptyState with the screen-list action ("plan an itinerary").
- First DistrictPick load: a spinner inside the column, then the layout jumps to full map height. P2: reserve the map's aspect box with a placeholder.
- Underway after Start: loading must use LoadingState, not EmptyState (F15, P1; ties to T1 "navigate after the write").
- Step indicator: covered by A3-2 + D1 (T11).
- Dialogs: the replace-trip AlertDialog needs no new visuals; if a stop-removal confirm or undo is added (UX#36), use the same pattern, not a red destructive button.
- Cross-cutting: state views have no icon or container (P2). D6 (pending): primary on canvas is 4.25:1 and fails AA for text.
These map to T12 (empty/error states) and the scope task T24 (Passport/Itineraries).

## 9. Architecture

- A1 (C): two event pipelines: the bundled snapshot (used by the planner) and live Has Things + Firestore cache (`viewmodel/`, unused by screens). Recommendation: decide one (keep the snapshot per CLAUDE.md 7 and the spec's "bundled with the app"), and delete or quarantine the other (P1-09).
- A2 (C): persistence diverges from the spec (P1-13: DataStore vs Firestore). This needs an ADR either way; it changes T5 semantics (sign-out clears local data vs it is per-account remote data).
- A3 (C): time is read with `LocalDateTime.now` in the notifier, screens and AtStop. A single injectable `Clock` would enable P1-04 (past filter + demo mode), P1-07 and deterministic tests.
- A4 (C): domain terms (Leave By, Stamp key, expected arrival) are computed in screens or not at all. Move them into pure domain functions so notifier and screens agree (P1-06).
- A5 (C): decision-record precedence is undefined: CLAUDE.md says the data design is final; ADR 0002 overrides part of it; CLAUDE.md does not list ADRs. Add a line to CLAUDE.md: "ADRs in `docs/adr/` record decisions; on conflict ask the user" (team-lead).

## 10. Test gaps (spec rules with no test)

| Rule (source) | Test today | Needed |
|---|---|---|
| 21+ hidden for 18-24 (screen-list) | **Wrong test** (`PlannerRulesTest:55`) | Flip and add A25_34 case (P1-02) |
| Past Events hidden | none | Clock-based planner test (P1-04) |
| Reachability = departure + Leg + buffer | none (`selection` untested; already in T13a) | T13a |
| Bars from 17:00 / audience | none | T13a `mealOptions` (P1-10) |
| Meal Block holds back Events / no overlap | none | T13a (F2) |
| One Stamp ever / chain once | none | `mergeStamps` test (P1-03) |
| Leave By = min(departure, next start - leg) | none | pure function test (P1-06) |
| Late warning | none | `isLate` test (P1-07) |
| Remove Stop keeps the others | none | ViewModel test (P1-05) |
| Finish needs at least one Event | Selection enforces it (`ScreensPlanning.kt:289`); Review Start only checks `stops.isNotEmpty()` (`:420`), so a meal-only trip can start after removals | ViewModel/Compose test (LOW, P1-14) |
| Firestore rules | none | emulator rules test (P1-09) |
| Attribution shown where event data is | none | Compose test (LOW) |

## 11. Improvements (non-defects)

- Delete or archive `prep/api-business/test.txt`, `prep/design/test.txt`, `prep/api-event/thing.md`; mark `prep/design/*.kt` and the old `.py` as reference-only (user's folder; ask first).
- Rank `price_source = NONE` places slightly lower in `mealOptions` (data design 7.2) and use the "bars" cuisine chip as a bar preference (P1-12, LOW).
- Arrival check at expected arrival instead of `stop.start` (W9, LOW; fix `expectedArrival` KDoc vs code, Q12).
- Add "spec source" comments (screen-list section) on the domain rules, so future audits can trace them.

## 12. Relation to M1

| M1 task | Relation to prep findings |
|---|---|
| T0 baseline commit | No conflict. Commit before any P1 work |
| T6 lint + notification locale | None |
| T13a failing tests | **Extend**: add P1-02 (flip the 21+ test, as a failing test), P1-03 `mergeStamps`, P1-06 `leaveBy` (if extracted), P1-10 bars, with `mealOptions` and `selection` as planned. These are spec-derived and need no production change to write, except the pure functions, which will fail until they exist |
| T1 atomic store | Prerequisite for P1-03 (stamp writes) |
| T2 alarm sync | **Overlaps** P1-06 (Leave By) and P1-04 (Clock). Do them in T2 or right after, to avoid touching `UnderwayNotifier.schedule` twice |
| T3 stamps at last stop | **Overlaps** P1-03 (stamp key). Decide the key before T3 writes stamps, or T3 bakes in the wrong dedupe |
| T5 sign-out clears data | Depends on the P1-13 decision (local vs Firestore). Local clearing is still right for now |
| T4, T8, T9 | No conflict |
| Conflicts | None blocks M1. P1-01 is a release blocker, not an M1 blocker |

## 13. Remediation plan (proposed; needs user approval, not added silently)

| # | Task | Findings | Owner | Depends | Acceptance | Reviewer | Priority |
|---|---|---|---|---|---|---|---|
| T15 | Decide Google data vs ADR 0002; act on it | P1-01, A5 | user + team-lead; feature-engineer if rebuild | none | ADR written; `places.db` consistent with it; CLAUDE.md updated | workflow-auditor | **CRITICAL: before any demo or distribution** |
| T16 | Age rule: hide if minAge > age band min | P1-02 | feature-engineer | T13a | flipped test passes | workflow-auditor | HIGH, in M1 next to T7, or right after T6 (small) |
| T17 | Stamp key: event id / brand key / place id, plus cityId | P1-03 | feature-engineer | T1; with T3 | `mergeStamps` tests pass | workflow-auditor, ux-designer | HIGH, together with T3 |
| T18 | Injectable Clock; filter past Events; default date >= today; debug demo mode | P1-04, A3 | feature-engineer | T2 | Clock-based tests; emulator alarm check via run-android | workflow-auditor | HIGH |
| T19 | Remove Stop = that Stop only; reselect within the gap | P1-05 | feature-engineer | T7, ux-designer confirmation | ViewModel test | ux-designer, workflow-auditor | MEDIUM (M2) |
| T20 | Leave By and late warning in the domain; correct mode; ticking clock | P1-06, P1-07 | feature-engineer | T2, T18 | pure function tests | workflow-auditor | MEDIUM (with T2/T8) |
| T21 | Ticket link on Stop | P1-08 | feature-engineer | T23 decision if live data | round-trip + Compose test | ux-designer, visual-designer | MEDIUM |
| T22 | Bars rule decision | P1-10 | user, then feature-engineer | none | `mealOptions` test per the decided rule | workflow-auditor | MEDIUM |
| T23 | One event pipeline; lock down or remove the Firestore cache | P1-09, A1 | team-lead decides | none | rules not client-writable, or path removed | workflow-auditor | MEDIUM |
| T24 | Scope decision: Itineraries tab, saved plans, Passport screen, Firestore persistence | P1-11, P1-13, R2 | user | M1 | ADR + new milestone | team-lead | Plan for M3+ |

Out of scope for now (needs the user): prep folder clean-up (R1), Firestore emulator tooling for rules tests.

### User decisions (2026-11-22, relayed by team-lead)
| Task | Decision |
|---|---|
| T15 Google data vs ADR 0002 | **Open.** The user decides with the team. `places.db` stays untouched and builds are not distributed (CLAUDE.md open item 11) |
| T16 age rule | **Approved and scheduled** (next after T13a). Failing test: `SpecEligibilityTest.aTwentyOnePlusEventIsHiddenFor18To24`; flip `PlannerRulesTest:55` in the same change |
| T17 Stamp key | **Approved, done with T3.** Failing test: androidTest `UnderwayStoreTest.theSameEventNeverEarnsTwoStamps` (through `addStamps`, so it compiles today); T17 extracts a pure `mergeStamps` and adds its unit test |
| T18 Clock / past events / demo mode | **Approved, after T2** |
| T22 Bars | **Approved**: from 17:00 including dinner, adults-only audiences only (ADR 0003). Failing test: `MealOptionsTest.barsAreOfferedAtDinnerFrom17hForAdults` |
| T19, T20, T21, T23, T24 | Proposals; not scheduled yet |
| P1-15, P1-16 (from ux-designer) | New; proposed: P1-15 with T3 (end the day early), P1-16 with T12 (translated tags) |

**P1 status: completed** (sections 7 and 8 contain the ux-designer and visual-designer input). Label: CHANGES REQUESTED for the app against the prep spec; the open release blocker is T15.
