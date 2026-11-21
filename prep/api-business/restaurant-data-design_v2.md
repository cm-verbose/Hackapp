# Restaurant Data Design (v2)
**Montréal Trip Planner (Hackathon, sponsored by the City of Montréal)**

> Status: Data design only. The database engine and the backend stack will be chosen later.
> This version applies the team decisions from 2026-11-21:
> - City of Montréal only
> - No international fast-food chains
> - Simpler schema with no duplicated fields
> - Clear status model
> - Price brackets instead of $ signs
> - Two classification fields instead of five
> - Dietary tags kept; seating and accessibility tags dropped

---

## 1. Data Sources

| Source | Role in the design |
|---|---|
| **City of Montréal — *Établissements alimentaires*** (`businesses.csv`) | **Defines which places exist.** A place enters our DB only if the City lists it as operating. |
| **OpenStreetMap** (Overpass export) | Display name and cuisine. Also gives chain detection (`brand`) and dietary tags. |
| **Google Places API (New)** | Price, rating, opening hours and photos. Also gives a second check on whether a place is closed. |

---

## 2. Ingestion Rules: filter *before* storing

Every rule runs in the import step. Nothing that fails a rule is ever written to the main database.

| # | Rule | Rows left |
|---|---|---|
| 0 | Raw City file | 54,937 |
| 1 | **Inside the City of Montréal** (section 2.1) | 50,614 |
| 2 | **Tourist-relevant food types only** (section 5.1) | 25,065 |
| 3 | **City status = `Ouvert`** (section 3) | 6,529 |
| 4 | **Not an international fast-food chain** (section 2.2) | 6,276 |
| 5 | **One row per physical place** (de-duplication, section 2.3) | **6,241** |

Of the final 6,241 places:
- 2,475 are matched to OSM.
- 1,627 have a cuisine from OSM. The rest get their cuisine from Google types during enrichment.
- 301 have dietary tags from OSM.

### 2.1 City of Montréal boundary
The City file covers the **whole island**, which includes the separate municipalities that left the City in 2006. Its `city` column holds the sector name.

**Keep these 15 values.** They are all inside the City of Montréal:
`Montréal`, `St-Laurent`, `St-Léonard`, `Montréal-Nord`, `LaSalle`, `Verdun`, `Anjou`, `Lachine`, `Pierrefonds`, `Roxboro`, `Outremont`, `L'Ile Bizard`, `Ste-Geneviève`, `Pointe-aux-Trembles`, `Rivière-des-Prairies`.

**Drop these 14 values.** They are separate municipalities:
`Westmount`, `Mont-Royal`, `Côte Saint-Luc`, `Hampstead`, `Montréal-Ouest`, `Montréal-Est`, `Dorval`, `Pointe-Claire`, `Dollard-des-Ormeaux`, `Kirkland`, `Beaconsfield`, `Baie D'Urfé`, `Ste-Anne-de-Bellevue`, `Senneville`.

OSM data is already limited to the City, because the Overpass query uses `geocodeArea: Montréal`.

> **Later improvement:** replace the text filter with a point-in-polygon test against the City's official boundary file (*limites administratives*). This also lets us derive the **borough** for each place.

### 2.2 International fast-food chains
- **Rule:** remove a place if its brand comes from outside Canada and the place is fast food, a café chain, or a dessert chain.
- **Detection:**
  1. The OSM `brand` tag, when the place is matched to OSM.
  2. Otherwise, the normalized City `name` starting with a blocked brand name.
  3. Brands of 4 letters or fewer (e.g. KFC, CoCo) need an exact or alias match. This avoids false positives such as *Coco Frutti*, which is a local business.

**Blocklist (v1):** McDonald's, Subway, Starbucks, Domino's, Pizza Hut, Dairy Queen, KFC, Burger King, Wendy's, Five Guys, Papa John's, Popeyes, Taco Bell, Little Caesars, Quiznos, Krispy Kreme, Ben & Jerry's, Baskin-Robbins, TCBY, CoCo Fresh Tea, Presotea, Chatime, Gong Cha, Kung Fu Tea.

This rule removed **253 places**. The largest were Subway (85), McDonald's (45), Starbucks (22), Domino's (21), Pizza Hut (21) and Dairy Queen (17).

