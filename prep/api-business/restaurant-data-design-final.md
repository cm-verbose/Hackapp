# Restaurant Data Design (v3.1)
**Montréal Trip Planner (Hackathon, sponsored by the City of Montréal)**

> Status: Data design only. The database engine and the backend stack will be chosen later.

**Changes since v2** (team feedback, 2026-11-21):
1. **Boundary:** membership is decided by the official City polygon, so **all 19 boroughs** are covered and the text filter is gone.
2. **Boroughs:** come from a configurable `area` reference table that we own (section 3).
3. **Chains:** only **Québec-local chains** are kept, through an allowlist. Thaï Express, Sushi Shop and all Canadian national chains are blocked.
4. **Price:** stored per person in the **same shape as Google's `priceRange`**. The user-facing brackets are reference data.
5. **New `description` field.**
6. **Bars:** included, but limited to evening slots.
7. **Cuisine for unmatched places:** derived from Google types.

---

## 1. Data Sources

| Source | Role |
|---|---|
| **City of Montréal: *Établissements alimentaires*** | Defines which places exist and whether they are operating |
| **City of Montréal: administrative boundaries** | Defines the City limits and the borough polygons (section 3) |
| **OpenStreetMap** | Display name, cuisine, chain detection (`brand`), dietary tags |
| **Google Places API (New)** | Price range, rating, opening hours, photo, description, closure check, cuisine fallback |

---

## 2. Ingestion Rules: filter *before* storing

| # | Rule | Rows left* |
|---|---|---|
| 0 | Raw City file | 54,937 |
| 1 | **Location falls inside the City of Montréal polygon** (section 3) | ≈ 50,600 |
| 2 | **Tourist-relevant food types** (section 6.1) | ≈ 25,000 |
| 3 | **City status = `Ouvert`** (section 4) | ≈ 6,500 |
| 4 | **Chain rule: only Québec-local chains** (section 5) | ≈ 6,000 |
| 5 | **One row per physical place** | **≈ 5,960** |

\* Measured with the sector-name filter from v2, which gives the same result as the polygon for these 15 sector names. The exact counts will be recomputed once the polygon is loaded.

Nothing that fails a rule is written to the main `Place` table. Raw rows stay in the staging area only.

---

## 3. Geography: City Limits & Boroughs (our own rules)

### 3.1 Principle
- **Do not use the City file's `city` text column** to decide membership.
  - It holds legacy sector names such as `St-Laurent`, `Pierrefonds` and `Roxboro`, not borough names.
  - A single value, `Montréal`, covers many boroughs.
  - It also includes the independent municipalities on the island (Westmount, Mont-Royal, Dorval, …). These are **not boroughs** and are not part of the City.
- **Use geometry instead.** A place belongs to Montréal if its coordinates fall inside one of the City's **19 borough polygons**.

### 3.2 Reference table: `area`
We own this table. Any boundary change, renaming or new custom zone is a data change here, followed by a re-run of the assignment job. No code change is needed.

| Field | Type | Notes |
|---|---|---|
| `code` | string, unique | e.g. `ville-marie`, `plateau-mont-royal` |
| `kind` | enum | `BOROUGH` (official) · `ZONE` (custom, e.g. tourist districts) |
| `name_fr`, `name_en` | string | display names |
| `geometry` | polygon / multipolygon | |
| `source` | string | e.g. `ville-de-montreal:limites-administratives` or `team-custom` |
| `version` | string / date | lets us track boundary updates |
| `aliases` | list of strings | legacy names found in source data (section 3.3) |
| `active` | boolean | lets us retire a zone without deleting it |

**Assignment rules:**
1. `Place.borough` is set to the `BOROUGH` polygon that contains the place.
2. A place that falls in **no** borough polygon is rejected at import, because it is outside the City.
3. A place on a border goes to the first matching borough by code. Points exactly on a border are rare.
4. `ZONE` areas, such as "Vieux-Montréal" or "Mile End", may overlap boroughs and each other. A place can be in several zones. Zones are **computed at query time**, not stored on `Place`, so editing a zone never needs a data migration.

### 3.3 The 19 boroughs and the legacy aliases in the City file

