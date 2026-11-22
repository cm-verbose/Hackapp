"""Run: python -m pytest tests  (or python tests/test_etl.py)"""
import json, sys, tempfile
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import numpy as np
from mtl_food_etl import geo
from mtl_food_etl.google_enrich import GoogleEnricher, choose_match, parse_price, parse_hours, parse_description
from mtl_food_etl.text import trade_name
from mtl_food_etl.rules import BrandMatcher, CuisineNormalizer, Reference, merge_dietary

REF = Reference.load(Path(__file__).resolve().parent.parent / "config" / "reference.yaml")

def test_point_in_polygon_with_hole_and_multipolygon():
    sq = [[0, 0], [10, 0], [10, 10], [0, 10], [0, 0]]
    hole = [[4, 4], [6, 4], [6, 6], [4, 6], [4, 4]]
    a = geo.Area("a", geo._polygons_of({"type": "MultiPolygon", "coordinates": [[sq, hole], [[[20, 20], [21, 20], [21, 21], [20, 20]]]]}))
    lat = np.array([5, 1, 5, 20.2, 50]); lng = np.array([5, 1, 11, 20.8, 50])
    assert a.contains(lat, lng).tolist() == [False, True, False, True, False]

def test_load_boroughs_detects_name_property_and_skips_municipalities():
    feats = [{"type": "Feature", "properties": {"NOM": "Ville-Marie", "TYPE": "Arrondissement"},
              "geometry": {"type": "Polygon", "coordinates": [[[-73.6, 45.49], [-73.54, 45.49], [-73.54, 45.52], [-73.6, 45.52], [-73.6, 45.49]]]}},
             {"type": "Feature", "properties": {"NOM": "Westmount", "TYPE": "Ville liée"},
              "geometry": {"type": "Polygon", "coordinates": [[[-73.61, 45.47], [-73.59, 45.47], [-73.59, 45.49], [-73.61, 45.47]]]}}]
    with tempfile.NamedTemporaryFile("w", suffix=".geojson", delete=False) as f:
        json.dump({"type": "FeatureCollection", "features": feats}, f)
    areas, missing = geo.load_boroughs(f.name, REF.boroughs)
    assert [a.code for a in areas] == ["ville-marie"] and len(missing) == 18
    out = geo.assign_borough([45.505, 45.48], [-73.56, -73.60], areas)
    assert out.tolist() == ["ville-marie", None]

def test_brand_rules():
    m = BrandMatcher(REF["brands"])
    assert m.decide(None, "OSMOW'S SHAWARMA")[1] == "BLOCK"
    assert m.decide(None, "OSMOWS")[1] == "BLOCK"
    assert m.decide(None, "MIKE'S PIZZA")[1] == "BLOCK"
    assert m.decide(None, "COCO FRUTTI (JEAN-TALON)") == (None, None)       # local, not CoCo
    assert m.decide(None, "MARIUS ET FANNY") == (None, None)                 # name_match: false
    assert m.decide("La Belle Province", "LA BELLE PROVINCE")[1] == "ALLOW"
    assert m.decide("La Belle Province", "OSMOW'S SHAWARMA")[1] == "BLOCK"  # wrong OSM tag: BLOCK wins
    assert m.decide("Some Unknown Brand", "X")[1] == "BLOCK"                # unknown brand -> BLOCK

def test_cuisines():
    c = CuisineNormalizer(REF)
    assert c.from_osm("Coffee_Shop;regional;restaurant;zzz") == ["coffee", "quebecois"]
    assert c.from_google(["vietnamese_restaurant", "restaurant", "food", "point_of_interest"]) == ["vietnamese"]
    assert c.from_google(["hamburger_restaurant", "fast_food_restaurant"]) == ["burger"]
    assert c.dietary_from_google(["vegan_restaurant"], True) == ["VEGAN", "VEGETARIAN"]
    assert merge_dietary(["VEGAN"], ["VEGAN_ONLY", "VEGETARIAN"]) == ["VEGAN_ONLY", "VEGETARIAN"]

def test_google_parsing():
    est = REF["price_level_estimate"]
    assert parse_price({"priceRange": {"startPrice": {"currencyCode": "CAD", "units": "20"}, "endPrice": {"currencyCode": "CAD", "units": "30"}}}, est) == (20, 30, "GOOGLE_RANGE")
    assert parse_price({"priceRange": {"startPrice": {"units": "100"}}}, est) == (100, None, "GOOGLE_RANGE")
    assert parse_price({"priceLevel": "PRICE_LEVEL_MODERATE"}, est) == (20, 50, "GOOGLE_LEVEL_ESTIMATE")
    assert parse_price({}, est) == (None, None, "NONE")
    h = json.loads(parse_hours({"regularOpeningHours": {"periods": [{"open": {"day": 5, "hour": 18}, "close": {"day": 6, "hour": 2, "minute": 30}}]}}))
    assert h == [{"od": 5, "o": "18:00", "cd": 6, "c": "02:30"}]
    assert parse_description({"generativeSummary": {"overview": {"text": "x"}}}) == ("x", "GOOGLE_GENERATIVE")
    assert parse_description({"editorialSummary": {"text": "y"}, "generativeSummary": {"overview": {"text": "x"}}}) == ("y", "GOOGLE_EDITORIAL")

def test_choose_match_tiers_and_parentheses():
    lat, lng = 45.5, -73.6
    near = lambda m: lat + m / 110_540            # m metres north
    c = [{"id": "far", "name": "Zibo! Galeries d'Anjou", "lat": near(300), "lng": lng},
         {"id": "gold", "name": "Solid Gold", "lat": near(20), "lng": lng}]
    assert choose_match(["COMPLEXE LUXURIA (BAR SALON LE SOLID GOLD)"], lat, lng, c)["id"] == "gold"
    assert choose_match(["ZIBO GALERIES D'ANJOU"], lat, lng, c)["id"] == "far"      # 300 m, score >= 0.85
    assert choose_match(["Something Else"], lat, lng, c) is None
    assert choose_match(["X", "SOLID GOLD"], lat, lng, c)["id"] == "gold"            # alt name used

def test_legacy_cache_entries_are_researched():
    d = tempfile.mkdtemp()
    Path(d, "1.json").write_text(json.dumps({"match": None, "details": None}))
    Path(d, "2.json").write_text(json.dumps({"candidates": [], "match": None, "details": None}))
    e = GoogleEnricher(None, d)
    assert e.cached("1") is None and e.cached("2") is not None
    assert e.needs_work(e.cached("2"))

def test_numbered_company_names():
    assert trade_name("9461-2660 QUEBEC INC. (TEMPURA KYODAI)") == ("Tempura Kyodai", True)
    assert trade_name("MR. GREECH (9489-4771 QUEBEC INC.)") == ("Mr. Greech", True)
    assert trade_name("9546-1489 Quebec Inc.( Jus Booster)") == ("Jus Booster", True)
    assert trade_name("9513-5836 QUEBEC INC.") == (None, True)
    assert trade_name("9450-6060 QC INC. (RP0290)") == (None, True)       # licence code only
    assert trade_name("2982935 CANADA INC") == (None, True)
    assert trade_name("BAR 2801") == (None, False)                          # real name that contains digits
    assert trade_name("LE PETIT TABLIER") == (None, False)

if __name__ == "__main__":
    for k, f in list(globals().items()):
        if k.startswith("test_"):
            f(); print("ok", k)
