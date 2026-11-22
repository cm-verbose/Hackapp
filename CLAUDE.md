# CLAUDE.md: Agent Context for Hackapp

Single entry point for AI agents. Read this first. If anything here is unclear or contradicts the code, ask the user before acting.

## 1. Project
- **Hackapp**: Android (Kotlin, Jetpack Compose) **Montréal trip planner** for a hackathon sponsored by the City of Montréal.
- App name pattern: **Plan[City]**, using the City Pack's city. Montréal is **Plan Montréal** (`app_name`; EN "Plan Montreal"). "Hackapp" is only the repository and code identifier (root project `HackApp`).
- Accounts are for people 18 and over; adults planning with children use the family-friendly Audience Preference.
- Gradle: AGP 9.5.2, Kotlin 2.4.21, minSdk 29, compileSdk 37. Namespace and `applicationId` are both `com.example.hackapp` (the `applicationId` was `com.example.androidtemplate` until the `layout` merge on 2026-11-21; an old install on a device stays as a separate app).
- Version catalog: `gradle/libs.versions.toml`. Navigation is Compose Navigation with `@Serializable` destinations (`scaffold/navigation/AppNavigation.kt`): start = `ScreenLoginDestination` -> onboarding (if Firebase `isNewUser`) or `ScreenHomeDestination`; Home leads to Planning 1-5 and Underway; Itinerary/Passport are tabs; `PlacesDestination` and the old `PlanningScreenDestination` are still registered. ScreenA/ScreenB were deleted.
- Auth (from branch `layout`): Google sign-in with Firebase Auth + Credential Manager (`auth/AuthHandler.kt`, `auth/AuthTag.kt`, `scaffold/screens/login/`). `MainActivity` calls `FirebaseApp.initializeApp`. Config in `app/google-services.json` (committed by the `layout` branch; it contains the Firebase API key).
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
| `app/src/main/java/com/example/hackapp/ui/theme/` | Design system from Figma: `Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt` (`HackappTheme`), `DesignSystemPreview.kt`. See section 6 |
| `app/src/main/java/com/example/hackapp/ui/components/` | Reusable Compose components from the Figma groups "Qindong" and "Qindong 2", each with `@Preview`. See section 6.2 |
| `app/src/main/res/values/strings.xml`, `values-en/strings.xml` | **French is the default locale** (`values/`), English in `values-en/`. Text comes from the Figma "strings.xml" page |
| `app/src/main/java/com/example/hackapp/auth/`, `scaffold/screens/{login,planning}/` | Google sign-in (Firebase), from branch `layout` |
| `app/src/main/java/com/example/hackapp/scaffold/screens/places/PlacesScreen.kt` | Restaurant browse screen (search + venue-type chips) over `PlaceRepository` |
| `app/src/main/java/com/example/hackapp/domain/` | Trip flow logic: `Profile`/`ProfileStore`, `Itinerary`, `EventRules`, `Planner`, `UnderwayStore`, `FlowViewModel` |
| `app/src/main/java/com/example/hackapp/services/` | `UnderwayNotifier` (alarms/notifications), `AppLocale`, `CityServices`, `LegEstimator`, `TripHandoff` |
| `app/src/main/java/com/example/hackapp/{citypack,viewmodel,widgets}/` | City pack data, events UI state (`viewmodel/{montreal,toronto}`), home-screen widget |
| `scaffold/screens/{onboarding,planning,underway,settings,home,itinerary,passport}/` | Flow screens (see section 8) |
| `docs/adr/` | Architecture decision records |
| `docs/implementation-plan.md` | Task board and current plan (team-lead) |
| `docs/{engineering,ux,design,qa}/`, `docs/skills-inventory.md` | Team audit reports and design decisions |
| `.claude/agents/`, `.claude/skills/` | Agent team role definitions; project skills (`places-db`, `add-compose-component`) |

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
- **venue_type**: `restaurant, fast_food, cafe, bakery, dessert, food_truck, bar`. Bars: **from 17:00 including dinner, adults-only audiences only** (user decision 2026-11-22, `docs/adr/0003-...`; supersedes the data design's evening-only rule; code change is task T22). Slots live in the `venue_type_slot` reference table.
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
# --no-google builds a DB WITHOUT Google data. To refresh the shipped DB from the cache with zero
# Google calls, use --google-key= instead (see section 8 and the places-db skill); boundary file is data/boundaries.json
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

## 6. UI: design system and components
Figma file `ETS-HackApp`, fileKey `VXMqds2RqjgaZ0JXHNNM6o`. Pages: design system `0:1` (foundations `26:2`, dark scheme `26:67`, Hackapp roles `49:2`), Components `53:1079`, strings.xml `55:2` (FR frame `55:3`, EN frame `55:7`), City Pack -> Compose `56:5`. The Figma design system replaces the older "Hochelaga" colours of the research brief (`prep/api-business/ui_design_research_brief_*.md`, still useful for onboarding and map-first layout ideas).

### 6.1 Theme (`ui/theme/`)
- `HackappTheme(darkTheme, stopTypePalette = Analogue) { }` = `MaterialTheme` + `LocalAppColors` + `LocalStopTypeColors`. No dynamic colour. Access extras with `HackappTheme.appColors` and `HackappTheme.stopTypeColors[StopType.Meal]`. `AndroidTemplateTheme` is a deprecated alias kept for old code.
- Colours (`Color.kt`): M3 `LightColors` / `DarkColors` from the Figma variables (primary `#E4002B`, primaryContainer `#FFDAD5` / on `#930018`, tertiaryContainer `#ADF0E0` / on `#005145`, surfaceContainerHigh `#F0E6E2`). `AppColors` adds hero `#5D002C` / onHero `#E77488`, heroCard `#181A1B` / onHeroCard `#E9E4DE`, canvas `#F0F0F1`, card, success/warning/info. Assumed (not in Figma): dark canvas `#19120E`, dark onSurfaceVariant `#D7C2BA`.
- `StopType` (Music, Festival, Theatre, Comedy, Exhibition, Sport, Cinema, Family, Meal, Bar) with three palettes (`StopTypePaletteMode`: Analogue, Triadique, ComplementaireDivisee); text on stop-type colours is `#151515`.
- Type (`Type.kt`): full M3 scale from Figma (e.g. displaySmall 36/44/-0.75 bold, labelLarge 14/20/0.1 medium). `HackappFonts.Brand` / `Plain` are `FontFamily.Default` for now (TODO: bundle Figtree in `res/font`).
- Shapes (`Shape.kt`): `HackappCorners` ExtraSmall 4, Small 8, Medium 16, LargeIncreased 20, Large 24, ExtraLarge 32, ExtraExtraLarge 48.

### 6.2 Components (`ui/components/`)
Conventions: colours/type/corners only from theme tokens; user-facing text from `stringResource` (defaults as parameters so callers can override); every component has an `@Preview` wrapped in `HackappTheme` using `PreviewDefaults` (380 dp, canvas background).
- Shared: `HackappIcons.kt` (`HackappIcon` enum -> Material icons, `HackappIconView`), `PreviewSupport.kt` (preview-only stand-ins for Eric's StopTypeTag / ItineraryStrip), `HackappSmallButton.kt` (filled `HackappSmallButton`, `HackappSmallTonalButton`, `HackappSmallTextButton`; M3 Round/Small, 40 dp).
- Group "Qindong" (home, leg): `HeroHeader`, `UnderwayItineraryCard`, `PlanItineraryCard`, `ItinerarySuggestionsCard`, `LegTimes`, `LegHeader`, `TransitFareRow`, `MealBlockPrompt`.
- Group "Qindong 2" (frame `90:91`): `LeaveByCard`, `LateArrivalWarning`, `ArrivalStatusCard`, `NextStopCard`, `InfoCallout`, `DataAttribution`, `RadioOptionRow` (M3 RadioButton, whole row selectable), `DistrictPreviewSheet` (content for a ModalBottomSheet; M3 `FilterChip` per Figma Code Connect), `DistrictMap`, `ItineraryRouteMap`.
- **Maps (real, City Pack based; replaced the MapPin placeholders on 2026-11-21)**, package `ui/components/map/`, designed in `prep/design/`:
  - `CityMap` city-agnostic base: parses `districts.json` SVG paths once, draws districts on a Canvas, hit-tests taps with `android.graphics.Region`, zooms to a `focus` set, exposes `overlay` (drawn in map units) and `content` (composables placed with `MapTransform.toScreen`).
  - `DistrictMap` (Figma **DistrictPickMap**): `stateOf(id)` -> `DistrictState` (Available, Previewed, Selected, Unavailable), `eventCountOf(id)` -> badge, `onDistrictClick`. `ItineraryRouteMap`: zoomed to the visited districts, dashed route line, numbered `RouteStop(order, districtId, type, location?)` markers coloured by `StopType` (position = `MapProjection.project(location)` when the pack has a projection, else the district label anchor).
  - `citypack/` (minimal): `CityPack`, `LocalCityPack` (default `MontrealCityPack`), `DistrictMapData`, `DistrictShape`, `MapProjection`, `GeoPoint`, `rememberDistrictMap()` (loads `assets/citypacks/mtl/districts.json` off the main thread). More cities = more packs, no code change.
  - `assets/citypacks/mtl/districts.json`: 34 shapes. The 19 boroughs use the **`area.code` ids of places.db** (`ville-marie`, `plateau-mont-royal`, ...), so places and events join to districts by id; the 15 linked cities have `groupId = "linked"`. Built from `etl/data/boundaries.json` by `tools/geojson_to_districts.py` (fixed to read UTF-8 explicitly: on Windows the default cp1252 corrupted the accents), then ids remapped to area codes. Rebuild recipe: run the script with `--name-prop NOM --group-map <{slug: "linked"}>`, then replace ids with `reference.yaml` borough codes (match by `norm_label` of the French name).
  - `DistrictEventCount` is Eric's (`components/DistrictEventCount.kt`, copied unchanged from `origin/components/Eric`; args `result`, `badgeColor`, `badgeSize`; its text is a fixed 26 sp, so `DistrictMap` uses `badgeSize = 32.dp`; badges overlap downtown at this size).
  - `MapPins.kt` (`MapPin`, `MapFrame`, `DefaultMapPinMarker`) is kept but no longer used by the maps. maps-compose is still **not** a dependency.
  - Known upstream quirks (not changed): `ItineraryRouteMap` fans several stops of one district by a fixed offset in map units, so they overlap when zoomed out; a selected district and its badge are both `primary` red.
- **"First Eric Group" (Figma `39:1880`) is now ours** (2026-11-21: the team took over Eric's components; the Figma note now names Qindong). Implemented in `ui/components/`: `StopTypeTag` (+ `StopType.icon`, `stopTypeLabel(type)`), `ItineraryStrip` (`StripStop(time, type)`, `currentIndex`), `StopRow` (time, dot, name/details, optional "Retirer" via `onRemove`), `Stamp` (+ `stampInitials(name)`; lemon disc, dashed ink border), `LocalNotificationPreview` (in-app mock of a notification, not a real one). `components/DistrictEventCount.kt` was rewritten to the Figma spec (primary fill, label/large onPrimary text, `99+` cap) with the same signature (`result`, `badgeColor`, `badgeSize`), so `DistrictMap` is unchanged. The preview stand-ins in `PreviewSupport.kt` were removed; `LegHeader` / `UnderwayItineraryCard` previews use the real `StopTypeTag` / `ItineraryStrip`. New strings: `action_remove`, `action_arrived`, `action_leaving`, `notification_preview_header`, `notification_arrival_*`, `notification_leave_*` (FR from Figma, EN by the agent).

### 6.3 Strings
- French default in `values/strings.xml`, English in `values-en/strings.xml` (team decision, 2026-11-21: "French default for Montréal"). Do not add `values-fr*` folders; the `layout` branch's `values-fr-rCA` was folded in and removed.
- French text is copied from Figma frame `55:3`. English for the Qindong 2 strings was written by the agent and has not yet been checked against Figma frame `55:7`.
- `map_district_boundaries` was added in code and is not on the Figma strings page yet.

## 7. Working rules
- Ask the user about anything unclear and confirm facts before executing.
- Verify against the design doc (`prep/api-business/restaurant-data-design-final.md`) before changing data rules; it is final.
- The scaffold folder `scaffold/restaurant/` was never git-tracked; deleting there is not recoverable via git.
- Kotlin block comments nest: never write `/*` inside a comment (e.g. "Icons/*" in KDoc) or the file fails with "Unclosed comment".
- Line endings: the working tree is CRLF on Windows, the index is LF. When running git from the Linux VM, pass `-c core.autocrlf=true` (status, add, merge, commit); otherwise every file looks modified. Commit as `Qindong-huang <jjie9622@gmail.com>`, English messages, no Claude co-author trailer (user request).
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
- To re-run the ETL with the cache but **no** Google calls: unset `GOOGLE_MAPS_API_KEY` and pass `--google-key=` (in Windows PowerShell 5.1, `--google-key ""` does not work: the empty argument is dropped and argparse aborts; do not use `--no-google`, it ignores the cache and drops all Google data).
- Numbered companies ("9513-5836 QUEBEC INC."): `text.trade_name` + `pipeline.step_names`. Order: OSM/Google name, then the trade name inside the legal name (9 places), then the nearest cached Google search candidate within 25 m (5 places, listed in `out/review_name_rescues.csv` for manual check; guesses, no type check), else the place is removed (10, `out/removed_unnamed.csv`). Data version 4, 5,694 places. Zero Google calls.
- Known name noise left as is: food trucks named like `(kamio) Name (plaque RP2503H-6)` or `... Quebec Inc. (plaque RR7075F)`.
- All of the above is committed on `feat-business` (user commits, e.g. `ffb457a`, `c3c1b91`).
- Design system from Figma implemented in `ui/theme/` and wired into `MainActivity` (user commit `8da73e3`, with the first strings).
- Figma group "Qindong" and "Qindong 2" components implemented in `ui/components/` (section 6.2). Fixed a build break caused by `/*` inside KDoc.
- Committed as 8 commits (`9165aff`..`a617867`): icons/preview helpers, small buttons, home components, leg/meal components, Qindong 2 strings, underway cards, planning helpers, placeholder maps.
- Merged `origin/layout` into `feat-business` (`b8cde2f`): Google sign-in (Firebase), login/planning screens, app rename, `applicationId` -> `com.example.hackapp`. Conflicts resolved by keeping both Gradle sides (Room/KSP + google-services/Credentials/Firebase), Login -> Planning navigation (Places kept as a route), French-default strings. `ExampleInstrumentedTest` now expects `com.example.hackapp`. `assembleDebug` compiles; tests need a running emulator (`No connected devices!` otherwise). Not pushed yet (the user pushes).
- Toronto restaurant DB: research done, work **paused** by the user (see open item 9).

- **Flow screens built (2026-11-21, not committed yet)**: Onboarding 1-4, Planning 1-5, Underway (Leg, Arrival, AtStop, EndOfDay), Settings; Passport/Itinerary tabs untouched. Code: `domain/` (`Profile.kt` + `ProfileStore` on DataStore, `Itinerary.kt`, `EventRules.kt`, `Planner.kt` = `ItineraryPlanner`, `UnderwayStore.kt`, `FlowViewModel.kt` activity-scoped draft state), `services/UnderwayNotifier.kt` (inexact alarms: arrival check +30 min with Arrived/Skip buttons, leave reminder -30 min), `services/AppLocale.kt` (FR/EN switch via `attachBaseContext`, no AppCompat), screens under `scaffold/screens/{onboarding,planning,underway,settings}`. Event `type` -> `StopType` in `eventStopType` (`other` is hidden). Meals: `PlaceRepository.search` around the last stop (or the district's places centroid), `Place.borough` == district id. Motion helpers in `ui/motion/Motion.kt`, transitions in `scaffold/navigation/NavTransitions.kt`.
- Known gaps: the whole flow was not driven end to end on a device (login needs real credentials); `TestNavHostController` crashes at teardown when a test pushes 3+ pages in a row (so `AppNavigationTest` opens each page directly); travel times are distance estimates; notification actions are untested on a device; the profile edit in Settings reuses the onboarding pages.

### Open items
1. [DONE] Download the City borough boundary GeoJSON into `etl/data/boundaries.json` and re-run with `--boundary` (DONE: file is `etl/data/boundaries.json`, 19/19 boroughs assigned).
2. Write the Google venue rules into the design doc so doc and code agree.
3. Classify the 64 names in `out/review_chain_candidates.csv` (e.g. Amir, Basha, Van Houtte, Copper Branch) as ALLOW or BLOCK in `reference.yaml`.
4. Optional: treat "Google found a different business at the same address" as closed.
5. Optional: "Taste of Montréal" has only about 95 places, because Google types have no `quebecois`; consider a curated list.
6. Bundle the Figtree font (`res/font`) and point `HackappFonts.Brand` / `Plain` to it.
7. [DONE] Eric's components (StopTypeTag, ItineraryStrip, StopRow, Stamp, LocalNotificationPreview, DistrictEventCount) implemented by us; screens should pass them into the existing slots.
8. Check the English Qindong 2 strings against Figma frame `55:7`; add `map_district_boundaries` to the Figma strings page. Add maps-compose when the real maps are built.
9. Toronto restaurant DB (paused, low priority). Decisions so far: free sources only, **no Google Places**; whole city; separate `places_toronto.db` with the same schema (no Room version change for Montréal); chain rule = Ontario-local chains only (agent drafts an ALLOW list for review). Sources: DineSafe CSV (Toronto Public Health, CKAN package `dinesafe`, one row per infraction, dedupe by establishment, keep latest inspection), 158 neighbourhoods GeoJSON (CKAN package `neighbourhoods`, EPSG:4326) as the area polygons, OSM Overpass `area["wikidata"="Q172"]` with amenity restaurant|fast_food|cafe|bar|pub|ice_cream, `out center tags`. Files go in `etl/data/toronto/` (download commands were given to the user; nothing downloaded yet). Plan: `--profile toronto` + `config/reference_toronto.yaml`; read DineSafe column names from the real file before writing the loader.
10. `app/google-services.json` (Firebase key) is in the repo from the `layout` branch; tell the team / restrict the key.
11. **Unresolved conflict:** `docs/adr/0002-hand-curated-restaurant-data.md` says not to store Google Places content, but the shipped `places.db` contains Google-derived prices, hours, photo refs and descriptions. The user decides with the team (2026-11-22). Until then agents do not modify `places.db`, and builds are not distributed outside the team.