| Borough (`code`) | Aliases seen in `city` column |
|---|---|
| Ahuntsic-Cartierville | Montréal |
| Anjou | Anjou |
| Côte-des-Neiges–Notre-Dame-de-Grâce | Montréal |
| Lachine | Lachine |
| LaSalle | LaSalle |
| Le Plateau-Mont-Royal | Montréal |
| Le Sud-Ouest | Montréal |
| L'Île-Bizard–Sainte-Geneviève | L'Ile Bizard, Ste-Geneviève |
| Mercier–Hochelaga-Maisonneuve | Montréal |
| Montréal-Nord | Montréal-Nord |
| Outremont | Outremont |
| Pierrefonds-Roxboro | Pierrefonds, Roxboro |
| Rivière-des-Prairies–Pointe-aux-Trembles | Rivière-des-Prairies, Pointe-aux-Trembles |
| Rosemont–La Petite-Patrie | Montréal |
| Saint-Laurent | St-Laurent |
| Saint-Léonard | St-Léonard |
| Verdun | Verdun |
| Ville-Marie | Montréal |
| Villeray–Saint-Michel–Parc-Extension | Montréal |

Aliases are used only to **sanity-check** the polygon result (a place labelled `LaSalle` should land in the LaSalle polygon). They never decide membership on their own.

**Polygon source:** the City of Montréal open-data administrative boundaries dataset, which lists boroughs and the independent municipalities separately. We keep only the borough features. The exact dataset name and fields still need to be confirmed when we download it.

---

## 4. Status Model

Two different questions, kept separate:

| Question | Field | Stored? |
|---|---|---|
| Does this business still exist? | `status` | Yes: `ACTIVE` · `TEMPORARILY_CLOSED` · `CLOSED` |
| Is it open at time *t*? | none | No: computed from `opening_hours` at query time |

| City `statut` | Google `businessStatus` | Our `status` |
|---|---|---|
| `Ouvert` | `OPERATIONAL` or unknown | `ACTIVE` |
| `Ouvert` | `CLOSED_TEMPORARILY` | `TEMPORARILY_CLOSED` |
| `Ouvert` | `CLOSED_PERMANENTLY` | `CLOSED` |
| `Fermé` / `Fermé changement d'exploitant` | any | `CLOSED` |
| `En traitement` / `Sous inspection fédérale` | any | not imported |

- **First import:** only `ACTIVE` places are written.
- **Refresh:** a place may change to `CLOSED`. It is hidden but kept, so it is not re-imported by mistake.
- **App:** only `ACTIVE` places are served.

---

## 5. Chain Rule: Québec-local chains only

### 5.1 Rule
- A place is a **chain location** if any of these is true:
  - (a) its OSM `brand` is set;
  - (b) its normalized name matches a known brand or alias in the `brand` table;
  - (c) its normalized name appears at **3 or more addresses** in the City file. These are flagged for review rather than decided automatically.
- A chain location is **kept only if its brand has `decision = ALLOW`**. Everything else is removed at import.
- Kept chain locations get `is_chain = true`.

### 5.2 Reference table: `brand`

| Field | Notes |
|---|---|
| `name` | canonical brand name |
| `aliases` | name variants, e.g. `COCO THE ET JUS FRAIS` for CoCo |
| `decision` | `ALLOW` / `BLOCK` |
| `note` | why it was decided this way |

**Allow criterion:** a chain founded in Québec that still operates mainly in Québec **and is not part of a large franchise network**.

**Proposed `ALLOW` list:** La Belle Province, Restaurants Lafleur, Café Dépôt, Allô! Mon Coco, Poutineville, Frite Alors!, Benny & Co., L'Oeufrier, La Cage, Scores, Pacini, Cultures, Boustan, Marius, Jack Le Coq.

