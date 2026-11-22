# Underway contract (state machine and alerts)

Owner: Domain Engineer, team Underway. Package `com.example.hackapp.underway`. UI teams build pages against this file. Strings are in `underway-strings.md`.

## Pieces

| Piece | Role |
|---|---|
| `UnderwayEngine` | Pure Kotlin. `reduce(ctx, state, event, now) -> Transition(state, effects, rejection)`. No clock, no Android |
| `UnderwayClock` | `now(): Instant`, `zone`. Everything reads time here, never `Instant.now()`. A demo clock replaces `SystemUnderwayClock` later |
| `UnderwayAlertScheduler` | `replaceAll(itineraryId, alerts)`, `cancelAll`, `canScheduleExact()`. Android and fake implementations |
| `UnderwayNotifier` | Shows and cancels notifications. Android and fake implementations |
| `UnderwayRepository` | Persists one `UnderwayState` per Itinerary (DataStore and in-memory) |
| `UnderwayCoordinator` | Loads, runs the engine, saves, syncs the Itinerary, reschedules alerts, awards Stamps. **Pages call only this** |
| `UnderwayEngine.nextMomentAfter(ctx, state, time)` | The next alert time, Stop start, Leave By or automatic end after `time`. A demo clock jumps to it |

## State

`UnderwayState.phase`: `Leg` (heading to `currentStopId`), `Arrival` (a transit or Uber app was opened, not yet arrived), `AtStop`, `Ended`.

```
Start -> Leg -> Arrival -> AtStop -> Leg (next Stop) ... -> AtStop (last) -> Ended
```

- `transport` (`Transit` or `Uber`): first Leg `Transit`, later Legs keep the last choice. Leave By always uses the transit estimate.
- Each Stop has a `StopRecord`: `arrivedAtMillis`, `leftAtMillis`, `skippedAtMillis`, `nextActionAtMillis`.
- State is saved after every event. After process death, `coordinator.onAppOpen(uid)` resumes it, so Home shows the Underway card.
- The Itinerary is synced from the state after every event: arrived Stops are `Confirmed`, skipped and unvisited Stops `Skipped`. The Itinerary becomes `Completed` at the end. A visited Event that was too brief stays `Confirmed` (Past shows it visited), so **never recount Stamps from Itinerary statuses: use `EndOfDay`**.

## Events (`UnderwayEvent`)

| Event | Allowed in | Result |
|---|---|---|
| `OpenTransport(choice, stopId?)` | Leg, Arrival | `Arrival`, records the choice. Does not assume they left. A `stopId` that is not the current Stop is rejected with `StaleStop` |
| `ReturnToLeg` | Arrival | `Leg`, to change transport |
| `ConfirmArrival(stopId?)` | Leg, Arrival | `AtStop`, records `arrivedAt` (late arrival is only recorded, never replans) |
| `Leaving(stopId?)` | AtStop, not last Stop | Next `Leg`. Rejected on the last Stop |
| `SkipStop(stopId?, fromNotification = false)` | Leg, Arrival (current Stop). AtStop: only the **next** Stop, from the late warning | Marks it skipped, then next `Leg`. Skipping the last remaining Stop ends the day, so a `fromNotification` skip of it is rejected with `NeedsConfirmation` and the page asks first |
| `GoAnyway(stopId)` | AtStop | Dismisses the late warning for that Stop |
| `EndItinerary(confirmed)` | Any non-ended | Last Stop at AtStop: ends, no confirmation. Otherwise `confirmed = false` returns `Rejection.NeedsConfirmation`, and `true` ends and marks remaining Stops skipped |
| `NotificationBannerHandled` | Any | Marks the notifications-off banner as shown |
| `Tick` | Any | Fires due alerts and the automatic end. Sent by alarms, app open and boot |

`stopId` guards stale notification taps: if it is not the current Stop, the event is rejected with `Rejection.StaleStop` and changes nothing. Notification actions always pass it. Other rejections: `WrongPhase`, `Ended`, `NeedsConfirmation`.

Nothing else can change an Underway Itinerary (no add, move or edit). A Stop is never skipped automatically while the Itinerary is active.

## Coordinator API (suspend, one mutex)

