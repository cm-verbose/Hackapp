# Plan[City] implementation plan

Living checklist for the current implementation step. Update it in the same change that finishes an item.

- **Spec:** `prep/design/screen-list.md`
- **Terms:** `GLOSSARY.md`
- **Design:** Figma `ETS-HackApp` (`VXMqds2RqjgaZ0JXHNNM6o`), page `maquettes`
- **Working branch:** `feat/ui-foundation`
- **Test device:** physical phone. The emulator can be killed by the host's out-of-memory killer.

## Working rules

- New or changed pages get a Figma maquette before Android work.
- Screens are stateless composables that take a `UiState` and callbacks.
- Screens reach data and external apps through interfaces (`CityServices`, `AuthService`), never a specific provider or city.
- User-facing text comes from `strings.xml`, in French and English.
- Do not run device tests on the phone, because they uninstall the app and clear its data.

## Ownership

| Area | Owner |
|---|---|
| Passport | `cmvb` |
| Onboarding, then Home, Itineraries, Settings, Planning and Underway | Niko and Claude |

## Decisions

| Topic | Decision |
|---|---|
| App name | Plan[City]: Plan Montréal in French, Plan Montreal in English |
| Age | Accounts are 18+. Adults planning with children use the family-friendly preference |
| Navigation | Use `cmvb`'s bottom bar and tab destinations |
| City selection | Chosen at build time (ADR 0001) |
| Event data | Bundled `events.json` behind `EventRepository` |
| City services | Manual `CityServices` container built from the City Pack |
| Past Itinerary | Tapping one opens a read-only summary with route map, Stops and earned Stamps |
| "Other" events | Show them in their own tab or group for now. If their label cannot be made clearer than "Other", hide them for the demo |
| District map technology | Keep the custom Canvas map |
| District map layout | Full-height map, selected boroughs as chips on top, details in a bottom sheet |
| District map gestures | Pinch zoom 1x to 6x, one-finger drag, double-tap zoom, bounded edges, reset button |
| District map badges | Fixed-size badges. Lower-count overlapping badges hide until zoomed in, and selected boroughs always show their order. **Revisit after the first iteration** |
| District selection | Tap opens a sheet with Add as #N, or Remove if selected. Remaining boroughs renumber. Boroughs with no matching Events and linked cities are gray and untappable |
| District count | Events starting in the time window that fit the audience, including unknown prices |
| Itinerary drafts | Created after Date and time, autosaved after each step behind `ItineraryRepository`, local until Firestore is connected |
| Stop selection | One time gap at a time, grouped by hour and type. Adding a Stop refreshes the list from its departure |
| Missing end times | Planned Departure sheet opens prefilled with a typical stay for the Event type, and the user can change it |
| Planning travel time | Public transit estimate plus buffer |
| Editing earlier steps | Warn which Stops will be removed and keep those that still fit. **May change during implementation** |
| Borough order | Change it only by removing and re-adding a borough. No drag-to-reorder |
| Borough sheet content | Name and Event types only, with no description. **Revisit borough descriptions later** |
| 3-borough limit | Add shows a maximum-reached message and offers to replace a selected borough |
| Date | Nov 21 to Dec 6, defaulting to the next date with data. Demo mode can pin Sat Nov 28 |
| Time window | At least 2 hours, ending no later than 04:00. Day/night switch resets to 10:00–18:00 or 18:00–02:00 |
| Meal choice | Multi-select within the window: lunch 12:00, dinner 18:30, late bite 22:00, with editable times. Lunch and dinner reserve 90 minutes. Late bite is 45 minutes |
| Meal Block conflicts | Events overlapping a reserved Meal Block are hidden. The Meal prompt appears when planning reaches the block |
| Meal location | Meal list has **Here** and **In [next borough]** tabs. The second shows only restaurants reachable by the block's start after transit. Choosing one moves planning to that borough after the Meal, and Back can undo it |
| Advancing boroughs | **Next borough** moves on. **Finish here** skips remaining boroughs and goes to Review, and needs at least one Event. The last borough shows only **Finish**. Back keeps Stops. A borough left with no Stops is dropped |
| Replacing a borough | The maximum-reached message lists the 3 selected boroughs, and the new one takes the replaced borough's position |
| Late bite | 45 minutes total, including its shorter margins |
| Removing a Stop | Return to selection for that gap, keep later reachable Stops, and warn before removing others |
| One Underway Itinerary | Starting a new Itinerary warns that the current one will end. The old one moves to Past, keeps Stamps for confirmed Stops, and marks remaining Stops as skipped |
| Start date | **Start** works only on the Itinerary's date, except in demo mode |
| Resuming drafts | Tapping a draft reopens its last step with all choices kept |
| Deleting drafts | Drafts have a Delete action with confirmation. Past Itineraries cannot be deleted |
| Expired drafts | Moved to an **Archived** section in Itineraries once their time window has ended. They open read-only with the note "Archived because its date has passed", plus **Choose a new date** and **Delete**. A new date uses the same warning for Stops that no longer fit |
| Event details | Sheet with title, time, venue, price, audience, description when available, ticket link and Add to Itinerary |
| Skipping a Meal | **Skip this Meal** frees the block so Events in that time appear again |
| Budget defaults | Onboarding asks for Lunch (Meal 1), Dinner (Meal 2) and total Events budgets using the four brackets, plus an optional Late-night snack add-on that is off by default |
| Budget overrides | Date and time edits this Itinerary's Event budget. Meal choice shows an editable budget beside each selected Meal. The Profile is unchanged. **Revisit and lock this down after the first implementation** |
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
- [ ] Merge `cmvb`'s latest `layout` refactor (`5aeb142`), resolving `MainBottomAppBar.kt` and `ScreenHome.kt`
- [ ] Open a PR from `feat/ui-foundation` into `main`
- [ ] Update the old data attribution in Figma

