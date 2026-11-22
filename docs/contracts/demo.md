# Demo mode contract

Owner: Domain Engineer, team Demo. Package `com.example.hackapp.underway`. The product owner approved the maquettes and these rules.

## Goal

Start any planned Itinerary whatever its date, move through the day quickly, and earn real Stamps in the Passport.

## Pieces

| Piece | Role |
|---|---|
| `DemoModeRepository` | Persists the simulated time. Read synchronously at process start |
| `DemoClock` | The one `UnderwayClock` (`HackappApplication.underwayClock`). Real time when off. When on, simulated time runs only in the foreground |
| `DemoController` | `enable`, `exit`, `startFor`, `nextMoment`, `observe`. `HackappApplication.demoController` |
| `DemoControlsViewModel` | Holder for the UI: `state` (`enabled`, `simulatedNow`, `canAdvance`), `lastResult`, `actionFailed`, `exitFailed`, `enable()`, `nextMoment()`, `exit()`, `retry()` |

Demo mode is on exactly when `clock.isSimulated` is true. There is no second flag.

## Time

- `clock.now()`: simulated when on, real when off.
- `clock.realNow()`: always real.
- `clock.planningDate()`: `CityPack.demoDate` when on (Sat Nov 28, 2026 for Montréal). It is the default for a new draft.
- `clock.planningNow()`: the first day of Event coverage when on, so no Event of any date reads as past while planning.
- **The simulated clock runs only while the app is in the foreground.** It pauses when the last Activity stops and restarts paused at its saved time after process death. A demo left overnight does not move, so it cannot auto-end.
- The time is saved on every jump, every Underway event of a demo run and every pause, so a restart is never earlier than a recorded arrival or departure.
- Foreground time is measured with `elapsedRealtime`, so a wall-clock change never moves simulated time.
- `coordinator.displayNow(uid)` is the time an Underway page draws: simulated for a demo run, real for a real Itinerary.

## Rules

| Situation | Rule |
|---|---|
| Enable | Jumps to `CityPack.demoDate`, 10:00, and reopens this account's archived drafts. **Refused with `RealItineraryUnderway` while this account has a real Itinerary Underway, even if demo mode is already on.** A demo run does not count |
| Accounts | Demo mode belongs to the account that enabled it. **Signing out exits demo mode** under the coordinator lock, ending that person's run with Stamps kept. If sign-out did not finish (process death, expired session), the next account's app open exits it first. Another account can never start in it |
| Start in demo mode | No date check. The clock moves to the first Stop's Leave By from the District of stay, in the default Leg mode (walking inside a District, otherwise transit), or 30 minutes before the first Stop with no origin. The time stays on the Itinerary's date and before its window ends, so the normal Start rule would accept it too |
| Start while another is Underway | Same warning as outside demo mode. The clock does not move until it is confirmed, and the old Itinerary ends at its own time. Start is the only move that can go backward, because the next Itinerary may be on an earlier date |
| Demo run | `UnderwayState.demoRun = true`. It reads simulated time, **never schedules a real alarm** and does not ask for exact alarms. Legs use the same default mode as real Itineraries |
| Real Itinerary | Always reads real time and keeps its real alarms, even while the clock is simulated (for example, another account on the device) |
| Moment suivant | Goes to the next of: alert, Leave By, expected arrival, Stop start, Planned Departure of the last Stop, automatic end. **It never moves backward.** A Late warning 1 second after a Leave By shares its press, so it never advances by only 1 second. It then runs `Tick`, so due alerts show immediately as normal notifications. It does not confirm arrivals or departures: the person still acts |
| Draft archiving | Paused while demo mode is on, so any planned Itinerary stays startable |
| Exit demo mode | See below |
| Demo run with no demo clock (lost settings) | Ended on the next app open, at the later of real time and its latest recorded time |
| Run with missing state | A real run is never marked as a demo run. Only the Itinerary recorded by demo Start is rebuilt as one |
| Last-change time | `updatedAtMillis` stays real for demo runs, so sorted lists stay truthful |

## Exit demo mode (Underway menu)

`exit(uid)` does the following, in order:

1. **An unfinished run ends now, at its simulated time.** Stops already visited stay visited, and the remaining Stops become Skipped. The Itinerary moves to Past and is not resumable. Its reason is `EndedEarly`, or `Finished` when no Stop remained.
2. **Its Stamps are kept**, under the normal rules, and stay in the Passport.
3. Reopened drafts whose date has passed in real time return to Archived.
4. The clock returns to real time.

Exit cannot be cancelled once it holds the lock. If storage fails, `exit` throws and demo mode stays on, so it can be retried (`exitFailed` in the holder). A retry also finishes an Itinerary a failed exit left Underway. Sign-out is the exception: the clock always stops, and a run that could not be saved ends on its owner's next app open. When a run was Underway, the Underway page then shows End of day, because `UnderwayViewModel` loads it once the Itinerary is no longer Underway. Show a confirmation only when `state.canAdvance` is true, since only then is a run ended.

## Stamps

Demo Stamps are real Passport Stamps and stay after exit. They follow the normal rules (Meal arrival, 20% for Events, one Stamp per identity). `PassportStamp.date` is the Itinerary's date. `awardedAtMillis` is **real** time, so a simulated date never sorts above Stamps earned later.

## API

```kotlin
val app = context.applicationContext as HackappApplication
val demo = app.demoController

demo.enabled
demo.enable(uid): DemoEnableResult    // Enabled | RealItineraryUnderway(current)
demo.exit(uid)                        // throws on storage failure: retry
demo.startFor(uid, itineraryId, origin, confirmEndCurrent = false, originDistrictId = null): UnderwayStartResult
demo.nextMoment(uid): DemoAdvance     // Moved(at, dayEnded) | NothingUnderway | NotEnabled
demo.observe(uid): Flow<DemoStatus>   // enabled, simulatedNow, canAdvance
```

Review needs no extra call: its Start passes `demoMode = clock.isSimulated` to `coordinator.start`.

For the UI:

```kotlin
val vm: DemoControlsViewModel = viewModel(factory = DemoControlsViewModel.factory(uid, app.demoController))
val state by vm.state.collectAsState()   // enabled, simulatedNow, canAdvance
vm.enable()         // vm.lastResult reports RealItineraryUnderway
vm.nextMoment()     // Moment suivant
vm.exit()           // vm.exitFailed reports a failure
vm.actionFailed     // StateFlow<Boolean>: enable or nextMoment failed, show demo_error with Retry
vm.retry()          // repeats the action that failed last, including exit
```

Any new attempt clears `actionFailed` and `exitFailed`. A refused enable (`RealItineraryUnderway`) is not a failure. If `nextMoment` fails after the clock has moved, Retry goes to the following moment, and alerts already due still fire.
