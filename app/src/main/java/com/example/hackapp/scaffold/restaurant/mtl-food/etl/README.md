# mtl-food-etl

Builds `places.db`, the SQLite database that ships inside the Android app (Room, `createFromAsset`).
It implements the data design v3.1 (`prep/api-business/restaurant-data-design-final.md` in the repo root). Agent-facing summary: repo-root `CLAUDE.md`.

## Setup
```bash
cd etl
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

## Inputs (put them in `etl/data/`)

| File | Where to get it |
|---|---|
| `businesses.csv` | City of Montréal open data, *Établissements alimentaires* (CSV) |
| `export.geojson` | overpass-turbo, query in Appendix A of the design doc, **Export → GeoJSON** |
| `boundaries.geojson` | City of Montréal open data, *Limites administratives de l'agglomération* (GeoJSON, WGS84) |

## Run
```bash
# 1) No Google: free; checks the filters and matching
python -m mtl_food_etl.pipeline --city data/businesses.csv --osm data/export.geojson \
    --boundary data/boundaries.geojson --no-google --out out/

# 2) Google on a small sample first (costs money): check out/report.json and the cache
export GOOGLE_MAPS_API_KEY=...
python -m mtl_food_etl.pipeline --city data/businesses.csv --osm data/export.geojson \
    --boundary data/boundaries.geojson --google-limit 50 --out out/

# 3) Full run. Places already in cache/google/ are never fetched again.
python -m mtl_food_etl.pipeline --city data/businesses.csv --osm data/export.geojson \
    --boundary data/boundaries.geojson --out out/

# 4) Copy into the app
# copy to <repo>/app/src/main/assets/databases/places.db
```
Without `--boundary`, the pipeline falls back to the City file's sector names. Membership is then the same, but most places get no borough, because the sector name "Montréal" covers 9 boroughs.

## Outputs (`out/`)

| File | Purpose |
|---|---|
| `places.db` | the app database (schema: `sql/schema.sql`, `PRAGMA user_version = 1`) |
| `report.json` | row counts after each rule, coverage numbers, Google call counts |
| `removed_chains.csv` | every place removed by the chain rule, with the brand that matched |
| `review_chain_candidates.csv` | names found at 3+ addresses that are not in the brand list yet; add them to `brands` as ALLOW or BLOCK |
| `review_low_score_matches.csv` | City↔OSM matches with a score of 0.75–0.85, to spot-check |

## Changing rules
All business rules live in **`config/reference.yaml`**: brands (ALLOW / BLOCK), borough names and aliases, venue types, meal slots, cuisine vocabulary and groups, price brackets. Edit the file and re-run; no code change is needed.

## Google cost
Each new place costs up to 3 calls:
1. Text Search (Pro)
2. Place Details (Enterprise + Atmosphere)
3. Place Details for the English description only (Atmosphere)

Use `--google-limit` while testing. Responses are cached in `cache/google/<city_id>.json`, so re-runs are free. To skip the English description call, construct `GoogleEnricher(..., second_lang=None)`.

## Schema changes
`sql/schema.sql` must match what Room generates from the Kotlin entities. If you change an entity:
1. Build the app; it exports `app/schemas/<db>/<version>.json`.
2. Copy the CREATE statements into `sql/schema.sql`.
3. Bump `DB_VERSION` in `pipeline.py` and `version` in `@Database`.

## Tests
```bash
python tests/test_etl.py
```