- `start(uid, itineraryId, origin: LatLng?, demoMode = false, confirmEndCurrent = false): UnderwayStartResult` is `Started(view, askExactAlarm)`, `Blocked(StartBlock)`, `NeedsConfirmation(current)` or `NotFound`. It ends and awards the current Underway Itinerary first when `confirmEndCurrent`. `askExactAlarm` is true once, at Start, when `canScheduleExact()` is false: show `underway_exact_alarm_rationale`, launch `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` on Allow. A refusal is fine: alerts fall back to normal ones and Start is never blocked.
- `dispatch(uid, itineraryId, event): DispatchResult(view, rejection)`.
- `observeView(uid, notificationsAllowed: () -> Boolean): Flow<UnderwayView?>` emits on every state change; `view(uid, notificationsAllowed)` reads once. Neither ticks: `UnderwayViewModel` adds a minute ticker on the injected `UnderwayClock`.
- `onAppOpen(uid)`, `onAlarm(uid, itineraryId)`, `onBoot(uid)`: tick, reschedule and retry unfinished Stamp awards. `onBoot(null)` does nothing: only the signed-in account is rescheduled. `onSignOut(uid)` cancels that account's alarms and notifications; `rememberSignOut` calls it.
- `endOfDay(uid, itineraryId): EndOfDay?`: null until Ended. Awards Stamps if not yet done. Retry-safe. If the Passport cannot be read, `EndOfDay.stampsAvailable = false` and every `stamp` is null: show the results without Stamps and call again later. Nothing is lost.

## `UnderwayView` (what pages draw)

