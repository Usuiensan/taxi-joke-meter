# オフライン住所DB（address_db.bin）

アプリに同梱するオフライン住所引きDBの生成ツール群です。
DB は `app/src/main/assets/address_db.bin` としてアプリに埋め込まれ、
`AddressDatabase`（Kotlin）が読み取ります。通信は一切不要です。

## データ出典・ライセンス

| データ | 出典 | 備考 |
|---|---|---|
| 街区レベル位置参照情報 | 国土交通省 国土政策局 国土情報課「位置参照情報」 | 都市計画区域相当範囲の街区代表点（市区町村名・大字／丁目名・小字／通称名・街区符号／地番・住居表示フラグ・緯度経度） |
| 大字・町丁目レベル位置参照情報 | 同上 | 全国の町丁目代表点。街区レベルの無い地域の補完 |
| 名称つき道路 | © OpenStreetMap contributors | ODbL（Open Database License）。Geofabrik の Japan 抽出を使用 |

- 位置参照情報: https://nlftp.mlit.go.jp/isj/
- OpenStreetMap の帰属表示: アプリ内クレジットおよび本 README に明記しています。
  © OpenStreetMap contributors — https://www.openstreetmap.org/copyright

### 住所表記について

- 「街区符号・地番」列は原データの表記をそのまま保持します（枝番のハイフン等を
  正規化しません）。意味は住居表示フラグとセットで解釈します:
  - 住居表示フラグ=1: 街区符号 = 住所の「番」（例: ○○町△丁目□番）
  - 住居表示フラグ=0: 地番 = 住所の「番地」（例: 大字○○ 小字△△ □□番地）
- 住所の最後の「号」（住居番号）は公開データに存在しないため、DB には含まれません。

### 地番点の間引きについて

- 地番区域の点は原データで 16,765,367 点と膨大なため、ビルダーは同じ
  （市区町村・町・小字）グループ内で 15m 以内に保持済み点がある地番点を
  代表1点に集約しています（住居表示区域の点は全て保持）。
- このため地番の密集地域では、表示される番地が実際の区画ではなく
  近傍（15m以内）の代表点のものになる場合があります。町・小字の表示は
  変わりません。

### 生成実績（2026-11-26、令和7年データ）

- 街区レベル: 3,468,082 点（住居表示 1,127,687 点は全保持、地番は間引き後
  約 234 万点。間引きで除外 14,434,357 点）
- 大字・町丁目レベル: 191,106 点
- 道路: 名称つき way 699,516 件 → 1,531,349 セグメント
- `address_db.bin`: 40,593,474 バイト（内訳: 点 20,394,356 / 道路 16,296,154 /
  文字列 2,106,832 / 町テーブル 1,528,424 / その他ディレクトリ等）
  - 25MB の目標は超過しています。主因は地番約234万点と名称つき道路
    153万セグメントの保持で、これ以上の削減は番地・通り名の網羅性を
    損なうため、この構成を採用しました。

## 再生成手順

前提: Python 3.11+。OSM 抽出には pyosmium が必要です
（`python3 -m venv .venv && .venv/bin/pip install osmium`）。

```bash
cd tools/address-db

# 1. 位置参照情報（全都道府県・最新年度）を公式サービスから取得
python3 download_isj.py --out-dir ./isj-data

# 2. Geofabrik から Japan 地域別 PBF を取得し、名称つき道路を抽出
#    https://download.geofabrik.de/asia/japan/ （hokkaido/tohoku/kanto/chubu/
#    kansai/chugoku/shikoku/kyushu の *-latest.osm.pbf）
.venv/bin/python extract_osm_roads.py --out ./roads.jsonl.gz *.osm.pbf

# 3. バイナリDBを生成
python3 build_address_db.py --isj-dir ./isj-data --roads ./roads.jsonl.gz \
    --out ../../app/src/main/assets/address_db.bin

# 4. 検証（既知地点の検索）
python3 address_db.py ../../app/src/main/assets/address_db.bin

# 単体テスト用フィクスチャの再生成
python3 make_test_fixture.py
```

## ファイル

- `download_isj.py` — 公式ダウンロードサービスのフォームフローを再現して
  全都道府県分の ZIP を取得する（年度はサイト側の最新を選択）。
- `extract_osm_roads.py` — PBF から highway かつ name のある way を抽出し、
  Douglas-Peucker（15m）で簡略化して JSONL.gz に出す
  （ビルダー側でも 25m で再簡略化し、2点セグメントに分割する）。
- `build_address_db.py` — バイナリ形式の定義とビルダー本体。形式の詳細は
  ファイル冒頭のドキュメントを参照。
- `address_db.py` — 検証用リーダー（Kotlin 実装と同じ検索仕様）。
- `make_test_fixture.py` — 単体テスト用合成フィクスチャの生成。

## 検索仕様（アプリ側と共通）

1. 街区レベルの最寄り点を 1000m 以内で探す（住居表示・地番どちらの点も対象）。
2. なければ全レベルの最寄り点（最大 20000m）。町丁目点が補完になる。
3. 通り名は 40m 以内の最寄り名称つき道路セグメントから取る。
