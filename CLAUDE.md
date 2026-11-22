# CLAUDE.md: Agent Context for Hackapp

Single entry point for AI agents. Read this first. If anything here is unclear or contradicts the code, ask the user before acting.

## 1. Project
- **Hackapp**: Android (Kotlin, Jetpack Compose) **Montréal trip planner** for a hackathon sponsored by the City of Montréal.
- Gradle: AGP 9.5.2, Kotlin 2.4.21, minSdk 29, compileSdk 37. Namespace `com.example.hackapp` (note: `applicationId` is `com.example.androidtemplate`).
- Version catalog: `gradle/libs.versions.toml`. Navigation is Compose Navigation with `@Serializable` destinations (`scaffold/navigation/AppNavigation.kt`); screens are still placeholders (ScreenA/ScreenB).
- Git: work happens on feature branches (current: `feat-business`); main branch is `main`. Do not commit unless asked.

## 2. Repository map
| Path | What |
|---|---|
| `app/src/main/java/com/example/hackapp/data/{model,local,repository}` | Restaurant data layer (Room). See section 4 |
| `app/src/main/assets/databases/places.db` | Prepackaged SQLite built by the ETL: 5,694 places, Google-enriched, all with a polygon borough, `user_version = 4` (2026-11-21) |
| `app/schemas/` | Room exported schema JSON (commit it) |
| `app/src/androidTest/.../data/PlacesDatabaseTest.kt` | Instrumented smoke test for the DB and a sample search |
| `app/src/main/java/com/example/hackapp/scaffold/restaurant/mtl-food/etl/` | Python ETL that builds `places.db` (section 5). Source of truth for data rules. `data/`, `cache/`, `out/` are git-ignored |
| `prep/api-business/restaurant-data-design-final.md` | **Data design v3.1. Final; wins over code on any conflict** |
| `prep/api-business/mtl_food_core_v2.csv` | Core restaurant CSV from earlier prep |
| `prep/api-business/ui_design_research_brief_*.md` | UI research: Montréal "Hochelaga" civic design system, Chrono/Maps/Waze, Tinder/Hinge-style onboarding |
| `prep/api-event/` | Event API research and a draft generalised event data struct (not covered by this file) |

## 3. Restaurant system architecture
```
City CSV + OSM export + borough polygons
        -> ETL (rules 1-5, optional Google enrichment)
        -> places.db  (prepackaged, read-only)
        -> Room (createFromAsset) -> PlaceRepository -> UI / planner
```
Design decisions (full detail in the design doc; do not re-litigate):
- **Boundary**: membership by point-in-polygon on the 19 official borough polygons, never by the City file's `city` text. Places in no borough are rejected.
- **Status**: only `ACTIVE` places are served. "Open at time t" is computed from `opening_hours` at query time, not stored.
- **Chains**: only Québec-local chains are kept (ALLOW list in `config/reference.yaml`). International and Canadian national chains, plus Thaï Express, Sushi Shop, Mikes, Valentine, St-Hubert, Cora, 3 Brasseurs are blocked.
- **venue_type**: `restaurant, fast_food, cafe, bakery, dessert, food_truck, bar`. Bars are evening-only. Slots live in the `venue_type_slot` reference table.
- **Cuisine**: controlled vocabulary; from OSM `cuisine`, else Google types, else empty. Onboarding chips are `cuisine_group` rows (11 groups; "bars" matches `venue_type = bar`).
- **Price**: per person, CAD, same shape as Google `priceRange`: `price_min` inclusive, `price_max` **exclusive**, `null` max = "$100+". `price_source`: `GOOGLE_RANGE | GOOGLE_LEVEL_ESTIMATE | NONE`. Brackets: `< $20`, `< $50`, `< $100`, `$100+` (table `price_bracket`).
- **Dietary**: ranking boost only; hard filters only for `vegetarian_only` / `vegan_only`.
- **Description**: bilingual (fr/en) with source order Google editorial -> Google generative -> OSM -> template.
- **Google venue rules** (added 2026-11-21, after enrichment; not yet written into the design doc): by Google `primaryType`, concession stands in arenas, stadiums, theatres, cinemas, clubs, schools, hospitals, etc. are **dropped**; places matched to the building (hotel, food/grocery store, supermarket, mall) are **kept without Google data**. Then one row per `google_place_id` (best name score wins). Lists live in `reference.yaml` -> `google_venue_rules`.
- **Deferred**: custom `ZONE` areas (tourist districts); decide at the end of the project.
- **Resolved on real data (2026-11-21)**: Google price ranges are mostly 10-20 / 20-30 (also irregular ones such as 20-60), so the 20/50/100 brackets stand. Cuisine coverage after Google fallback: 3,510 of 5,696 places.

