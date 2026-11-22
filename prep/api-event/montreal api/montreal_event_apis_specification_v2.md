# Montreal Event APIs Reference & Data Mapping

## What We Want / Need

| Criteria | Description | Example | 
| ----- | ----- | ----- | 
| **Event type** | A category or description that lets users filter events (concert, show, workshop, exhibition, market, comedy, etc.). | Querying the Ville de Montréal event calendar for **“Autos de course et hélicoptères en LEGO”** returns an event described as a hands-on construction workshop for children. | 
| **Target demographic** | The intended audience, ideally with age ranges where available. | The same event is listed as being for **ages 9–13**. Another example, **“Atelier de Rigamajig,”** is for **children aged 6–12**. | 
| **Where** | Venue name, street address, borough/neighbourhood, and ideally latitude/longitude for map markers. | **“Autos de course et hélicoptères en LEGO”** takes place at **Bibliothèque Marc-Favreau, 500 boulevard Rosemont, Montréal, QC H2S 0C4**. | 
| **When** | Start and end date/time. Store timestamps and display local Montréal time. Prefer 15-minute precision when the source provides it; do not invent a missing end time. | **“Autos de course et hélicoptères en LEGO”** is listed for **November 21, 2026, 14:00–15:00**. The event **“Atelier : Stimuler le langage chez le bébé”** is listed for **November 22, 2026, 10:30–11:15** (a 45-minute event). | 
| **Cost** | Price or a clear free/paid/unknown value. If possible, retain the source's original wording and currency. | Both examples above are listed as **free (Gratuit)**. The event record also indicates whether registration is required. | 

## API References

### 1. Ville de Montréal Public Events Dataset (`donnees.montreal.ca`)

