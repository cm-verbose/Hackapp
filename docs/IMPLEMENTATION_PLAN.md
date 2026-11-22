# Plan[City] implementation plan

Living checklist for the current implementation step. Update it in the same change that finishes an item.

- **Spec:** `prep/design/screen-list.md`
- **Terms:** `GLOSSARY.md`
- **Design:** Figma `ETS-HackApp` (`VXMqds2RqjgaZ0JXHNNM6o`), page `maquettes`
- **Working branch:** `feat/ui-foundation`
- **Test device:** physical phone. The emulator can be killed by the host's out-of-memory killer.

## Current status and handoff

Last updated 2026-11-22 05:40. Start here if you are picking up this work.

**Revision pass (2026-11-22 06:45).** Branch `feat/integration-revisions`, from `team/integration`, applies Niko's phone revision notes (see the Decisions marked "revision 2026-11-22"). 546 unit tests pass and `assembleDebug` builds. Not yet tried on a device, and not pushed.

**Where the code is.** Use **`origin/team/integration`**. It contains Planning, Underway, the dark-mode and travel-time fixes, demo mode and the physics Passport. `origin/feat/ui-foundation` is still at `5d0eb80` and contains none of these until Niko approves the milestone on the phone.

| Branch | Contains | State |
|---|---|---|
| `team/integration` | Everything below except Firestore | **Start here.** 536 unit and 9 emulator tests pass, and it opens on the emulator. Demo and Passport UX fixes are in. Niko is testing it on the phone |
| `team/planning-fixes` | Meals, Sheets, Selection, Review, Underway, Start, District of stay origin, dark card fix, walking and transit Legs | Superseded by `team/integration` |
| `team/demo` | Demo engine, Settings switch, Underway control and exit | Superseded by `team/integration`, which also has the code review fixes |
| `team/passport-integration` | `cmvb`'s `shape-playground` merge, real Stamps, all six shapes, shrink-as-you-collect, details and list | Merged into `team/integration` |
| `team/firestore` | Firestore repositories, flag off | Not merged or deployed. Needs rule tests, a Cloud Function aggregate and a product decision on the `eventSources` rule |

**Next steps, in order:**
1. Apply any fixes from Niko's phone review on `team/integration`.
2. Niko runs a full demo Itinerary, earns Stamps and approves the milestone.
3. Only after that approval, merge into `feat/ui-foundation` and push.
4. Then do Firestore switch-on, Itinerary sharing through the flicking code (`ThrowChannel`, `ShareMessage.Throw.payload`), the ETL `is_chain=0` chain rows, and the page-by-page review.

**Not yet checked on a real device:** demo mode, Passport physics, tilt and small Stamp initials, notifications and exact alarms, transit, Uber and walking handoffs, sign-out during a demo, TalkBack, two-device sharing, and an R8 release build.

**Where teammates should start:** create your own branch from `origin/team/integration`. Do not commit directly to any `team/*` branch, because Niko's teams are still working from them. Flicking is switched off in the Passport and has no sharing control yet. A later Itinerary share must use `ShareMessage.Throw.payload`.

**Rules for agents taking over:**
- Never push or open a PR into `main`. Push `feat/ui-foundation` only after Niko approves a milestone on the phone.
- New or changed pages need a Figma maquette that Niko approves first.
- Run one Gradle build and one emulator at a time, through `hackapp-team-build.sh` on Niko's computer, because the host runs out of memory.
- Never run connected tests on the phone. They uninstall the app and clear its data.
- Do not commit `.idea`, `.kotlin` or `local.properties`. No Claude co-author trailer.
- Do not deploy Firestore or change the live Firebase project without approval. No live Google calls, and never delete the Google cache.
- Contracts for each feature are in `docs/contracts/`. Team roles and workflow are in `docs/AGENT_PLAN.md`.

## Working rules

- New or changed pages get a Figma maquette before Android work.
- Screens are stateless composables that take a `UiState` and callbacks.
- Screens reach data and external apps through interfaces (`CityServices`, `AuthService`), never a specific provider or city.
- User-facing text comes from `strings.xml`, in French and English.
- Do not run device tests on the phone, because they uninstall the app and clear its data.
- Never push into `main`. Keep updates on this branch.
- Use Material icons, never emojis, in the UI and its source data.
- Selected chips and segmented controls show state by colour only, with no checkmark. Selected price brackets toggle off when tapped again.

## Ownership

| Area | Owner |
|---|---|
| Passport | `cmvb` |
| Onboarding, then Home, Itineraries, Settings, Planning and Underway | Niko and Claude |

## Decisions