## 4. Android data layer
Files (package `com.example.hackapp.data`):
- `local/Entities.kt` Room entities. **Must match `etl/sql/schema.sql` exactly** (Room validates the prepackaged file on first open and throws on any difference).
- `local/Daos.kt` `PlaceDao.searchInBox(...)` and `ReferenceDao`.
- `local/PlacesDatabase.kt` `createFromAsset("databases/places.db")`, `fallbackToDestructiveMigration(dropAllTables = true)` (Room 2.7+).
- `model/` `Place`, `PriceRange`, `OpeningHours`, `Geo`, enums (`VenueType`, `MealSlot`, `Dietary`, ...).
- `repository/PlaceRepository.kt` `PlaceRepository` interface + `RoomPlaceRepository`. UI and planner should only depend on the interface.

Dependencies: Room 2.8.5 and KSP 2.3.14 (see `libs.versions.toml`); schema export to `app/schemas`.

Usage:
```kotlin
val db = PlacesDatabase.get(context)
val repo: PlaceRepository = RoomPlaceRepository(db.placeDao(), db.referenceDao())
val brackets = repo.priceBrackets()
val places = repo.search(PlaceQuery(
    center = LatLng(45.5075, -73.5540), radiusMeters = 600.0,
    venueTypes = repo.venueTypesFor(MealSlot.LUNCH),
    cuisineGroups = setOf("taste_of_mtl", "french"),
    maxPrice = brackets.first { it.code == "UNDER_50" }.maxPrice,
    openAt = ZonedDateTime.of(2026, 12, 5, 12, 30, 0, 0, ZoneId.of("America/Toronto")),
))
```
Query semantics:
- Price uses the upper bound: match if `price_max <= maxPrice`; `price_source = NONE` always matches; `price_max = null` ("$100+") matches only when `maxPrice = null`.
- Cuisine chips match by cuisine group membership, or by venue type for the "Bars & Pubs" chip.
- `openAt` keeps open places plus places with unknown hours.
- The DAO filters on a lat/lng bounding box; the repository computes exact distance and sorts.

Updating data: re-run the ETL, copy the new `places.db` to `app/src/main/assets/databases/`, bump `PlacesDatabase.VERSION` **and** `DB_VERSION` in `etl/mtl_food_etl/pipeline.py`. Keep user data (favourites, saved trips) in a **separate** database, because this one is replaced on every data update.

Entity/schema change procedure: build the app (exports `app/schemas/<db>/<version>.json`), copy the CREATE statements into `etl/sql/schema.sql`, bump both versions.

## 5. ETL (`.../scaffold/restaurant/mtl-food/etl/`)
Python, deps in `requirements.txt`. All business rules (brands, boroughs and aliases, venue types, slots, cuisine vocabulary and groups, price brackets) live in `config/reference.yaml`; edit it and re-run, no code change.

