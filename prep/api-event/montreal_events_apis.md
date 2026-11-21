# Montreal Events APIs

Research date: November 21, 2026  
Purpose: Find public events happening in Montréal and collect the information needed by a concert/event finder.

## What we want / need

The app should represent each event with the following information:

| Requirement | What the app should store | Concrete Montréal example |
|---|---|---|
| **Event type** | A category such as concert, festival, workshop, exhibition, sports event, market, comedy or public activity. | **Festival du Jeu** is listed in the City of Montréal event calendar for November 21, 2026. |
| **Target demographic** | Intended audience or age restriction (children, adults, all ages, families, etc.). | **Matinée des tout-petits (0 à 3 ans)**, listed at Bibliothèque Saul-Bellow on November 21, 2026, explicitly targets children aged 0–3. |
| **Where** | Venue name, street address, borough/neighbourhood and, if possible, latitude/longitude. | The City calendar lists **Bibliothèque Saul-Bellow** for the toddler activity and **Bibliothèque de Saint-Léonard** for Festival du Jeu. |
| **When** | Local start and end date/time. Prefer exact times, normalized to 15-minute increments where necessary. Keep the original time too; do not invent an end time if the source does not provide one. | The City calendar lists Matinée des tout-petits at **10:15 on November 21, 2026** and Festival du Jeu at **10:00 on November 21, 2026**. |
| **Cost** | Free, paid, or unknown; include the amount and currency when supplied. | The City of Montréal dataset has a `cout` field whose documented examples include “gratuit” and “payant”. The event listing itself does not show an amount in the search result, so the price for these two examples should be treated as **unconfirmed**, not assumed free. |

**Important data rule:** Store missing values as `unknown`/`null`. A date-only event must not be assigned a made-up start time, and an event with no published end time must not be given an estimated end time.

---

## API references

### 1. Ville de Montréal — Événements publics (Open Data)

