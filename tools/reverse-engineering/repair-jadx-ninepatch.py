#!/usr/bin/env python3
"""Repair JADX-exported Nine-Patch PNGs using Apktool's decoded equivalents.

JADX can export a .9.png without Android's stretch/content chunk. Apktool
preserves the encoded Nine-Patch border and this repair copies only matching
resource names from the decoded resource tree. Unmatched files are left intact.
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import struct
import zlib
from pathlib import Path
from typing import Any

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def normalized_folder(name: str) -> str:
    """Normalize density/configuration folders such as drawable-mdpi-v4."""
    return re.sub(r"-v\d+$", "", name)


def png_is_well_formed(path: Path) -> bool:
    """Validate the PNG signature, chunk framing and CRCs."""
    data = path.read_bytes()
    if not data.startswith(PNG_SIGNATURE):
        return False
    offset = len(PNG_SIGNATURE)
    saw_iend = False
    while offset + 12 <= len(data):
        length = struct.unpack(">I", data[offset : offset + 4])[0]
        chunk_end = offset + 12 + length
        if chunk_end > len(data):
            return False
        chunk_type = data[offset + 4 : offset + 8]
        chunk_data = data[offset + 8 : offset + 8 + length]
        expected_crc = struct.unpack(">I", data[offset + 8 + length : chunk_end])[0]
        actual_crc = zlib.crc32(chunk_type + chunk_data) & 0xFFFFFFFF
        if expected_crc != actual_crc:
            return False
        offset = chunk_end
        if chunk_type == b"IEND":
            saw_iend = length == 0 and offset == len(data)
            break
    return saw_iend


def build_source_index(source_root: Path) -> dict[tuple[str, str], Path]:
    index: dict[tuple[str, str], Path] = {}
    for source in source_root.rglob("*.9.png"):
        key = (normalized_folder(source.parent.name), source.name)
        # Prefer the first equivalent resource. Apktool's decode normally has
        # one matching resource per density/configuration folder.
        index.setdefault(key, source)
    return index


def repair(source_root: Path, target_root: Path) -> dict[str, Any]:
    if not source_root.is_dir():
        raise FileNotFoundError(f"Apktool resource directory not found: {source_root}")
    if not target_root.is_dir():
        return {
            "status": "no-jadx-res-directory",
            "repaired": [],
            "unmatched": [],
            "total_nine_patch_files": 0,
        }

    source_index = build_source_index(source_root)
    repaired: list[dict[str, str]] = []
    unmatched: list[str] = []
    invalid_source: list[str] = []
    candidates = sorted(target_root.rglob("*.9.png"))

    for target in candidates:
        key = (normalized_folder(target.parent.name), target.name)
        source = source_index.get(key)
        if source is None:
            unmatched.append(str(target.relative_to(target_root)))
            continue
        if not png_is_well_formed(source):
            invalid_source.append(str(source.relative_to(source_root)))
            continue
        # The APKTool resource copy was successfully consumed by Apktool's
        # own round-trip build; preserve its Nine-Patch metadata for AAPT.
        shutil.copy2(source, target)
        if not png_is_well_formed(target):
            raise RuntimeError(f"Copied PNG failed structural validation: {target}")
        repaired.append(
            {
                "target": str(target.relative_to(target_root)),
                "source": str(source.relative_to(source_root)),
            }
        )

    return {
        "status": "completed",
        "total_nine_patch_files": len(candidates),
        "repaired_count": len(repaired),
        "unmatched_count": len(unmatched),
        "invalid_source_count": len(invalid_source),
        "repaired": repaired,
        "unmatched": unmatched,
        "invalid_sources": invalid_source,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("apktool_res", type=Path)
    parser.add_argument("jadx_res", type=Path)
    parser.add_argument("report", type=Path)
    args = parser.parse_args()

    result = repair(args.apktool_res, args.jadx_res)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(
        "JADX Nine-Patch resource repair: "
        f"{result.get('repaired_count', 0)} repaired, "
        f"{result.get('unmatched_count', 0)} unmatched, "
        f"{result.get('invalid_source_count', 0)} invalid source(s)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