**Kept on purpose:** Canadian and Québec chains, because many of them are part of local food culture:
- Québec: La Belle Province, St-Hubert, Lafleur, Valentine, Sushi Shop, Thaï Express, Café Dépôt, Allô! Mon Coco, Poutineville, Frite Alors!, Cora
- Canadian: Tim Hortons, A&W Canada, Pizza Pizza, Second Cup, Harvey's

These keep `is_chain = true`, so the planner can rank them lower (235 places).

> ⚠️ **Team decision needed:** do Tim Hortons and the other Canadian national chains count as "international"? Removing them is a one-line change to the blocklist.

### 2.3 De-duplication
- The City file can hold several rows for the same address, for example after a change of operator. Several City rows can also match the same OSM place.
- **Rule:** keep one row per OSM place (the best match). Unmatched City rows stay as their own places.

---

## 3. Status Model

The earlier design mixed up two different questions. They are now separate.

| Question | Field | Stored? | Values |
|---|---|---|---|
| **Does this business still exist?** | `status` | ✅ Stored | `ACTIVE`, `TEMPORARILY_CLOSED`, `CLOSED` |
| **Is it open at a given time?** | *(none)* | ❌ **Never stored.** Computed at query time from `opening_hours`. | — |

**How `status` is decided:**

| City `statut` | Google `businessStatus` | Our `status` |
|---|---|---|
| `Ouvert` | `OPERATIONAL` or unknown | `ACTIVE` |
| `Ouvert` | `CLOSED_TEMPORARILY` | `TEMPORARILY_CLOSED` |
| `Ouvert` | `CLOSED_PERMANENTLY` | `CLOSED` |
| `Fermé` / `Fermé changement d'exploitant` | any | `CLOSED` |
| `En traitement` / `Sous inspection fédérale` | any | not imported |

- On first import only `ACTIVE` places are written, because rule 3 drops everything else.
- On later refreshes a place can change to `CLOSED`. We keep the row and hide it, so we don't re-import it by mistake. Only `ACTIVE` places are returned to the app.
- `status_verified_at` records when the status was last checked against the sources.
- A "changement d'exploitant" (change of operator) closes the old record. The new operator shows up in the City file as a new record.

---

## 4. Price Brackets

Price is **per person, in CAD**, stored as one bracket.

| Code | Label in app |
|---|---|
| `UNDER_25` | < $25 |
| `UNDER_50` | $25–50 |
| `UNDER_100` | $50–100 |
| `OVER_100` | $100+ |