**Links**
- [Dataset documentation (Données Québec)](https://www.donneesquebec.ca/recherche/dataset/vmtl-evenements-publics)
- [CSV download](https://donnees.montreal.ca/dataset/6a4cbf2c-c9b7-413a-86b1-e8f7081e2578/resource/6decf611-6f11-4f34-bb36-324d804c9bad/download/evenements.csv)
- [GeoJSON download](https://donnees.montreal.ca/dataset/6a4cbf2c-c9b7-413a-86b1-e8f7081e2578/resource/35307457-a00f-4912-9941-8095ead51f6e/download/evenements.geojson)
- [City of Montréal event calendar](https://montreal.ca/evenements)

**Information**
- Official dataset published by the **Ville de Montréal**, based on events appearing in the City's public event calendar.
- Includes fields for `titre`, `description`, `date_debut`, `date_fin`, `type_evenement`, `public_cible`, `cout`, `adresse_principale`, `code_postal`, `lat` and `long`.
- Available as CSV and GeoJSON. The dataset is updated daily, but it does **not** represent every event in Montréal—only events published on the City's calendar. The dataset is in French.

**How it maps to our needs**
- **Event type:** `type_evenement` provides the category. Example: the City calendar lists **Festival du Jeu** on November 21, 2026; the dataset's event-type field is intended to classify listings such as shows, public meetings, exhibitions, workshops and markets.
- **Target demographic:** `public_cible` directly supports this. Example: **Matinée des tout-petits (0 à 3 ans)** at Bibliothèque Saul-Bellow on November 21, 2026 is explicitly for children aged 0–3.
- **Where:** `titre_adresse`, `adresse_principale`, `code_postal`, `lat` and `long` support map pins and address display. Example: the toddler activity is listed at **Bibliothèque Saul-Bellow**.
- **When:** `date_debut` and `date_fin` provide start and end date/time. Example: the City calendar lists the toddler activity at **10:15, November 21, 2026**. Check whether the source supplies a time before displaying it as exact.
- **Cost:** `cout` records whether an event is free or paid. Example: the dataset documentation gives “gratuit” and “payant” as example values. An exact dollar amount may require the event detail page.

**Example event query / record to test**
- Search the City calendar for **“Matinée des tout-petits (0 à 3 ans)”**.
- Published listing: **November 21, 2026 at 10:15**, **Bibliothèque Saul-Bellow**.
- This is a useful test record for the audience, start-time and venue fields. Retrieve the corresponding CSV/GeoJSON row for its exact `date_fin`, `cout` and coordinates rather than filling those fields by assumption.

**Assessment:** Best match for the app's core requirements because it explicitly contains event type, target audience, start/end date-time, cost and geographic fields. Limitation: coverage is limited to the City's calendar.

---

### 2. Montréal Has Things — Events API and feeds

**Links**
- [API and feed documentation](https://montreal.hasthings.com/feeds)
- [Events API](https://montreal.hasthings.com/api/events)
- [JSON event feed](https://montreal.hasthings.com/feeds/events.json)
- [CSV event feed](https://montreal.hasthings.com/feeds/events.csv)

**Information**
- Montréal-specific open event listings. The JSON, CSV, RSS and iCalendar feeds cover up to the next **60 days**; no API key or sign-up is required for ordinary use, and CORS is enabled.
- `/api/events` supports filters such as date, date range, category, venue, neighbourhood, tag and status. Categories include `music`, `film`, `comedy`, `dj-party`, `theatre`, `art`, `talk` and `other`.
- Each occurrence can include its title, UTC start timestamp, whether the start time is known, doors time, category, status, age restriction, venue, ticket URL, sources and (when supplied) end timestamp. The feed has a documented 60-request-per-minute limit; a free key can raise this to 600 requests per minute.

**How it maps to our needs**
- **Event type:** `category` supports music, film, comedy, DJ parties, theatre, art and talks. Example query: request Montréal events with `category=music,comedy` to find concerts and comedy shows.
- **Target demographic:** `ageRestriction` can contain values such as `19+` or `All ages`. Example: an event marked `19+` can be filtered from an all-ages recommendation list. An empty field means the source did not specify an age restriction.
- **Where:** `venue.name` and `venue.slug` identify the venue; venue endpoints provide venue information. Example: filter by a venue slug to retrieve upcoming listings at **Centre Bell** or **Club Soda**.
- **When:** `startsAt`, `startTimeKnown`, `doorsAt`, `nightDate` and (when available) `endsAt` are useful. Example: request events for **November 21, 2026** with `date=2026-11-21`. The documentation warns that `startsAt` is UTC and that a placeholder noon value may be used when the source did not publish a clock time; check `startTimeKnown` before displaying a precise time.
- **Cost:** The bulk event fields documented on this feed do not provide a reliable ticket-price field. `ticketUrl` links to the seller/source, where the price can be checked. Example: for a Centre Bell event, follow its `ticketUrl` to confirm the current ticket price rather than assuming a price from the API.

**Example API queries**
```text
# All Montréal events on November 21, 2026
https://montreal.hasthings.com/api/events?date=2026-11-21

# Music and comedy events on that date
https://montreal.hasthings.com/api/events?date=2026-11-21&category=music,comedy

# Montréal events in a date/time window (ISO 8601 instants)
https://montreal.hasthings.com/api/events?from=2026-11-21T00:00:00-05:00&to=2026-11-22T00:00:00-05:00
```

**Example event query to test**
- Use the date query for **November 21, 2026** and inspect events at venues such as **Centre Bell**, **Club Soda** or **MTELUS**.
- The feed's venue directory showed upcoming listings for Centre Bell, Club Soda and MTELUS when checked on November 21, 2026. Use the returned event records for the actual title/time and follow each `ticketUrl` for price details.

**Licence / attribution**
- Listing metadata is published under the **Open Database Licence 1.0**. Attribute it as **“Data: Has Things (hasthings.com)”**. If publishing a database derived from the feed, the ODbL share-alike requirements apply.

**Assessment:** Very useful for live music and nightlife discovery, and easy to integrate. It is weaker for target demographics and cost than the City dataset; prices should be obtained from the linked ticket seller.

---

### 3. Ticketmaster — Discovery API v2

**Links**
- [Official Discovery API documentation](https://developer.ticketmaster.com/products-and-docs/apis/discovery/v2/)
- [API Explorer](https://developer.ticketmaster.com/api-explorer/)
- [Developer portal / API key registration](https://developer.ticketmaster.com/)

**Information**
- Searches events, attractions and venues across supported countries and ticketing sources.
- Event search supports filters such as keyword, city/region/country, date-time range, classification/genre, venue and location.
- Event detail responses can include the event name, dates, venue/location, attractions, event URL and price ranges when the event source provides them.

**How it maps to our needs**
- **Event type:** classification and genre fields can distinguish concerts, sports, theatre and other event categories. Example: search for the keyword **“Canadiens”** in Montréal to find a hockey event.
- **Target demographic:** Ticketmaster event details may expose age limits or event-specific notes when supplied, but this is not guaranteed for every listing. Example: inspect the event detail record for an age restriction before marking a show all-ages.
- **Where:** event results include venue and location information. Example: a Montréal search for **Canadiens vs Flyers** can return **Bell Centre** as the venue.
- **When:** event date/time fields provide scheduled start information. Example: the Montréal event calendar lists **Canadiens vs Flyers on November 21, 2026 at 19:00** at Bell Centre; a matching Ticketmaster listing can be used to retrieve the ticketing event's exact date/time.
- **Cost:** price ranges may be included when available, and the event URL links to ticket purchase. Example: retrieve the price range for the matching Canadiens event if Ticketmaster supplies one; otherwise open the event URL and mark the price unknown until confirmed.

**Example API query**
```text
# Search for events in Montréal, Canada.
# Replace {API_KEY} with the key from your Ticketmaster developer account.
https://app.ticketmaster.com/discovery/v2/events.json?city=Montreal&countryCode=CA&apikey={API_KEY}

# Example date-filtered query (use local date/time converted to the API's required ISO format).
https://app.ticketmaster.com/discovery/v2/events.json?city=Montreal&countryCode=CA&startDateTime=2026-11-21T00:00:00Z&endDateTime=2026-11-22T04:59:00Z&apikey={API_KEY}
```

**Access notes**
- A developer API key is required. Without a valid key, requests return `401 Invalid ApiKey`.
- API coverage is ticketing-oriented, so it will not include every free community event, library activity or small local gathering.

**Assessment:** A useful complement for ticketed concerts, sports and major shows, especially when event URLs and ticket price ranges matter. It should not be the only event source.

---

## Recommended approach

Use the **Ville de Montréal open dataset** as the strongest source for the five required fields, then supplement it with **Montréal Has Things** for concerts, comedy and nightlife. Add **Ticketmaster Discovery API** for ticketed major events and ticket-price links.

| Need | Preferred source | Why |
|---|---|---|
| Event type | City dataset + Has Things | Explicit public-event types plus music/nightlife categories |
| Target demographic | City dataset | Dedicated `public_cible` field |
| Where / map | City dataset + Has Things | Coordinates/address in City data; venue listings in Has Things |
| Start and end | City dataset | Dedicated `date_debut` and `date_fin`; Has Things is a useful occurrence-level supplement |
| Cost | City dataset + Ticketmaster | City dataset identifies free/paid; Ticketmaster can provide ticket price ranges where available |

### Implementation cautions
1. Convert all timestamps to **America/Toronto** for display, while retaining the original timestamp and timezone.
2. Round only for display if needed; preserve the source time. If the source has no known start time, do not fabricate a 15-minute time.
3. Treat missing end times and prices as unknown. Follow the official event/ticket URL to fill them when possible.
4. Deduplicate events across sources using normalized title, venue and start time, while keeping all source URLs.
5. Respect each source's licence, attribution and rate limits.
