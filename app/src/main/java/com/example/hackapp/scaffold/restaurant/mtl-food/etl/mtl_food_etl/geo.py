"""Geometry helpers without third-party GIS dependencies (numpy + scipy only)."""
from __future__ import annotations

import json
import math
from dataclasses import dataclass, field

import numpy as np
from scipy.spatial import cKDTree

from .text import norm_label

_LAT0 = math.radians(45.5)  # Montréal; good enough for metre-scale distances on the island


def to_xy(lat, lng) -> np.ndarray:
    """Equirectangular projection to metres, accurate to <0.5% across the island."""
    lat = np.asarray(lat, dtype=float)
    lng = np.asarray(lng, dtype=float)
    return np.c_[lng * 111_320 * math.cos(_LAT0), lat * 110_540]


def kdtree(lat, lng) -> cKDTree:
    return cKDTree(to_xy(lat, lng))


# ---------------------------------------------------------------- point in polygon
def _ring_contains(ring: np.ndarray, x: np.ndarray, y: np.ndarray) -> np.ndarray:
    """Vectorized even-odd ray casting. ring: (n, 2) array of [lng, lat]."""
    inside = np.zeros(x.shape, dtype=bool)
    xs, ys = ring[:, 0], ring[:, 1]
    xj, yj = np.roll(xs, 1), np.roll(ys, 1)
    for xi_, yi_, xj_, yj_ in zip(xs, ys, xj, yj):
        crosses = (yi_ > y) != (yj_ > y)
        if not crosses.any():
            continue
        x_int = (xj_ - xi_) * (y - yi_) / ((yj_ - yi_) or 1e-15) + xi_
        inside ^= crosses & (x < x_int)
    return inside


@dataclass
class Area:
    code: str
    polygons: list[list[np.ndarray]] = field(default_factory=list)  # [ [outer, hole, ...], ... ]

    def bbox(self):
        pts = np.vstack([p[0] for p in self.polygons])
        return pts[:, 0].min(), pts[:, 1].min(), pts[:, 0].max(), pts[:, 1].max()

    def contains(self, lat: np.ndarray, lng: np.ndarray) -> np.ndarray:
        lat = np.asarray(lat, dtype=float)
        lng = np.asarray(lng, dtype=float)
        result = np.zeros(lat.shape, dtype=bool)
        minx, miny, maxx, maxy = self.bbox()
        cand = (lng >= minx) & (lng <= maxx) & (lat >= miny) & (lat <= maxy)
        if not cand.any():
            return result
        x, y = lng[cand], lat[cand]
        hit = np.zeros(x.shape, dtype=bool)
        for rings in self.polygons:
            in_poly = _ring_contains(rings[0], x, y)
            for hole in rings[1:]:
                in_poly &= ~_ring_contains(hole, x, y)
            hit |= in_poly
        result[cand] = hit
        return result


def _polygons_of(geometry: dict) -> list[list[np.ndarray]]:
    if geometry["type"] == "Polygon":
        return [[np.asarray(r, dtype=float)[:, :2] for r in geometry["coordinates"]]]
    if geometry["type"] == "MultiPolygon":
        return [[np.asarray(r, dtype=float)[:, :2] for r in poly] for poly in geometry["coordinates"]]
    return []


def load_boroughs(path: str, boroughs: dict[str, tuple[str, str, list[str]]]) -> tuple[list[Area], list[str]]:
    """Read a GeoJSON boundary file and keep only features that are one of our 19 boroughs.

    The property holding the borough name is detected automatically: for each feature, any
    string property whose loose-normalized value equals a borough's French or English name
    identifies that borough. Independent municipalities (Westmount, Dorval, ...) never match
    and are therefore ignored. Coordinates must be WGS84 (lng, lat).
    """
    with open(path, encoding="utf-8") as f:
        gj = json.load(f)
    by_label = {}
    for code, (fr, en, _aliases) in boroughs.items():
        by_label[norm_label(fr)] = code
        by_label[norm_label(en)] = code
    areas: dict[str, Area] = {}
    for feat in gj.get("features", []):
        code = None
        for v in (feat.get("properties") or {}).values():
            if isinstance(v, str) and norm_label(v) in by_label:
                code = by_label[norm_label(v)]
                break
        if code is None:
            continue
        area = areas.setdefault(code, Area(code))
        area.polygons.extend(_polygons_of(feat["geometry"]))
    missing = [c for c in boroughs if c not in areas]
    return list(areas.values()), missing


def assign_borough(lat, lng, areas: list[Area]) -> np.ndarray:
    """Borough code per point, or None when the point is outside every borough (outside the City)."""
    lat = np.asarray(lat, dtype=float)
    out = np.full(lat.shape, None, dtype=object)
    for area in sorted(areas, key=lambda a: a.code):  # deterministic tie-break on borders
        hit = area.contains(lat, lng) & (out == None)  # noqa: E711 (numpy elementwise)
        out[hit] = area.code
    return out
