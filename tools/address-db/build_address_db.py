#!/usr/bin/env python3
"""位置参照情報（街区レベル＋大字・町丁目レベル）と OpenStreetMap の
名称つき道路から、アプリ同梱用のオフライン住所DB `address_db.bin` を生成する。

使い方:
    python3 download_isj.py --out-dir ./isj-data
    python3 extract_osm_roads.py --out ./roads.jsonl.gz *.osm.pbf
    python3 build_address_db.py --isj-dir ./isj-data --roads ./roads.jsonl.gz \
        --out ../../app/src/main/assets/address_db.bin

データ出典:
    住所: 国土交通省「位置参照情報」（街区レベル／大字・町丁目レベル）
    道路: © OpenStreetMap contributors (ODbL)

データの取捨:
    - 街区レベルの代表点（代表フラグ=1、無ければ同住所の先頭行）を採用する。
    - 住居表示区域の点は全て保持する。
    - 地番区域の点は 16,765,367 件と膨大なため、同じ（市区町村・町・小字）
      グループ内で 15m 以内に保持済み点がある場合は代表1点に集約する
      （間引き）。町・小字は変わらず、番地は近傍の代表点のものになる場合がある。

バイナリ形式 v2（リトルエンディアン）:
    ヘッダ 128 バイト:
        0   char[8]  magic "TJADB002"
        8   u32      formatVersion = 2
        12  u32      stringCount
        16  u32      townCount
        20  u32      pointBlockCount
        24  u32      roadBlockCount
        28  u32      stringsOff
        32  u32      townsOff
        36  u32      pointBlockDirOff
        40  u32      roadBlockDirOff
        44  u32      pointTotal
        48  u32      roadSegmentTotal
        52  u32      prefTableOff
        56  ..       reserved (0)
    文字列セクション（stringsOff から townsOff の直前まで）:
        u32 rawSize の後に zlib 圧縮された本体:
            u32 offsets[stringCount + 1]（本体データ先頭からの相対）
            連結した UTF-8 文字列本体。index 0 は空文字列。
    都道府県テーブル（prefTableOff）:
        48 × { u32 prefStrIdx, u32 firstTownIdx }（47 都道府県＋末尾番兵。
        町は都道府県順にソートされており、townIdx の属する都道府県を二分探索で引く）
    町セクション:
        townCount × { u32 muniStrIdx, u32 townStrIdx }（都道府県・市区町村・町名順）
    ブロックディレクトリ（点・道路共通）:
        count × { i32 blockLatIdx, i32 blockLonIdx, u32 compOff, u32 compSize, u32 rawSize }
        (blockLatIdx, blockLonIdx) 昇順。compOff はファイル先頭からの絶対位置。
    点ブロック（zlib 圧縮）:
        8×8 セル分を row-major に繰り返し:
            u16 pointCount
            pointCount × レコード（セル内は townIdx, dLat, dLon 昇順）:
                varuint townDelta  前点との townIdx 差分（セル先頭は 0 からの差分）
                varuint packedCoord  dLatE5 * 1024 + dLonE5
                                     （セル南西隅からのオフセット、1e-5 度単位、0..1000）
                u8 flags  bit0: レベル(1=街区/0=町丁目) bit1: 住居表示 bit2: 小字あり bit3: 街区符号が文字列
                レベル=街区 のとき:
                    flags bit3=1: varuint blockStrIdx（原データの「街区符号・地番」文字列そのまま）
                    それ以外:     varuint blockMain, varuint blockSub（0=枝番なし）
                                  ※ "123" / "123-4" と完全一致する形だけを数値化し、
                                    それ以外は文字列として原表記を保持する
                小字ありのとき: varuint azaStrIdx
    道路ブロック（zlib 圧縮）:
        8×8 セル分:
            u16 refCount
            refCount × varuint segIdx（ブロック内セグメント番号。セグメントの bbox と
                                 交わるセルから参照される）
        u32 segCount
        segCount × レコード（セグメントは常に2点）:
            varuint nameStrIdx
            初点: zigzag varint ×2（ブロック南西隅からの 1e-5 度オフセット）
            終点: zigzag varint ×2（初点からの差分）

    セル = 0.01 度 × 0.01 度（cellIdx = floor(deg * 100)）。
    ブロック = 8×8 セル（blockIdx = cellIdx // 8）。
    varuint は unsigned LEB128、zigzag varint は符号付き LEB128。
"""
import argparse, csv, gzip, io, json, math, os, re, struct, sys, zipfile
import zlib
from collections import defaultdict