* **Source URL:** [https://donnees.montreal.ca/dataset/evenements-publics](https://donnees.montreal.ca/dataset/evenements-publics)
* **Info Points:**
  1. Official open dataset updated daily by the City of Montreal, accessible via CKAN Datastore API, GeoJSON, CSV, and SHP endpoints.
  2. Aggregates all public activities hosted across municipal facilities, public libraries, cultural centers, and boroughs.
  3. Provides raw French metadata fields specifically formatted for municipal programming including age groups, precise venue coordinates, and registration rules.

* **How the API Maps to Requirements:**
  * **Event type:** Maps directly to `type_evenement`.
    * *Concrete Example:* `type_evenement: "Atelier"` for the *Autos de course et hélicoptères en LEGO* workshop.
  * **Target demographic:** Maps directly to `public_cible`.
    * *Concrete Example:* `public_cible: "Enfants (9 à 13 ans)"` for *Autos de course et hélicoptères en LEGO*, and `public_cible: "Enfants (6 à 12 ans)"` for *Atelier de Rigamajig*.
  * **Where:** Maps to `titre_adresse` (venue name), `adresse_principale` (street address), `arrondissement` (borough), `lat` (latitude), and `long` (longitude).
    * *Concrete Example:* `titre_adresse: "Bibliothèque Marc-Favreau"`, `adresse_principale: "500 boulevard Rosemont"`, `arrondissement: "Rosemont–La Petite-Patrie"`, `lat: 45.5342`, `long: -73.5931`.
  * **When:** Maps to ISO format fields `date_debut` and `date_fin`.
    * *Concrete Example:* `date_debut: "2026-11-21T14:00:00"`, `date_fin: "2026-11-21T15:00:00"` for the LEGO workshop; `date_debut: "2026-11-22T10:30:00"`, `date_fin: "2026-11-22T11:15:00"` for *Atelier : Stimuler le langage chez le bébé*.
  * **Cost:** Maps to `cout` and `inscription`.
    * *Concrete Example:* `cout: "Gratuit"`, `inscription: "Sur inscription"`.

---

### 2. HasThings Montreal Aggregator Feeds (`montreal.hasthings.com`)

* **Source URL:** [https://montreal.hasthings.com/feeds](https://montreal.hasthings.com/feeds)
* **Info Points:**
  1. Community aggregator reading directly from Montreal local venue calendars, DIY spaces, independent music venues, and community bars.
  2. Outputted in standard feed formats (JSON Feed, iCal `/ical`, RSS `/rss`, CSV) designed for easy integration and periodic pulling.
  3. Fills gaps for niche, grassroots, and small-venue local events missing from commercial platforms like Eventbrite or Ticketmaster.

* **How the API Maps to Requirements:**
  * **Event type:** Mapped via `categories` (JSON Feed / RSS) or parsed from title tags and event descriptions.
    * *Concrete Example:* Category tag `["Concert", "DIY Workshop"]` associated with a local show listing.
  * **Target demographic:** Extracted via text parsing of the feed item `summary` / `content_text`.
    * *Concrete Example:* Description text noting `"Atelier ouvert aux 9-13 ans / All-ages show"`.
  * **Where:** Mapped via `location` property in iCal or `location.name` / `location.address` in JSON Feed schema.
    * *Concrete Example:* `location: "Bibliothèque Marc-Favreau, 500 boulevard Rosemont, Montréal, QC H2S 0C4"`.
  * **When:** Mapped via `dtstart` and `dtend` in iCal or `date_published` / `date_modified` / custom event dates in JSON Feed.
    * *Concrete Example:* `dtstart: "20261121T140000"`, `dtend: "20261121T150000"` (or ISO timestamps `2026-11-21T14:00:00-05:00`).
  * **Cost:** Parsed from item `summary` or cost metadata tags.
    * *Concrete Example:* `summary: "Gratuit / Free admission"`.

---

### 3. Eventbrite API (v3)

* **Info Points:**
  1. RESTful platform API endpoint (`/v3/events/search/`) filtered by geographic bounding box or location string (`"Montreal, QC"`).
  2. Excellent coverage of community workshops, independent local shows, classes, and pop-up events in Montreal.
  3. Provides structured timezone-aware start/end timestamps and clear fee status indicators (`is_free`).

* **How the API Maps to Requirements:**
  * **Event type:** Maps to `category_id` / `subcategory_id` and `format_id`.
    * *Concrete Example:* Category mapped as `"Class, Training & Workshop"` or `"Arts & Culture"` for hands-on youth sessions.
  * **Target demographic:** Derived from `summary`, `description.text`, or custom search tags.
    * *Concrete Example:* Text snippet in event summary indicating `"Pour enfants âgés de 9 à 13 ans / For kids ages 9–13"`.
  * **Where:** Maps to nested object `venue` (`venue.name`, `venue.address.address_1`, `venue.address.postal_code`, `venue.latitude`, `venue.longitude`).
    * *Concrete Example:* `name: "Bibliothèque Marc-Favreau"`, `address_1: "500 boulevard Rosemont"`, `postal_code: "H2S 0C4"`, `latitude: "45.5342"`, `longitude: "-73.5931"`.
  * **When:** Maps to `start.local` / `start.utc` and `end.local` / `end.utc` with `start.timezone: "America/Toronto"`.
    * *Concrete Example:* `start.local: "2026-11-21T14:00:00"`, `end.local: "2026-11-21T15:00:00"` (exact 60-minute duration).
  * **Cost:** Maps to `is_free` (boolean) and `ticket_classes[].cost.display`.
    * *Concrete Example:* `is_free: true` and display price string `"Gratuit" / "$0.00 CAD"`.

---

### 4. Ticketmaster Discovery API

* **Info Points:**
  1. Commercial REST API querying major venue events in Montreal using location parameters (`city=Montreal`, `marketId=102`).
  2. Primary source for major concerts, theatre productions, comedy shows, and large-scale exhibitions in venues like Place des Arts, Centre Bell, or MTELUS.
  3. Offers granular classification trees (`segment`, `genre`, `subGenre`) and standardized venue spatial data.

* **How the API Maps to Requirements:**
  * **Event type:** Maps to `classifications[0].segment.name` and `classifications[0].genre.name`.
    * *Concrete Example:* `segment.name: "Arts & Theatre"`, `genre.name: "Children's Theatre / Workshop"`.
  * **Target demographic:** Maps to `ageRestrictions` object and `pleaseNote` advisory text.
    * *Concrete Example:* `ageRestrictions.legalAgeEnforced: false` with description `"All Ages / Pour tous (recommandé 9-13 ans)"`.
  * **Where:** Maps to `_embedded.venues[0]` (`name`, `address.line1`, `city.name`, `postalCode`, `location.latitude`, `location.longitude`).
    * *Concrete Example:* `name: "Place des Arts"`, `line1: "175 Sainte-Catherine St W"`, `latitude: "45.5088"`, `longitude: "-73.5654"`.
  * **When:** Maps to `dates.start.dateTime` and `dates.end.dateTime` (ISO-8601).
    * *Concrete Example:* `dates.start.dateTime: "2026-11-22T10:30:00Z"`, `dates.end.dateTime: "2026-11-22T11:15:00Z"`.
  * **Cost:** Maps to `priceRanges` array (`min`, `max`, `currency`).
    * *Concrete Example:* `min: 0.00`, `max: 0.00`, `currency: "CAD"` (or explicitly marked as non-ticketed/free in venue notices).

---

### 5. PredictHQ Demand Intelligence API

* **Info Points:**
  1. Event intelligence API aggregating and enriching event data globally, with extensive coverage across Greater Montreal.
  2. Categorizes events using a standardized taxonomy (`concerts`, `community`, `expos`, `performing-arts`, `sports`).
  3. Automatically normalizes event timings, polygon locations, and target audience labels across multiple raw sources.

* **How the API Maps to Requirements:**
  * **Event type:** Maps to `category` and `labels` array.
    * *Concrete Example:* `category: "community"`, `labels: ["workshop", "hands-on", "family"]`.
  * **Target demographic:** Mapped via the enriched `labels` tag array.
    * *Concrete Example:* `labels: ["children", "kids", "ages-6-12", "family"]`.
  * **Where:** Maps to `entities` (venue object) and `location` coordinate array `[longitude, latitude]`.
    * *Concrete Example:* `entities[0].name: "Bibliothèque Marc-Favreau"`, `location: [-73.5931, 45.5342]`.
  * **When:** Maps to UTC timestamps `start` and `end`, converted to local time via timezone field `"America/Toronto"`.
    * *Concrete Example:* `start: "2026-11-21T19:00:00Z"` (which corresponds to `14:00 EST` local time in Montreal) and `end: "2026-11-21T20:00:00Z"`.
  * **Cost:** Maps to `phq_attendance` metadata and underlying pricing indicator fields.
    * *Concrete Example:* `cost: "free"` with local booking requirements flagged under metadata.