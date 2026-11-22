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

- [ ] Shared onboarding state and step navigation
- [ ] Resident or Visitor
- [ ] Profile
- [ ] Budget and Audience Preference
- [ ] Interests and cuisines
- [ ] Save the Profile behind a `ProfileRepository`
- [ ] Route accounts with no Profile to onboarding

### 3. Design decisions (grilling)

- [ ] District map behavior
- [ ] Itinerary draft and Planning flow
- [ ] Update maquettes from these decisions

### 4. Main tabs

- [ ] Home
- [ ] Itineraries: In planning, Past and empty state
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

## Data facts for planning

- The snapshot covers Nov 21 to Dec 6, 2026, with 1,236 scheduled Events.
- 574 scheduled Events (46%) have no published end time.
- 549 have an unknown price, 671 are free, and only 16 are marked paid.
- On Sat Nov 28 from 18:00 to 02:00, Ville-Marie has 17 Events and Le Plateau-Mont-Royal has 11. Every other District has 2 or fewer.

## Open questions

Tracked in the current grilling round. Move answers to **Decisions**.