CELL_PER_BLOCK = 8
CELL_E5 = 1000  # 0.01 度を 1e-5 度単位で表した値

# ---------- varint ----------
def varuint(n: int) -> bytes:
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)

def zigzag(n: int) -> bytes:
    return varuint((n << 1) if n >= 0 else ((-n << 1) - 1))

# ---------- 文字列・町テーブル ----------
class Interner:
    def __init__(self):
        self.map = {"": 0}
        self.list = [""]
    def get(self, s: str) -> int:
        i = self.map.get(s)
        if i is None:
            i = len(self.list)
            self.map[s] = i
            self.list.append(s)
        return i

class Towns:
    """町テーブル。登録順ではなく (都道府県, 市区町村, 町名) 順に並べ替えて確定する。"""
    def __init__(self, strings: Interner):
        self.strings = strings
        self.map = {}
        self.keys = []  # (pref, muni, town) の文字列キー
    def get(self, pref: str, muni: str, town: str) -> int:
        key = (pref, muni, town)
        i = self.map.get(key)
        if i is None:
            i = len(self.keys)
            self.map[key] = i
            self.keys.append(key)
        return i
    def finalize(self):
        """並べ替え後の (新idxリスト, pref_table) を返す。
        戻り値: (order, pref_entries)
          order[old_idx] = new_idx
          pref_entries: [(prefStrIdx, firstNewTownIdx)] × 47 + 番兵"""
        sorted_keys = sorted(self.keys)
        order = [0] * len(self.keys)
        for new_idx, key in enumerate(sorted_keys):
            order[self.map[key]] = new_idx
        pref_entries = []
        seen = {}
        for new_idx, key in enumerate(sorted_keys):
            if key[0] not in seen:
                seen[key[0]] = new_idx
                pref_entries.append((self.strings.get(key[0]), new_idx))
        return sorted_keys, order, pref_entries

# ---------- ISJ 読み込み ----------
NUMERIC_BLOCK = re.compile(r"^([0-9]+)(?:-([0-9]+))?$")
# 地番点の間引き間隔（メートル）。同じ（市区町村・町・小字）グループ内で
# この距離以内にある地番点は代表1点に集約する。住居表示点は間引かない。
CHIBAN_THINNING_METERS = 15.0

def cell_of(lat: float, lon: float):
    return math.floor(lat * 100), math.floor(lon * 100)

def load_isj(isj_dir, strings, towns, stats):
    """戻り値: dict[(blockLat, blockLon)] -> dict[(cellLat, cellLon)] -> [point tuples]
    point tuple: (townIdx(仮), dLatE5, dLonE5, flags, blockMain, blockSub, blockStrIdx, azaStrIdx)
    townIdx は Towns.finalize() 後に振り直すため、ここでは仮 index を保持し、
    呼び出し側で town_order を使って変換する。
    flags: bit0 level(1=街区), bit1 住居表示, bit2 小字あり, bit3 街区符号は文字列
    """
    blocks = defaultdict(lambda: defaultdict(list))
    zips = sorted(f for f in os.listdir(isj_dir) if f.endswith(".zip"))
    if not zips:
        raise SystemExit(f"ISJ の ZIP が見つかりません: {isj_dir}")
    for zname in zips:
        zpath = os.path.join(isj_dir, zname)
        z = zipfile.ZipFile(zpath)
        for name in z.namelist():
            if not name.endswith(".csv"):
                continue
            with z.open(name) as fh:
                text = io.TextIOWrapper(fh, encoding="cp932", errors="replace")
                reader = csv.reader(text)
                header = next(reader)
                if "街区符号・地番" in header:
                    load_block_csv(reader, blocks, strings, towns, stats)
                elif "大字町丁目名" in header:
                    load_town_csv(reader, blocks, strings, towns, stats)
                else:
                    print(f"未知のCSV形式をスキップ: {zname}/{name}: {header}", file=sys.stderr)
    return blocks

