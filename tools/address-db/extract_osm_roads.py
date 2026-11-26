#!/usr/bin/env python3
"""Geofabrik の Japan 地域別 PBF から、名称つき道路（highway かつ name）の
ポリラインを抽出して JSONL.gz に書き出す。

使い方:
    python3 extract_osm_roads.py --out roads.jsonl.gz file1.osm.pbf [file2.osm.pbf ...]

出典: © OpenStreetMap contributors (ODbL)
"""
import argparse, gzip, json, math, sys
import osmium

EARTH_R = 6371000.0

def simplify(points, tol_m=15.0):
    """Douglas-Peucker（メートル換算の近似平面で判定）。points: [(lat, lon), ...]"""
    if len(points) <= 2:
        return points
    lat0 = math.radians(points[0][0])
    kx = EARTH_R * math.cos(lat0)
    ky = EARTH_R
    xy = [(math.radians(lon) * kx, math.radians(lat) * ky) for lat, lon in points]
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        if b <= a + 1:
            continue
        ax, ay = xy[a]; bx, by = xy[b]
        dx, dy = bx - ax, by - ay
        denom = math.hypot(dx, dy)
        best_i, best_d = -1, -1.0
        for i in range(a + 1, b):
            px, py = xy[i]
            if denom == 0:
                d = math.hypot(px - ax, py - ay)
            else:
                d = abs((px - ax) * dy - (py - ay) * dx) / denom
            if d > best_d:
                best_d, best_i = d, i
        if best_d > tol_m:
            keep[best_i] = True
            stack.append((a, best_i)); stack.append((best_i, b))
    return [p for p, k in zip(points, keep) if k]

class RoadHandler(osmium.SimpleHandler):
    def __init__(self, out):
        super().__init__()
        self.out = out
        self.count = 0
    def way(self, w):
        tags = w.tags
        if "highway" not in tags or "name" not in tags:
            return
        name = tags["name"].strip()
        if not name:
            return
        pts = [(n.lat, n.lon) for n in w.nodes if n.location.valid()]
        if len(pts) < 2:
            return
        pts = simplify(pts)
        if len(pts) < 2:
            return
        self.out.write(json.dumps({"id": w.id, "name": name,
                                   "pts": [[round(a, 6), round(o, 6)] for a, o in pts]},
                                  ensure_ascii=False) + "\n")
        self.count += 1

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("files", nargs="+")
    args = ap.parse_args()
    with gzip.open(args.out, "wt", encoding="utf-8") as out:
        h = RoadHandler(out)
        for f in args.files:
            h.apply_file(f, locations=True)
            print(f"{f}: cumulative ways={h.count}", file=sys.stderr)
    print(f"total named road ways: {h.count}", file=sys.stderr)

if __name__ == "__main__":
    main()
