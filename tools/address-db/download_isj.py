#!/usr/bin/env python3
"""国土交通省「位置参照情報ダウンロードサービス」から、都道府県単位の
街区レベル／大字・町丁目レベル CSV（ZIP）を全都道府県分ダウンロードする。

公式サイトのフォーム CGI の流れ（ダウンロード方式選択 → 都道府県選択 →
ファイル選択 → 規約同意 → ダウンロード）をスクリプトで再現している。
データ出典: 国土交通省 国土政策局 国土情報課「位置参照情報」
    https://nlftp.mlit.go.jp/isj/

使い方:
    python3 download_isj.py --out-dir ./isj-data

ZIP は --out-dir にそのまま保存される（展開は build_address_db.py 側で行う）。
年度はサイト側の最新（都道府県選択ページで selected になっている年度）を用いる。
"""
import argparse, json, math, os, re, sys, time
import urllib.parse, urllib.request

BASE = "https://nlftp.mlit.go.jp/cgi-bin/isj/dls/"
# サイトのページ内スクリプトが発行する bot 対策クッキーを再現する
_COOKIE_V = math.floor(1795690200 * 3.1415926535898)
HEADERS = {"User-Agent": "Mozilla/5.0", "Cookie": f"__zjc3348={_COOKIE_V}"}


def post_raw(url, body: bytes) -> bytes:
    req = urllib.request.Request(url, data=body, headers=HEADERS)
    return urllib.request.urlopen(req, timeout=300).read()


def q(value) -> str:
    if isinstance(value, str):
        value = value.encode("shift_jis")
    return urllib.parse.quote_from_bytes(value)


def get_pref_page():
    """都道府県選択ページを取得し、(action値, 年度, [(都道府県id, ac値)]) を返す。"""
    raw = post_raw(BASE + "_view_cities_wards.cgi", b"sbm=2")
    aval = re.search(rb'name="action" value="([^"]*)"', raw).group(1)
    sel = re.search(rb'<select name="cyn".*?</select>', raw, re.S).group(0)
    cyn = re.search(rb'<option value="([^"]*)" selected', sel).group(1)
    acs = re.findall(rb'<input type="checkbox" value="(\d{4})" name="ac" id="ac(\d{2})"', raw)
    return aval, cyn, [(aid.decode(), v.decode()) for v, aid in acs]


def choose_files(ac_value, aval, cyn):
    """ファイル選択ページから [(chn, data_kind)] を得る。cln=11（全レベル）。"""
    body = "&".join(["sbm=2", "srh=", "oa=", "pc=", f"ac={ac_value}", "cln=11",
                     "cyn=" + q(cyn), "action=" + q(aval)]).encode("ascii")
    html = post_raw(BASE + "_choose_files.cgi", body).decode("shift_jis", errors="replace")
    files = []
    for m in re.finditer(r'<input type="checkbox" value="(\d+)" name="chn" id="chn(\d+)">', html):
        kind = re.search(rf'<input type="hidden" value="(\d)" name="data_kind" id="data_kind{m.group(2)}">', html)
        files.append((m.group(1), kind.group(1) if kind else "?"))
    return files


def download_urls(ac_value, cyn_str, files):
    """規約ページ経由でダウンロードページにある実ファイルURL一覧を得る。"""
    parts = ["sbm=2", "srh=", "oa=", "pc=", f"ac={ac_value}", "cln=11", "cyn=" + q(cyn_str)]
    for chn, kind in files:
        parts.append(f"chn={chn}")
        parts.append(f"data_kind={kind}")
    post_raw(BASE + "_view_stipulation.cgi", "&".join(parts).encode("ascii"))
    joined = "<delimiter>".join(chn for chn, _ in files)
    body = "&".join(["chn=" + urllib.parse.quote(joined), "sbm=2", "srh=", "oa=", "pc=",
                     f"ac={ac_value}", "cln=11", "cyn=" + q(cyn_str)]).encode("ascii")
    html = post_raw(BASE + "_download_files.cgi", body).decode("shift_jis", errors="replace")
    return re.findall(r"DownLd_isj\('([^']+)','([^']+)','([^']+)'", html)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out-dir", required=True)
    args = ap.parse_args()
    os.makedirs(args.out_dir, exist_ok=True)
    manifest_path = os.path.join(args.out_dir, "manifest.json")
    manifest = json.load(open(manifest_path)) if os.path.exists(manifest_path) else {}

    aval, cyn, acs = get_pref_page()
    cyn_str = cyn.decode("shift_jis")
    print(f"年度: {cyn_str} / 都道府県数: {len(acs)}", flush=True)
    for pref_id, ac_value in acs:
        done = manifest.get(pref_id)
        if done and all(os.path.exists(os.path.join(args.out_dir, e["file"])) for e in done):
            print(pref_id, "skip", flush=True)
            continue
        files = choose_files(ac_value, aval, cyn)
        if not files:
            print(pref_id, "ファイルなし", flush=True)
            manifest[pref_id] = []
            continue
        entries = []
        for _size_label, fname, path in download_urls(ac_value, cyn_str, files):
            dest = os.path.join(args.out_dir, fname)
            if not os.path.exists(dest):
                req = urllib.request.Request("https://nlftp.mlit.go.jp" + path, headers=HEADERS)
                with urllib.request.urlopen(req, timeout=600) as r, open(dest, "wb") as f:
                    while True:
                        chunk = r.read(1 << 20)
                        if not chunk:
                            break
                        f.write(chunk)
            entries.append({"file": fname, "url": path, "bytes": os.path.getsize(dest)})
            print(pref_id, fname, entries[-1]["bytes"], flush=True)
        manifest[pref_id] = entries
        json.dump(manifest, open(manifest_path, "w"), ensure_ascii=False, indent=1)
        time.sleep(0.5)
    print("完了", flush=True)


if __name__ == "__main__":
    sys.exit(main())
