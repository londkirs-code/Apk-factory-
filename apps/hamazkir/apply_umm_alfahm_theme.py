#!/usr/bin/env python3
"""Apply the Hamazkir-specific Umm al-Fahm theme to a decoded official APK.

This deliberately edits only the existing third theme (enum value GALILEE).
The internal enum constant is preserved so SharedPreferences values survive.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.azless.app"
THEME_LABEL = "עיצוב אום אל פחם"
THEME_DESCRIPTION = "אדום וירוק: עמוק, טבעי ומאוזן"

# Existing Galil palette => Umm al-Fahm red/green palette.
GALIL_COLORS = {
    "0xfffbf3eeL": "0xfff5f7f2L",
    "0xff3a2a24L": "0xff203126L",
    "0xff8e6f62L": "0xff667769L",
    "0xffb09a90L": "0xff96a699L",
    "0xffd9667eL": "0xffc62828L",
    "0xffc4566cL": "0xffa61f26L",
    "0xff8f2f48L": "0xff741820L",
    "0xfffbe1e3L": "0xfffce8e8L",
    "0xfffbede6L": "0xfff2f7f0L",
    "0xff5e9466L": "0xff347a3dL",
    "0xff3f6e48L": "0xff24572dL",
    "0xffe3f2e1L": "0xffe5f2e6L",
    "0xff6a7fcbL": "0xff3f7b47L",
    "0xffe6eafaL": "0xffeaf4ebL",
    "0xff8c74d0L": "0xff9b3542L",
    "0xffede6fbL": "0xfff7e9ebL",
    "0xffd6455aL": "0xffd13c44L",
    "0xfffce3e6L": "0xfffce7e8L",
    "0xfff2e3dcL": "0xffe6eee6L",
    "0xfff1ddd4L": "0xffdce8dcL",
    "0xff1d1624L": "0xff141f18L",
    "0xff2a1f2eL": "0xff1d2b22L",
    "0xff362839L": "0xff29392dL",
    "0xff4a3850L": "0xff405747L",
    "0xfff6ecefL": "0xfff2f6f2L",
    "0xffc4a9b4L": "0xffb6c6b9L",
    "0xfff0927aL": "0xffa61f26L",
    "0xffffb9a6L": "0xffffb7b7L",
    "0xff9cc79aL": "0xff9ccba4L",
    "0xffa9b7f2L": "0xffb2d7b8L",
    "0xff2b2a4aL": "0xff293e2eL",
    "0xffcdbdf6L": "0xffffc4c4L",
    "0xff34284eL": "0xff4c2b31L",
    "0xfff28b9bL": "0xffff999eL",
    "0xff4b2030L": "0xff4b2227L",
    "0xff263a2cL": "0xff263d2dL",
    "0xfff4a37cL": "0xffc62828L",
    "0xffe2786fL": "0xffc62828L",
}

FILE_PATCHES = {
    "ui/theme/GalilKt.smali": {
        "0xff2e2329L": "0xff1d2b22L",
        "0xfffcf3eeL": "0xfff5f7f2L",
        "0xffa8465aL": "0xff8c3039L",
        "0xff5a3f9eL": "0xff2f6c3bL",
        "0xffb0603fL": "0xff9d4146L",
        "0xffffc8b0L": "0xffffd6d4L",
        "0xffe3d2ffL": "0xffdceeddL",
        "0xffffd9e4L": "0xffffe1e1L",
    },
    "ui/settings/LookChoiceKt$LookChoice$1.smali": {
        "0xffffd3beL": "0xffd64a4aL",
        "0xfffbeff0L": "0xfff1f7efL",
        "0xffe9dcffL": "0xff3b8146L",
    },
    "ui/nav/GalilBarKt.smali": {
        "0xff2c1f30L": "0xff1d2b22L",
        "0xffffb3a3L": "0xffffb7b7L",
        "0xfffbe3daL": "0xfffce8e8L",
    },
}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def smali_unicode(text: str) -> str:
    return "".join(
        ch if 0x20 <= ord(ch) <= 0x7E
        else (f"\\u{ord(ch):04x}" if ord(ch) <= 0xFFFF else ch)
        for ch in text
    )


def replace_once_or_already(text: str, old: str, new: str, label: str) -> tuple[str, bool]:
    count_old = text.count(old)
    count_new = text.count(new)
    if count_old == 1:
        return text.replace(old, new, 1), True
    if count_old == 0 and count_new >= 1:
        return text, False
    raise RuntimeError(
        f"{label}: expected exactly one original token, found {count_old}; new token count={count_new}"
    )


def patch_file(path: Path, mapping: dict[str, str], report: list[dict], label: str) -> None:
    if not path.is_file():
        raise RuntimeError(f"Required smali file is missing: {path}")
    original = path.read_text(encoding="utf-8")
    updated = original
    changes = 0
    for old, new in mapping.items():
        updated, changed = replace_once_or_already(updated, old, new, f"{label} ({old})")
        changes += int(changed)
    if updated != original:
        path.write_text(updated, encoding="utf-8", newline="\n")
    report.append({
        "file": str(path),
        "changes_applied": changes,
        "sha256_before": sha256(original.encode("utf-8")),
        "sha256_after": sha256(updated.encode("utf-8")),
    })


def patch_enum(path: Path, report: list[dict]) -> None:
    if not path.is_file():
        raise RuntimeError(f"Required enum file is missing: {path}")
    original = path.read_text(encoding="utf-8")
    updated = original
    label_old = 'const-string v1, "' + smali_unicode("עיצוב גלילי") + '"'
    label_new = 'const-string v1, "' + smali_unicode(THEME_LABEL) + '"'
    desc_old = 'const-string v2, "' + smali_unicode("רך ומתמזג: אפרסק, ורוד ולבנדר") + '"'
    desc_new = 'const-string v2, "' + smali_unicode(THEME_DESCRIPTION) + '"'
    changes = 0
    for old, new, name in (
        (label_old, label_new, "theme label"),
        (desc_old, desc_new, "theme description"),
    ):
        updated, changed = replace_once_or_already(updated, old, new, name)
        changes += int(changed)
    for token in (
        '"GALILEE"', '"JERUSALEM"', '"TEL_AVIV"',
        ".field public static final enum GALILEE:",
        ".field public static final enum JERUSALEM:",
        ".field public static final enum TEL_AVIV:",
    ):
        if token not in updated:
            raise RuntimeError(f"Enum compatibility check failed; missing {token}")
    if updated != original:
        path.write_text(updated, encoding="utf-8", newline="\n")
    report.append({
        "file": str(path),
        "changes_applied": changes,
        "sha256_before": sha256(original.encode("utf-8")),
        "sha256_after": sha256(updated.encode("utf-8")),
        "preserved_enum_values": ["GALILEE", "JERUSALEM", "TEL_AVIV"],
    })


def validate_manifest(project: Path) -> None:
    manifest_path = project / "AndroidManifest.xml"
    if not manifest_path.is_file():
        raise RuntimeError("AndroidManifest.xml is missing")
    root = ET.parse(manifest_path).getroot()
    if root.attrib.get("package") != PACKAGE:
        raise RuntimeError(f"Unexpected application package: {root.attrib.get('package')!r}")
    apktool_yml = project / "apktool.yml"
    if not apktool_yml.is_file():
        raise RuntimeError("apktool.yml is missing")
    yml = apktool_yml.read_text(encoding="utf-8")
    if not re.search(r"(?m)^\\s*versionCode:\\s*['\\"]?\\d+", yml):
        raise RuntimeError("A numeric versionCode was not found in apktool.yml")


def validate_patched(project: Path) -> None:
    enum = (project / "smali_classes2/com/azless/app/data/settings/AppLook.smali").read_text(encoding="utf-8")
    if smali_unicode(THEME_LABEL) not in enum or smali_unicode(THEME_DESCRIPTION) not in enum:
        raise RuntimeError("Umm al-Fahm display label/description validation failed")
    if '"GALILEE"' not in enum or '"JERUSALEM"' not in enum or '"TEL_AVIV"' not in enum:
        raise RuntimeError("One or more persisted enum values were changed")

    palette = (project / "smali_classes2/com/azless/app/ui/theme/Galil.smali").read_text(encoding="utf-8")
    for value in GALIL_COLORS.values():
        if value not in palette:
            raise RuntimeError(f"Expected new palette value was not written: {value}")
    accent_to_snippet = (
        "sput-wide v2, Lcom/azless/app/ui/theme/Galil;->AccentFrom:J\n\n"
        "    const-wide v0, 0xff347a3dL\n"
    )
    if accent_to_snippet not in palette:
        raise RuntimeError("Accent gradient was not set to red-to-green")

    for relative, mapping in FILE_PATCHES.items():
        text = (project / "smali_classes2/com/azless/app" / relative).read_text(encoding="utf-8")
        for new in mapping.values():
            if new not in text:
                raise RuntimeError(f"Expected theme color {new} missing from {relative}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("project", type=Path, help="Decoded Apktool project directory")
    parser.add_argument("--report", type=Path, default=None, help="Optional JSON report output")
    args = parser.parse_args()
    project = args.project.resolve()
    try:
        validate_manifest(project)
        report: list[dict] = []
        base = project / "smali_classes2/com/azless/app"
        patch_enum(base / "data/settings/AppLook.smali", report)
        patch_file(base / "ui/theme/Galil.smali", GALIL_COLORS, report, "Galil palette")
        palette_path = base / "ui/theme/Galil.smali"
        palette_text = palette_path.read_text(encoding="utf-8")
        old_accent_to = (
            "sput-wide v2, Lcom/azless/app/ui/theme/Galil;->AccentFrom:J\n\n"
            "    .line 67\n    invoke-static {v0, v1}, Landroidx/compose/ui/graphics/ColorKt;->Color(J)J"
        )
        new_accent_to = (
            "sput-wide v2, Lcom/azless/app/ui/theme/Galil;->AccentFrom:J\n\n"
            "    const-wide v0, 0xff347a3dL\n\n"
            "    .line 67\n    invoke-static {v0, v1}, Landroidx/compose/ui/graphics/ColorKt;->Color(J)J"
        )
        palette_text, changed = replace_once_or_already(
            palette_text, old_accent_to, new_accent_to, "AccentTo gradient"
        )
        if changed:
            palette_path.write_text(palette_text, encoding="utf-8", newline="\n")
        report.append({"file": str(palette_path), "accent_to_green_added": changed})

        for relative, mapping in FILE_PATCHES.items():
            patch_file(base / relative, mapping, report, relative)
        validate_patched(project)

        output = {
            "status": "success",
            "package": PACKAGE,
            "display_name": THEME_LABEL,
            "description": THEME_DESCRIPTION,
            "preserved_persisted_theme_values": ["GALILEE", "JERUSALEM", "TEL_AVIV"],
            "palette": "Umm al-Fahm red/green; light and dark mode updated",
            "files": report,
        }
        serialized = json.dumps(output, ensure_ascii=False, indent=2) + "\n"
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(serialized, encoding="utf-8")
        print(serialized, end="")
        return 0
    except (OSError, RuntimeError, ET.ParseError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
