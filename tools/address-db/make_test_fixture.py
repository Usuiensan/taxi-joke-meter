#!/usr/bin/env python3
"""単体テスト用の小さな合成 address_db.bin を生成する。

    python3 make_test_fixture.py

合成データ（テスト県テスト市）:
    - 住居表示の街区点: 中央一丁目 8番 (35.0005, 135.0005)
    - 地番の街区点:     大字高田 小字・前田 123-4 (34.9900, 134.9900)
    - 町丁目点:         中央一丁目 (35.0000, 135.0000)、山田 (35.0100, 135.0100)
    - 道路:             テスト通り（街区点の約 5.6m 南を通る線分）
出力先: app/src/test/resources/address_db_fixture.bin
"""
import gzip, io, json, os, subprocess, sys, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
WORK = os.path.join(HERE, ".fixture-work")

BLOCK_HEADER = ["都道府県名", "市区町村名", "大字・丁目名", "小字・通称名", "街区符号・地番",
                "座標系番号", "Ｘ座標", "Ｙ座標", "緯度", "経度", "住居表示フラグ",
                "代表フラグ", "更新前履歴フラグ", "更新後履歴フラグ"]
BLOCK_ROWS = [
    ["テスト県", "テスト市", "中央一丁目", "", "8", "6", "0", "0", "35.000500", "135.000500", "1", "1", "0", "0"],
    ["テスト県", "テスト市", "大字高田", "前田", "123-4", "6", "0", "0", "34.990000", "134.990000", "0", "1", "0", "0"],
]
TOWN_HEADER = ["都道府県コード", "都道府県名", "市区町村コード", "市区町村名", "大字町丁目コード",
               "大字町丁目名", "緯度", "経度", "原典資料コード", "大字・字・丁目区分コード"]
TOWN_ROWS = [
    ["99", "テスト県", "99999", "テスト市", "999990001001", "中央一丁目", "35.000000", "135.000000", "0", "3"],
    ["99", "テスト県", "99999", "テスト市", "999990002001", "山田", "35.010000", "135.010000", "0", "1"],
]
ROADS = [{"id": 1, "name": "テスト通り", "pts": [[35.000450, 135.000300], [35.000450, 135.000700]]}]


def csv_bytes(header, rows):
    buf = io.StringIO()
    buf.write(",".join(header) + "\n")
    for row in rows:
        buf.write(",".join(row) + "\n")
    return buf.getvalue().encode("shift_jis")


def main():
    isj_dir = os.path.join(WORK, "isj")
    os.makedirs(isj_dir, exist_ok=True)
    with zipfile.ZipFile(os.path.join(isj_dir, "99000-24.0a.zip"), "w") as z:
        z.writestr("99_2025.csv", csv_bytes(BLOCK_HEADER, BLOCK_ROWS))
    with zipfile.ZipFile(os.path.join(isj_dir, "99000-19.0b.zip"), "w") as z:
        z.writestr("99_2025.csv", csv_bytes(TOWN_HEADER, TOWN_ROWS))
    roads_path = os.path.join(WORK, "roads.jsonl.gz")
    with gzip.open(roads_path, "wt", encoding="utf-8") as fh:
        for way in ROADS:
            fh.write(json.dumps(way, ensure_ascii=False) + "\n")
    out = os.path.join(REPO_ROOT, "app", "src", "test", "resources", "address_db_fixture.bin")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    subprocess.run([sys.executable, os.path.join(HERE, "build_address_db.py"),
                    "--isj-dir", isj_dir, "--roads", roads_path, "--out", out],
                   check=True)
    print(f"fixture written: {out} ({os.path.getsize(out)} bytes)")


if __name__ == "__main__":
    main()