### 1. Authentication

- [x] Welcome
- [x] Register
- [x] Password reset
- [x] Temporary sign-out button on Home
- [ ] Confirm email sign-in, registration and reset on the phone
- [ ] Move sign-out to Settings

### 2. Onboarding

- [x] Shared onboarding state and step navigation
- [x] Resident or Visitor
- [x] Profile
- [ ] Update the Budget maquette for Lunch, Dinner, Events and Late-night snack
- [x] Budget and Audience Preference
- [x] Interests and cuisines
- [x] Save the Profile behind a `ProfileRepository`
- [x] Route accounts with no Profile to onboarding
- [ ] Confirm the onboarding flow on the phone

### 3. Design decisions (grilling)

- [ ] District map behavior
- [ ] Itinerary draft and Planning flow
- [ ] Update maquettes from these decisions

### 4. Main tabs

- [ ] Home
- [ ] Itineraries: In planning, Past, Archived and empty state (update maquette for Archived)
- [ ] Past Itinerary summary: maquette, then page
- [ ] Settings
- [ ] Passport (`cmvb`)

### 5. Planning

- [ ] Domain models: Itinerary, Stop, Leg, Meal Block
- [ ] `ItineraryRepository`
- [ ] Planning engine with unit tests: eligibility, ranking, Meal Blocks, reachability, Leave By, after-midnight dates
- [ ] Date and time
- [ ] District map with zoom, drag and selection
- [ ] Meal choice
- [ ] Event and Meal selection
- [ ] Planned Departure sheet
- [ ] Budget overrides and running total
- [ ] Event details sheet with optional ticket price
- [ ] `TicketPriceRepository` with median of user reports
- [ ] Itinerary review

### 6. Underway

- [ ] Persisted Underway state machine
- [ ] Leg handoff
- [ ] Arrival
- [ ] At the Stop
- [ ] End of day
- [ ] Local notifications

### 7. Connect and finish

- [ ] Firestore for Profile, Itineraries and Passport
- [ ] Idempotent Stamp awarding with chain identity
- [ ] Demo mode
- [ ] Check every screen against Figma
- [ ] Full rehearsal in French and English

### 8. Review after the first pass

- [ ] Do a page-by-page review of every screen
- [ ] Revisit and lock down budget overrides
- [ ] Revisit District map badges
- [ ] Revisit borough descriptions
- [ ] Revisit how edits to earlier Planning steps affect Stops

## Data facts for planning

- The snapshot covers Nov 21 to Dec 6, 2026, with 1,236 scheduled Events.
- 574 scheduled Events (46%) have no published end time.
- 549 have an unknown price, 671 are free, and only 16 are marked paid. None has a dollar amount.
- On Sat Nov 28 from 18:00 to 02:00, Ville-Marie has 17 Events and Le Plateau-Mont-Royal has 11. Every other District has 2 or fewer.

## Open questions

Tracked in the current grilling round. Move answers to **Decisions**.