| Topic | Decision |
|---|---|
| App name | Plan[City]: Plan Montréal in French, Plan Montreal in English |
| Age | Accounts are 18+. Adults planning with children use the family-friendly preference, which hides 18+ Events |
| Navigation | Use `cmvb`'s bottom bar and tab destinations |
| City selection | Chosen at build time (ADR 0001) |
| Event data | Bundled `events.json` behind `EventRepository` |
| City services | Manual `CityServices` container built from the City Pack |
| Past Itinerary | Tapping one opens a read-only summary with route map, Stops and earned Stamps |
| "Other" events | Hidden for the demo, because no clearer label exists |
| District map technology | Keep the custom Canvas map |
| District map layout | Full-height map, selected boroughs as chips on top, details in a bottom sheet |
| District map gestures | Pinch zoom 1x to 6x, two-finger rotation, one-finger drag, double-tap zoom, bounded edges, reset button (also resets rotation) |
| District map labels | Below 200% zoom, boroughs with Events show a count badge and boroughs with none stay blank. From 200%, boroughs show upright names with their Event counts. Boroughs with Events are colored, others and linked cities are gray, and linked cities have no marker. Overlapping lower-count markers hide until zoomed in, and selected boroughs always show their order. **Revisit after the first iteration** |
| System Back in flows | In Planning, the phone's Back action returns straight to Home and the draft stays saved. The top-left arrow goes to the previous step. Onboarding keeps step-by-step Back because no Home exists before a Profile |
| Flow transitions | Onboarding and Planning pages slide in the direction of progress, and the opposite way going back |
| Date and time default | A new Itinerary starts on Day out (revision 2026-11-22) |
| District of stay | Picked on a full-screen borough map in onboarding: names only, shown from 200% zoom, no counts. With coarse location allowed, the borough the person is in is preselected. Location is read once and never stored (revision 2026-11-22) |
| Selection intro | Arriving from Meal choice shows a full-screen colour panel, "Choisissez vos arrêts", that wipes away to the start side. A tap skips it (revision 2026-11-22) |
| Moving on | After an Event, or an Event and the Meal right after it, selection asks once per borough to move to the next one (revision 2026-11-22) |
| Late first borough | When the first borough's first Event starts 2 h or more after the gap opens and another order starts at least 1 h earlier, selection offers to sort the boroughs by their first Event. Only before any Stop is planned (revision 2026-11-22) |
| Meal conflicts | When the next Meal hides every remaining Event, or at least 3 and half of them, selection offers **Changer l'heure**. The Meal moves in 15-minute steps, the Event list updates live, and nothing is saved until **Enregistrer l'heure** (revision 2026-11-22) |
| Handoff return | Opening transit, Uber or maps moves to Arrival only once the person is back in the app, with the usual slide. A handoff that never leaves the app moves on after 3 s (revision 2026-11-22) |
| Home delete | Holding down a draft on Home asks to delete it (revision 2026-11-22) |
| Stamp shapes | Each new Stamp stores a random shape seed when awarded. Older Stamps keep their key-based shape (revision 2026-11-22) |
| Demo control look | 85% opacity with a 60% background, so it reads as a demo tool (revision 2026-11-22) |
| Itinerary sharing | Flick a card to a nearby phone through `cmvb`'s Nearby code, with the Itinerary in `ShareMessage.Throw.payload`. Share from Review or the Underway ⋮ menu; receive from the Home icon. The receiver gets a copy in Planning: drafts keep their step, Underway copies open on Review with past Stops skipped. No owner, budgets, ticket reports or confirmations are copied, and nothing syncs back. Live group sync waits for Firestore (revision 2026-11-22) |
| Time picker | Two rectangles for hour and minute, flicked on a looping wheel or tapped to type. No dial |
| District selection | Tap opens a sheet with Add as #N, or Remove if selected. Remaining boroughs renumber. Boroughs with no matching Events and linked cities are gray and untappable |
| District count | Events starting in the time window that fit the audience, including unknown prices |
| Itinerary drafts | Created after Date and time, autosaved after each step behind `ItineraryRepository`, local until Firestore is connected |
| Stop selection | One time gap at a time, grouped by hour and type. Adding a Stop refreshes the list from its departure |
| Missing end times | Planned Departure sheet opens prefilled with a typical stay for the Event type, and the user can change it |
| Planning travel time | Public transit estimate plus buffer |
| Editing earlier steps | Warn which Stops will be removed and keep those that still fit. **May change during implementation** |
| Borough order | The chosen boroughs show as a list in visit order on step 2. Drag a row by its handle, or long press it, to reorder. TalkBack has Move up and Move down (revision 2026-11-22) |
| Borough sheet content | Name and Event types only, with no description. **Revisit borough descriptions later** |
| 3-borough limit | Add shows a maximum-reached message and offers to replace a selected borough |
| Date | Nov 21 to Dec 6, defaulting to the next date with data. Demo mode can pin Sat Nov 28 |
| Time window | At least 2 hours, ending no later than 04:00. Day/night switch resets to 10:00–18:00 or 18:00–02:00 |
| Meal choice | Multi-select within the window: lunch 12:00, dinner 18:30, late bite 22:00, with editable times. Lunch and dinner reserve 90 minutes. Late bite is 45 minutes |
| Meal Block conflicts | Events with a fixed showtime that overlap a reserved Meal Block are hidden. Long drop-in Events, such as an all-day exhibition, stay visible because they can be visited before or after the Meal. The Meal prompt appears when planning reaches the block |
| Drop-in stay | A drop-in Stop defaults to arrival plus a typical stay, capped by the published end, the next Meal and the end of the outing. The person can adjust it in the Planned Departure sheet |
| Meal location | Meal list has **Here** and **In [next borough]** tabs. The second shows only restaurants reachable by the block's start after transit. Choosing one moves planning to that borough after the Meal, and Back can undo it |
| Advancing boroughs | **Next borough** moves on. **Finish here** skips remaining boroughs and goes to Review, and needs at least one Event. The last borough shows only **Finish**. Back keeps Stops. A borough left with no Stops is dropped |
| Replacing a borough | The maximum-reached message lists the 3 selected boroughs, and the new one takes the replaced borough's position |
| Late bite | 45 minutes total, including its shorter margins |
| Removing a Stop | Return to selection for that gap and keep later reachable Stops. If a later Stop can no longer be reached by transit in time, warn the user and offer to reschedule or reorganize their day instead of removing it silently |
| Planning progress | Every Planning page uses the 5-segment bar with "Étape N sur 5". The Budget step was removed (revision 2026-11-22) |
| Late-night Meal name | "Bouchée tardive" everywhere |
| Meal time field | Opens the same hour and minute rectangles as Date and time |
| Meal with no bracket | Falls back to the Profile budget for that Meal |
| Running total | Chosen Event prices plus the top of each chosen restaurant's range, compared with the Event budget plus selected Meal budgets. A Meal with no budget adds its actual price to the total and stays out of the limit |
| Time gaps | A gap ends when the next Meal Block starts, including its margins |
| Finish here | Disabled until at least one Event is added |
| Event with no reported price | Show "Prix inconnu" and keep the optional price field |
| Meal choice with no Meal that fits | Offer "Modifier l'horaire", which returns to Date and time |
| App writing | Concise, clear and not redundant in French and English. Text that the UI or interaction already makes clear appears only as a last resort |
| Underway start | Start opens the first Leg right away with its Leave By time, even if the Stop is hours away |
| Underway handoff | Opening transit or Uber moves to Arrival without assuming they left. They can return to the Leg to change transport |
| Underway skipping | Skip this Stop is on Leg and Arrival, with a one-tap confirmation |
| Late and early | Arriving late is recorded with no replanning. I'm leaving works any time after arrival |
| Go anyway | A late Stop stays on the Itinerary and can still earn its Stamp |
| Event Stamp rule | Every Event needs at least 20% of its expected time. Count the overlap between the stay (arrived to leaving) and the Event (start to end or Planned Departure). Time before the start does not count. If the person never taps leaving, the stay runs until their next app action, capped at the expected end |
| Meal Stamp rule | Confirming arrival is enough |
| Under 20% | The Stop shows as visited in Past, earns no Stamp and says "Trop bref pour un timbre" |
| Underway notifications | Denied notifications never block Start. Underway shows one dismissible banner with a Settings button, at most once per Itinerary |
| Underway alerts | Use exact alarms. Ask once at Start, and fall back to normal alerts if declined. Reschedule after a reboot and whenever the app opens. Arrival and Skip work directly from the notification. On the last remaining Stop, the notification offers Open instead of Skip, so the app can warn before the Itinerary ends |
| Past Leave By | Once the Leave By time has passed, Underway shows Departure: Now instead of a past time |
| First Leg origin | The first Leg starts from the Profile's District of stay, with no location permission at Underway. Its Leave By and travel time are approximate |
| Back during Underway | The phone Back action returns to Home, where the Underway card resumes. It never ends the Itinerary |
| Arrival reminder | Fires once. A Stop is never skipped automatically while the Itinerary is active |
| Late warning | Appears at a Stop when the current time plus travel passes the next Stop start, and as one alert per Stop if the app is closed |
| Leave By travel time | Always uses the public transit estimate, even with Uber |
| Transport choice | The first Leg defaults to public transit. Later Legs start on the last choice |
| Changes while Underway | Only skipping Stops and ending the Itinerary. No adding, moving or editing |
| Last Stop | Shows End Itinerary instead of I'm leaving, with no extra confirmation |
| Stamp timing | Awarded once at End of day, including automatic endings, and safe to retry |
| End of day content | Title, each Stop as visited, skipped or too brief, its Stamp result, and Retour à l'accueil |
| Lock screen | Notifications show Stop names and times |
| Ending early | End Itinerary asks for confirmation, marks remaining Stops skipped and goes to End of day |
| Forgotten Itineraries | End automatically 2 hours after the window closes, keeping Stamps for confirmed Stops |
| Demo mode | Pins Sat Nov 28 and adds Next moment. **Built at the end**, but Underway must read time and schedule alerts through replaceable interfaces from the start |
| Demo Stamps | Itineraries run in demo mode can start on any date and earn real Passport Stamps |
| Demo control | Shown only on Underway. **Moment suivant** jumps to Leave By, arrival, next Stop, then end of day. Start moves the clock to the first Leave By, or 30 minutes before the first Stop. The clock pauses while the app is closed. Turn it off in Settings or the Underway ⋮ menu |
| Physics Passport | Gravity starts locked. Stamps keep all six playful shapes from the prototype, with a stable shape per Stamp. The first Stamps are full size, and all shapes shrink together to about half size as the collection grows. They show initials and open a details sheet. **Tous les timbres** reaches Stamps that don't fit. No sharing control until Itinerary sharing is built |
| Travel between Stops | Planning and Underway use a public transit estimate between Districts and walking time within one District. A walk over 20 minutes uses transit instead |
| Passport direction | Integrate `cmvb`'s physics prototype from `shape-playground`, with real Stamps as the objects. Its flicking code now carries Itinerary sharing; Stamps are never sent |
| Onboarding start | Starts at Profile with 3 steps until Resident ships. The Visitor or Resident page stays in the code |
| Age notice | "18 ans et plus" on Register only |
| Home suggestions | Not tappable until Event selection can preselect the Event |
| End of day | Neutral title "Itinéraire terminé". A repeat visit shows "Déjà dans votre passeport" |
| French and English times | French uses 18:30 in controls and 18 h 30 in sentences. English stays 24-hour |
| One Underway Itinerary | Starting a new Itinerary warns that the current one will end. The old one moves to Past, keeps Stamps for confirmed Stops, and marks remaining Stops as skipped |
| Start date | **Start** works only on the Itinerary's date, except in demo mode |
| Resuming drafts | Tapping a draft reopens its last step with all choices kept |
| Deleting drafts | Drafts have a Delete action with confirmation. Past Itineraries cannot be deleted |
| Expired drafts | Moved to an **Archived** section in Itineraries once their time window has ended. They open read-only with the note "Archived because its date has passed", plus **Choose a new date** and **Delete**. A new date uses the same warning for Stops that no longer fit |
| Event details | Sheet with title, time, venue, price, audience, description when available, ticket link and Add to Itinerary |
| Skipping a Meal | **Skip this Meal** frees the block so Events in that time appear again |
| Budget defaults | Onboarding asks for Lunch (Meal 1), Dinner (Meal 2) and total Events budgets using the four brackets, plus an optional Late-night snack add-on that is off by default |
| Budget overrides | No Budget step. Meal choice shows each Meal's inherited budget with **Modifier le budget**, which reveals the brackets. The Event budget comes from the Profile. Drafts saved on the old Budget step resume at Districts (revision 2026-11-22) |
| Day total | Event budget plus the budgets of selected Meals, shown as a running total during selection |
| Event prices | Events are never hidden by budget. Free counts as $0. Users can enter a ticket price, which updates shared data so other users see it |
| Shared ticket prices | One editable report per signed-in user and Event. The app shows the median and clearly labels it as the median of user-reported prices. Reports accept $0 to $500 CAD, never show who submitted them, tell users the price is shared, and apply to all showtimes of the same title and venue that day |
| Ticket price storage | Separate Firestore collection behind `TicketPriceRepository`, merged with read-only `events.json` when read. Add the ODbL implications to the license notes |
| Meal cost | Counts at the top of the restaurant's price range. Restaurants above that Meal's budget are hidden. Unknown prices stay visible, rank lower and show as "+ unknown price" |
| Over budget | Warn but allow the Stop. Selection shows a running total |
| Ranking | Matching Interests, then known price within budget, then unknown price, then shortest travel time |
| Repeated showtimes | One card with selectable times. An Event already in the Itinerary is hidden from later gaps |
| Review map | Same zoom and drag. Tapping a marker highlights its Stop |
| Past summary | Date, route map, Stops marked confirmed or skipped, and Stamps earned. No editing or restart |

