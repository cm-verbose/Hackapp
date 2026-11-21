# Hackapp

A city outing companion that helps a person plan a day or night out, follow it stop by stop, and keep a record of it in a city passport.

## Language

### People

**Visitor**:
A person using the app to explore a city they do not live in.
_Avoid_: Tourist

**Resident**:
A person using the app to explore the city they live in.
_Avoid_: Local

**Profile**:
A person's lasting details and defaults, independent of any single Itinerary.
_Avoid_: Account details, onboarding data

**Audience Preference**:
A person's chosen filter for the kind of audience an Event suits, such as 18+ only or family-friendly.
_Avoid_: Demographic restriction

**Interest**:
A type of Event a person prefers, used to rank what they are shown rather than to hide anything.
_Avoid_: Category preference, tag

### Itineraries

**Itinerary**:
A single day out or night out, made up of ordered Stops, from planning through completion.
_Avoid_: Trip, outing, plan, route, schedule

**Planning**:
The phase of an Itinerary while its Stops are being chosen.

**Underway**:
The phase of an Itinerary while the person is travelling between and attending its Stops.
_Avoid_: Execution, pathing

**Leg**:
The travel between two consecutive Stops, or to the first Stop.
_Avoid_: Trip, ride, journey

**Stop**:
A place on an Itinerary, which is either an Event or a Meal.
_Avoid_: Destination, activity

**Event**:
A scheduled happening that a person can attend as a Stop.
_Avoid_: Activity

**Meal**:
A Stop at a business where a person eats or drinks, including a bar.
_Avoid_: Restaurant stop, food break

**Meal Block**:
The time reserved on an Itinerary for a Meal, including its margins before and after.
_Avoid_: Meal slot, food window

**Planned Departure**:
The time a person intends to leave a Stop, which they choose when an Event publishes no end time.
_Avoid_: Estimated end time

**Leave By**:
The latest time a person can leave a Stop, being the earlier of its Planned Departure and the time needed to reach the next Stop.
_Avoid_: Deadline, end alarm

**Passport**:
A person's lasting record of the Events and Meals they attended, across cities.
_Avoid_: History, log

**Stamp**:
The Passport's single, unrepeatable mark for an Event, restaurant or bar a person confirmed attending, with all locations of a chain counting as one restaurant.
_Avoid_: Badge, achievement

### Cities and white-labelling

**District**:
A named division of a city that a person can choose to visit, labelled in each city's own terms, such as a borough in Montréal.
_Avoid_: Borough (except as a city's display label), region, zone

**City Pack**:
The complete set of city-specific content and options that makes the app work for one city.
_Avoid_: City config, market

**Brand**:
The name and visual identity under which the app is presented.
_Avoid_: Theme, skin, tenant

**Transit App**:
A city's own public transit app, preferred for travelling a Leg by public transit.
_Avoid_: Transit provider
