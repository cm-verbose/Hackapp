# Demo mode contract

Owner: Domain Engineer, team Demo. Package `com.example.hackapp.underway`. The visible control and its Settings entry wait for the product owner's approval of the maquette.

## Goal

Start any planned Itinerary whatever its date, move through the day quickly, and earn real Stamps in the Passport.

## Pieces

| Piece | Role |
|---|---|
| `DemoModeRepository` | Persists the `DemoAnchor` (simulated instant and the real instant it was set). Read synchronously at process start |
| `DemoClock` | The one `UnderwayClock` (`HackappApplication.underwayClock`). Real time when off. When on, runs forward from the anchor |
| `DemoController` | `enable`, `disable`, `startFor`, `nextMoment`, `observe`. `HackappApplication.demoController` |
| `DemoControlsViewModel` | Thin holder for the future control: `state` (`enabled`, `simulatedNow`, `canAdvance`), `nextMoment()`, `enable()`, `disable()` |

Demo mode is on exactly when `clock.isSimulated` is true. There is no second flag.

## Time

- `clock.now()`: simulated when on, real when off.
- `clock.realNow()`: always real.
- `clock.planningDate()`: the pinned date, Sat Nov 28, 2026, when on. It is the default for a new draft.
- `clock.planningNow()`: the first day of event coverage when on, so no Event of any date reads as past while planning.
- The simulated clock keeps running from its anchor, including while the app is closed. A wall clock moved backward holds it at the anchor.

## Rules

| Situation | Rule |
|---|---|
| Enable | Jumps to Nov 28, 10:00. **Refused with `RealItineraryUnderway` while this account has an Itinerary Underway** |
| Start in demo mode | No date check. The clock jumps to the first Stop's Leave By from the District of stay, or 30 minutes before the first Stop without an origin. This can be just before the window, because the first Leg happens before it |
| Start while another is Underway | Same warning as outside demo mode. The clock does not move until it is confirmed, and the old Itinerary ends at its own time |
| Demo run | `UnderwayState.demoRun = true`. It reads simulated time. **It never schedules a real alarm** and does not ask for exact alarms |
| Real Itinerary | Always reads real time and keeps its real alarms, even while the clock is simulated (for example, another account on the device) |
| Next moment | Jumps to `UnderwayEngine.nextMomentAfter`, then runs `Tick` at once, so due alerts show immediately as normal notifications. Past the automatic end, it ends the day |
| Draft archiving | Paused while demo mode is on, so any planned Itinerary stays startable. It resumes on the next launch after disabling |
| Disable | Ends every unfinished demo run at its simulated time (`EndItinerary(confirmed = true)`), awards its Stamps, then returns to real time. If that fails, demo mode stays on and Disable can be retried |
| Demo run with no demo clock (lost settings) | Ended on the next app open |

## Stamps

Demo Stamps are real Passport Stamps and stay after disabling. They follow the normal rules (Meal arrival, 20% for Events, one Stamp per identity). `PassportStamp.date` is the Itinerary's date. `awardedAtMillis` is **real** time, so a simulated date never sorts above Stamps earned later.

## API

```kotlin
val demo = (context.applicationContext as HackappApplication).demoController

demo.enabled                                        // Boolean
demo.enable(uid): DemoEnableResult                  // Enabled | RealItineraryUnderway(current)
demo.disable()                                      // throws on storage failure: retry
demo.startFor(uid, itineraryId, origin, confirmEndCurrent = false): UnderwayStartResult
demo.nextMoment(uid): DemoAdvance                   // Moved(at, dayEnded) | NothingUnderway | NotEnabled
demo.observe(uid): Flow<DemoStatus>                 // enabled, simulatedNow, canAdvance
```

Review needs no extra call: its Start passes `demoMode = clock.isSimulated` to `coordinator.start`, which applies these rules.

For the control, in a Composable:

```kotlin
val demo: DemoControlsViewModel = viewModel(factory = DemoControlsViewModel.factory(uid, app.demoController))
val state by demo.state.collectAsState()   // enabled, simulatedNow, canAdvance
demo.nextMoment()
```

`demo.lastResult` reports `DemoEnableResult.RealItineraryUnderway` so the control can explain a refused enable.
