# App Screens for maquette

## welcome screen

App title,
login fields
OAuth
register button

## Registration/Onboarding

### Screen 1

Resident or visitor

currently: only visitor path

### Screen 2

name, age, gender, email, address/borough/ where stay

### Screen 3

How long is your stay/ What are we planning for? ()
// wording differs based off of resident or tourist
Plan duration: how long are we planning for? (start and end date, time)

### Screen 4

budget preference
Event demographic restrictions

--- BASIC ONBOARDING ENDS HERE ---

---PREPLANNING FLOW STARTS HERE ---

### Screen 5

is this a Day out or night out?
// app has default start and end times for these periods (day and night) that can be overridden by the user

### Screen 6 BOROUGH PICK

borough count: 1-3
user is shown a map of the city with borough divisions drawn.

They must pick an amount of boroughs within the count range in the order they want to visit them.

The boroughs will be drawn as a region, with the name and a red circle with an event amount in it.
If there are no events happening in a borough, it will be grayed out.

When a borough is tapped, it will have a very short description of the borough,
the types of events that are happening.
The user will then be able to confirm the borough, or choose a different one.

once the user has reached the max amount of boroughs, or are satisfied with their selection, they can continue to the next step.

### Screen 7

Are we planning for food or is the user figuring that out on their own?
When will the user want to eat?
(1h + 15min pre and post margin)

--- END OF PREPPLANNING ---

The user should then be shown multiple groups of events based on the time period they are choosing for.
Event groupings need to flow from time based, then be grouped by event types, filtered by the user's preference.
if the end time of an event more or less lines up with the common start time of a meal period, the user should be notified about choosing a meal for that period.
repeat this cycle until the user's plan duration has been filled.

--- END OF PLANNING ---

--- START OF PATHING ---

the user will then be presented a screen with a map that links their chosen destinations, both events and meals. They can then choose to start their day.

Their day will proceed in the follwing way:

When their day starts, the app will ask if they want to travel using public transit (Chrono, or Uber), and open the corresponding app with a trip to their destination loaded in.
Once they arrive to the destination, The app will enable an alarm for 30min before the end of the event, to make the user aware that they have to leave to make it to the next event on time.
When the user wants to leave the event, they can press a button to move onto the screen that will once again ask them if they want to use public transit or uber.
Repeat until the last event of the day.

When the user has reached the last event of the day, they can press a button to end their day.
This will log the day in the app, and add the event stamps in the the user's city passport.
