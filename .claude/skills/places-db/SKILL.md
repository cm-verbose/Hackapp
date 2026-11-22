---
name: places-db
description: Hackapp restaurant database procedures. Use when refreshing or rebuilding places.db (re-running the Montréal food ETL, changing reference.yaml rules, copying a new DB into assets, bumping the data version) or when changing a Room entity of PlacesDatabase (Entities.kt, schema.sql, app/schemas export). Enforces zero paid Google calls.
---

# places-db: data refresh and Room schema change

Background: CLAUDE.md sections 3, 4, 5, 5.1, 5.2. The design doc `prep/api-business/restaurant-data-design-final.md` is final; check it before changing any data rule.

Paths used below:
- ETL folder (call it `<etl>`): `app\src\main\java\com\example\hackapp\scaffold\restaurant\mtl-food\etl`
- App DB: `app\src\main\assets\databases\places.db`
- Versions: `DB_VERSION` in `<etl>\mtl_food_etl\pipeline.py` and `VERSION` in `app\src\main\java\com\example\hackapp\data\local\PlacesDatabase.kt`. They must always be equal.

## Hard rules (check before anything else)
1. **No Google calls, ever, from this skill.** Every Google call costs money (CLAUDE.md 5.1, 7).
   - The ETL reads `GOOGLE_MAPS_API_KEY` as the default for `--google-key`. The key must be cleared **in the same command line** that runs Python. Agent shells reset between tool calls, so clearing it in a separate call does nothing. The guard prefix is: `$env:GOOGLE_MAPS_API_KEY = $null; if ($env:GOOGLE_MAPS_API_KEY) { throw 'STOP: Google key is set' }`
   - Every pipeline command must contain **`--google-key=`** (equals sign, nothing after it). Never omit it.
   - **Do not write `--google-key ""`.** Windows PowerShell 5.1 drops empty-string arguments when it calls a native program. Python then receives `--google-key` with no value, and argparse aborts with "expected one argument" (verified by workflow-auditor, 2026-11-21). `--google-key=` reaches argparse as an empty value. With an empty key, `google_enrich.py` reads the cache and makes no network calls.
2. **Never pass `--no-google`.** It ignores the cache and drops all Google data from the DB.
3. **Never delete, move or clean `<etl>\cache\google\`** (about 5,900 paid responses). Do not run `git clean` in the ETL folder.
4. Do not commit unless the user asks. Data rules live in `<etl>\config\reference.yaml`; change rules there, not in code.
5. If the task seems to need new Google data (new places, `--google-limit`), stop and ask the user. That is outside this skill.

## Path A: refresh the data (rules or inputs changed)
Each command below is self-contained: it sets its own folder, because the working directory resets between agent tool calls. `<repo>` is `D:\29401\Documents\Kotlin\Hackapp`. `<etl>` is the full ETL path given above, under `<repo>`. The cache path `cache/google` is relative to `<etl>`, so pipeline runs must start there.
1. Check the inputs exist: `Set-Location '<etl>'; Get-ChildItem data\businesses.csv, data\export.geojson, data\boundaries.json`
2. Bump **both** versions by one, before running: `DB_VERSION` in `pipeline.py` and `VERSION` in `PlacesDatabase.kt`. The pipeline writes `DB_VERSION` into the file as `PRAGMA user_version`, so the bump must come first.
3. Run the pipeline. This is one line, with the guard prefix from hard rule 1:
   `$env:GOOGLE_MAPS_API_KEY = $null; if ($env:GOOGLE_MAPS_API_KEY) { throw 'STOP: Google key is set' }; Set-Location '<etl>'; python -m mtl_food_etl.pipeline --city data\businesses.csv --osm data\export.geojson --boundary data\boundaries.json --google-key= --out out\`
4. Read `<etl>\out\report.json`. Compare the place count and Google coverage with CLAUDE.md 5.1 and section 8 (5,694 places at version 4). If the counts drop sharply or Google coverage falls to zero, stop and report. Zero coverage means the cache was not used.
5. Run the ETL tests: `Set-Location '<etl>'; python tests\test_etl.py`
6. Copy the DB into the app: `Copy-Item '<etl>\out\places.db' '<repo>\app\src\main\assets\databases\places.db' -Force`
7. **Return to the repo root** and, with an emulator running: `Set-Location '<repo>'; .\gradlew.bat connectedDebugAndroidTest`. Gradle rule: see `docs/implementation-plan.md` (during audits only workflow-auditor runs Gradle). Without a device it fails with `No connected devices!`. Report that instead of claiming success.
8. Update CLAUDE.md section 8 only through team-lead, or the user if working alone: new version, place count, date.

## Path B: change a Room entity (column, table or index)
1. Edit `data\local\Entities.kt` (and `Daos.kt` / `model\` if needed). Room validates the prepackaged file on first open and throws on any mismatch (CLAUDE.md 4).
2. Bump `VERSION` in `PlacesDatabase.kt` and `DB_VERSION` in `pipeline.py` to the same new number.
3. Build to export the schema: `Set-Location '<repo>'; .\gradlew.bat assembleDebug`, following the Gradle rule in Path A step 7. Then confirm `app\schemas\com.example.hackapp.data.local.PlacesDatabase\<new version>.json` exists.
4. Copy the `createSql` statements from that JSON into `<etl>\sql\schema.sql`. Replace `${TABLE_NAME}` with the real table name. Keep the indices. The two must match exactly.
5. If the ETL must fill the new column, update the ETL code. Then run **Path A steps 1 and 3-6**, skipping step 2 because the versions were already bumped in step 2. The same Google rules apply. A schema change always needs a rebuilt asset DB, since the old one no longer matches.
6. Run `Set-Location '<repo>'; .\gradlew.bat connectedDebugAndroidTest` with an emulator (`PlacesDatabaseTest` opens the DB and fails on a schema mismatch).
7. Commit `app\schemas\` together with the change when the user asks for a commit.

## Done means
- Both versions are equal, and the asset DB was built with that version.
- `report.json` counts are plausible and the ETL tests pass.
- The connected test passes, or you state plainly that no device was available.
- Zero Google calls were made, and `cache\google\` is untouched.
