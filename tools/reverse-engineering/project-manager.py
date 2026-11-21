#!/usr/bin/env python3
"""APK Factory persistent reverse-engineering project manager."""
from __future__ import annotations
import argparse, json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "apps" / "reverse-engineered"
SLUG_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]{0,39}$")

def project_path(slug: str) -> Path:
    if not SLUG_RE.fullmatch(slug):
        raise SystemExit("Invalid project slug: use 1-40 letters, digits, '_' or '-'.")
    return ROOT / slug

def validate(path: Path) -> dict:
    required = [path / "apktool-project" / "apktool.yml",
                path / "apktool-project" / "AndroidManifest.xml"]
    missing = [str(p.relative_to(path)) for p in required if not p.is_file()]
    if missing:
        raise SystemExit("Incomplete project; missing: " + ", ".join(missing))
    metadata_file = path / "project.json"
    metadata = json.loads(metadata_file.read_text(encoding="utf-8")) if metadata_file.exists() else {}
    metadata["project_dir"] = str(path)
    metadata["apktool_project"] = "apktool-project"
    metadata["jadx_export_present"] = (path / "jadx-gradle-export").is_dir()
    metadata["analysis_present"] = (path / "analysis").is_dir()
    metadata["logs_present"] = (path / "logs").is_dir()
    metadata["ready_for_apktool_rebuild"] = True
    return metadata

def main() -> int:
    parser = argparse.ArgumentParser(description="Manage persistent APK Factory reverse-engineering projects.")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("list", help="List saved projects")
    show = sub.add_parser("show", help="Show project metadata and readiness")
    show.add_argument("slug")
    check = sub.add_parser("validate", help="Validate an existing project before rebuilding")
    check.add_argument("slug")
    args = parser.parse_args()
    ROOT.mkdir(parents=True, exist_ok=True)
    if args.command == "list":
        projects = sorted(p for p in ROOT.iterdir() if p.is_dir() and (p / "project.json").is_file())
        if not projects:
            print("No persistent reverse-engineering projects registered yet.")
            return 0
        for p in projects:
            try:
                meta = json.loads((p / "project.json").read_text(encoding="utf-8"))
                print(f'{p.name}\t{meta.get("package_name", "unknown-package")}\t{meta.get("created_at", "unknown-date")}')
            except (OSError, json.JSONDecodeError):
                print(f"{p.name}\tINVALID-METADATA")
        return 0
    path = project_path(args.slug)
    if not path.is_dir():
        raise SystemExit(f"Project not found: {path}")
    metadata = validate(path)
    print(json.dumps(metadata, ensure_ascii=False, indent=2))
    if args.command == "validate":
        print("VALID: editable Apktool project is ready for the existing rebuild workflow.")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, json.JSONDecodeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
