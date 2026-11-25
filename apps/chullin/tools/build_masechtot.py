#!/usr/bin/env python3
"""
בונה את נתוני התוספות לכל מסכתות הבבלי מספריא, לשימוש באפליקציית חולין.

הרצה (במחשב עם אינטרנט):
    python3 tools/build_masechtot.py                       # הורדה מספריא לכל המסכתות
    python3 tools/build_masechtot.py --only Berakhot Shabbat
    python3 tools/build_masechtot.py --input-dir ~/Downloads/tosafot   # קבצי TXT שהורדתם ידנית

הפלט:
    app/src/main/assets/masechtot/<Key>.json   מאגר הדיבורים המתחילים לכל עמוד, {"2a": ["...", ...]}
    app/src/main/assets/masechtot/catalog.json  קטלוג המסכתות שנבנו (שם, טווח דפים, קובץ נתונים)

קובץ TXT תקין מכיל שורות "Daf 2a" ואחריהן שורות של דיבורים. קובץ עם תווי NUL (\\x00) נדחה.
"""

import argparse
import json
import os
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

DOWNLOAD_TEMPLATE = (
    "https://www.sefaria.org.il/download/version/"
    "Tosafot%20on%20{title}%20-%20he%20-%20Vilna%20Edition.plain.txt"
)

# 37 מסכתות הבבלי: (שם לפי ספריא, שם בעברית)
MASECHTOT = [
    ("Berakhot", "ברכות"), ("Shabbat", "שבת"), ("Eruvin", "עירובין"), ("Pesachim", "פסחים"),
    ("Yoma", "יומא"), ("Sukkah", "סוכה"), ("Beitzah", "ביצה"), ("Rosh_Hashanah", "ראש השנה"),
    ("Taanit", "תענית"), ("Megillah", "מגילה"), ("Moed_Katan", "מועד קטן"), ("Chagigah", "חגיגה"),
    ("Yevamot", "יבמות"), ("Ketubot", "כתובות"), ("Nedarim", "נדרים"), ("Nazir", "נזיר"),
    ("Sotah", "סוטה"), ("Gittin", "גיטין"), ("Kiddushin", "קידושין"), ("Bava_Kamma", "בבא קמא"),
    ("Bava_Metzia", "בבא מציעא"), ("Bava_Batra", "בבא בתרא"), ("Sanhedrin", "סנהדרין"),
    ("Makkot", "מכות"), ("Shevuot", "שבועות"), ("Avodah_Zarah", "עבודה זרה"), ("Horayot", "הוריות"),
    ("Zevachim", "זבחים"), ("Menachot", "מנחות"), ("Chullin", "חולין"), ("Bekhorot", "בכורות"),
    ("Arakhin", "ערכין"), ("Temurah", "תמורה"), ("Keritot", "כריתות"), ("Meilah", "מעילה"),
    ("Tamid", "תמיד"), ("Niddah", "נידה"),
]

DAF_RE = re.compile(r"^Daf\s+(\d+)([ab])$", re.IGNORECASE)
LINE_RE = re.compile(r"^Line\s+\d+$", re.IGNORECASE)
TAG_RE = re.compile(r"<[^>]+>")


def parse_tosafot_text(text):
    """מקביל ל-parseSefariaTxt ב-HTML המקורי: מחזיר {"2a": ["דיבור", ...]}."""
    db = {}
    current = None
    for raw in text.split("\n"):
        line = raw.strip()
        if not line:
            continue
        m = DAF_RE.match(line)
        if m:
            current = f"{int(m.group(1))}{m.group(2).lower()}"
            db.setdefault(current, [])
            continue
        if LINE_RE.match(line):
            continue
        if line.startswith("http") or line.startswith("Tosafot on") or line.startswith("תוספות על") \
                or line == "Vilna Edition":
            continue
        if current is None:
            continue
        clean = TAG_RE.sub("", line).strip()
        dash = clean.find(" - ")
        endash = clean.find(" – ")
        if 0 <= dash < 95:
            dh = clean[:dash].strip()
        elif 0 <= endash < 95:
            dh = clean[:endash].strip()
        else:
            plain = clean.find("-")
            if 2 < plain < 80:
                dh = clean[:plain].strip()
            else:
                dh = " ".join(clean.split(" ")[:6])
        if dh:
            db[current].append(dh)
    return db


