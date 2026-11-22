# Toronto Event APIs Reference & Data Mapping

## What We Want / Need

| Criteria | Description |
| ----- | ----- |
| **Event type** | A category or description that lets users filter events (concert, show, workshop, exhibition, market, comedy, etc.). |
| **Target demographic** | The intended audience, ideally with age ranges where available. |
| **Where** | Venue name, street address, borough/neighbourhood, and ideally latitude/longitude for map markers. |
| **When** | Start and end date/time. Store timestamps and display local Toronto time. Prefer 15-minute precision when the source provides it; do not invent a missing end time. |
| **Cost** | Price or a clear free/paid/unknown value. If possible, retain the source's original wording and currency. |

## API References

### 1. Toronto Has Things Feeds (`toronto.hasthings.com`)

* **Source URL:** [https://toronto.hasthings.com/feeds](https://toronto.hasthings.com/feeds)
* **Info Points:**
  1. Aggregator of Toronto event listings pulled from other sources: in the query below, Ticketmaster Discovery (541 of 1,000 events), Eventbrite (262), Toronto Public Library (62), plus venue calendars such as Tranzac Club, The Rex and Jazz Bistro.
  2. The JSON feed returned 1,000 listings, flagged `"truncated": true`, over a 21-day window (query generated 2026-11-21: events from Nov 21 to Nov 29). Licensed ODbL 1.0 (share-alike), attribution "Data: Has Things (hasthings.com)".
  3. Each listing is one showtime (unique `id`) grouped under an `eventId` (439 of the 1,000 share an `eventId` with another listing). It has no description, street address, coordinates or price fields.

* **How the API Maps to Requirements:**
  * **Event type:** Maps to `category` (8 values in this query: music, comedy, community, theatre, film, dj-party, art, sports), with finer detail in `tags` where `kind` is `genre`.
    * *Concrete Example:* "Ashdale Writers Group" has `category: "community"` and a genre tag `"Writing Groups"`. "O Christmas Tea: A British Comedy" has `category: "comedy"` with genre tags `"Comedy"` and `"Theatre"`.
  * **Target demographic:** Maps to `ageRestriction` (`null` for 776 of 1,000; otherwise `19+`, `All ages`, `18+`, `12+`, `22+`) and to `tags` where `kind` is `audience` (only `Adults` and `Seniors`, 93 events). There are no age ranges, only a minimum age.
    * *Concrete Example:* "Ashdale Writers Group": `ageRestriction: "All ages"` with audience tag `"Adults"`. "Dylan Pilcher Quintet" at The Rex: `ageRestriction: "19+"` (so `minAge` 19).
  * **Where:** Maps to `venue.name` and `venue.neighbourhood` (null for 916 of 1,000). There is no street address, postal code or latitude/longitude, so these would need geocoding from the venue name.
    * *Concrete Example:* `venue.name: "Lee's Palace"`, `venue.neighbourhood: "The Annex"` for "Yoi Toki w/ Saint Pepsi". `venue.name: "Toronto Public Library – Gerrard/Ashdale"` with `neighbourhood: null` for "Ashdale Writers Group".
  * **When:** Maps to `startsAt` and `endsAt` (UTC, ISO 8601; convert to America/Toronto), `doorsAt` (25 events) and `nightDate` (the local evening the event belongs to). `endsAt` is present for 421 of 1,000, otherwise `null`. When `startTimeKnown` is `false` (4 events) `startsAt` is a placeholder and only `nightDate` should be trusted. Start times fall on :00/:15/:30/:45 for 996 of 1,000 events, but end times include :59, :50 and :55 values, so 15-minute precision is not guaranteed.
    * *Concrete Example:* "Ashdale Writers Group": `startsAt: "2026-11-21T15:00:00.000Z"`, `endsAt: "2026-11-21T16:30:00.000Z"`, which is 10:00–11:30 local (90 minutes). "Sauced Improv at FREE TIMES CAFE": `doorsAt: "2026-11-21T20:30:00.000Z"` (3:30 pm), `startsAt` 4:00 pm, `endsAt` 6:00 pm. "Blood in the Snow" (Tranzac Club): `startTimeKnown: false`, `nightDate: "2026-11-21"`, so date only, no time.
  * **Cost:** The feed has no price field. The closest signals are `ticketUrl` (968 of 1,000, linking to the seller) and `status` (`scheduled`, `cancelled`, `sold-out`). Cost should be recorded as unknown unless looked up at the ticket link.
    * *Concrete Example:* "Dabda, The Neverminds" at The Baby G: `status: "sold-out"`, `ticketUrl: "https://dice.fm/event/6aa42b646b03960001547a69"`, no price. "Dylan Pilcher Quintet" at The Rex: `ticketUrl: null`, no price information of any kind.