## Checklist

### 0. Foundation

- [x] Merge event data, `layout` navigation and `feat-business`
- [x] Import Figma strings and bundle Figtree
- [x] Add shared components and typed routes
- [x] Add data and city service interfaces
- [x] Bundle the Montréal event snapshot
- [x] Replace "Hackapp" with Plan[City] in Figma
- [x] Merge `cmvb`'s latest `layout` refactor and `main`
- [x] Update `screen-list.md` with the grilling decisions
- [x] Replace cuisine emojis with Material icons in data, UI and design docs
- [x] Remove selection checkmarks from chips and segmented controls in the app and Figma
- [x] Update the old data attribution in Figma

### 1. Authentication

- [x] Welcome
- [x] Register
- [x] Password reset
- [x] Temporary sign-out button on Home
- [ ] Confirm email sign-in, registration and reset on the phone
- [x] Move sign-out to Settings

### 2. Onboarding

- [x] Shared onboarding state and step navigation
- [x] Resident or Visitor
- [x] Profile
- [x] Update the Budget maquette for Lunch, Dinner, Events and Late-night snack
- [x] Budget and Audience Preference
- [x] Interests and cuisines
- [x] Save the Profile behind a `ProfileRepository`
- [x] Route accounts with no Profile to onboarding
- [ ] Confirm the onboarding flow on the phone
- [x] Add Sign out to onboarding step 1