def validate_raw(raw_bytes, name):
    if b"\x00" in raw_bytes:
        raise ValueError(f"{name}: הקובץ פגום (מכיל תווי NUL). יש להוריד אותו מחדש.")
    try:
        return raw_bytes.decode("utf-8")
    except UnicodeDecodeError as e:
        raise ValueError(f"{name}: קידוד UTF-8 לא תקין ({e}).")


def download(key):
    title = urllib.parse.quote(key.replace("_", " "))
    url = DOWNLOAD_TEMPLATE.format(title=title)
    with urllib.request.urlopen(url, timeout=60) as resp:
        return resp.read()


def build_one(key, he_name, raw_bytes, out_dir):
    text = validate_raw(raw_bytes, key)
    db = parse_tosafot_text(text)
    refs = [r for r in db if r[-1] in "ab"]
    dafs = sorted({(int(r[:-1]), r[-1]) for r in refs})
    if not dafs:
        raise ValueError(f"{key}: לא נמצאו כותרות 'Daf' בקובץ.")
    daf_to = max(d for d, _ in dafs)
    last_side_a_only = (daf_to, "b") not in dafs
    # מאגר רק מדף 2 ומעלה, כמו בחולין. עמודים בלי דיבורים נשארים כרשימה ריקה.
    clean_db = {r: v for r, v in db.items() if int(r[:-1]) >= 2 and v}
    data_name = f"{key}.json"
    (out_dir / data_name).write_text(json.dumps(clean_db, ensure_ascii=False, separators=(",", ":")),
                                     encoding="utf-8")
    entry = {
        "key": key,
        "heName": he_name,
        "dafFrom": 2,
        "dafTo": daf_to,
        "lastDafSideAOnly": last_side_a_only,
        "dataFile": f"masechtot/{data_name}",
    }
    total = sum(len(v) for v in clean_db.values())
    print(f"OK    {key:<16} דפים 2–{daf_to}  תוספות: {total}")
    return entry


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", nargs="*", help="שמות מסכתות באנגלית לפי ספריא")
    parser.add_argument("--input-dir", help="תיקייה עם קבצי TXT שהורדתם ידנית")
    parser.add_argument("--out", default=str(Path(__file__).resolve().parent.parent
                                             / "app/src/main/assets/masechtot"))
    args = parser.parse_args()

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    selected = [(k, h) for k, h in MASECHTOT if not args.only or k in args.only]
    catalog = []
    failures = []

    for key, he_name in selected:
        try:
            if args.input_dir:
                candidates = list(Path(args.input_dir).glob(f"*{key.replace('_', ' ')}*.txt"))
                if not candidates:
                    raise FileNotFoundError(f"{key}: לא נמצא קובץ TXT בתיקייה")
                raw = candidates[0].read_bytes()
            else:
                raw = download(key)
            catalog.append(build_one(key, he_name, raw, out_dir))
        except Exception as e:  # ממשיכים למסכתות הבאות
            failures.append((key, str(e)))
            print(f"SKIP  {key:<16} {e}", file=sys.stderr)

    if catalog:
        # חולין נשמר גם אם לא נבנה כאן, כדי שלא לאבד את הנתונים הקיימים
        catalog_path = out_dir / "catalog.json"
        existing = []
        if catalog_path.exists():
            existing = json.loads(catalog_path.read_text(encoding="utf-8"))
        merged = {e["key"]: e for e in existing}
        merged.update({e["key"]: e for e in catalog})
        ordered = [merged[k] for k, _ in MASECHTOT if k in merged]
        catalog_path.write_text(json.dumps(ordered, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"\nנכתב קטלוג עם {len(ordered)} מסכתות: {catalog_path}")

    if failures:
        print(f"\nנכשלו {len(failures)} מסכתות:", file=sys.stderr)
        for key, msg in failures:
            print(f"  {key}: {msg}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
