"""Google Places API (New) enrichment with an on-disk response cache.

Two calls per place (three with a second description language):
  1. Text Search (Pro fields only) to find the Google place for our name + address,
     verified by distance and name similarity.
  2. Place Details for price, rating, hours, photo, status, types and description.
     This call uses Enterprise + Atmosphere fields, the most expensive tier.
  3. (optional) Place Details again with only the description fields in a second language.

Every response is written to cache_dir/<city_id>.json, so a re-run never pays twice.
Run with --limit first to check matching quality and cost on a sample.
"""
from __future__ import annotations

import json
import math
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from .text import name_similarity, norm_name

BASE = "https://places.googleapis.com/v1"
SEARCH_FIELDS = "places.id,places.displayName,places.location"
DETAIL_FIELDS = ",".join([
    "id", "displayName", "types", "primaryType", "businessStatus",
    "priceLevel", "priceRange", "rating", "userRatingCount",
    "regularOpeningHours", "photos", "editorialSummary", "generativeSummary",
    "servesVegetarianFood",
])
DESC_FIELDS = "editorialSummary,generativeSummary"



def _request(method: str, url: str, key: str, field_mask: str, body: dict | None = None) -> dict:
    headers = {"X-Goog-Api-Key": key, "X-Goog-FieldMask": field_mask}
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.loads(resp.read().decode())
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 503) and attempt < 3:
                time.sleep(2 ** attempt)
                continue
            raise RuntimeError(f"{e.code} {e.reason}: {e.read().decode()[:300]}") from e
    raise RuntimeError("unreachable")


def _distance_m(lat1, lng1, lat2, lng2) -> float:
    r = 6_371_000
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


class GoogleEnricher:
    def __init__(self, api_key: str | None, cache_dir: str | Path,
                 primary_lang: str = "fr", second_lang: str | None = "en"):
        self.key = api_key
        self.cache = Path(cache_dir)
        self.cache.mkdir(parents=True, exist_ok=True)
        self.primary_lang = primary_lang
        self.second_lang = second_lang
        self.calls = {"search": 0, "details": 0, "details_desc": 0}

    def cached(self, city_id: str) -> dict | None:
        """Cached entry, or None if the place must be (re)searched.

        Entries from the first version of this script stored no search candidates when nothing
        matched; those are treated as missing so they get searched again (one Pro call each).
        """
        p = self.cache / f"{city_id}.json"
        if not p.exists():
            return None
        entry = json.loads(p.read_text(encoding="utf-8"))
        if entry.get("match") is None and "candidates" not in entry:
            return None
        return entry

    def needs_work(self, entry: dict | None) -> bool:
        return entry is None or entry.get("details") is None

    def _save(self, city_id: str, entry: dict) -> None:
        (self.cache / f"{city_id}.json").write_text(json.dumps(entry, ensure_ascii=False), encoding="utf-8")

    def _details(self, entry: dict) -> None:
        pid = entry["match"]["id"]
        q = urllib.parse.urlencode({"languageCode": self.primary_lang, "regionCode": "CA"})
        entry["details"] = _request("GET", f"{BASE}/places/{pid}?{q}", self.key, DETAIL_FIELDS)
        self.calls["details"] += 1
        if self.second_lang:
            q2 = urllib.parse.urlencode({"languageCode": self.second_lang, "regionCode": "CA"})
            entry[f"desc_{self.second_lang}"] = _request(
                "GET", f"{BASE}/places/{pid}?{q2}", self.key, DESC_FIELDS)
            self.calls["details_desc"] += 1

    def fetch(self, city_id: str, name: str, address: str, lat: float, lng: float,
              alt_names: tuple[str, ...] = ()) -> dict:
        """Returns {"candidates": [...], "match": {...} | None, "details": {...} | None, "desc_<lang>": {...}}.

        Cached search candidates are re-matched with the current rules for free, so the matching
        thresholds can be tuned without paying for the search again.
        """
        entry = self.cached(city_id)
        if entry is not None and entry.get("details") is not None:
            return entry
        if entry is None:
            if not self.key:
                return {"match": None, "details": None, "skipped": "no_api_key"}
            body = {
                "textQuery": f"{name}, {' '.join(str(address).split())}, Montréal",
                "maxResultCount": 5,
                "locationBias": {"circle": {"center": {"latitude": lat, "longitude": lng}, "radius": 300.0}},
                "languageCode": self.primary_lang,
            }
            res = _request("POST", f"{BASE}/places:searchText", self.key, SEARCH_FIELDS, body)
            self.calls["search"] += 1
            entry = {"candidates": [
                {"id": p["id"], "name": (p.get("displayName") or {}).get("text", ""),
                 "lat": (p.get("location") or {}).get("latitude"), "lng": (p.get("location") or {}).get("longitude")}
                for p in res.get("places", [])
            ], "match": None, "details": None}
        entry["match"] = choose_match([name, *alt_names], lat, lng, entry.get("candidates", []))
        if entry["match"] and self.key:
            self._details(entry)
        if self.key:
            self._save(city_id, entry)
        return entry