### 3. Design decisions (grilling)

- [x] District map behavior
- [x] Itinerary draft and Planning flow
- [x] Update maquettes from these decisions
- [x] Underway pages grilling

### 4. Main tabs

- [x] Home
- [x] Itineraries: In planning, Past, Archived and empty state
- [x] Past Itinerary summary and Archived detail: maquettes, then pages
- [x] Settings
- [ ] Confirm Home, Itineraries and Settings on the phone
- [ ] Passport: `cmvb`'s physics prototype with real Stamps, built on `team/passport-integration`, not yet on a device

### 5. Planning

- [x] Domain models: Itinerary, Stop, Leg, Meal Block
- [x] Local `ItineraryRepository`
- [x] Planning engine with unit tests: eligibility, ranking, Meal Blocks, reachability, Leave By, after-midnight dates
- [x] Date and time
- [x] District map with zoom, drag and selection
- [x] Review pass 1: system Back to Home, sliding transitions, time wheels, optional Budget step, map rotation, borough names
- [x] App-wide French and English writing pass, UX review and accessibility fixes
- [ ] Confirm Date and time, Budget and District map gestures on the phone
- [x] Meal choice
- [x] Event and Meal selection
- [x] Keep long drop-in Events visible across Meal Blocks
- [x] Planned Departure sheet
- [x] Itinerary budget override step
- [x] Per-Meal budget editing and running total UI
- [x] Event details sheet with optional ticket price
- [x] `TicketPriceRepository` logic with median of user reports (in memory until Firestore)
- [x] Connect Stop edit warnings to Date and time, District pick, Meal choice and Review
- [x] Itinerary review
- [ ] Niko approves Planning on the phone

