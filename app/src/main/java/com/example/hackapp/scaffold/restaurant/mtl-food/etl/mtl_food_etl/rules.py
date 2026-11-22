"""Business rules driven by config/reference.yaml."""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import yaml

from .text import norm_name


@dataclass
class Reference:
    raw: dict

    @classmethod
    def load(cls, path: str | Path) -> "Reference":
        with open(path, encoding="utf-8") as f:
            return cls(yaml.safe_load(f))

    def __getitem__(self, key):
        return self.raw[key]

    @property
    def boroughs(self) -> dict[str, tuple[str, str, list[str]]]:
        return {k: (v[0], v[1], v[2]) for k, v in self.raw["boroughs"].items()}


# ---------------------------------------------------------------- chains
class BrandMatcher:
    """Decides ALLOW / BLOCK for a place from its OSM brand or its name."""

    def __init__(self, brands: list[dict]):
        self.by_brand: dict[str, str] = {}      # exact OSM brand -> decision
        self.patterns: list[tuple[str, str, str]] = []  # (normalized pattern, canonical, decision)
        for b in brands:
            decision = b["decision"].upper()
            self.by_brand[b["name"]] = decision
            for n in [b["name"], *b.get("aliases", [])]:
                self.by_brand[n] = decision
                p = norm_name(n)
                if p and b.get("name_match", True):
                    self.patterns.append((p, b["name"], decision))
        self.patterns.sort(key=lambda t: -len(t[0]))  # longest pattern first

    def match_name(self, name: str) -> tuple[str, str] | None:
        """(canonical brand, decision) if the place name looks like a listed brand."""
        t = norm_name(name)
        if not t:
            return None
        # compact prefixes of t that end on a word boundary: "mike s pizza" -> mike, mikes, mikespizza
        words = t.split(" ")
        prefixes = {"".join(words[:k]) for k in range(1, len(words) + 1)}
        compact = t.replace(" ", "")
        for p, canonical, decision in self.patterns:
            pc = p.replace(" ", "")
            if len(pc) <= 4:
                ok = compact == pc               # short brands (KFC, Cora, CoCo): exact only
            else:
                ok = pc in prefixes              # "Osmows", "OSMOW'S SHAWARMA", "Mike's"
            if ok:
                return canonical, decision
        return None

    def decide(self, osm_brand: str | None, name: str) -> tuple[str | None, str | None]:
        """Returns (brand, decision). decision is None for non-chain places.

        Unknown OSM brands are BLOCK: only explicitly allowed Québec chains survive.
        BLOCK wins when the OSM brand and the name disagree (OSM brand tags can be wrong).
        """
        hit = self.match_name(name)
        if isinstance(osm_brand, str) and osm_brand:
            decision = self.by_brand.get(osm_brand, "BLOCK")
            if hit and hit[1] == "BLOCK":
                return hit
            return osm_brand, decision
        if hit:
            return hit
        return None, None


# ---------------------------------------------------------------- cuisines & dietary
class CuisineNormalizer:
    def __init__(self, ref: Reference):
        self.syn = ref["cuisine_synonyms"]
        self.drop = set(ref["cuisine_drop"])
        self.vocab = set(ref["cuisine_vocabulary"])
        self.g_suffixes = ref["google_type_suffixes"]
        self.g_syn = ref["google_type_synonyms"]
        self.g_diet = ref["google_type_to_dietary"]

    def _canon(self, code: str) -> str | None:
        code = code.strip().lower().replace(" ", "_").replace("-", "_")
        code = self.syn.get(code, code)
        if code in self.drop or code not in self.vocab:
            return None
        return code

    def from_osm(self, raw: str | None) -> list[str]:
        if not isinstance(raw, str):
            return []
        out = []
        for part in raw.split(";"):
            c = self._canon(part)
            if c and c not in out:
                out.append(c)
        return out

    def from_google(self, types: list[str]) -> list[str]:
        out = []
        for t in types or []:
            if t in self.g_syn:
                c = self._canon(self.g_syn[t])
            else:
                base = t
                for suf in self.g_suffixes:
                    if base.endswith(suf):
                        base = base[: -len(suf)]
                        break
                c = self._canon(base)
            if c and c not in out:
                out.append(c)
        return out

    def dietary_from_google(self, types: list[str], serves_vegetarian: bool | None) -> list[str]:
        out = [self.g_diet[t] for t in types or [] if t in self.g_diet]
        if serves_vegetarian:
            out.append("VEGETARIAN")
        return out


def dietary_from_osm(props: dict) -> list[str]:
    out = []
    for key, base in (("diet:vegetarian", "VEGETARIAN"), ("diet:vegan", "VEGAN")):
        v = props.get(key)
        if v == "only":
            out.append(base + "_ONLY")
        elif v == "yes":
            out.append(base)
    return out


def merge_dietary(*lists: list[str]) -> list[str]:
    """Union; an *_ONLY value makes the plain value redundant."""
    s = {d for lst in lists for d in lst}
    for base in ("VEGETARIAN", "VEGAN"):
        if base + "_ONLY" in s:
            s.discard(base)
    if "VEGAN_ONLY" in s:
        s.discard("VEGETARIAN_ONLY")  # vegan-only implies vegetarian-only
    return sorted(s)
