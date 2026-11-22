#!/usr/bin/env python3
"""Builds assets/citypacks/mtl/events.json from one-time exports of the Montréal event sources.

Inputs in --raw:
  hasthings.json           montreal.hasthings.com/feeds/events.json
  opendata.ndjson          one donnees.montreal.ca event record per line
  city_times.ndjson        {id, day, times, cancelled} read from each public event page
  venues_geocoded.ndjson   {slug, name, lat, lon, matched, category, type} from OpenStreetMap

--boundaries is the City's borough GeoJSON and --districts the City Pack's districts.json.
Only standard-library modules are used.
"""
import argparse
import json
import re
import unicodedata
from collections import Counter
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

ZONE_NAME = "America/Toronto"
ZONE = ZoneInfo(ZONE_NAME)
WINDOW_START = "2026-11-21"
WINDOW_END = "2026-12-06"
CITY_NAME = "Montréal"

CITY_TYPES = {
    "Musique": "music",
    "Théâtre": "theatre",
    "Danse": "theatre",
    "Exposition": "exhibition",
    "Art et artisanat": "exhibition",
    "Cinéma": "cinema",
    "Sport et plein air": "sport",
    "Heure du conte": "family",
    "Jeux": "family",
    "Festival": "festival",
    "Humour": "comedy",
}
# Administrative meetings are public events, but they are not outings.
CITY_EXCLUDED_TYPES = {"Conseil et comité"}
FAMILY_AUDIENCES = {"Enfants", "Enfants d'âge préscolaire", "Famille"}

HAS_THINGS_TYPES = {
    "music": "music",
    "dj-party": "music",
    "film": "cinema",
    "comedy": "comedy",
    "theatre": "theatre",
    "sports": "sport",
    "art": "exhibition",
    "festival": "festival",
}
REJECTED_OSM_CATEGORIES = {"shop", "highway"}
STOPWORDS = {"le", "la", "les", "l", "du", "de", "des", "d", "the", "of", "and", "et", "a", "au"}
MIN_NAME_SIMILARITY = 0.5


def fold(text):
    text = unicodedata.normalize("NFKD", text or "")
    return "".join(c for c in text if not unicodedata.combining(c)).casefold()


def tokens(text):
    return {t for t in re.findall(r"[a-z0-9]+", fold(text)) if t not in STOPWORDS}


def clean(value):
    if value is None:
        return None
    value = str(value).strip()
    return None if not value or value.casefold() == "nan" else value


def venue_is_trustworthy(venue):
    if venue.get("lat") is None or venue.get("category") in REJECTED_OSM_CATEGORIES:
        return False
    if re.match(r"\d", venue["name"]):  # The venue name is a street address.
        return True
    name, match = tokens(venue["name"]), tokens((venue.get("matched") or "").split(",")[0])
    return bool(name and match) and len(name & match) / len(name | match) >= MIN_NAME_SIMILARITY


def rings(geometry):
    if geometry["type"] == "Polygon":
        return [geometry["coordinates"]]
    if geometry["type"] == "MultiPolygon":
        return geometry["coordinates"]
    return []


