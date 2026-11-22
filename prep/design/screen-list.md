# App Screens

Hackathon demo: a working Android app covering onboarding, Itinerary planning, Itinerary execution and Passport Stamps. Montréal ships first, Toronto is a stretch goal, and Vancouver is out of scope for now.

Terms are defined in [`GLOSSARY.md`](../../GLOSSARY.md).

## App-wide rules

- **App name:** the app is named Plan[City], with the City Pack's city in place of the brackets. The Montréal build is Plan Montréal in French and Plan Montreal in English.
- **Age:** accounts are for people 18 and over. Adults planning an outing with children use the family-friendly Audience Preference.
- **Languages:** French is the priority and default. English is also required. French lives in `values/strings.xml`, English in `values-en/strings.xml`, and no user-facing text is hardcoded. Settings has a French/English switch.
- **City labels:** City Packs can override labels, such as "arrondissement" for a District. Toronto can default to English.
- **Data:** Events and Places are bundled with the app and read through repository interfaces, so they can later move to a database. Profile, Itineraries and Passport use Firestore.
- **Attribution:** show "Data: Has Things (hasthings.com), Ville de Montréal, © OpenStreetMap contributors" wherever Event data is used.
- **Demo mode:** a hidden control pins the date to one with good seed data and skips ahead to the next moment that matters. It also triggers alerts directly.

## Authentication

### Welcome

- App title (Plan[City])
- Log in with Google
- Log in with email and password
- Register button
- Password reset

### Register

- Create an account with email and password
- Firebase Auth keeps one account per email, so Google and email login at the same address reach the same Profile, Itineraries and Passport
- Every new account goes through onboarding
- Returning users with a Profile go straight to Home

## Onboarding

### 1. Resident or Visitor

- Visitor is selectable
- Resident is shown disabled with "bientôt disponible" (to revisit soon)

### 2. Profile

- Name and email, prefilled from Google when available
- Age range: 18–24, 25–34, 35–44, 45–54 or 55+
- Borough of stay

Gender and street address are not collected.

### 3. Budget and Audience Preference

- Default budgets, each using the brackets < $20, < $50, < $100 and $100+:
  - Lunch (Meal 1), per Meal
  - Dinner (Meal 2), per Meal
  - Events, total for the Itinerary
  - Late-night snack, an optional add-on that is off by default
- Audience Preference: 18+ only, family-friendly and no alcohol, all optional. 18+ only and family-friendly are mutually exclusive

### 4. Interests and cuisines

- Interests (event types), optional and multi-select
- Cuisines, optional and multi-select, using the chips in the restaurant data design
- These rank results and never hide them

Onboarding ends at Home.

## Home

- Plan a new Itinerary
- Itineraries in Planning (a user can have several)
- The Itinerary that is Underway (at most one), which resumes here after the app is closed
- Passport

## Itineraries

Top-level tab between Home and Passport.

- **In planning:** every Itinerary still in Planning. Tapping one resumes Planning
- **Past:** completed Itineraries, newest first, with their date and confirmed Stop and Stamp counts. Tapping one opens a read-only summary
- **Archived:** drafts whose time window has ended, with a note that they were archived. One opens read-only with "Archived because its date has passed", **Choose a new date** and **Delete**
- Drafts in Planning can be deleted after a confirmation. Past Itineraries cannot be deleted
- A read-only Past summary shows the date, route map, Stops marked confirmed or skipped, and Stamps earned
- An Itinerary that is Underway stays on Home and is not listed here
- Empty state with a button to plan a new Itinerary

## Planning

An Itinerary covers a single day out or night out. Multi-day planning is out of scope.

### 1. Date and time

- Date, limited to Nov 21 to Dec 6, 2026, defaulting to the next date with data. Demo mode can pin Sat Nov 28
- Day out (default 10:00–18:00) or night out (default 18:00–02:00), with editable times. An Itinerary lasts at least 2 hours and ends no later than 04:00
- Optional Event budget override for this Itinerary. The Profile is unchanged
- An Itinerary belongs to the date it starts on, including hours after midnight
- Continuing creates a draft, which autosaves after every step and resumes at its last step

### 2. District pick

- Full-height Canvas map with District boundaries, pinch zoom from 1x to 6x, one-finger drag, double-tap zoom, bounded edges and a reset button
- Choose 1 to 3 Districts, in visit order, shown as chips over the map. Order changes only by removing and adding a District
- Each District shows a fixed-size red circle counting Events that start in the time window and fit the audience, including unknown prices. Lower-count overlapping badges hide until zoomed in, and selected Districts always show their order
- A District with zero matching Events, or a linked city, is grayed out and cannot be picked
- Tapping a District opens a non-blocking sheet with its name and Event types, and **Add as #N** or **Remove**. Borough descriptions are deferred
- At the maximum, Add shows a message listing the 3 selected Districts and offers to replace one. The new District takes that visit position
- Continue once the maximum is reached or the user is satisfied

### 3. Meal choice

