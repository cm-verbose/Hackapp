"""Build places.db for the Android app.

    python -m mtl_food_etl.pipeline \
        --city data/businesses.csv \
        --osm data/export.geojson \
        --boundary data/limites-administratives.geojson \
        --google-key $GOOGLE_MAPS_API_KEY --google-limit 50 \
        --out out/

Steps (see prep/api-business/restaurant-data-design-final.md):
  1. inside the City of Montréal (borough polygons; sector-name fallback without --boundary)
  2. tourist-relevant City types
  3. City status = Ouvert
  4. match to OSM (distance + name), one row per physical place
  5. chain rule (only ALLOW brands survive)
  6. classification (venue_type, cuisines, dietary)
  7. Google enrichment (optional, cached)
  8. export SQLite + reports
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import sqlite3
from collections import Counter, OrderedDict
from pathlib import Path

import numpy as np
import pandas as pd

from . import geo
from .google_enrich import STATUS_MAP, GoogleEnricher, parse_description, parse_hours, parse_price
from .rules import BrandMatcher, CuisineNormalizer, Reference, dietary_from_osm, merge_dietary
from .text import name_similarity, norm_name, title_case, trade_name

DB_VERSION = 5  # must equal @Database(version = ...) in the app
ROOT = Path(__file__).resolve().parent.parent

VENUE_LABEL = {
    "RESTAURANT": ("Restaurant", "Restaurant"), "FAST_FOOD": ("Restauration rapide", "Quick service"),
    "CAFE": ("Café", "Café"), "BAKERY": ("Boulangerie", "Bakery"), "DESSERT": ("Desserts", "Desserts"),
    "FOOD_TRUCK": ("Camion de rue", "Food truck"), "BAR": ("Bar", "Bar"),
}


# ---------------------------------------------------------------- loading
def load_city(path: str) -> pd.DataFrame:
    df = pd.read_csv(path, dtype={"business_id": str, "date_statut": str})
    keep = ["business_id", "name", "address", "city", "type", "statut", "date_statut", "latitude", "longitude"]
    return df[keep]


def load_osm(path: str) -> pd.DataFrame:
    with open(path, encoding="utf-8") as f:
        feats = json.load(f)["features"]
    rows = []
    for ft in feats:
        p = ft.get("properties") or {}
        if not p.get("name"):
            continue
        c = ft["geometry"]["coordinates"]
        while isinstance(c[0], list):  # way/relation exported as polygon: take first vertex
            c = c[0]
        rows.append({"osm_id": p.get("@id") or p.get("id"), "osm_name": p["name"], "amenity": p.get("amenity"),
                     "cuisine": p.get("cuisine"), "brand": p.get("brand"), "lat": c[1], "lng": c[0],
                     "props": p})
    return pd.DataFrame(rows)


# ---------------------------------------------------------------- steps
def step_city_limits(df, ref, boundary_path, report):
    if boundary_path:
        areas, missing = geo.load_boroughs(boundary_path, ref.boroughs)
        if missing:
            raise SystemExit(f"Boundary file is missing boroughs: {missing}. "
                             "Check that it is the City's administrative boundary file in WGS84.")
        df = df.copy()
        df["borough"] = geo.assign_borough(df.latitude.values, df.longitude.values, areas)
        # sanity check against legacy sector names (aliases)
        alias_to = {}
        for code, (_fr, _en, aliases) in ref.boroughs.items():
            for a in aliases:
                alias_to.setdefault(a, set()).add(code)
        inside = df[df.borough.notna()]
        bad = inside[[b not in alias_to.get(c, {b}) for b, c in zip(inside.borough, inside.city)]]
        report["boundary_alias_mismatches"] = int(len(bad))
        report["boundary_mode"] = "polygon"
        return df[df.borough.notna()].copy()
    # fallback: legacy sector names; borough only when an alias maps to exactly one borough
    alias_to = {}
    for code, (_fr, _en, aliases) in ref.boroughs.items():
        for a in aliases:
            alias_to.setdefault(a, []).append(code)
    df = df[df.city.isin(alias_to)].copy()
    df["borough"] = [alias_to[c][0] if len(alias_to[c]) == 1 else None for c in df.city]
    report["boundary_mode"] = "sector-name fallback (pass --boundary for polygons and full borough coverage)"
    return df


def step_match_osm(df, osm, cfg):
    tree = geo.kdtree(osm.lat.values, osm.lng.values)
    osm_norm = [norm_name(n) for n in osm.osm_name]
    pts = geo.to_xy(df.latitude.values, df.longitude.values)
    best_j, best_s = [], []
    for name, p in zip(df.name, pts):
        n = norm_name(name)
        s_best, j_best = 0.0, -1
        for j in tree.query_ball_point(p, cfg["max_distance_m"]):
            s = name_similarity(n, osm_norm[j])
            if s > s_best:
                s_best, j_best = s, j
        ok = s_best >= cfg["min_name_score"]
        best_j.append(j_best if ok else -1)
        best_s.append(round(s_best, 3) if ok else None)
    df = df.copy()
    df["osm_idx"] = best_j
    df["match_score"] = best_s
    # one row per OSM place: keep the best-scoring City record
    matched = df[df.osm_idx >= 0].sort_values("match_score", ascending=False).drop_duplicates("osm_idx")
    return pd.concat([matched, df[df.osm_idx < 0]]).sort_values("business_id")


def step_chains(df, osm, brands: BrandMatcher, min_locations: int):
    osm_brand = [osm.brand.iat[j] if j >= 0 else None for j in df.osm_idx]
    decided = [brands.decide(b, n) for b, n in zip(osm_brand, df.name)]
    df = df.copy()
    df["brand"] = [d[0] for d in decided]
    df["brand_decision"] = [d[1] for d in decided]
    removed = df[df.brand_decision == "BLOCK"]
    kept = df[df.brand_decision != "BLOCK"].copy()
    kept["is_chain"] = kept.brand_decision == "ALLOW"
    # chain candidates for human review: same normalized name at N+ addresses, not yet decided
    nn = kept.name.map(norm_name)
    counts = nn[kept.brand_decision.isna()].value_counts()
    cand = counts[counts >= min_locations]
    review = pd.DataFrame({"normalized_name": cand.index, "locations": cand.values})
    return kept, removed, review


def step_classify(df, osm, ref, cuisines: CuisineNormalizer):
    venue_city = ref["city_type_to_venue"]
    venue_osm = ref["osm_amenity_to_venue"]
    vt, cu, di, nm = [], [], [], []
    for r in df.itertuples():
        if r.osm_idx >= 0:
            o = osm.iloc[r.osm_idx]
            vt.append(venue_osm.get(o.amenity) or venue_city[r.type])
            cu.append(cuisines.from_osm(o.cuisine))
            di.append(merge_dietary(dietary_from_osm(o.props)))
            nm.append(o.osm_name)
        else:
            vt.append(venue_city[r.type])
            cu.append([])
            di.append([])
            nm.append(None)
    df = df.copy()
    df["venue_type"], df["cuisines"], df["dietary"], df["osm_name"] = vt, cu, di, nm
    df["osm_id"] = [osm.osm_id.iat[j] if j >= 0 else None for j in df.osm_idx]
    return df


def step_google(df, ref, cuisines: CuisineNormalizer, enricher: GoogleEnricher | None, limit: int | None):
    est = ref["price_level_estimate"]
    cols = {k: [] for k in ["google_place_id", "g_name", "price_min", "price_max", "price_source", "rating",
                            "rating_count", "opening_hours", "photo_ref", "g_status", "desc_fr", "desc_en",
                            "desc_source", "enriched_at", "g_primary_type", "g_match_score", "g_rule"]}
    rules = ref.raw.get("google_venue_rules", {})
    drop_types = set(rules.get("drop", []))
    strip_types = set(rules.get("strip", []))
    new_calls = 0
    for r in df.itertuples():
        res = None
        if enricher:
            res = enricher.cached(r.business_id)
            if enricher.needs_work(res) and (res is not None or limit is None or new_calls < limit):
                # re-matching cached candidates is free; only new searches count toward --google-limit
                if res is None:
                    new_calls += 1
                res = enricher.fetch(r.business_id, _v(r.osm_name) or title_case(r.name), r.address,
                                     r.latitude, r.longitude, alt_names=(r.name,))
        d = (res or {}).get("details")
        rule = None
        if d and d.get("primaryType") in drop_types:
            rule = "DROP"      # concession inside an arena, stadium, theatre, ...: not a tourist restaurant
        elif d and d.get("primaryType") in strip_types:
            rule = "STRIP"     # matched the building (hotel, grocery), not the restaurant: ignore Google data
        if not d or rule:
            for k in cols:
                cols[k].append(None)
            cols["price_source"][-1] = "NONE"
            cols["g_primary_type"][-1] = d.get("primaryType") if d else None
            cols["g_rule"][-1] = rule
            continue
        lo, hi, src = parse_price(d, est)
        lang1, lang2 = enricher.primary_lang, enricher.second_lang
        t1, s1 = parse_description(d)
        t2, s2 = parse_description(res.get(f"desc_{lang2}")) if lang2 else (None, None)
        desc = {lang1: t1, lang2: t2} if lang2 else {lang1: t1}
        types = d.get("types", []) + ([d["primaryType"]] if d.get("primaryType") else [])
        if not r.cuisines:  # Google fallback for cuisine (team decision)
            r.cuisines.extend(cuisines.from_google(types))
        r.dietary[:] = merge_dietary(r.dietary, cuisines.dietary_from_google(types, d.get("servesVegetarianFood")))
        photos = d.get("photos") or []
        vals = dict(google_place_id=d.get("id"), g_name=(d.get("displayName") or {}).get("text"),
                    price_min=lo, price_max=hi, price_source=src, rating=d.get("rating"),
                    rating_count=d.get("userRatingCount"), opening_hours=parse_hours(d),
                    photo_ref=photos[0]["name"] if photos else None,
                    g_status=STATUS_MAP.get(d.get("businessStatus")),
                    desc_fr=desc.get("fr"), desc_en=desc.get("en"), desc_source=s1 or s2,
                    enriched_at=dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
                    g_primary_type=d.get("primaryType"), g_match_score=(res.get("match") or {}).get("name_score"),
                    g_rule=None)
        for k in cols:
            cols[k].append(vals[k])
    df = df.copy()
    for k, v in cols.items():
        df[k] = v
    return df


def step_google_venue_rules(df):
    """Drop places matched to non-food venues, then keep one row per Google place.

    Several City records (concession stands) can match the same Google place (e.g. Centre Bell).
    The record with the best name score is kept; ties prefer records also matched to OSM.
    """
    dropped = df[df.g_rule == "DROP"]
    df = df[df.g_rule != "DROP"].copy()
    has_g = df[df.google_place_id.notna()].copy()
    has_g["_osm"] = has_g.osm_idx >= 0
    has_g = has_g.sort_values(["g_match_score", "_osm"], ascending=False)
    keep = has_g.drop_duplicates("google_place_id").drop(columns="_osm")
    dupes = has_g[~has_g.index.isin(keep.index)].drop(columns="_osm")
    out = pd.concat([keep, df[df.google_place_id.isna()]]).sort_values("business_id")
    return out, dropped, dupes


def template_description(venue_type, cuisines, borough, price_max, ref, groups_by_cuisine):
    """Always-available fallback description in fr and en."""
    bfr, ben = ref.boroughs[borough][:2] if borough in ref.boroughs else (None, None)
    vfr, ven = VENUE_LABEL[venue_type]
    gfr = gen = None
    for c in cuisines:
        if c in groups_by_cuisine:
            gfr, gen = groups_by_cuisine[c]
            break
    price = None
    if price_max is not None:
        for b in ref["price_brackets"]:
            if b["max"] is not None and price_max <= b["max"]:
                price = b["label"]
                break
    fr = " · ".join(x for x in [vfr, gfr, bfr, price] if x)
    en = " · ".join(x for x in [ven, gen, ben, price] if x)
    return fr, en


# ---------------------------------------------------------------- export
def step_names(df, enricher: GoogleEnricher | None, max_dist_m: float = 25.0):
    """Give a shop name to places that are only a numbered company ("9513-5836 QUEBEC INC.").

    Order: (1) a name from OSM/Google; (2) the trade name written inside the legal name;
    (3) the nearest Google search candidate stored in the cache, within `max_dist_m` metres
    (free, no API call); (4) otherwise the place is removed: a business number is useless in the app.
    Returns (df, removed, rescued) where `rescued` is for manual review.
    """
    names, gids, drop, rescued = [], [], [], []
    for r in df.itertuples():
        display, gid, remove = None, None, False
        if not (_v(r.osm_name) or _v(r.g_name)):
            trade, numbered = trade_name(r.name)
            if trade:
                display = trade
            elif numbered:
                cand = None
                if enricher is not None:
                    p = enricher.cache / f"{r.business_id}.json"
                    if p.exists():
                        entry = json.loads(p.read_text(encoding="utf-8"))
                        near = [(geo_dist_m(r.latitude, r.longitude, c["lat"], c["lng"]), c)
                                for c in entry.get("candidates", []) if c.get("lat") is not None]
                        near = [t for t in near if t[0] <= max_dist_m]
                        cand = min(near, key=lambda t: t[0]) if near else None
                if cand:
                    display, gid = cand[1]["name"], cand[1]["id"]
                    rescued.append({"business_id": r.business_id, "legal_name": r.name, "address": r.address,
                                    "venue_type": r.venue_type, "new_name": display, "distance_m": round(cand[0])})
                else:
                    remove = True
        names.append(display)
        gids.append(gid)
        drop.append(remove)
    df = df.assign(display_name=names, rescued_gid=gids)
    df["google_place_id"] = [g if g and not _v(old) else old for g, old in zip(gids, df.google_place_id)]
    removed = df[drop]
    return df[[not d for d in drop]], removed, pd.DataFrame(rescued)


def geo_dist_m(lat1, lng1, lat2, lng2) -> float:
    return float(np.hypot((lat1 - lat2) * 111320.0, (lng1 - lng2) * 111320.0 * np.cos(np.radians(lat1))))


def _v(x):
    """pandas NaN/None -> None, everything else unchanged."""
    if x is None:
        return None
    try:
        return None if pd.isna(x) else x
    except (TypeError, ValueError):
        return x


def export_sqlite(df, ref, out_path: Path):
    if out_path.exists():
        out_path.unlink()
    con = sqlite3.connect(out_path)
    con.executescript((ROOT / "sql" / "schema.sql").read_text(encoding="utf-8"))
    today = dt.date.today().isoformat()
    groups_by_cuisine = {}
    for g in ref["cuisine_groups"]:
        for c in g.get("cuisines", []):
            groups_by_cuisine.setdefault(c, (g["fr"], g["en"]))

    place_rows, cu_rows, di_rows = [], [], []
    for pid, r in enumerate(df.itertuples(), start=1):
        status = _v(r.g_status) or "ACTIVE"
        fr, en = _v(r.desc_fr), _v(r.desc_en)
        src = _v(r.desc_source)
        if not fr and not en:
            fr, en = template_description(r.venue_type, r.cuisines, _v(r.borough), _v(r.price_max), ref,
                                          groups_by_cuisine)
            src = "GENERATED"
        place_rows.append((
            pid, r.business_id, _v(r.osm_id), _v(r.google_place_id),
            _v(r.osm_name) or _v(r.g_name) or _v(r.display_name) or title_case(r.name),
            " ".join(str(r.address).split()), float(r.latitude), float(r.longitude), _v(r.borough),
            r.venue_type, int(bool(r.is_chain)),
            None if pd.isna(r.price_min) else int(r.price_min),
            None if pd.isna(r.price_max) else int(r.price_max), r.price_source,
            fr, en, src,
            None if pd.isna(r.rating) else float(r.rating),
            None if pd.isna(r.rating_count) else int(r.rating_count),
            _v(r.opening_hours), _v(r.photo_ref), status, today, _v(r.enriched_at),
        ))
        cu_rows += [(pid, c) for c in dict.fromkeys(r.cuisines)]
        di_rows += [(pid, d) for d in dict.fromkeys(r.dietary)]
    con.executemany(f"INSERT INTO place VALUES ({','.join('?' * 24)})", place_rows)
    con.executemany("INSERT INTO place_cuisine VALUES (?,?)", cu_rows)
    con.executemany("INSERT INTO place_dietary VALUES (?,?)", di_rows)

    con.executemany("INSERT INTO area VALUES (?,?,?,?)",
                    [(code, "BOROUGH", v[0], v[1]) for code, v in ref.boroughs.items()])
    for i, g in enumerate(ref["cuisine_groups"]):
        con.execute("INSERT INTO cuisine_group VALUES (?,?,?,?,?,?)",
                    (g["code"], g["icon"], g["fr"], g["en"], g.get("venue_type"), i))
        con.executemany("INSERT INTO cuisine_group_member VALUES (?,?)",
                        [(g["code"], c) for c in g.get("cuisines", [])])
    con.executemany("INSERT INTO price_bracket VALUES (?,?,?,?)",
                    [(b["code"], b["label"], b["max"], i) for i, b in enumerate(ref["price_brackets"])])
    con.executemany("INSERT INTO venue_type_slot VALUES (?,?)",
                    [(v, s) for v, slots in ref["venue_type_slots"].items() for s in slots])
    con.execute(f"PRAGMA user_version = {DB_VERSION}")
    con.commit()
    con.execute("VACUUM")
    con.close()
    return len(place_rows)


# ---------------------------------------------------------------- main
def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--city", required=True, help="City of Montréal businesses.csv")
    ap.add_argument("--osm", required=True, help="Overpass GeoJSON export")
    ap.add_argument("--boundary", help="City administrative boundaries GeoJSON (WGS84)")
    ap.add_argument("--reference", default=str(ROOT / "config" / "reference.yaml"))
    ap.add_argument("--google-key", default=os.environ.get("GOOGLE_MAPS_API_KEY"))
    ap.add_argument("--google-limit", type=int, help="max NEW places to fetch from Google this run")
    ap.add_argument("--no-google", action="store_true", help="skip Google entirely (cache included)")
    ap.add_argument("--cache", default="cache/google")
    ap.add_argument("--out", default="out")
    a = ap.parse_args(argv)

    ref = Reference.load(a.reference)
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    report: dict = OrderedDict()
    counts: dict = OrderedDict()

    city = load_city(a.city)
    counts["0_raw_city_rows"] = len(city)
    df = step_city_limits(city, ref, a.boundary, report)
    counts["1_inside_city_of_montreal"] = len(df)
    df = df[df.type.isin(ref["city_type_to_venue"])]
    counts["2_tourist_food_types"] = len(df)
    df = df[df.statut == "Ouvert"].dropna(subset=["latitude", "longitude"])
    counts["3_status_ouvert"] = len(df)

    osm = load_osm(a.osm)
    df = step_match_osm(df, osm, ref["matching"])
    counts["4_one_row_per_place"] = len(df)
    df, removed, chain_review = step_chains(df, osm, BrandMatcher(ref["brands"]),
                                            ref["matching"]["chain_candidate_min_locations"])
    counts["5_after_chain_rule"] = len(df)

    cuisines = CuisineNormalizer(ref)
    df = step_classify(df, osm, ref, cuisines)
    enricher = None
    if not a.no_google:
        enricher = GoogleEnricher(a.google_key, a.cache)
    df = step_google(df, ref, cuisines, enricher, a.google_limit)
    df, venue_dropped, google_dupes = step_google_venue_rules(df)
    counts["6_after_google_venue_rules"] = len(df)
    df, unnamed, rescued = step_names(df, enricher)
    counts["7_after_unnamed_removed"] = len(df)
    n = export_sqlite(df, ref, out / "places.db")
    counts["8_exported_places"] = n
    venue_dropped.assign(reason="google_venue_type")[["business_id", "name", "address", "g_primary_type", "reason"]] \
        .to_csv(out / "removed_google_venues.csv", index=False)
    google_dupes[["business_id", "name", "address", "google_place_id", "g_name"]] \
        .to_csv(out / "removed_google_duplicates.csv", index=False)

    unnamed[["business_id", "name", "address"]].to_csv(out / "removed_unnamed.csv", index=False)
    rescued.to_csv(out / "review_name_rescues.csv", index=False)
    report["numbered_companies"] = {"removed_no_name": int(len(unnamed)), "named_from_google_candidate": int(len(rescued))}

    lo, hi = ref["matching"]["review_band"]
    band = df[(df.match_score >= lo) & (df.match_score < hi)]
    band[["business_id", "name", "osm_name", "match_score", "address"]].to_csv(out / "review_low_score_matches.csv", index=False)
    chain_review.to_csv(out / "review_chain_candidates.csv", index=False)
    removed[["business_id", "name", "brand", "address"]].to_csv(out / "removed_chains.csv", index=False)

    report["counts"] = counts
    report["matched_to_osm"] = int((df.osm_idx >= 0).sum())
    report["with_cuisine"] = int(sum(bool(c) for c in df.cuisines))
    report["with_dietary"] = int(sum(bool(d) for d in df.dietary))
    report["quebec_chain_locations_kept"] = int(df.is_chain.sum())
    report["chains_removed_top"] = dict(Counter(removed.brand).most_common(15))
    report["venue_types"] = dict(Counter(df.venue_type))
    report["boroughs_assigned"] = int(df.borough.notna().sum())
    report["google_enriched"] = int(df.google_place_id.notna().sum())
    report["removed_google_venues"] = dict(Counter(venue_dropped.g_primary_type))
    report["removed_google_duplicates"] = int(len(google_dupes))
    report["google_data_ignored_strip"] = dict(Counter(df[df.g_rule == "STRIP"].g_primary_type))
    report["price_sources"] = dict(Counter(df.price_source))
    if enricher:
        report["google_new_calls_this_run"] = enricher.calls
    report["review_low_score_matches"] = int(len(band))
    report["review_chain_candidates"] = int(len(chain_review))
    (out / "report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(report, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