Inputs go in `etl/data/` (not in the repo): `businesses.csv` (City "Établissements alimentaires"), `export.geojson` (Overpass export), `boundaries.geojson` (City administrative limits, WGS84).
```bash
cd etl && pip install -r requirements.txt
python -m mtl_food_etl.pipeline --city data/businesses.csv --osm data/export.geojson \
    --boundary data/boundaries.geojson --no-google --out out/
# Google (costs money): add --google-limit 50 first; needs GOOGLE_MAPS_API_KEY
# then copy out/places.db to <repo>/app/src/main/assets/databases/places.db
python tests/test_etl.py
```
Outputs in `out/`: `places.db`, `report.json` (counts per rule, coverage, Google calls), `removed_chains.csv`, `review_chain_candidates.csv` (names at 3+ addresses to classify ALLOW/BLOCK), `review_low_score_matches.csv` (City<->OSM matches scoring 0.75-0.85). `etl/sample_out/` holds one earlier sample run.
Also `removed_google_venues.csv` and `removed_google_duplicates.csv` (section 5.1).
Google cost: one Text Search (Pro) per new place, plus two Place Details calls (Enterprise + Atmosphere: French details, English description) when it matches. Responses are cached in `cache/google/<city_id>.json`; re-runs are free. Always use `--google-limit` while testing.
Without `--boundary` the pipeline falls back to sector names and most places get no borough.

### 5.1 Google enrichment (done 2026-11-21)
- Full run completed: 5,900 searches and about 10,150 details calls, roughly **US$260**, paid from the Google Cloud free-trial credit ($421, expires 2027-02-20). The trial account is **not upgraded**; do not upgrade it.
- API key: auto-created "Maps Platform API Key", restricted to **Places API (New)** only, no application restriction (the ETL runs on a PC). Set it with `$env:GOOGLE_MAPS_API_KEY = "..."` (PowerShell). Never commit it.
- Cache: `etl/cache/google/` (one JSON per City `business_id`, about 5,900 files). **Never delete it; back it up outside the repo.** Deleting it means paying again. Teammates can copy it to run the ETL for free.
- Matching (`google_enrich.choose_match`): candidates are stored in the cache, so tuning re-matches for free. Tiers: <=150 m and name score >=0.6; <=400 m and >=0.85; <=40 m and >=0.45. Both the OSM name and the City legal name are compared, plus the text inside and outside parentheses.
- Results: 5,094 of 5,929 matched (86%) before the venue rules. Unmatched places are mostly closed or replaced businesses (the City file still lists them as `Ouvert`). Google marked 177 places `CLOSED` and 111 `TEMPORARILY_CLOSED`; they are kept in the DB but never served.
- Final DB (after venue rules): 5,696 places; 4,761 with Google data; 3,906 with a price range; every place has fr/en descriptions (Google editorial, Google generative or template).
- Venue rules removed 174 concession stands (arena 40, association 21, theatre 19, stadium 17, sports club 17, cinema 13, event venue 13, catering 12, ...) and 59 duplicate records. A few may be real restaurants (some `catering_service`, `amusement_center` hits); move a type from `drop` to `strip` to keep those places.