**How the bracket is assigned** (first rule that applies):
1. **Google `priceRange`** (start and end price). Use the **upper bound**. If the upper bound is missing (Google's "$100+" case), use `OVER_100`.
2. **Google `priceLevel`**, as an approximate fallback:
   - `INEXPENSIVE` → `UNDER_25`
   - `MODERATE` → `UNDER_50`
   - `EXPENSIVE` → `UNDER_100`
   - `VERY_EXPENSIVE` → `OVER_100`
3. If Google has neither, leave it `null`. The app shows "price unknown". The planner treats it as matching any budget, with a small score penalty.

**Matching the user's budget:** a user who picks "< $50" sees places in `UNDER_25` and `UNDER_50`.

Assumptions to verify:
- that Google's `priceRange` is per person, as Google Maps displays it;
- that the `priceLevel` fallback is accurate enough. It should be checked on a sample of places that have both fields.

---

## 5. Classification: two fields

The earlier design had five overlapping fields (`mtl_type`, `osm_amenity`, `cuisines`, `categories`, `google_types`). They are merged into two.

| Field | Answers | Cardinality |
|---|---|---|
| `venue_type` | *What kind of place is it?* | one value |
| `cuisines` | *What food does it serve?* | list of codes |

The onboarding **chips are not stored on each place**. They are a separate reference table that groups cuisine codes (section 5.3). A chip such as "Taste of Montréal" is then just a query over `cuisines`, so no separate signature flag is needed.

### 5.1 `venue_type`
Rule: use the OSM `amenity` when the place is matched to OSM, because it is more precise; otherwise map the City `type`.

| `venue_type` | City `type` values | OSM `amenity` | Count |
|---|---|---|---|
| `restaurant` | Restaurant | restaurant | 3,585 |
| `fast_food` | Restaurant service rapide, Casse-croûte, Restaurant mets pour emporter | fast_food | 978 |
| `bar` | Bar salon / taverne, Brasserie | bar, pub | 621 |
| `cafe` | Café, thé, infusion, tisane | cafe | 441 |
| `bakery` | Pâtisserie/Boulangerie, Boulangerie | — | 363 |
| `food_truck` | Camion-cuisine | — | 154 |
| `dessert` | Bar laitier, Bar laitier saisonnier | ice_cream | 99 |

All other City types are not imported (grocery, daycare, wholesale, cafeterias, butchers, cheese shops, …).

### 5.2 `cuisines`
- **Values:** lowercase codes from one controlled vocabulary.
- **Sources:**
  1. OSM `cuisine`, split on `;`.
  2. Google `types`, with the suffix removed (`chinese_restaurant` → `chinese`).
- **Normalization rules:**
  - Merge synonyms: `coffee_shop` → `coffee`; `regional` / `local` / `quebecois` → `quebecois`.
  - Drop codes that carry no cuisine meaning: `restaurant`, `food`, `international`.
  - Drop values outside the vocabulary.
- **Raw source values** are kept only in the import staging area, never in the main table.

### 5.3 Reference table: `cuisine_group` (onboarding chips)

| Group code | Chip | Cuisine codes |
|---|---|---|
| `taste_of_mtl` | 🥯 Taste of Montréal | bagel, poutine, quebecois, deli, smoked_meat |
| `french` | 🥐 French & Bistro | french, bistro, crepe |
| `japanese_korean` | 🍣 Japanese & Korean | japanese, sushi, ramen, korean, poke |
| `chinese_se_asian` | 🍜 Chinese & SE Asian | chinese, dumplings, vietnamese, thai, cambodian, asian |
| `italian_pizza` | 🍝 Italian & Pizza | italian, pizza, pasta |
| `middle_eastern` | 🧆 Middle Eastern & Mediterranean | lebanese, greek, mediterranean, middle_eastern, turkish, kebab, shawarma |
| `latin_caribbean` | 🌶️ Latin & Caribbean | mexican, caribbean, haitian, peruvian, portuguese |
| `south_asian` | 🍛 Indian & South Asian | indian |
| `quick_bites` | 🍔 Quick Bites | burger, chicken, sandwich, hot_dog, fries |
| `cafe_sweets` | ☕ Cafés & Sweets | coffee, tea, bubble_tea, cake, pastry, dessert, ice_cream, donut |

A place can belong to several groups.

---

## 6. Dietary

- **Field:** `dietary`, a list.
- **Values:** `vegetarian`, `vegan`, `vegetarian_only`, `vegan_only`.

**Mapping:**
- From OSM `diet:vegetarian` / `diet:vegan`:
  - `yes` → `vegetarian` / `vegan`
  - `only` → `vegetarian_only` / `vegan_only`
  - `limited` / `no` → not set
- From Google `servesVegetarianFood = true` → `vegetarian`.

**Coverage is low** (301 places from OSM). The planner uses dietary tags as a **ranking boost**. It never uses them as a hard filter, except for `*_only` when the user asks for strictly vegan or vegetarian.

---

## 7. Logical Data Model

The model is independent of the database. Types are generic; they will be mapped once the DB is chosen.

### 7.1 `Place` (main entity)

| Field | Type | Source (priority order) | Notes |
|---|---|---|---|
| `id` | id | generated | internal key |
| `city_id` | string, unique | City `business_id` | ties the place back to the City file for status refresh |
| `osm_id` | string, unique, nullable | OSM `@id` | e.g. `node/123456` |
| `google_place_id` | string, unique, nullable | Google | key for enrichment refresh |
| `name` | string | OSM `name` → Google `displayName` → City `name` (title-cased) | one display name only |
| `address` | string | City `address` | whitespace cleaned up |
| `location` | geo point (lat, lng) | City coordinates | one location field |
| `venue_type` | enum | section 5.1 | |
| `cuisines` | list of codes | section 5.2 | |
| `dietary` | list of codes | section 6 | |
| `is_chain` | boolean | OSM `brand` present | Canadian/Québec chains only (international ones were filtered out) |
| `price_bracket` | enum, nullable | section 4 | |
| `rating` | decimal (0–5), nullable | Google | |
| `rating_count` | integer, nullable | Google | used to smooth the rating |
| `opening_hours` | structured weekly schedule, nullable | Google `regularOpeningHours` | "open now" is computed from this, never stored |
| `photo_ref` | string, nullable | Google | one main photo |
| `status` | enum | section 3 | |
| `status_verified_at` | date | refresh job | |
| `enriched_at` | timestamp, nullable | Google refresh job | |

### 7.2 Reference data
- **`cuisine_group`**: group code, chip label (FR / EN), emoji, list of cuisine codes (section 5.3).
- **`chain_blocklist`**: brand name, aliases (section 2.2).

### 7.3 Removed from v1

| Removed | Reason |
|---|---|
| `state`, `city`, `x`, `y` | Always the same value, or redundant with `location` |
| `legal_name` | Merged into `name` |
| `borough` | Will be derived spatially later (section 2.1) |
| `mtl_type`, `osm_amenity`, `google_types`, `categories` | Merged into `venue_type` and `cuisines`, plus the `cuisine_group` reference table |
| `is_montreal_signature` | Covered by the `taste_of_mtl` group |
| `price_level`, `price_min`, `price_max`, `price_currency` | Replaced by `price_bracket` |
| `outdoor_seating`, `wheelchair` | Out of scope (team decision) |
| `mtl_statut`, `mtl_statut_date` | Replaced by `status` and `status_verified_at` |
| `match_score` | Import-time only; kept in staging, not in `Place` |
| `photo_refs[]` | Reduced to one `photo_ref` |

### 7.4 Example record
```json
{
  "id": 1042,
  "city_id": "48571",
  "osm_id": "node/9876543210",
  "google_place_id": "ChIJ...",
  "name": "Maixiang Dumpling",
  "address": "1150 Boulevard Saint-Laurent",
  "location": { "lat": 45.5081, "lng": -73.5606 },
  "venue_type": "restaurant",
  "cuisines": ["chinese", "dumplings"],
  "dietary": [],
  "is_chain": false,
  "price_bracket": "UNDER_25",
  "rating": 4.4,
  "rating_count": 812,
  "opening_hours": { "mon": [["11:00","21:30"]], "tue": [["11:00","21:30"]] },
  "photo_ref": "places/ChIJ.../photos/AUc7...",
  "status": "ACTIVE",
  "status_verified_at": "2026-11-21",
  "enriched_at": "2026-11-21T19:00:00Z"
}
```
The address, ID, rating and hours above are illustrative only, not real data.

---

## 8. Data Flow

```
City CSV ──┐
           ├─► [Staging: raw rows + match_score]
OSM export ┘          │
                      ▼  rules 1–5 (section 2)
               [Place: ACTIVE only]
                      │
                      ▼  Google enrichment (price, rating, hours, photo, businessStatus)
               [Place: enriched]
                      │
                      ▼  weekly refresh: City status + OSM changes → update status
               [App queries ACTIVE places only]
```

---

## 9. Open Questions

1. **Canadian national chains:** keep them (current default, ranked lower) or block them like international chains?
2. **Bars (`venue_type = bar`):** include them in a tourist planner (621 places), or only show them in an evening slot?
3. **Price bracket edge case:** use the upper bound (current rule) or the midpoint of Google's range? Upper bound is safer for budgets; midpoint is closer to the typical spend.
4. **Places without a cuisine** (about 4.6k unmatched to OSM before Google enrichment): exclude them if Google gives no usable type, or keep them under "Other"?
5. **Boundary:** switch to the official polygon now so we get boroughs, or later?

---

## Appendix: Files
- `businesses.csv`: City of Montréal *Établissements alimentaires* (raw, 54,937 rows)
- `export.geojson`: OSM export for the City of Montréal (4,414 POIs)
- `mtl_food_core_v2.csv`: sample output of rules 1–5 (6,241 places). Google fields are empty until enrichment.