def _name_variants(names: list[str]) -> list[str]:
    """'COMPLEXE LUXURIA (BAR SALON LE SOLID GOLD)' -> full, outside and inside the parentheses."""
    out = []
    for name in names:
        if not isinstance(name, str):
            continue
        out.append(name)
        m = re.match(r"^(.*?)\((.*?)\)(.*)$", name)
        if m:
            out += [m.group(1) + m.group(3), m.group(2)]
    return list(dict.fromkeys(n for n in (norm_name(v) for v in out) if n))


# (max distance in metres, min name score): the first tier a candidate satisfies wins
MATCH_TIERS = [(150, 0.60), (400, 0.85), (40, 0.45)]


def choose_match(names: list[str], lat: float, lng: float, candidates: list[dict]) -> dict | None:
    variants = _name_variants(names)
    best = None
    for c in candidates:
        if c.get("lat") is None:
            continue
        d = _distance_m(lat, lng, c["lat"], c["lng"])
        g = norm_name(c.get("name", ""))
        s = max((name_similarity(v, g) for v in variants), default=0.0)
        if any(d <= md and s >= ms for md, ms in MATCH_TIERS):
            key = (s, -d)
            if best is None or key > best[0]:
                best = (key, {"id": c["id"], "distance_m": round(d, 1), "name_score": round(s, 2)})
    return best[1] if best else None


# ---------------------------------------------------------------- response parsing
def parse_price(details: dict, level_estimate: dict) -> tuple[int | None, int | None, str]:
    pr = details.get("priceRange")
    if pr and pr.get("startPrice"):
        lo = int(pr["startPrice"].get("units", 0))
        hi = pr.get("endPrice")
        return lo, (int(hi.get("units", 0)) if hi else None), "GOOGLE_RANGE"
    lvl = details.get("priceLevel")
    if lvl in level_estimate:
        lo, hi = level_estimate[lvl]
        return lo, hi, "GOOGLE_LEVEL_ESTIMATE"
    return None, None, "NONE"


def parse_hours(details: dict) -> str | None:
    """Compact JSON: [{"od":1,"o":"11:00","cd":1,"c":"21:30"}, ...]; day 0 = Sunday (Google).
    A period with no close ("cd"/"c" absent) means open 24/7."""
    periods = (details.get("regularOpeningHours") or {}).get("periods")
    if not periods:
        return None
    out = []
    for p in periods:
        o, c = p.get("open", {}), p.get("close")
        item = {"od": o.get("day", 0), "o": f"{o.get('hour', 0):02d}:{o.get('minute', 0):02d}"}
        if c:
            item["cd"] = c.get("day", 0)
            item["c"] = f"{c.get('hour', 0):02d}:{c.get('minute', 0):02d}"
        out.append(item)
    return json.dumps(out, separators=(",", ":"))


def parse_description(payload: dict | None) -> tuple[str | None, str | None]:
    """(text, source) from editorialSummary, else generativeSummary.overview."""
    if not payload:
        return None, None
    ed = (payload.get("editorialSummary") or {}).get("text")
    if ed:
        return ed, "GOOGLE_EDITORIAL"
    gen = ((payload.get("generativeSummary") or {}).get("overview") or {}).get("text")
    if gen:
        return gen, "GOOGLE_GENERATIVE"
    return None, None


STATUS_MAP = {
    "OPERATIONAL": "ACTIVE",
    "CLOSED_TEMPORARILY": "TEMPORARILY_CLOSED",
    "CLOSED_PERMANENTLY": "CLOSED",
}