### 6. Underway

- [x] Persisted Underway state machine
- [x] Leg handoff
- [x] Arrival
- [x] At the Stop
- [x] End of day
- [x] Local notifications and exact alarms, not yet tried on a real device
- [ ] Niko approves Underway on the phone

### 7. Connect and finish

- [ ] Firestore for Profile, Itineraries and Passport
- [x] Idempotent Stamp awarding with chain identity
- [ ] Demo mode: built on `team/demo`, code review and device test pending
- [ ] Check every screen against Figma
- [ ] Full rehearsal in French and English

### 8. Review after the first pass

- [ ] Do a page-by-page review of every screen
- [ ] Revisit and lock down budget overrides
- [ ] Revisit District map labels
- [ ] Revisit borough descriptions
- [ ] Revisit how edits to earlier Planning steps affect Stops

## Data facts for planning

- The snapshot covers Nov 21 to Dec 6, 2026, with 1,233 scheduled Events.
- 571 scheduled Events (46%) have no published end time.
- 546 have an unknown price, 671 are free, and only 16 are marked paid. None has a dollar amount.
- On Sat Nov 28 from 18:00 to 02:00, Ville-Marie has 17 Events and Le Plateau-Mont-Royal has 11. Every other District has 2 or fewer.

## Open questions


Move answers to **Decisions**.