- Is the app planning food, or is the user handling it?
- If planning food, the user can select any Meals inside the window: lunch at 12:00, dinner at 18:30 and a late bite at 22:00, with editable times
- Lunch and dinner reserve a 90-minute Meal Block (1 hour plus 15 minutes on each side). A late bite reserves 45 minutes in total
- Each selected Meal shows an editable budget for this Itinerary
- If the user handles food, no Meal prompts appear

### 4. Event and Meal selection

Planning moves strictly through the chosen Districts in order, and the user chooses when to advance to the next one.

- The user fills one time gap at a time. Adding a Stop refreshes the list from its departure
- Events are grouped by start time in 1-hour blocks, then by event type. "Other" Events stay in their own group unless their label cannot be made clearer, in which case they are hidden for the demo
- Events rank by matching Interests, then known price within budget, then unknown price, then shortest travel time
- Repeated showtimes of one title at one venue on one day appear as one card with selectable times. An Event already in the Itinerary is hidden from later gaps
- Tapping an Event opens details with title, time, venue, price, audience, description when available, ticket link, optional ticket price and **Add to Itinerary**
- Only reachable Events appear: start time at or after the previous Planned Departure plus the public transit Leg estimate and buffer
- If an Event has no published end time, the Planned Departure sheet opens prefilled with a typical stay for its type. The user can change it, and the source end time stays empty
- Events that overlap a reserved Meal Block are hidden. When planning reaches the block, the user chooses a Meal or **Skip this Meal**, which frees the block
- The Meal list has **Here** and, when another District follows, **In [next District]**. The second tab shows only places reachable by the block's start after transit, and choosing one moves planning to that District after the Meal
- Meal options are open during the block and fit the Meal budget and audience, sorted by distance from the previous Stop with cuisine preferences boosted
- Bars count as Meals and are available from 17:00, on day and night Itineraries. They are hidden for family-friendly or no-alcohol Audience Preferences
- **Next District** moves on at any time. **Finish here** skips remaining Districts and goes to Review, and needs at least one Event. The last District shows only **Finish**. Back keeps Stops, and a District left with no Stops is dropped
- Selection shows a running total of the Event budget plus selected Meal budgets. Going over budget warns but does not block

**Prices:**

- Events are never hidden by budget, and free Events count as $0
- A Meal counts at the top of its price range. Places above that Meal's budget are hidden. Unknown prices stay visible, rank lower and show as "+ unknown price"
- Each signed-in user can keep one editable ticket price report per Event, from $0 to $500 CAD. It applies to all showtimes of that title and venue that day, and the user is told it is shared
- The app shows the median, labeled as the median of user-reported prices, and never shows who reported it

**Event eligibility:**

- Hide cancelled, sold-out and past Events
- Hide Events with no known start time
- Hide Events whose minimum age is above the person's age range, such as 21+ Events for someone aged 18–24
- Show children-only Events only with a family-friendly Audience Preference, because every account holder is an adult
- Keep Events with an unknown price, labelled "prix inconnu", and rank them lower when a budget is set
- Ticket purchases open the Event's own link

### 5. Itinerary review

- Map linking all Stops, both Events and Meals
- The map supports zoom and drag, and tapping a numbered marker highlights its Stop
- Remove a Stop, which returns the user to selection for that time gap. Later reachable Stops stay, and the app warns before removing others
- Changing an earlier Planning step warns which Stops will be removed and keeps those that still fit
- Stops cannot be dragged to reorder
- Start the Itinerary, which requests notification permission. It can start only on its own date, except in demo mode

Only one Itinerary can be Underway at a time. Starting another warns that the current one will end. The old one moves to Past, keeps Stamps for confirmed Stops, and marks remaining Stops as skipped.

## Underway

### 1. Leg handoff

- The first Leg starts from the user's current location. If location is denied, the transit app or Uber asks for the starting point
- Choose public transit or Uber
- **Public transit:** show the fare needed from the City Pack's fare table, then open the city's Transit App (Chrono in Montréal). Google Maps transit directions are the fallback if the Transit App is missing or cannot load the trip
- A pre-loaded Chrono trip or ticket cart is a bonus, only if testing on a device proves it is possible
- **Uber:** open Uber with the next Stop as the drop-off

### 2. Arrival

- The user taps "I've arrived"; arrival is not detected by GPS
- If they have not confirmed 30 minutes after the expected arrival, send a local notification with "I've arrived" and "Skip this Stop"

### 3. At the Stop

- A high-priority notification fires 30 minutes before Leave By
- The last Stop has no leave alert
- The user taps a button when leaving, which moves to the next Leg
- If they can no longer reach the next Stop on time, warn them and offer "Go anyway" or "Skip this Stop". There is no automatic replanning

### 4. End of day

- After the last Stop, or at any time to end early, the user ends the day
- The Itinerary is logged and Stamps are awarded for confirmed Stops only

## Passport

- Organized by city, then District
- Each Stamp shows the name, date and District
- Confirmed Events and Meals, including bars, earn Stamps. Skipped Stops do not
- Each Event earns one Stamp ever, even if attended again on another day
- Each restaurant or bar earns one Stamp ever, even if visited again
- A chain counts as one restaurant, so only the first location visited earns a Stamp
- One Passport per person, across all cities
