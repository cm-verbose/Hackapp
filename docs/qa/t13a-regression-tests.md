# T13a: failing regression tests (workflow-auditor, 2026-11-22)

Label: **APPROVED as a reproduction suite.** Each test that is meant to fail does fail on current code, at assertion level, for the documented reason. Every characterisation test passes. This label is about whether the tests reproduce the defects; it does not mean "tests passed".

## Commands (run by workflow-auditor, in this order; Gradle then handed to feature-engineer)
- `.\gradlew.bat testDebugUnitTest --continue` -> `BUILD FAILED in 13s` (test failures only). 45 tests, 3 failures, 0 errors.
- `.\gradlew.bat connectedDebugAndroidTest` (emulator-5554, Medium_Phone API 37) -> `BUILD FAILED in 1m 2s` (test failures only). 17 tests, 3 failures, 0 errors.
- An earlier attempt had one **compilation failure** in my own test (`MealOptionsTest.kt:83`, missing `CuisineGroup.venueType`). I fixed it and reran; that is not a product defect. In the first connected run the alarm test counted 6 instead of 4, because the regex also matched `dumpsys alarm` history sections. It now counts only the "pending alarms" section (test-infrastructure fix). The rerun gives the value the code predicts (4).

## Classification of every non-passing test (rerun)
| Test | Class | Actual | Expected behaviour | Finding | Fixed by |
|---|---|---|---|---|---|
| `MealOptionsTest.barsAreOfferedAtDinnerFrom17hForAdults` | expected regression failure | BAR not in the venue types queried for an 18:30 dinner (AssertionError) | Bars from 17:00 including dinner, adults only (ADR 0003) | P1-10 | T22 |
| `PlannerSelectionTest.eventsOverlappingAnUnchosenMealBlockAreHeldBack` | expected regression failure | `expected:<[]> but was:<[overlaps]>` (an 11:00-12:30 event is offered against a 11:45-13:15 block) | Events overlapping an unchosen Meal Block are held back | F2 | T7 |
| `SpecEligibilityTest.aTwentyOnePlusEventIsHiddenFor18To24` | expected regression failure | 21+ event is eligible for A18_24 (AssertionError) | Hidden (screen-list example) | P1-02 | T16 (also flip `PlannerRulesTest:55`) |
| `UnderwayStoreTest.concurrentUpdatesAreNotLost` | expected regression failure | `round 0 expected:<40.0> but was:<1.0>`: 39 of 40 updates lost | All concurrent updates land | Q1 / F14 | T1 |
| `UnderwayStoreTest.theSameEventNeverEarnsTwoStamps` | expected regression failure | `expected:<1> but was:<2>` | One Stamp per Event ever | P1-03 | T17 (with T3) |
| `UnderwayStoreTest.schedulingANewTripRemovesTheOldTripsAlarms` | expected regression failure | `expected:<1> but was:<4>`: old trip's arrival checks for stops 1-2 and leave reminder for stop 1, plus the new alarm. Its first assertion (5 alarms for the 3-stop trip) passes | Only the new trip's alarms remain | Q2 / F13 | T2 |

Unexpected failures: none. Compilation failures: none in the rerun. Infrastructure failures: none in the rerun.

## Tests meant to fail that passed
None. All 6 tests marked "EXPECTED TO FAIL" in their KDoc failed.

## Characterisation tests (meant to pass on current code; they guard the fixes against regressions)
- `PlannerSelectionTest` (6): window start, reachability = previous departure + Leg + buffer, chosen events excluded, 1-hour grouping, events starting inside the block held back, block released once the meal is chosen.
- `MealOptionsTest` (7): evening bars for adults, hidden for family-friendly / no-alcohol, hidden before 17:00, budget/openAt/venue types passed to search, district-only without anchor, centroid of district places, cuisine interests rank first without hiding.
- `UnderwayProgressTest` (5): initial state, arrived then confirmed, leave-and-skip-next, last stop finishes and further calls are no-ops, JSON round-trip (LocalDateTime, null location).
- `SpecEligibilityTest` (2): 21+ shown from 25, 18+ shown to every band.

## Race test design (Q1)
- 5 rounds; each round saves fresh progress, starts 40 writers on `Dispatchers.Default` behind a `CompletableDeferred` start gate, then asserts exactly 40 increments. On current code round 0 already fails with 1.0.
- `store.current()` is `progress.first()` over `store.data`, which emits the last value DataStore committed to its file. It is not a copy kept by the test or the store. A "fresh instance" read cannot bypass DataStore's process-wide singleton (`preferencesDataStore` delegate) without a process restart. After T1, the 5-round pass plus a repeated connected run is the acceptance; a cold-process read-back would need a separate instrumentation run.

## Side effects and limits
- The instrumented tests use the app's real `trip` DataStore and alarms. Each run wipes any trip in progress on the device and adds two test Stamps with unique ids, `test-evt-*`. There is no API to delete Stamps. Run on the emulator only.
- Not covered on purpose: P1-06 (Leave By) and P1-04 (past events / Clock), which wait for T18/T20 APIs; F12 (arrival alarm after Arrived/Skip), which needs T2's API. I'll add them when those signatures exist.

## Files (new, test-only)
- `app/src/test/java/com/example/hackapp/domain/{PlannerFakes,PlannerSelectionTest,MealOptionsTest,UnderwayProgressTest,SpecEligibilityTest}.kt`
- `app/src/androidTest/java/com/example/hackapp/domain/UnderwayStoreTest.kt`