`phase`, `currentStop`, `legFrom` (previous Stop or null), `expectedArrival`, `leaveBy` (Leg: when to leave to arrive on time, null for the first Leg when the District of stay is unknown; AtStop: this Stop's Leave By), `nextStop`, `isLastStop`, `transport`, `remainingStops`, `lateWarningStop` (AtStop, next Stop that can no longer be reached on time and was not dismissed), `endNeedsConfirmation`, `showNotificationBanner` (notifications off and not yet handled for this Itinerary), `endedReason`.

## End of day (`EndOfDay`)

`results: List<StopDayResult(stop, outcome, stamp, arrivedLateMinutes)>` in Stop order. `outcome`: `Visited`, `Skipped`, `TooBrief`. `stamp`: `StopStampResult` (`NewStamp`, `AlreadyInPassport`, `NoStamp`) or null when the Passport is unavailable. Map to strings as in `underway-strings.md`: Visited and NewStamp = `end_of_day_stamp_new`; TooBrief = `end_of_day_too_brief`.

## Rules

- **Stamp rule.** Meal: arrival confirmed. Event: overlap of the stay with the expected period must be at least 20% of it (inclusive, in milliseconds). Expected period = `Stop.start` to Planned Departure for every Event (Selection sets `start` to the arrival for a drop-in). Time before the start does not count. Stay = arrived to `leftAt`. With no `leftAt` it runs to the next user action (any accepted event except Tick and NotificationBannerHandled), or the automatic end, capped by the expected end.
- **Award.** At End of day only, including automatic and replaced endings. The engine builds a Completed copy of the Itinerary where too-brief Events are `Skipped`, then calls `StampRepository.award`. `PassportUnavailableException` is caught, like any resolver or storage failure, and retried on the next `endOfDay`, `onAppOpen` or `onBoot`. The Place lookup behind `PlaceStampIdentityResolver` must include non-ACTIVE Places.
- **Automatic end.** 2 hours after the Itinerary window closes (`windowEnd`), at that exact time, even if the app opens later. Hours after midnight belong to the Itinerary's date.
- **Alerts** (ids `<kind>:<stopId>`, each fires once, tracked in state):
  - `ArrivalReminder`: Stop start + 30 min, while on Leg or Arrival for it.
  - `LeaveBy` (high priority, id `LeaveBy:<stopId>:<nextStopId>`, so skipping the next Stop alerts again for the new one): Leave By - 30 min, at the Stop, never on the last remaining Stop.
  - `AutoEnd` is not an alert but a wake-up alarm at the automatic end (`alarmsToSchedule`), so a forgotten Itinerary ends and awards Stamps with the app closed.
  - `LateWarning`: once per next Stop, when now + transit estimate passes its start, while at the previous Stop.
- Alert actions (never open the app, except Ouvrir): arrival = `action_arrived` (`ConfirmArrival`) + `action_skip_stop` or `action_skip_meal` (Meal); leave = `action_leaving` (`Leaving`) + `notification_action_open` (opens the app); late = `action_go_anyway` (`GoAnyway`) + `action_skip_stop` or `action_skip_meal` (`SkipStop` of the next Stop). **On the last remaining Stop an alert whose Skip would end the day (the arrival reminder) offers `notification_action_open` instead** (`PlannedAlert.skipStopId` is null), so the app shows the end-early confirmation.

## Android

Manifest gets `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM` (no max SDK: on Android 13+ it is requestable, not granted by default), `RECEIVE_BOOT_COMPLETED`, an alarm receiver, an action receiver and a boot receiver (also time and timezone changes). Notifications use `VISIBILITY_PUBLIC` and the keys of `underway-strings.md`; times are formatted with `formatSentenceTime` in the city zone (`UnderwayClock.zone`). The boot receiver accepts only boot, package replaced, time changed and timezone changed. Alerts are rescheduled on boot and on every `onAppOpen`; `HackappApplication` calls `onAppOpen` whenever any Activity resumes, so pages need no extra call for Home. `HackappApplication.underwayCoordinator` builds the real graph.

## Open items for the lead

See the final report of the Domain Engineer: arrival reminder due at Start, skipping the last Stop ends the day.

## Wiring Start and screens (integration)

```kotlin
val app = context.applicationContext as HackappApplication
val coordinator = app.underwayCoordinator
val result = coordinator.start(
    uid = uid,                       // signed-in account
    itineraryId = itinerary.id,
    origin = origin,                 // the Profile's District of stay, or null
    demoMode = false,                // true only in demo mode (skips the date check)
    confirmEndCurrent = false,       // true after the user accepts the "current one will end" warning
)
```

The first Leg starts from the Profile's District of stay, with no location permission. `resolveStayOrigin` (`citypack/DistrictOrigin.kt`) converts that District's label anchor back to coordinates with the City Pack's projection. A missing or unreadable Profile, District, anchor or projection gives `origin = null`, and Start still works: the first Leg then shows no Leave By. Review reads time from `coordinator.clock`, the same clock as Underway.

Handle the result: `Started(view, askExactAlarm)` navigate to Leg. `NeedsConfirmation(current)` show the warning, then call again with `confirmEndCurrent = true`. `Blocked(reason)` shows why (`WrongDate`, `WindowEnded`, `NoEvents`, `NotInPlanning`). `NotFound` is an error.

Permissions at Start, in this order, neither blocking Start:

1. `POST_NOTIFICATIONS` (API 33+): request with the standard launcher before calling `start`. If denied, do nothing more: pass `notificationsAllowed = { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }` to the ViewModel and the banner (`view.showNotificationBanner`) appears once. Its button opens `Settings.ACTION_APP_NOTIFICATION_SETTINGS`. Send `NotificationBannerHandled` when it is dismissed or the button is used.
2. Exact alarms: after `start`, if `askExactAlarm` is true, show the `underway_exact_alarm_*` strings. On Allow, `startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))`. On refusal do nothing. The next `onResume` reschedules with the new permission.

The Start button uses the helper in `scaffold/screens/underway/UnderwayPermissions.kt`, which does both steps:

```kotlin
val permissions = rememberUnderwayStartPermissions()
// Place once in the Start screen. Any answer continues to Underway.
UnderwayExactAlarmRationale(permissions, onClosed = { controller.navigate(UnderwayLegRoute) })

// On Start:
permissions.requestNotifications()
when (val result = coordinator.start(uid, itinerary.id, origin)) {
    is UnderwayStartResult.Started ->
        if (result.askExactAlarm) permissions.offerExactAlarm() else controller.navigate(UnderwayLegRoute)
    // NeedsConfirmation, Blocked and NotFound as above.
}
```

Do not navigate while the rationale is showing: it lives in the Start screen and would disappear unseen. `UnderwayLegRoute` is the only Underway route: `UnderwayScreen` draws Leg, Arrival, At the Stop or End of day from the phase.

Screens call `UnderwayViewModel.create(uid, coordinator, app.underwayClock, notificationsAllowed)` (a Composable, in `scaffold/screens/underway/`, one shared ViewModel for Leg, Arrival, At the Stop and End of day; `factory(...)` is the same for a `ViewModelProvider`). It exposes `state: StateFlow<LoadState<UnderwayScreenData(view, now)>>`, where `now` comes from the injected clock and moves on a minute ticker: pages never read time themselves. Also `endOfDay: StateFlow<LoadState<EndOfDay>>`, `retry()` (reloads End of day and retries Stamp awarding after `stampsAvailable = false`), `rejections: SharedFlow<Rejection>`, `dispatch(event)` and `onResume()` (call on every resume). `state` is `Failed` only when storage failed: show Retry (`onResume()`). `nothingUnderway: StateFlow<Boolean>` becomes true when no Itinerary is Underway and no day just ended: go straight Home (`state` stays `Loading`). The one clock is `HackappApplication.underwayClock`, shared by the coordinator, ViewModel and pages; demo mode replaces it there. When the day ends, `state` keeps its last value and `endOfDay` loads: navigate to End of day when it is `Ready`.

## Process death and failures

`UnderwayViewModel` keeps the Itinerary id in a `SavedStateHandle`, so End of day still loads after the process was killed. It also loads End of day whenever `view.phase` is `Ended` and it is not `Ready`. If the Itinerary save fails after the state was saved, alerts are still scheduled and notifications cleared before the error is rethrown; the next app open re-syncs the Itinerary.
