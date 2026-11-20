#!/usr/bin/env python3
"""Set one identical numeric versionCode in an Apktool project."""
import re, sys, xml.etree.ElementTree as ET
from pathlib import Path
project = Path(sys.argv[1])
new = int(sys.argv[2])
if new < 1 or new >= 2100000000:
    raise SystemExit("versionCode is outside the supported Android range")
yml_path = project / "apktool.yml"
manifest_path = project / "AndroidManifest.xml"
yml = yml_path.read_text(encoding="utf-8")
manifest = manifest_path.read_text(encoding="utf-8")
section = re.search(r"(?ms)^versionInfo:\s*\n(.*?)(?=^[A-Za-z_][A-Za-z0-9_-]*:|\Z)", yml)
if not section:
    raise SystemExit(f"versionInfo missing: {project}")
code = re.search(r"(?m)^(\s*versionCode:\s*['\"]?)(\d+)(['\"]?\s*(?:#.*)?)$", section.group(1))
if not code:
    raise SystemExit(f"Numeric versionCode missing: {project}")
body = section.group(1)
body = body[:code.start()] + code.group(1) + str(new) + code.group(3) + body[code.end():]
yml = yml[:section.start(1)] + body + yml[section.end(1):]
root = re.search(r"<manifest\b[^>]*>", manifest, re.S)
if not root:
    raise SystemExit(f"Manifest root missing: {project}")
tag = root.group(0)
attr = re.search(r"\bandroid:versionCode\s*=\s*(['\"])(.*?)\1", tag, re.S)
if attr:
    tag = tag[:attr.start(2)] + str(new) + tag[attr.end(2):]
else:
    tag = tag[:-1] + f' android:versionCode="{new}">'
if not re.search(r"\bxmlns:android\s*=", tag):
    tag = tag[:-1] + ' xmlns:android="http://schemas.android.com/apk/res/android">'
manifest = manifest[:root.start()] + tag + manifest[root.end():]
ET.fromstring(manifest)
yml_path.write_text(yml, encoding="utf-8")
manifest_path.write_text(manifest, encoding="utf-8")