def in_ring(lon, lat, ring):
    inside = False
    j = len(ring) - 1
    for i, point in enumerate(ring):
        xi, yi = point[0], point[1]
        xj, yj = ring[j][0], ring[j][1]
        if (yi > lat) != (yj > lat) and lon < (xj - xi) * (lat - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


class DistrictLocator:
    """Finds the borough containing a point. Linked cities are not Districts with Places."""

    def __init__(self, boundaries_path, districts_path):
        pack = json.loads(Path(districts_path).read_text(encoding="utf-8"))
        ids = {fold(d["name"]): d["id"] for d in pack["districts"] if d.get("groupId") != "linked"}
        ids = {re.sub(r"[^a-z0-9]", "", k): v for k, v in ids.items()}
        self.shapes = []
        boundaries = json.loads(Path(boundaries_path).read_text(encoding="utf-8"))
        for feature in boundaries["features"]:
            key = re.sub(r"[^a-z0-9]", "", fold(feature["properties"].get("NOM")))
            if key in ids:
                self.shapes.append((ids[key], rings(feature["geometry"])))
        if len(self.shapes) != len(ids):
            raise SystemExit(f"Matched {len(self.shapes)} boundaries to {len(ids)} Districts")

    def locate(self, lat, lon):
        for district_id, polygons in self.shapes:
            for polygon in polygons:
                if in_ring(lon, lat, polygon[0]) and not any(in_ring(lon, lat, hole) for hole in polygon[1:]):
                    return district_id
        return None


def without_none(value):
    if isinstance(value, dict):
        return {k: without_none(v) for k, v in value.items() if v is not None}
    return value


def city_cost(text):
    lower = (text or "").casefold()
    status = "free" if "gratuit" in lower else "paid" if "payant" in lower or "$" in lower else "unknown"
    return status


def registration(text):
    lower = (text or "").casefold()
    if "sur inscription" in lower or "billet requis" in lower:
        return True
    if "entrée libre" in lower or "sans billet" in lower or "sans inscription" in lower:
        return False
    return None


def city_events(raw, locator, stats):
    times = {}
    for line in (raw / "city_times.ndjson").read_text(encoding="utf-8").splitlines():
        row = json.loads(line)
        times[row["id"]] = row
    for line in (raw / "opendata.ndjson").read_text(encoding="utf-8").splitlines():
        record = json.loads(line)
        page = times.get(record["_id"])
        if page is None:
            continue
        kind = clean(record.get("type_evenement"))
        if kind in CITY_EXCLUDED_TYPES:
            stats["city_excluded_type"] += 1
            continue
        if not page["times"]:
            stats["city_no_time"] += 1
            continue
        # Pages can repeat one time as both -05:00 and -0500, so compare parsed times.
        moments = sorted({datetime.fromisoformat(t) for t in page["times"]})
        start = moments[0].isoformat(timespec="seconds")
        # Exactly two times are a start and an end. Other counts are sessions, so the end stays empty.
        end = moments[1].isoformat(timespec="seconds") if len(moments) == 2 else None
        # Leg estimates need coordinates, so an Event without them cannot be planned.
        if clean(record.get("lat")) is None or clean(record.get("long")) is None:
            stats["city_no_coordinates"] += 1
            continue
        lat, lon = float(record["lat"]), float(record["long"])
        district = locator.locate(lat, lon)
        if district is None:
            stats["city_outside_districts"] += 1
            continue
        audience = clean(record.get("public_cible"))
        event_type = "family" if audience in FAMILY_AUDIENCES else CITY_TYPES.get(kind, "other")
        url = clean(record.get("url_fiche"))
        stable = url.rsplit("-", 1)[-1] if url and url.rsplit("-", 1)[-1].isdigit() else record["_id"]
        cost_text = clean(record.get("cout"))
        cost_status = city_cost(cost_text)
        address = ", ".join(a for a in (clean(record.get("adresse_principale")), clean(record.get("adresse_secondaire"))) if a)
        yield without_none({
            "id": f"donneesmtl:{stable}:{start}",
            "title": clean(record.get("titre")) or "(Sans titre)",
            "description": clean(record.get("description")),
            "url": url,
            "type": event_type,
            "categories": [c for c in (kind, clean(record.get("emplacement"))) if c] or None,
            "targetAudience": {"description": audience} if audience else None,
            "location": without_none({
                "venueName": clean(record.get("titre_adresse")),
                "address": address or None,
                "city": CITY_NAME,
                "borough": clean(record.get("arrondissement")),
                "postalCode": clean(record.get("code_postal")),
                "latitude": lat,
                "longitude": lon,
            }),
            "schedule": without_none({"startDate": start, "endDate": end, "timezone": ZONE_NAME}),
            "cost": without_none({
                "status": cost_status,
                "currency": "CAD" if cost_status == "paid" else None,
                "originalText": cost_text,
                "registrationRequired": registration(clean(record.get("inscription"))),
            }),
            "districtId": district,
            "status": "cancelled" if page["cancelled"] else "scheduled",
        })


def local_iso(value):
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(ZONE)
    return parsed.isoformat(timespec="seconds")


def has_things_events(raw, locator, stats):
    venues = {}
    for line in (raw / "venues_geocoded.ndjson").read_text(encoding="utf-8").splitlines():
        venue = json.loads(line)
        if venue_is_trustworthy(venue):
            venues[venue["slug"]] = venue
    stats["venues_trusted"] = len(venues)
    feed = json.loads((raw / "hasthings.json").read_text(encoding="utf-8"))
    for item in feed["items"]:
        if not item.get("startTimeKnown"):
            stats["hasthings_no_time"] += 1
            continue
        venue = venues.get(item["venue"]["slug"])
        if venue is None:
            stats["hasthings_unplaced_venue"] += 1
            continue
        lat, lon = float(venue["lat"]), float(venue["lon"])
        district = locator.locate(lat, lon)
        if district is None:
            stats["hasthings_outside_districts"] += 1
            continue
        age = clean(item.get("ageRestriction"))
        min_age = re.match(r"(\d+)\+", age or "")
        category = item.get("category") or "other"
        labels = list(dict.fromkeys(t["label"] for t in item.get("tags", []) if t.get("kind") != "provider" and t.get("label")))
        audience = {"description": age, "minAge": int(min_age.group(1)) if min_age else None} if age else None
        yield without_none({
            "id": f"hasthings:{item['id']}",
            "title": item["title"],
            "url": item.get("ticketUrl") or item.get("url"),
            "type": HAS_THINGS_TYPES.get(category, "other"),
            "categories": [category] + labels,
            "targetAudience": without_none(audience) if audience else None,
            "location": {
                "venueName": item["venue"]["name"],
                "city": CITY_NAME,
                "latitude": lat,
                "longitude": lon,
            },
            "schedule": without_none({
                "startDate": local_iso(item["startsAt"]),
                "endDate": local_iso(item["endsAt"]) if item.get("endsAt") else None,
                "timezone": ZONE_NAME,
            }),
            "cost": {"status": "unknown"},
            "districtId": district,
            "status": "cancelled" if item.get("status") == "cancelled" else "scheduled",
        })


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw", type=Path, required=True)
    parser.add_argument("--boundaries", type=Path, required=True)
    parser.add_argument("--districts", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()

    stats = Counter()
    locator = DistrictLocator(args.boundaries, args.districts)
    merged = {}
    # City records go first, so they win when both sources list the same Event.
    for source, events in (("city", city_events(args.raw, locator, stats)), ("hasthings", has_things_events(args.raw, locator, stats))):
        for event in events:
            day = event["schedule"]["startDate"][:10]
            if not WINDOW_START <= day <= WINDOW_END:
                stats[f"{source}_outside_window"] += 1
                continue
            key = (" ".join(sorted(tokens(event["title"]))), event["schedule"]["startDate"], event["districtId"])
            if key in merged:
                stats[f"{source}_duplicate"] += 1
                continue
            merged[key] = event
            stats[f"{source}_kept"] += 1

    events = sorted(merged.values(), key=lambda e: (e["schedule"]["startDate"], e["id"]))
    if len({e["id"] for e in events}) != len(events):
        raise SystemExit("Duplicate Event ids")
    snapshot = {
        "cityId": "mtl",
        "generatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "events": events,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(snapshot, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    report = dict(sorted(stats.items()))
    report["total"] = len(events)
    report["scheduled"] = sum(e["status"] == "scheduled" for e in events)
    report["types"] = dict(Counter(e["type"] for e in events).most_common())
    report["districts"] = len({e["districtId"] for e in events})
    print(json.dumps(report, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
