#!/usr/bin/env python3
"""Safely normalize APK, XAPK, APKS and ZIP inputs into a base + split set."""
from pathlib import Path, PurePosixPath
import shutil, subprocess, sys, zipfile

src = Path(sys.argv[1])
dst = Path(sys.argv[2])
aapt = Path(sys.argv[3])
MAX_ARCHIVE = 1024 * 1024 * 1024
MAX_APK = 512 * 1024 * 1024
MAX_TOTAL_UNPACKED = 2 * 1024 * 1024 * 1024
MAX_APKS = 128

def is_apk(path):
    try:
        with zipfile.ZipFile(path) as z:
            return "AndroidManifest.xml" in z.namelist()
    except (zipfile.BadZipFile, OSError):
        return False

def safe_name(name):
    p = PurePosixPath(name)
    return not p.is_absolute() and all(part not in ("", ".", "..") for part in p.parts)

def package_name(path):
    result = subprocess.run([str(aapt), "dump", "badging", str(path)],
                            text=True, capture_output=True, check=True)
    for line in result.stdout.splitlines():
        if line.startswith("package:"):
            import re
            match = re.search(r"name='([^']+)'", line)
            if match:
                return match.group(1)
    raise SystemExit(f"Cannot read Android package name: {path.name}")

if not src.is_file() or src.stat().st_size == 0:
    raise SystemExit("Downloaded input is missing or empty")
if src.stat().st_size > MAX_ARCHIVE:
    raise SystemExit("Download exceeds the 1 GiB compressed input limit")

dst.mkdir(parents=True, exist_ok=True)
splits = dst / "splits"
splits.mkdir(exist_ok=True)

if is_apk(src):
    shutil.copy2(src, splits / "base.apk")
    print("Input recognized as one complete APK.")
else:
    if not zipfile.is_zipfile(src):
        raise SystemExit("Input is neither an APK nor a valid XAPK/APKS/ZIP archive")
    with zipfile.ZipFile(src) as archive:
        infos = [i for i in archive.infolist() if not i.is_dir()]
        if len(infos) > 50000:
            raise SystemExit("Archive contains too many entries")
        if sum(i.file_size for i in infos) > MAX_TOTAL_UNPACKED:
            raise SystemExit("Archive expands beyond the 2 GiB safety limit")
        apk_infos = [i for i in infos if i.filename.lower().endswith(".apk")]
        if not apk_infos:
            raise SystemExit("Bundle contains no APK files")
        if len(apk_infos) > MAX_APKS:
            raise SystemExit(f"Bundle contains more than {MAX_APKS} APK files")
        total = 0
        for info in apk_infos:
            if not safe_name(info.filename):
                raise SystemExit("Unsafe archive path: " + info.filename)
            if info.flag_bits & 1:
                raise SystemExit("Encrypted APK entries are not supported")
            if info.file_size <= 0 or info.file_size > MAX_APK:
                raise SystemExit("An embedded APK is empty or exceeds 512 MiB")
            total += info.file_size
            name = PurePosixPath(info.filename).name
            target = splits / name
            if target.exists():
                raise SystemExit("Duplicate APK filename in bundle: " + name)
            with archive.open(info) as inp, target.open("wb") as out:
                shutil.copyfileobj(inp, out, 1024 * 1024)
            if not is_apk(target):
                target.unlink(missing_ok=True)
                raise SystemExit("Invalid APK entry: " + info.filename)
        print(f"Extracted {len(apk_infos)} APK file(s) from bundle.")

apk_files = sorted(p for p in splits.glob("*.apk") if p.is_file())
if not apk_files:
    raise SystemExit("No APK files were normalized")
if len(apk_files) == 1:
    base = apk_files[0]
else:
    preferred = [p for p in apk_files if p.name.lower() in
                 ("base.apk", "base-master.apk", "universal.apk")]
    if not preferred:
        preferred = [p for p in apk_files
                     if not p.name.lower().startswith(("config.", "split_config.",
                                                       "config_", "split_config_"))
                     and "config." not in p.name.lower()]
    if len(preferred) != 1:
        raise SystemExit("Could not identify exactly one base APK. Expected base.apk/base-master.apk or one unambiguous non-config APK.")
    base = preferred[0]
if base.name != "base.apk":
    normalized = splits / "base.apk"
    if normalized.exists():
        raise SystemExit("base.apk filename collision")
    base.rename(normalized)
    base = normalized

expected_package = package_name(base)
reports = []
for apk in sorted(splits.glob("*.apk")):
    actual = package_name(apk)
    if actual != expected_package:
        raise SystemExit(f"Package mismatch: {apk.name} is {actual}, expected {expected_package}")
    reports.append(f"{apk.name}\t{actual}\t{apk.stat().st_size}")
(dst / "base-path.txt").write_text(str(base.resolve()) + "\n", encoding="utf-8")
(dst / "package-name.txt").write_text(expected_package + "\n", encoding="utf-8")
(dst / "split-inventory.tsv").write_text("filename\tpackage\tsize_bytes\n" + "\n".join(reports) + "\n", encoding="utf-8")
print(f"Validated package {expected_package}: base + {len(apk_files)-1} split(s)")
