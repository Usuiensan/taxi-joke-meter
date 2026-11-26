#!/usr/bin/env python3
"""address_db.bin (形式 v2) のリーダー（検証用）。アプリ側 Kotlin 実装と同じ検索仕様:
    1. 街区レベルの最寄り点を 1000m 以内で探す
    2. なければ全レベルの最寄り点（最大 20000m）
    3. 通り名は 40m 以内の最寄り名称つき道路セグメント
"""
import bisect, math, struct, zlib

CELL_PER_BLOCK = 8
EARTH_R = 6371000.0

class Reader:
    def __init__(self, path):
        self.data = open(path, "rb").read()
        d = self.data
        assert d[0:8] == b"TJADB002", "magic mismatch"
        (self.version, self.string_count, self.town_count,
         self.point_block_count, self.road_block_count, self.strings_off) = \
            struct.unpack_from("<IIIIII", d, 8)
        assert self.version == 2
        self.towns_off, self.point_dir_off, self.road_dir_off = \
            struct.unpack_from("<III", d, 32)
        self.point_total, self.road_seg_total = struct.unpack_from("<II", d, 44)
        (self.pref_off,) = struct.unpack_from("<I", d, 52)
        # 文字列（zlib 圧縮セクションを展開）
        (raw_size,) = struct.unpack_from("<I", d, self.strings_off)
        raw = zlib.decompress(d[self.strings_off + 4: self.pref_off])
        assert len(raw) == raw_size
        offs = struct.unpack_from(f"<{self.string_count + 1}I", raw, 0)
        base = 4 * (self.string_count + 1)
        self.strings = [raw[base + offs[i]: base + offs[i + 1]].decode("utf-8")
                        for i in range(self.string_count)]
        # 都道府県テーブル
        pref_count = (self.towns_off - self.pref_off) // 8
        self.pref_entries = [struct.unpack_from("<II", d, self.pref_off + 8 * i)
                             for i in range(pref_count)]
        self.pref_firsts = [e[1] for e in self.pref_entries]
        # 町（8バイトレコード）
        self.towns = [struct.unpack_from("<II", d, self.towns_off + 8 * i)
                      for i in range(self.town_count)]
        self.point_dir = self._read_dir(self.point_dir_off, self.point_block_count)
        self.road_dir = self._read_dir(self.road_dir_off, self.road_block_count)
        self._block_cache = {}

    def _read_dir(self, off, count):
        result = {}
        for i in range(count):
            blat, blon, coff, csize, rsize = struct.unpack_from("<iiIII", self.data, off + 20 * i)
            result[(blat, blon)] = (coff, csize, rsize)
        return result

    def _block(self, directory, key):
        cache_key = (id(directory), key)
        if cache_key in self._block_cache:
            return self._block_cache[cache_key]
        ent = directory.get(key)
        if ent is None:
            return None
        coff, csize, _ = ent
        raw = zlib.decompress(self.data[coff: coff + csize])
        self._block_cache[cache_key] = raw
        return raw

    @staticmethod
    def _varuint(buf, pos):
        result = 0
        shift = 0
        while True:
            b = buf[pos]
            pos += 1
            result |= (b & 0x7F) << shift
            if not (b & 0x80):
                return result, pos
            shift += 7

    @staticmethod
    def _zigzag(buf, pos):
        v, pos = Reader._varuint(buf, pos)
        return (v >> 1) ^ -(v & 1), pos

    def _skip_point(self, raw, pos, flags):
        if flags & 1:
            if flags & 8:
                _, pos = self._varuint(raw, pos)
            else:
                _, pos = self._varuint(raw, pos)
                _, pos = self._varuint(raw, pos)
        if flags & 4:
            _, pos = self._varuint(raw, pos)
        return pos

    def _cell_points(self, raw, cell_idx):
        pos = 0
        for idx in range(64):
            (count,) = struct.unpack_from("<H", raw, pos)
            pos += 2
            town_idx = 0
            if idx == cell_idx:
                pts = []
                for _ in range(count):
                    delta, pos = self._varuint(raw, pos)
                    town_idx += delta
                    packed, pos = self._varuint(raw, pos)
                    flags = raw[pos]; pos += 1
                    bmain = bsub = bstr = aza = 0
                    if flags & 1:
                        if flags & 8:
                            bstr, pos = self._varuint(raw, pos)
                        else:
                            bmain, pos = self._varuint(raw, pos)
                            bsub, pos = self._varuint(raw, pos)
                    if flags & 4:
                        aza, pos = self._varuint(raw, pos)
                    pts.append((packed // 1024, packed % 1024, town_idx, flags,
                                bmain, bsub, bstr, aza))
                return pts
            else:
                for _ in range(count):
                    _, pos = self._varuint(raw, pos)
                    _, pos = self._varuint(raw, pos)
                    flags = raw[pos]; pos += 1
                    pos = self._skip_point(raw, pos, flags)
        return []

    @staticmethod
    def _dist_m(lat1, lon1, lat2, lon2):
        p1, p2 = math.radians(lat1), math.radians(lat2)
        dp = math.radians(lat2 - lat1)
        dl = math.radians(lon2 - lon1)
        a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
        return 2 * EARTH_R * math.asin(math.sqrt(a))

    def _cells_in_circle(self, lat, lon, radius_m):
        dlat = math.degrees(radius_m / EARTH_R)
        dlon = math.degrees(radius_m / (EARTH_R * max(0.01, math.cos(math.radians(lat)))))
        for clat in range(math.floor((lat - dlat) * 100), math.floor((lat + dlat) * 100) + 1):
            for clon in range(math.floor((lon - dlon) * 100), math.floor((lon + dlon) * 100) + 1):
                yield clat, clon

    def nearest_point(self, lat, lon, block_only, max_radius):
        best = None
        for radius in (200, 500, 1000, 2500, 6000, 20000):
            r = min(radius, max_radius)
            seen_blocks = set()
            for clat, clon in self._cells_in_circle(lat, lon, r):
                bkey = (clat // CELL_PER_BLOCK, clon // CELL_PER_BLOCK)
                if bkey in seen_blocks:
                    continue
                seen_blocks.add(bkey)
                raw = self._block(self.point_dir, bkey)
                if raw is None:
                    continue
                for clat2, clon2 in self._cells_in_circle(lat, lon, r):
                    if (clat2 // CELL_PER_BLOCK, clon2 // CELL_PER_BLOCK) != bkey:
                        continue
                    cell_idx = (clat2 - bkey[0] * CELL_PER_BLOCK) * CELL_PER_BLOCK + \
                               (clon2 - bkey[1] * CELL_PER_BLOCK)
                    for pt in self._cell_points(raw, cell_idx):
                        if block_only and not (pt[3] & 1):
                            continue
                        plat = clat2 / 100 + pt[0] / 100_000
                        plon = clon2 / 100 + pt[1] / 100_000
                        dist = self._dist_m(lat, lon, plat, plon)
                        if best is None or dist < best[0]:
                            best = (dist, pt, plat, plon)
            if best is not None and best[0] <= r:
                return best
            if r >= max_radius:
                break
        if best is not None and best[0] <= max_radius:
            return best
        return None

    def point_to_address(self, pt):
        dlat, dlon, town_idx, flags, bmain, bsub, bstr, aza = pt
        muni_i, town_i = self.towns[town_idx]
        pref_pos = bisect.bisect_right(self.pref_firsts, town_idx) - 1
        pref_i = self.pref_entries[pref_pos][0]
        block_raw = None
        if flags & 1:
            if flags & 8:
                block_raw = self.strings[bstr]
            else:
                block_raw = str(bmain) + (f"-{bsub}" if bsub else "")
        return {
            "prefecture": self.strings[pref_i],
            "municipality": self.strings[muni_i],
            "town": self.strings[town_i],
            "aza": self.strings[aza] if flags & 4 else None,
            "block": block_raw,
            "jyukyo": bool(flags & 2) if flags & 1 else None,
            "level": "block" if flags & 1 else "town",
        }

    def nearest_road(self, lat, lon, max_m=40.0):
        best = None
        seen_blocks = set()
        for clat, clon in self._cells_in_circle(lat, lon, max_m):
            bkey = (clat // CELL_PER_BLOCK, clon // CELL_PER_BLOCK)
            if bkey in seen_blocks:
                continue
            seen_blocks.add(bkey)
            raw = self._block(self.road_dir, bkey)
            if raw is None:
                continue
            segs, refs = self._road_block_cells(raw, bkey, lat, lon, max_m)
            for seg_idx in refs:
                name_idx, pts = segs[seg_idx]
                for i in range(len(pts) - 1):
                    dist = self._seg_dist_m(lat, lon, pts[i], pts[i + 1])
                    if best is None or dist < best[0]:
                        best = (dist, self.strings[name_idx])
        if best is not None and best[0] <= max_m:
            return best
        return None

    def _road_block_cells(self, raw, bkey, lat, lon, radius_m):
        pos = 0
        cell_refs = {}
        wanted = set()
        for clat, clon in self._cells_in_circle(lat, lon, radius_m):
            if (clat // CELL_PER_BLOCK, clon // CELL_PER_BLOCK) == bkey:
                wanted.add((clat - bkey[0] * CELL_PER_BLOCK) * CELL_PER_BLOCK +
                           (clon - bkey[1] * CELL_PER_BLOCK))
        for idx in range(64):
            (count,) = struct.unpack_from("<H", raw, pos)
            pos += 2
            refs = []
            for _ in range(count):
                v, pos = self._varuint(raw, pos)
                refs.append(v)
            if idx in wanted:
                cell_refs[idx] = refs
        (seg_count,) = struct.unpack_from("<I", raw, pos)
        pos += 4
        segs = []
        origin_lat = bkey[0] * CELL_PER_BLOCK * 1000
        origin_lon = bkey[1] * CELL_PER_BLOCK * 1000
        for _ in range(seg_count):
            name_idx, pos = self._varuint(raw, pos)
            pts = []
            prev = None
            for _ in range(2):
                dlat, pos = self._zigzag(raw, pos)
                dlon, pos = self._zigzag(raw, pos)
                if prev is None:
                    cur = (origin_lat + dlat, origin_lon + dlon)
                else:
                    cur = (prev[0] + dlat, prev[1] + dlon)
                pts.append((cur[0] / 100_000, cur[1] / 100_000))
                prev = cur
            segs.append((name_idx, pts))
        all_refs = []
        for refs in cell_refs.values():
            all_refs.extend(refs)
        return segs, all_refs

    @staticmethod
    def _seg_dist_m(lat, lon, p1, p2):
        kx = EARTH_R * math.cos(math.radians(lat))
        ky = EARTH_R
        ax, ay = math.radians(p1[1]) * kx, math.radians(p1[0]) * ky
        bx, by = math.radians(p2[1]) * kx, math.radians(p2[0]) * ky
        px, py = math.radians(lon) * kx, math.radians(lat) * ky
        dx, dy = bx - ax, by - ay
        denom = dx * dx + dy * dy
        t = 0.0 if denom == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / denom))
        return math.hypot(px - (ax + t * dx), py - (ay + t * dy))

    def lookup(self, lat, lon):
        hit = self.nearest_point(lat, lon, block_only=True, max_radius=1000)
        if hit is None:
            hit = self.nearest_point(lat, lon, block_only=False, max_radius=20000)
        result = {"lat": lat, "lon": lon}
        if hit is not None:
            dist, pt, plat, plon = hit
            result["distance_m"] = round(dist, 1)
            result["address"] = self.point_to_address(pt)
        road = self.nearest_road(lat, lon)
        result["road"] = ({"name": road[1], "distance_m": round(road[0], 1)} if road else None)
        return result


if __name__ == "__main__":
    import sys, json
    r = Reader(sys.argv[1])
    for lat, lon in [(34.8526, 135.7807), (34.9855, 135.7585), (35.6812, 139.7671)]:
        print(json.dumps(r.lookup(lat, lon), ensure_ascii=False))
