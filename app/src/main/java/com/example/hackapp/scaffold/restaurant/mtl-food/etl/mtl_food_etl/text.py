"""Name normalization and similarity used for matching and chain detection."""
from __future__ import annotations

import difflib
import re
import unicodedata

_STOPWORDS = (
    "restaurant", "resto", "cafe", "bar", "inc", "ltee", "enr", "le", "la", "les",
    "l", "du", "de", "des", "the", "et", "and",
)
_STOP_RE = re.compile(r"\b(" + "|".join(_STOPWORDS) + r")\b")


def strip_accents(s: str) -> str:
    return unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode()


def norm_name(s: object) -> str:
    """Lowercase, no accents, no punctuation, no generic words ("restaurant", "le", ...)."""
    if s is None or (isinstance(s, float) and s != s):  # None or NaN
        return ""
    s = strip_accents(str(s)).lower()
    s = re.sub(r"[^a-z0-9]+", " ", s)
    s = _STOP_RE.sub(" ", s)
    return re.sub(r"\s+", " ", s).strip()


def norm_label(s: str) -> str:
    """Looser normalization for matching official borough names (dashes, accents, apostrophes)."""
    s = strip_accents(s).lower()
    return re.sub(r"[^a-z0-9]+", "", s)


def name_similarity(a: str, b: str) -> float:
    """Similarity of two *normalized* names in [0, 1]. Containment counts as 0.9."""
    if not a or not b:
        return 0.0
    score = difflib.SequenceMatcher(None, a, b).ratio()
    if a in b or b in a:
        score = max(score, 0.9)
    return score


# Québec "numbered companies" ("9513-5836 QUEBEC INC.", "2982935 CANADA INC") are legal names, not shop names.
_NUMBERED_CO = re.compile(
    r"\b(?:\d{4}[\s-]?\d{4}|\d{6,8})\s*(?:qu[ée]bec|qc|canada)\b\.?\s*(?:inc\.?|ltée|ltee)?",
    re.IGNORECASE,
)


def trade_name(legal_name: str) -> tuple[str | None, bool]:
    """Returns (shop name, is_numbered_company).

    '9461-2660 QUEBEC INC. (TEMPURA KYODAI)' -> ('Tempura Kyodai', True)
    '9513-5836 QUEBEC INC.'                  -> (None, True)     no usable name
    'LE PETIT TABLIER'                       -> (None, False)    not a numbered company
    """
    s = str(legal_name)
    if not _NUMBERED_CO.search(s):
        return None, False
    rest = re.sub(r"[()\[\]]+|\s+-\s+|^[\s\-.,]+|[\s\-.,]+$", " ", _NUMBERED_CO.sub(" ", s))
    rest = re.sub(r"\s+", " ", rest).strip(" -.,")
    if sum(ch.isalpha() for ch in rest) < 3:  # e.g. only a food-truck licence code like "RP0290"
        return None, True
    return title_case(rest), True


def title_case(s: str) -> str:
    """Readable display name from an uppercase legal name: 'RESTAURANT CHEZ X' -> 'Restaurant Chez X'."""
    s = re.sub(r"\s+", " ", str(s)).strip()
    return " ".join(w if any(c.isdigit() for c in w) else w.capitalize() for w in s.split(" "))