**`BLOCK` covers everything else**, including:
- international chains (McDonald's, Subway, Starbucks, …);
- Canadian national chains (Tim Hortons, A&W, Pizza Pizza, Second Cup, Harvey's, …);
- **Thaï Express**, **Sushi Shop**, **Mikes**, **Valentine** and **St-Hubert** (large franchise networks), by team decision;
- Cora and 3 Brasseurs;
- **Baton Rouge**, **Presse Café** and **Nickels**, by team decision.

On the sample data this rule removes about **530 places** (Subway 85, Tim Hortons 74, McDonald's 42, A&W 30, Starbucks 22, Pizza Hut 21, Domino's 20, Pizza Pizza 19, Sushi Shop 19, …). Fewer than 50 Québec-chain locations remain.

> Decided: the criterion is "Québec-founded **and** not part of a large franchise network". Changing it later is a `decision` edit in the `brand` table only.

---

## 6. Classification: `venue_type` + `cuisines`

### 6.1 `venue_type` (single value)
- **Source:** the OSM `amenity` when the place is matched to OSM; otherwise the City `type`; otherwise Google's `primaryType`.

| `venue_type` | City `type` | OSM `amenity` | Allowed planner slots |
|---|---|---|---|
| `restaurant` | Restaurant | restaurant | breakfast*, lunch, dinner |
| `fast_food` | Restaurant service rapide, Casse-croûte, Restaurant mets pour emporter | fast_food | lunch, dinner, snack |
| `cafe` | Café, thé, infusion, tisane | cafe | breakfast, snack |
| `bakery` | Pâtisserie/Boulangerie, Boulangerie | — | breakfast, snack |
| `dessert` | Bar laitier, Bar laitier saisonnier | ice_cream | snack |
| `food_truck` | Camion-cuisine | — | lunch, snack |
| `bar` | Bar salon / taverne, Brasserie | bar, pub | **evening only** |

\* Breakfast applies only if `cuisines` contains `breakfast` / `brunch`, or the opening hours start before 10:00.

The slot rules are stored as reference data (`venue_type_slot`), so they can be tuned without touching `Place`.

### 6.2 `cuisines` (list of codes)
Codes come from one controlled vocabulary. They are filled in this order:
1. OSM `cuisine`, split on `;` and normalized:
   - merge synonyms: `coffee_shop` → `coffee`; `regional` / `local` / `quebecois` → `quebecois`;
   - drop codes with no cuisine meaning: `restaurant`, `food`, `international`.
2. **Google `types` and `primaryType`, for places without OSM cuisine.** Strip the suffix (`vietnamese_restaurant` → `vietnamese`) and keep the result only if it is in the vocabulary.
3. If both are empty, `cuisines = []`. These places still appear when the user picks no cuisine, but they never match a cuisine chip.

> After the first Google enrichment run, measure how many of the roughly 4,500 places without OSM cuisine get a usable Google type. Then decide whether empty-cuisine places stay in the pool.

### 6.3 Reference table: `cuisine_group` (onboarding chips)

Chips use Material icons, never emojis. `cuisine_group.icon` stores the Material icon name.

| Group | Material icon | Chip | Codes |
|---|---|---|---|
| `taste_of_mtl` | `restaurant_menu` | Taste of Montréal | bagel, poutine, quebecois, deli, smoked_meat |
| `french` | `bakery_dining` | French & Bistro | french, bistro, crepe |
| `japanese_korean` | `set_meal` | Japanese & Korean | japanese, sushi, ramen, korean, poke |
| `chinese_se_asian` | `ramen_dining` | Chinese & SE Asian | chinese, dumplings, vietnamese, thai, cambodian, asian |
| `italian_pizza` | `local_pizza` | Italian & Pizza | italian, pizza, pasta |
| `middle_eastern` | `outdoor_grill` | Middle Eastern & Mediterranean | lebanese, greek, mediterranean, middle_eastern, turkish, kebab, shawarma |
| `latin_caribbean` | `local_fire_department` | Latin & Caribbean | mexican, caribbean, haitian, peruvian, portuguese |
| `south_asian` | `rice_bowl` | Indian & South Asian | indian |
| `quick_bites` | `fastfood` | Quick Bites | burger, chicken, sandwich, hot_dog, fries |
| `cafe_sweets` | `local_cafe` | Cafés & Sweets | coffee, tea, bubble_tea, cake, pastry, dessert, ice_cream, donut |
| `bars` | `local_bar` | Bars & Pubs | *(matches `venue_type = bar`)* |

---

## 7. Price: per person, Google-compatible

### 7.1 Stored value: same shape as Google `priceRange`
Google's `priceRange` is per person. Its **start price is inclusive** and its **end price is exclusive**; the end can be empty, meaning no upper bound (e.g. "More than $100").

| Field | Type | Notes |
|---|---|---|
| `price_min` | integer, nullable | per person, inclusive, CAD |
| `price_max` | integer, nullable | per person, **exclusive**; `null` means no upper bound |
| `price_source` | enum | `GOOGLE_RANGE` · `GOOGLE_LEVEL_ESTIMATE` · `NONE` |

- `price_min` and `price_max` are copied directly from Google `priceRange` (`startPrice.units`, `endPrice.units`).
- **Fallback** when Google has only `priceLevel`. Store an estimated range and mark it `GOOGLE_LEVEL_ESTIMATE`:

| `priceLevel` | estimated `price_min` – `price_max` |
|---|---|
| `INEXPENSIVE` | 0 – 20 |
| `MODERATE` | 20 – 50 |
| `EXPENSIVE` | 50 – 100 |
| `VERY_EXPENSIVE` | 100 – null |

### 7.2 User-facing brackets (reference data, not stored on `Place`)

| Bracket | Label | `max` (exclusive) |
|---|---|---|
| `UNDER_20` | < $20 | 20 |
| `UNDER_50` | < $50 | 50 |
| `UNDER_100` | < $100 | 100 |
| `ANY` | $100+ | none |

**Matching rule (upper bound):** a place fits bracket *B* if `price_max` is not null **and** `price_max ≤ B.max`.
- Example: Google "$20–30" → `price_max = 30` → fits `UNDER_50`, not `UNDER_20`.
- `ANY` matches everything, including `price_max = null` ("$100+").
- Places with `price_source = NONE` fit every bracket, with a small ranking penalty.

> Brackets are aligned to Google's range steps (10–20, 20–30, 30–50, 50–100, 100+). Confirm these steps on the first enrichment sample; if they differ, only the `price_bracket` table changes.

---

## 8. Description

| Field | Type | Notes |
|---|---|---|
| `description` | localized text `{ fr, en }` | short, 1–2 sentences |
| `description_source` | enum | `GOOGLE_EDITORIAL` · `GOOGLE_GENERATIVE` · `OSM` · `GENERATED` |

**Source order:**
1. **Google `editorialSummary`**, requested with `languageCode=fr` and then `en`. Google requires the text to be shown **as-is**, without changes.
2. **Google `generativeSummary`** (AI-generated by Google).
3. **OSM `description`** (rare: 48 places).
4. **Template** built from our own fields, e.g. *"Québécois · Poutine · Le Plateau-Mont-Royal · < $25"*. This is always available, so no place is left without a description.

**Cost note:** these Google fields are in the *Enterprise + Atmosphere* tier, the most expensive one. Request them once during enrichment, never per user query.

---

## 9. Dietary

- **Field:** `dietary`, a list.
- **Values:** `vegetarian`, `vegan`, `vegetarian_only`, `vegan_only`.

**Mapping:**
- From OSM `diet:vegetarian` / `diet:vegan`:
  - `yes` → `vegetarian` / `vegan`
  - `only` → `vegetarian_only` / `vegan_only`
  - `limited` / `no` → not set
- From Google `servesVegetarianFood = true` → `vegetarian`.

**Planner use:**
- The tags give a **ranking boost** only, because coverage is low (about 290 places from OSM).
- The exception is a strict vegan or vegetarian request, which filters on the `*_only` values.

---

## 10. Logical Data Model

### 10.1 `Place`

| Field | Type | Source |
|---|---|---|
| `id` | id | generated |
| `city_id` | string, unique | City `business_id` |
| `osm_id` | string, unique, nullable | OSM `@id` |
| `google_place_id` | string, unique, nullable | Google |
| `name` | string | OSM `name` → Google `displayName` → City `name` (title-cased) |
| `address` | string | City `address` (cleaned) |
| `location` | geo point | City coordinates |
| `borough` | `area.code` (kind = BOROUGH) | point-in-polygon (section 3) |
| `venue_type` | enum | section 6.1 |
| `cuisines` | list of codes | section 6.2 |
| `dietary` | list of codes | section 9 |
| `is_chain` | boolean | section 5 (Québec-local chains only) |
| `brand` | `brand.name`, nullable | section 5; all locations of a chain share one value, and the Passport awards one Stamp per brand |
| `price_min` | integer, nullable | section 7 |
| `price_max` | integer, nullable | section 7 |
| `price_source` | enum | section 7 |
| `description` | localized text | section 8 |
| `description_source` | enum | section 8 |
| `rating` | decimal 0–5, nullable | Google |
| `rating_count` | integer, nullable | Google |
| `opening_hours` | weekly schedule, nullable | Google `regularOpeningHours` |
| `photo_ref` | string, nullable | Google |
| `status` | enum | section 4 |
| `status_verified_at` | date | refresh job |
| `enriched_at` | timestamp, nullable | Google enrichment job |

### 10.2 Reference tables

| Table | Purpose | Section |
|---|---|---|
| `area` | borough polygons + custom zones | 3 |
| `brand` | chain allow / block decisions | 5 |
| `cuisine_group` | onboarding chips → cuisine codes | 6.3 |
| `venue_type_slot` | which meal slots each venue type may fill | 6.1 |
| `price_bracket` | user-facing budget brackets | 7.2 |

### 10.3 Example record
```json
{
  "id": 1042,
  "city_id": "48571",
  "osm_id": "node/9876543210",
  "google_place_id": "ChIJ...",
  "name": "Maixiang Dumpling",
  "address": "1150 Boulevard Saint-Laurent",
  "location": { "lat": 45.5081, "lng": -73.5606 },
  "borough": "ville-marie",
  "venue_type": "restaurant",
  "cuisines": ["chinese", "dumplings"],
  "dietary": [],
  "is_chain": false,
  "brand": null,
  "price_min": 10,
  "price_max": 20,
  "price_source": "GOOGLE_RANGE",
  "description": {
    "fr": "Raviolis chinois faits maison au cœur du Quartier chinois.",
    "en": "Handmade Chinese dumplings in the heart of Chinatown."
  },
  "description_source": "GOOGLE_EDITORIAL",
  "rating": 4.4,
  "rating_count": 812,
  "opening_hours": { "mon": [["11:00", "21:30"]] },
  "photo_ref": "places/ChIJ.../photos/AUc7...",
  "status": "ACTIVE",
  "status_verified_at": "2026-11-21",
  "enriched_at": "2026-11-21T19:00:00Z"
}
```
The values above are illustrative only, not real data.

---

## 11. Data Flow

```
City CSV ─────┐
OSM export ───┼─► [Staging: raw rows, match scores]
Boundary file ┘          │
                         ▼  rules 1–5: polygon · type · status · brand · de-dup
                  [Place: ACTIVE, borough assigned]
                         │
                         ▼  Google enrichment, once per place:
                         │  priceRange/priceLevel · rating · hours · photo ·
                         │  editorial/generative summary (fr, en) · businessStatus · types
                  [Place: enriched]
                         │
                         ▼  weekly refresh: City status, OSM changes, area changes → re-run rules
                  [App: ACTIVE places only]
```

---

## 12. Decisions Log

| # | Question | Decision |
|---|---|---|
| 1 | Canadian national chains | **Block** (only Québec-local chains allowed) |
| 2 | Bars in the planner | **Both**: included, but limited to evening slots |
| 3 | Price edge case | **Upper bound** of Google's range |
| 4 | Places without cuisine | **Derive from Google types**; re-evaluate after the first enrichment run |
| 5 | Boundary polygon | **Now**: polygon-based membership and boroughs (section 3) |
| — | Thaï Express, Sushi Shop | **Block** |
| — | Price unit | **Per person**, Google-compatible range |
| — | Description | **Add**, bilingual, with fallbacks |
| — | Mikes, Valentine, St-Hubert | **Block** (large franchise networks) |
| — | Price brackets | **< $20 / < $50 / < $100 / $100+**, aligned with Google's range steps |
| — | Cuisine fallback | **Google types**, confirmed |
| — | Custom `ZONE`s | **Deferred**; decided at the end of the project |
| — | Chain Stamps | **One Stamp per `brand`**, on the first location visited |
| — | National and international chains | **Removed** from `mtl_food_core_v2.csv`; local chains only |
| — | Baton Rouge, Presse Café, Nickels | **Block** and removed from `mtl_food_core_v2.csv` |

## 13. Still Open
1. Confirm Google's actual range steps for Montréal on the first enrichment sample (the brackets assume 20 / 50 / 100).
2. After enrichment: measure Google-type coverage for places without OSM cuisine.
3. Custom `ZONE` list for tourist districts (deferred).
