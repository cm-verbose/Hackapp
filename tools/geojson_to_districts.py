#!/usr/bin/env python3
"""tools/geojson_to_districts.py — build assets/citypacks/{city}/districts.json from an official GeoJSON.

  python3 geojson_to_districts.py montreal_limites.geojson mtl --id-prop CODEID --name-prop NOM --group-prop TYPE
  python3 geojson_to_districts.py toronto_neighbourhoods.geojson tor --id-prop AREA_SHORT_CODE --name-prop AREA_NAME \
          --group-map toronto_divisions.json     # {"<id>": "downtown" | "midtown" | "east" | "west" | "north"}

Sources: Montréal open data "Limites administratives de l'agglomération de Montréal";
Toronto Open Data "Neighbourhoods" (158). Stdlib only. Output viewBox width 1000.
"""
import argparse, json, math, re, unicodedata

def slug(s):
    s = unicodedata.normalize('NFD', str(s)).encode('ascii', 'ignore').decode()
    return re.sub(r'[^a-z0-9]+', '-', s.lower()).strip('-')

def rings(geom):
    if geom['type'] == 'Polygon': return [geom['coordinates'][0]]
    if geom['type'] == 'MultiPolygon': return [p[0] for p in geom['coordinates']]
    return []

def simplify(pts, eps):  # Ramer–Douglas–Peucker
    if len(pts) < 3: return pts
    (x1, y1), (x2, y2) = pts[0], pts[-1]
    dx, dy = x2 - x1, y2 - y1; n = math.hypot(dx, dy) or 1e-9
    dmax, idx = 0, 0
    for i, (x, y) in enumerate(pts[1:-1], 1):
        d = abs(dy * x - dx * y + x2 * y1 - y2 * x1) / n
        if d > dmax: dmax, idx = d, i
    if dmax <= eps: return [pts[0], pts[-1]]
    return simplify(pts[:idx + 1], eps)[:-1] + simplify(pts[idx:], eps)

def simplify_ring(pts, eps):
    if len(pts) > 1 and pts[0] == pts[-1]: pts = pts[:-1]
    if len(pts) < 4: return pts
    mid = len(pts) // 2
    return simplify(pts[:mid + 1], eps)[:-1] + simplify(pts[mid:] + [pts[0]], eps)[:-1]

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('geojson'); ap.add_argument('city')
    ap.add_argument('--id-prop'); ap.add_argument('--name-prop', required=True)
    ap.add_argument('--group-prop'); ap.add_argument('--group-map')
    ap.add_argument('--width', type=float, default=1000); ap.add_argument('--eps', type=float, default=0.8)
    a = ap.parse_args()
    feats = json.load(open(a.geojson, encoding='utf-8'))['features']
    gmap = json.load(open(a.group_map, encoding='utf-8')) if a.group_map else {}
    allpts = [c for f in feats for r in rings(f['geometry']) for c in r]
    lat0 = math.radians(sum(p[1] for p in allpts) / len(allpts)); k = math.cos(lat0)
    xs = [p[0] * k for p in allpts]; ys = [-p[1] for p in allpts]
    minx, miny = min(xs), min(ys); s = a.width / (max(xs) - minx); h = (max(ys) - miny) * s
    proj = lambda p: ((p[0] * k - minx) * s, (-p[1] - miny) * s)
    out = []
    for f in feats:
        pr = f['properties']; name = pr[a.name_prop]
        did = slug(pr[a.id_prop]) if a.id_prop else slug(name)
        path, cx, cy, area = [], 0, 0, 0
        for r in rings(f['geometry']):
            pts = simplify_ring([proj(p) for p in r], a.eps)
            path.append('M' + ' L'.join(f'{x:.1f},{y:.1f}' for x, y in pts) + ' Z')
            for (x1, y1), (x2, y2) in zip(pts, pts[1:] + pts[:1]):  # area-weighted centroid
                c = x1 * y2 - x2 * y1; area += c; cx += (x1 + x2) * c; cy += (y1 + y2) * c
        lab = [round(cx / (3 * area), 1), round(cy / (3 * area), 1)] if area else [0, 0]
        group = gmap.get(did) or (slug(pr[a.group_prop]) if a.group_prop else None)
        out.append({'id': did, 'name': name, 'groupId': group, 'label': lab, 'path': ' '.join(path)})
    proj = {'kx': k, 'minX': minx, 'minY': miny, 'scale': s}  # used by MapProjection.project() in the app
    json.dump({'cityId': a.city, 'viewBox': [0, 0, round(a.width), round(h)], 'projection': proj, 'districts': out},
              open(f'districts_{a.city}.json', 'w', encoding='utf-8'), ensure_ascii=False)
    print(f'{len(out)} districts → districts_{a.city}.json')

if __name__ == '__main__': main()
