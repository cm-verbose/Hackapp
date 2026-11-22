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

- Per-person budget: < $20, < $50, < $100, $100+
- Audience Preference, such as 18+ only, family-friendly or no alcohol-focused events

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
- An Itinerary that is Underway stays on Home and is not listed here
- Empty state with a button to plan a new Itinerary

## Planning

An Itinerary covers a single day out or night out. Multi-day planning is out of scope.

### 1. Date and time

- Date, limited to what the seed data covers
- Day out (default 10:00–18:00) or night out (default 18:00–02:00), with editable times
- Optional budget override for this Itinerary
- An Itinerary belongs to the date it starts on, including hours after midnight

### 2. District pick

- Map of the city with District boundaries drawn
- Choose 1 to 3 Districts, in the order they will be visited
- Each District shows its name and a red circle counting Events that match the time window, budget and audience
- A District with zero matching Events is grayed out and cannot be picked
- Tapping a District shows a very short description and its event types, then confirm or choose another
- Continue once the maximum is reached or the user is satisfied

### 3. Meal choice

- Is the app planning food, or is the user handling it?
- If planning food, when do they want to eat? Each answer reserves a 90-minute Meal Block (1 hour plus 15 minutes on each side)
- If the user handles food, no Meal prompts appear

### 4. Event and Meal selection

Planning moves strictly through the chosen Districts in order, and the user chooses when to advance to the next one.

- Events are grouped by start time in 1-hour blocks, then by event type
- Only reachable Events appear: start time at or after the previous Planned Departure plus Leg time and buffer
- Leg time is a distance-based estimate plus a buffer
- If an Event has no published end time, the user sets how long they will stay or when they expect to leave. That becomes the Planned Departure, and the source end time stays empty
- When planning reaches a Meal Block, the user is prompted to choose a Meal
- Bars count as Meals and are available from 17:00, on day and night Itineraries. They are hidden for family-friendly or no-alcohol Audience Preferences
- Repeat until the window is filled, or finish early with at least one Event

**Event eligibility:**

- Hide cancelled, sold-out and past Events
- Hide Events with no known start time
- Hide Events whose minimum age is above the person's age range, such as 21+ Events for someone aged 18–24
- Show children-only Events only with a family-friendly Audience Preference, because every account holder is an adult
- Keep Events with an unknown price, labelled "prix inconnu", and rank them lower when a budget is set
- Ticket purchases open the Event's own link

### 5. Itinerary review

- Map linking all Stops, both Events and Meals
- Remove a Stop, which returns the user to selection for that time gap
- Stops cannot be dragged to reorder
- Start the Itinerary, which requests notification permission

Only one Itinerary can be Underway at a time.

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
