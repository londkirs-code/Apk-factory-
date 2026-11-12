#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$1"
OUT_DIR="$2"
APKTOOL_JAR="$HOME/apktool_3.0.3.jar"
BUILD_TOOLS="$ANDROID_HOME/build-tools/36.0.0"
PROJECT_DIR="$(realpath "$PROJECT_DIR")"
mkdir -p "$OUT_DIR"/{logs,outputs,analysis}

[[ -s "$PROJECT_DIR/apktool.yml" && -s "$PROJECT_DIR/AndroidManifest.xml" ]] || {
  echo "::error::This is not a decoded Apktool project."; exit 1;
}
[[ -s "$APKTOOL_JAR" ]] || { echo "::error::Pinned Apktool JAR is missing."; exit 1; }
[[ -x "$BUILD_TOOLS/zipalign" && -x "$BUILD_TOOLS/aapt" ]] || {
  echo "::error::Android build-tools are missing."; exit 1;
}

# Build in a disposable checkout and increment versionCode, keeping the user's source untouched.
python3 - "$PROJECT_DIR/apktool.yml" "$PROJECT_DIR/AndroidManifest.xml" "$OUT_DIR/analysis/version-code.txt" <<'PY'
import re,sys
from pathlib import Path
yml_path, manifest_path, report_path = map(Path,sys.argv[1:])
yml=yml_path.read_text(encoding="utf-8")
manifest=manifest_path.read_text(encoding="utf-8")
section=re.search(r"(?ms)^versionInfo:\s*\n(.*?)(?=^[A-Za-z_][A-Za-z0-9_-]*:|\Z)",yml)
if not section:
    raise SystemExit("versionInfo section is missing from apktool.yml")
code=re.search(r"(?m)^(\s*versionCode:\s*['\"]?)(\d+)(['\"]?\s*(?:#.*)?)$",section.group(1))
if not code:
    raise SystemExit("Numeric versionCode is missing from apktool.yml; refusing to guess.")
manifest_code=re.search(r"android:versionCode\s*=\s*['\"](\d+)['\"]",manifest)
if not manifest_code:
    raise SystemExit("Numeric android:versionCode is missing from AndroidManifest.xml.")
old=max(int(code.group(2)),int(manifest_code.group(1)))
if old >= 2100000000:
    raise SystemExit("versionCode is already at the Android maximum.")
new=old+1
new_section=section.group(1)[:code.start()]+code.group(1)+str(new)+code.group(3)+section.group(1)[code.end():]
yml=yml[:section.start(1)]+new_section+yml[section.end(1):]
manifest=manifest[:manifest_code.start(1)]+str(new)+manifest[manifest_code.end(1):]
yml_path.write_text(yml,encoding="utf-8")
manifest_path.write_text(manifest,encoding="utf-8")
report_path.write_text(f"previous_versionCode={old}\nnew_versionCode={new}\n",encoding="utf-8")
print(f"versionCode incremented: {old} -> {new}")
PY

UNSIGNED="$OUT_DIR/outputs/reconstructed-unsigned.apk"
ALIGNED="$OUT_DIR/outputs/reconstructed-aligned-unsigned.apk"
java -jar "$APKTOOL_JAR" b "$PROJECT_DIR" -o "$UNSIGNED" 2>&1 | tee "$OUT_DIR/logs/apktool-build.log"
test -s "$UNSIGNED"
"$BUILD_TOOLS/zipalign" -f -p 4 "$UNSIGNED" "$ALIGNED"
"$BUILD_TOOLS/aapt" dump badging "$ALIGNED" > "$OUT_DIR/analysis/aapt-badging.txt"
EXPECTED_VERSION="$(sed -n 's/^new_versionCode=//p' "$OUT_DIR/analysis/version-code.txt")"
ACTUAL_VERSION="$(sed -n "s/.*versionCode='\([0-9][0-9]*\)'.*/\1/p" "$OUT_DIR/analysis/aapt-badging.txt" | head -n1)"
[[ "$ACTUAL_VERSION" == "$EXPECTED_VERSION" ]] || {
  echo "::error::Built APK versionCode ($ACTUAL_VERSION) does not match incremented value ($EXPECTED_VERSION)."; exit 1;
}
sha256sum "$ALIGNED" | tee "$OUT_DIR/analysis/SHA256SUMS.txt"
cp "$ALIGNED" "$OUT_DIR/reconstructed-aligned-unsigned.apk"
echo "Apktool build, versionCode increment, manifest validation and zip alignment completed."