### 5.2 Windows / PowerShell notes
- Run Gradle as `.\gradlew.bat ...` (PowerShell does not run programs from the current folder without `.\`).
- Use one-line commands (no `\` or backtick continuations); paths use `\`.
- Full refresh: run the pipeline -> `Copy-Item out\places.db <repo>\app\src\main\assets\databases\places.db -Force` -> bump `DB_VERSION` and `PlacesDatabase.VERSION` -> `.\gradlew.bat connectedDebugAndroidTest` with an emulator running.

## 6. UI direction (from `prep/api-business/ui_design_research_brief_*.md`)
- Civic look: Civic Red `#E4002B`, Deep Slate `#1E242B`, surfaces `#F4F6F8` / `#FFFFFF`, 1px `#DCE1E7` borders, 8px radius; bilingual FR/EN parity.
- Onboarding: 5-7 screen card deck, segmented progress bar, swipeable preference cards, chips (cuisine groups, budget).
- Exploration: full-bleed map with a 3-state draggable bottom sheet and floating route cards.

## 7. Working rules
- Ask the user about anything unclear and confirm facts before executing.
- Verify against the design doc (`prep/api-business/restaurant-data-design-final.md`) before changing data rules; it is final.
- The scaffold folder `scaffold/restaurant/` was never git-tracked; deleting there is not recoverable via git.
- Pre-existing quirk, left untouched: `kotlin("plugin.serialization") version "2.0.21"` in `app/build.gradle.kts` differs from Kotlin 2.4.21.
- Anything that calls Google costs money: always pass `--google-limit` for new places and never delete `etl/cache/google/`. In the app, map display (Maps SDK, client-side styling) is free, but each Google photo load (`photo_ref`) is billed: cache images and load photos only on detail screens. Do not add live Google search to the app; read restaurants from `places.db`.

## 8. Work log
### 2026-11-21
- Built the ETL and the Room data layer (option A: prepackaged SQLite, no backend). Design doc v3.1 finalised (`prep/api-business/restaurant-data-design-final.md`).
- `app/build.gradle.kts`: added `androidTestImplementation(platform(libs.androidx.compose.bom))`; without it `ui-test-junit4` has no version and `connectedDebugAndroidTest` fails to resolve. This is a template bug, not ours.
- `.gitignore`: added `**/mtl-food/etl/{data,cache,out}/`, `__pycache__/`, `*.pyc`.
- ETL inputs placed in `etl/data/` (`businesses.csv`, `export.geojson`).
- Google: fixed the matcher (it used to cache "no match" without candidates, so those places were never retried) and added tiered matching; ran the full enrichment; added the Google venue rules and de-duplication.
- Data version bumped to 2 (`DB_VERSION`, `PlacesDatabase.VERSION`); `app/schemas/.../2.json` exported. `PlacesDatabaseTest` passes on the `Medium_Phone` AVD emulator.
- Boundary polygons loaded (`--boundary data/boundaries.json`, re-run with an empty Google key so zero API calls); DB bumped to version 3 (`DB_VERSION`, `PlacesDatabase.VERSION`), copied into assets, `connectedDebugAndroidTest` passes.
- Added `PlaceDao.getAllActive()`, `PlaceRepository.allPlaces()` and a browse screen `scaffold/screens/places/PlacesScreen.kt` (search + venue-type chips), now the start destination in `AppNavigation.kt`.
- To re-run the ETL with the cache but **no** Google calls: unset `GOOGLE_MAPS_API_KEY` and pass `--google-key ""` (do not use `--no-google`, it ignores the cache and drops all Google data).
- Numbered companies ("9513-5836 QUEBEC INC."): `text.trade_name` + `pipeline.step_names`. Order: OSM/Google name, then the trade name inside the legal name (9 places), then the nearest cached Google search candidate within 25 m (5 places, listed in `out/review_name_rescues.csv` for manual check; guesses, no type check), else the place is removed (10, `out/removed_unnamed.csv`). Data version 4, 5,694 places. Zero Google calls.
- Known name noise left as is: food trucks named like `(kamio) Name (plaque RP2503H-6)` or `... Quebec Inc. (plaque RR7075F)`.
- Not committed yet: all of the above, on branch `feat-business`.

### Open items
1. [DONE] Download the City borough boundary GeoJSON into `etl/data/boundaries.json` and re-run with `--boundary` (DONE: file is `etl/data/boundaries.json`, 19/19 boroughs assigned).
2. Write the Google venue rules into the design doc so doc and code agree.
3. Classify the 64 names in `out/review_chain_candidates.csv` (e.g. Amir, Basha, Van Houtte, Copper Branch) as ALLOW or BLOCK in `reference.yaml`.
4. Optional: treat "Google found a different business at the same address" as closed.
5. Optional: "Taste of Montréal" has only about 95 places, because Google types have no `quebecois`; consider a curated list.