def _add_point(blocks, lat, lon, town_idx, flags, block_main, block_sub, block_str_idx, aza_idx):
    cell_lat, cell_lon = cell_of(lat, lon)
    d_lat = int(round((lat - cell_lat / 100.0) * 100_000))
    d_lon = int(round((lon - cell_lon / 100.0) * 100_000))
    d_lat = min(max(d_lat, 0), CELL_E5)
    d_lon = min(max(d_lon, 0), CELL_E5)
    key = (cell_lat // CELL_PER_BLOCK, cell_lon // CELL_PER_BLOCK)
    blocks[key][(cell_lat, cell_lon)].append(
        (town_idx, d_lat, d_lon, flags, block_main, block_sub, block_str_idx, aza_idx))

def _encode_block_value(block_raw, strings):
    """「街区符号・地番」を (block_main, block_sub, block_str_idx, is_text) に変換する。
    "123" / "123-4" の完全な正規形だけを数値化し（再構成が原表記と一致する）、
    それ以外は原文字列をそのまま文字列テーブルで保持する。"""
    m = NUMERIC_BLOCK.match(block_raw.strip())
    if m:
        main_s, sub_s = m.group(1), m.group(2)
        if str(int(main_s)) == main_s and (sub_s is None or str(int(sub_s)) == sub_s):
            return int(main_s), int(sub_s or 0), 0, False
    return 0, 0, strings.get(block_raw), True

def load_block_csv(reader, blocks, strings, towns, stats):
    # 列: 都道府県名,市区町村名,大字・丁目名,小字・通称名,街区符号・地番,座標系番号,
    #     Ｘ座標,Ｙ座標,緯度,経度,住居表示フラグ,代表フラグ,更新前履歴フラグ,更新後履歴フラグ
    groups = {}
    for row in reader:
        if len(row) < 14:
            continue
        stats["block_rows"] += 1
        key = (row[1], row[2], row[3], row[4], row[10])
        cur = groups.get(key)
        is_rep = row[11] == "1"
        if cur is None or (is_rep and not cur[0]):
            groups[key] = (is_rep, row)
    chiban_candidates = []
    for (muni, town, aza, block_raw, jyukyo), (_is_rep, row) in groups.items():
        pref = row[0]
        try:
            lat, lon = float(row[8]), float(row[9])
        except ValueError:
            continue
        town_idx = towns.get(pref, muni, town)
        aza_idx = strings.get(aza) if aza else 0
        block_main, block_sub, block_str, is_text = _encode_block_value(block_raw, strings)
        if jyukyo == "1":
            flags = 0b0001 | 0b0010
            if is_text:
                flags |= 0b1000
            if aza:
                flags |= 0b0100
            _add_point(blocks, lat, lon, town_idx, flags, block_main, block_sub, block_str, aza_idx)
            stats["block_points"] += 1
        else:
            chiban_candidates.append((town_idx, aza_idx, lat, lon,
                                      block_main, block_sub, block_str, is_text))
    kept = thin_chiban_points(chiban_candidates, stats)
    for (town_idx, aza_idx, lat, lon, block_main, block_sub, block_str, is_text) in kept:
        flags = 0b0001
        if is_text:
            flags |= 0b1000
        if aza_idx:
            flags |= 0b0100
        _add_point(blocks, lat, lon, town_idx, flags, block_main, block_sub, block_str, aza_idx)
        stats["block_points"] += 1

def thin_chiban_points(candidates, stats):
    """地番点を、同じ（町・小字）グループ内で CHIBAN_THINNING_METERS 以内に
    保持済み点がある場合は捨てる（グリーディ）。決定的にするため入力を
    (town, aza, lat, lon) でソートしてから処理する。"""
    spacing = CHIBAN_THINNING_METERS
    ordered = sorted(candidates, key=lambda c: (c[0], c[1], c[2], c[3]))
    grid = {}
    kept = []
    for cand in ordered:
        town_idx, aza_idx, lat, lon = cand[0], cand[1], cand[2], cand[3]
        kx = 111320.0 * math.cos(math.radians(lat))
        ky = 110540.0
        x, y = lon * kx, lat * ky
        cx, cy = int(x // spacing), int(y // spacing)
        group = (town_idx, aza_idx)
        too_close = False
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for (gx, gy) in grid.get((group, cx + dx, cy + dy), ()):
                    if (gx - x) ** 2 + (gy - y) ** 2 < spacing * spacing:
                        too_close = True
                        break
                if too_close:
                    break
            if too_close:
                break
        if too_close:
            stats["chiban_thinned_out"] += 1
            continue
        grid.setdefault((group, cx, cy), []).append((x, y))
        kept.append(cand)
    return kept

def load_town_csv(reader, blocks, strings, towns, stats):
    # 列: 都道府県コード,都道府県名,市区町村コード,市区町村名,大字町丁目コード,
    #     大字町丁目名,緯度,経度,原典資料コード,大字・字・丁目区分コード
    for row in reader:
        if len(row) < 8:
            continue
        stats["town_rows"] += 1
        try:
            lat, lon = float(row[6]), float(row[7])
        except ValueError:
            continue
        town_idx = towns.get(row[1], row[3], row[5])
        _add_point(blocks, lat, lon, town_idx, 0, 0, 0, 0, 0)
        stats["town_points"] += 1

# ---------- OSM 道路読み込み ----------
def pre_intern_road_names(path, strings):
    """道路名を出現頻度の降順に文字列テーブルへ先行登録する。
    頻出名ほど小さい index になり、セグメントの nameStrIdx varint が短くなる。"""
    from collections import Counter
    counts = Counter()
    with gzip.open(path, "rt", encoding="utf-8") as fh:
        for line in fh:
            counts[json.loads(line)["name"]] += 1
    for name, _n in counts.most_common():
        strings.get(name)

def simplify_m(points, tol_m=25.0):
    """Douglas-Peucker（メートル換算）。points: [(latE5, lonE5), ...]"""
    if len(points) <= 2:
        return points
    lat0 = math.radians(points[0][0] / 100_000)
    kx = 6371000.0 * math.cos(lat0)
    ky = 6371000.0
    xy = [(math.radians(lon / 100_000) * kx, math.radians(lat / 100_000) * ky)
          for lat, lon in points]
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
            d = math.hypot(px - ax, py - ay) if denom == 0 else \
                abs((px - ax) * dy - (py - ay) * dx) / denom
            if d > best_d:
                best_d, best_i = d, i
        if best_d > tol_m:
            keep[best_i] = True
            stack.append((a, best_i)); stack.append((best_i, b))
    return [p for p, k in zip(points, keep) if k]

def load_roads(path, strings, stats):
    """戻り値: dict[(blockLat, blockLon)] -> {"segs": [seg...], "cells": dict[cell]->[segIdx]}
    seg: (nameStrIdx, [(latE5, lonE5), ...])（2点セグメント）
    """
    blocks = defaultdict(lambda: {"segs": [], "cells": defaultdict(list)})
    seen_ids = set()
    with gzip.open(path, "rt", encoding="utf-8") as fh:
        for line in fh:
            way = json.loads(line)
            wid = way["id"]
            if wid in seen_ids:
                continue
            seen_ids.add(wid)
            stats["road_ways"] += 1
            name_idx = strings.get(way["name"])
            pts = [(int(round(a * 100_000)), int(round(o * 100_000))) for a, o in way["pts"]]
            pts = simplify_m(pts)
            for i in range(len(pts) - 1):
                p1, p2 = pts[i], pts[i + 1]
                if p1 == p2:
                    continue
                stats["road_segments"] += 1
                seg = (name_idx, [p1, p2])
                lat_min = min(p1[0], p2[0]) / 100_000
                lat_max = max(p1[0], p2[0]) / 100_000
                lon_min = min(p1[1], p2[1]) / 100_000
                lon_max = max(p1[1], p2[1]) / 100_000
                clat0, clon0 = cell_of(lat_min, lon_min)
                clat1, clon1 = cell_of(lat_max, lon_max)
                touched_blocks = set()
                for clat in range(clat0, clat1 + 1):
                    for clon in range(clon0, clon1 + 1):
                        touched_blocks.add((clat // CELL_PER_BLOCK, clon // CELL_PER_BLOCK))
                for bkey in touched_blocks:
                    blk = blocks[bkey]
                    seg_idx = len(blk["segs"])
                    blk["segs"].append(seg)
                    for clat in range(max(clat0, bkey[0] * CELL_PER_BLOCK),
                                      min(clat1, bkey[0] * CELL_PER_BLOCK + CELL_PER_BLOCK - 1) + 1):
                        for clon in range(max(clon0, bkey[1] * CELL_PER_BLOCK),
                                          min(clon1, bkey[1] * CELL_PER_BLOCK + CELL_PER_BLOCK - 1) + 1):
                            blk["cells"][(clat, clon)].append(seg_idx)
    return blocks

# ---------- エンコード ----------
def build_point_payload(block_key, cells, town_order):
    b_lat, b_lon = block_key
    out = bytearray()
    for dclat in range(CELL_PER_BLOCK):
        for dclon in range(CELL_PER_BLOCK):
            raw_pts = cells.get((b_lat * CELL_PER_BLOCK + dclat,
                                 b_lon * CELL_PER_BLOCK + dclon), [])
            pts = sorted(
                ((town_order[p[0]], p[1], p[2], p[3], p[4], p[5], p[6], p[7]) for p in raw_pts),
                key=lambda p: (p[0], p[1], p[2]))
            out += struct.pack("<H", len(pts))
            prev_town = 0
            for (town_idx, d_lat, d_lon, flags, b_main, b_sub, b_str, aza_idx) in pts:
                out += varuint(town_idx - prev_town)
                prev_town = town_idx
                out += varuint(d_lat * 1024 + d_lon)
                out.append(flags)
                if flags & 0b0001:
                    if flags & 0b1000:
                        out += varuint(b_str)
                    else:
                        out += varuint(b_main)
                        out += varuint(b_sub)
                if flags & 0b0100:
                    out += varuint(aza_idx)
    return bytes(out)

def build_road_payload(block_key, blk):
    b_lat, b_lon = block_key
    origin_lat_e5 = b_lat * CELL_PER_BLOCK * CELL_E5
    origin_lon_e5 = b_lon * CELL_PER_BLOCK * CELL_E5
    out = bytearray()
    for dclat in range(CELL_PER_BLOCK):
        for dclon in range(CELL_PER_BLOCK):
            refs = blk["cells"].get((b_lat * CELL_PER_BLOCK + dclat,
                                     b_lon * CELL_PER_BLOCK + dclon), [])
            out += struct.pack("<H", len(refs))
            for r in refs:
                out += varuint(r)
    out += struct.pack("<I", len(blk["segs"]))
    for name_idx, pts in blk["segs"]:
        out += varuint(name_idx)
        prev = None
        for (lat_e5, lon_e5) in pts:
            if prev is None:
                out += zigzag(lat_e5 - origin_lat_e5)
                out += zigzag(lon_e5 - origin_lon_e5)
            else:
                out += zigzag(lat_e5 - prev[0])
                out += zigzag(lon_e5 - prev[1])
            prev = (lat_e5, lon_e5)
    return bytes(out)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--isj-dir", required=True)
    ap.add_argument("--roads", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    strings = Interner()
    towns = Towns(strings)
    stats = {"block_rows": 0, "block_points": 0, "town_rows": 0, "town_points": 0,
             "road_ways": 0, "road_segments": 0, "chiban_thinned_out": 0}
    print("ISJ 読み込み中...", file=sys.stderr)
    point_blocks = load_isj(args.isj_dir, strings, towns, stats)
    print(f"点: 街区 {stats['block_points']} / 町丁目 {stats['town_points']}", file=sys.stderr)
    print("OSM 道路読み込み中...", file=sys.stderr)
    pre_intern_road_names(args.roads, strings)
    road_blocks = load_roads(args.roads, strings, stats)
    print(f"道路: way {stats['road_ways']} / セグメント {stats['road_segments']}", file=sys.stderr)

    # 町テーブルの確定（都道府県順ソート＋都道府県テーブル）
    sorted_town_keys, town_order, pref_entries = towns.finalize()
    towns_section = bytearray()
    for (_pref, muni, town) in sorted_town_keys:
        towns_section += struct.pack("<II", strings.get(muni), strings.get(town))
    pref_section = bytearray()
    for (pref_str_idx, first_idx) in pref_entries:
        pref_section += struct.pack("<II", pref_str_idx, first_idx)
    pref_section += struct.pack("<II", 0, len(sorted_town_keys))  # 番兵

    # 文字列セクション（zlib 圧縮）
    str_offsets = []
    str_data = bytearray()
    for s in strings.list:
        str_offsets.append(len(str_data))
        str_data += s.encode("utf-8")
    str_offsets.append(len(str_data))
    strings_raw = struct.pack(f"<{len(str_offsets)}I", *str_offsets) + bytes(str_data)
    strings_comp = zlib.compress(strings_raw, 9)
    strings_section = struct.pack("<I", len(strings_raw)) + strings_comp

    # 点ブロック
    point_dir = []
    for key in sorted(point_blocks.keys()):
        payload = build_point_payload(key, point_blocks[key], town_order)
        point_dir.append((key, zlib.compress(payload, 9), len(payload)))
    # 道路ブロック
    road_dir = []
    for key in sorted(road_blocks.keys()):
        payload = build_road_payload(key, road_blocks[key])
        road_dir.append((key, zlib.compress(payload, 9), len(payload)))

    header_size = 128
    strings_off = header_size
    pref_off = strings_off + len(strings_section)
    towns_off = pref_off + len(pref_section)
    point_dir_off = towns_off + len(towns_section)
    point_data_off = point_dir_off + 20 * len(point_dir)
    road_dir_off = point_data_off + sum(len(c) for _, c, _ in point_dir)
    road_data_off = road_dir_off + 20 * len(road_dir)

    out = bytearray()
    header = bytearray(128)
    header[0:8] = b"TJADB002"
    struct.pack_into("<IIIIII", header, 8, 2, len(strings.list), len(sorted_town_keys),
                     len(point_dir), len(road_dir), strings_off)
    struct.pack_into("<III", header, 32, towns_off, point_dir_off, road_dir_off)
    struct.pack_into("<II", header, 44, stats["block_points"] + stats["town_points"],
                     stats["road_segments"])
    struct.pack_into("<I", header, 52, pref_off)
    out += header
    out += strings_section
    out += pref_section
    out += towns_section
    pos = point_data_off
    dir_bytes = bytearray()
    for (key, comp, raw_len) in point_dir:
        dir_bytes += struct.pack("<iiIII", key[0], key[1], pos, len(comp), raw_len)
        pos += len(comp)
    out += dir_bytes
    for _, comp, _ in point_dir:
        out += comp
    pos = road_data_off
    dir_bytes = bytearray()
    for (key, comp, raw_len) in road_dir:
        dir_bytes += struct.pack("<iiIII", key[0], key[1], pos, len(comp), raw_len)
        pos += len(comp)
    out += dir_bytes
    for _, comp, _ in road_dir:
        out += comp

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "wb") as f:
        f.write(out)
    stats["strings"] = len(strings.list)
    stats["towns"] = len(sorted_town_keys)
    stats["point_blocks"] = len(point_dir)
    stats["road_blocks"] = len(road_dir)
    stats["file_bytes"] = len(out)
    stats["strings_bytes"] = len(strings_section)
    stats["towns_bytes"] = len(towns_section) + len(pref_section)
    stats["points_bytes"] = sum(len(c) for _, c, _ in point_dir)
    stats["roads_bytes"] = sum(len(c) for _, c, _ in road_dir)
    print(json.dumps(stats, ensure_ascii=False, indent=1))

if __name__ == "__main__":
    main()
