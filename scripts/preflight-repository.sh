#!/usr/bin/env bash
set -euo pipefail

# Mandatory repository guardrails before spending time on an Android release build.
git diff --check
test -s AI_WORKFLOW.md || { echo "::error::Missing required AI_WORKFLOW.md development checklist."; exit 1; }
test -s apps/mishnat-yosef/RELEASE_NOTES.md || { echo "::error::Missing release notes."; exit 1; }
test -s apps/mishnat-yosef/CHANGELOG.md || { echo "::error::Missing changelog."; exit 1; }
test -s .github/workflows/import-external-android-app.yml || { echo "::error::Missing external importer workflow."; exit 1; }

# The external importer is a manual utility and must never generate red checks on ordinary pushes.
if grep -Eq '^  push:' .github/workflows/import-external-android-app.yml; then
  echo "::error::The external Android importer must be workflow_dispatch-only."
  exit 1
fi

# Parse every repository workflow with Ruby's bundled YAML parser before building.
ruby -e 'require "yaml"; Dir.glob(".github/workflows/*.{yml,yaml}").each { |f| YAML.load_file(f, aliases: true); puts "YAML OK: #{f}" }'

python3 <<'PY'
import json
import re
from pathlib import Path

release = Path("apps/mishnat-yosef/RELEASE_NOTES.md").read_text(encoding="utf-8").strip()
lines = release.splitlines()
title = lines[0].lstrip("# ").strip() if lines else ""
details = "\n".join(lines[1:]).strip() if len(lines) > 1 else ""
if not title or not details:
    raise SystemExit("::error::RELEASE_NOTES.md must contain a title and meaningful release details.")

manifest = Path("downloads/mishnat-yosef/update.json")
if manifest.exists():
    data = json.loads(manifest.read_text(encoding="utf-8"))
    if not isinstance(data.get("versionCode"), int) or not data.get("versionName") or not data.get("apkUrl"):
        raise SystemExit("::error::update.json is missing versionCode, versionName, or apkUrl.")

# When app Java changes, require matching user-facing release notes and changelog updates.
import subprocess
try:
    changed = subprocess.check_output(
        ["git", "diff", "--name-only", "HEAD^", "HEAD"], text=True
    ).splitlines()
except subprocess.CalledProcessError:
    changed = []
java_changed = any(p.startswith("apps/mishnat-yosef/") and p.endswith((".java", ".kt")) for p in changed)
if java_changed:
    required = {
        "apps/mishnat-yosef/RELEASE_NOTES.md",
        "apps/mishnat-yosef/CHANGELOG.md",
    }
    missing = sorted(required - set(changed))
    if missing:
        raise SystemExit("::error::App source changed without updating release notes/changelog: " + ", ".join(missing))

# Fast typo/copy-paste guard for duplicate primitive/string declarations on one line.
duplicate = re.compile(
    r"(?P<decl>\b(?:final\s+)?(?:int|long|float|double|boolean|String)\s+"
    r"[A-Za-z_$][\w$]*\s*=\s*[^;\n]+;)[ \t]*(?P=decl)"
)
checked = 0
for root in (Path("apps"), Path("templates")):
    if not root.exists():
        continue
    for path in root.rglob("*"):
        if path.suffix not in {".java", ".kt"} or not path.is_file():
            continue
        checked += 1
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if duplicate.search(line):
                raise SystemExit(f"::error file={path},line={number}::Duplicate declaration repeated on one line.")
print(f"Repository preflight passed; checked {checked} Java/Kotlin files.")
PY
