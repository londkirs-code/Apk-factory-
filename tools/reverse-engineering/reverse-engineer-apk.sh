#!/usr/bin/env bash
set -eo pipefail

INPUT_APK="$1"
OUT_DIR="$2"
APKTOOL_JAR="$HOME/apktool_3.0.3.jar"
JADX_BIN="$HOME/jadx/bin/jadx"
BUILD_TOOLS="$ANDROID_HOME/build-tools/36.0.0"

if [[ -z "$INPUT_APK" || -z "$OUT_DIR" ]]; then
  echo "::error::Usage: reverse-engineer-apk.sh input.apk output-directory"; exit 2
fi
mkdir -p "$OUT_DIR"/{analysis,logs,native-libs,outputs}
INPUT_APK="$(realpath "$INPUT_APK")"
OUT_DIR="$(realpath "$OUT_DIR")"
[[ -s "$INPUT_APK" && -s "$APKTOOL_JAR" && -x "$JADX_BIN" ]] || { echo "::error::Input or pinned decompiler is missing."; exit 1; }
[[ -x "$BUILD_TOOLS/apksigner" && -x "$BUILD_TOOLS/zipalign" ]] || { echo "::error::Android apksigner/zipalign are missing."; exit 1; }

APK_BYTES="$(stat -c '%s' "$INPUT_APK")"
if (( APK_BYTES > 536870912 )); then echo "::error::APK exceeds the 512 MiB safety limit."; exit 1; fi

python3 - "$INPUT_APK" <<'PY'
import sys, zipfile
from pathlib import PurePosixPath
try:
    with zipfile.ZipFile(sys.argv[1]) as z:
        names=z.namelist()
        if len(names)>50000: raise SystemExit("Too many ZIP entries")
        if "AndroidManifest.xml" not in names: raise SystemExit("AndroidManifest.xml missing; supply one complete APK")
        dex=[n for n in names if PurePosixPath(n).name=="classes.dex" or (PurePosixPath(n).name.startswith("classes") and PurePosixPath(n).suffix==".dex")]
        if not dex: raise SystemExit("No root DEX files; split APK sets are not supported")
        if sum(i.file_size for i in z.infolist())>2147483648: raise SystemExit("APK expands beyond 2 GiB")
        if any(i.flag_bits & 1 for i in z.infolist()): raise SystemExit("Encrypted ZIP entries are not supported")
        bad=z.testzip()
        if bad: raise SystemExit("Corrupt ZIP entry: "+bad)
        print(f"APK container validated: {len(names)} entries, {len(dex)} DEX file(s)")
except zipfile.BadZipFile: raise SystemExit("Input is not a valid APK/ZIP container")
PY

INPUT_SHA256="$(sha256sum "$INPUT_APK" | awk '{print $1}')"
printf '%s  input.apk\n' "$INPUT_SHA256" > "$OUT_DIR/analysis/SHA256SUMS.txt"
cp "$INPUT_APK" "$OUT_DIR/analysis/original.apk"
APKTOOL_VERSION="$(java -jar "$APKTOOL_JAR" --version 2>&1 | tail -n 1 || true)"
JADX_VERSION="$("$JADX_BIN" --version 2>&1 | tail -n 1 || true)"

# The Apktool project preserves Smali, manifest, resources and assets.
java -jar "$APKTOOL_JAR" d --force --all-src -o "$OUT_DIR/apktool-project" "$INPUT_APK" \
  > "$OUT_DIR/logs/apktool-decode.log" 2>&1
if [[ ! -s "$OUT_DIR/apktool-project/apktool.yml" ]]; then
  echo "::error::Apktool did not produce a complete project."; exit 1
fi

# JADX creates readable Java plus a best-effort Gradle project (not the original source).
set +e
"$JADX_BIN" --show-bad-code --deobf --export-gradle --export-gradle-type android-app \
  -d "$OUT_DIR/jadx-gradle-export" "$INPUT_APK" > "$OUT_DIR/logs/jadx-export.log" 2>&1
JADX_STATUS=$?
set -e
printf 'JADX exit code: %s\n' "$JADX_STATUS" > "$OUT_DIR/analysis/jadx-status.txt"

# Extract only native .so files and reject unsafe archive paths.
python3 - "$INPUT_APK" "$OUT_DIR/native-libs" <<'PY'
import sys,zipfile
from pathlib import PurePosixPath,Path
apk,dst=sys.argv[1],Path(sys.argv[2]); count=0
with zipfile.ZipFile(apk) as z:
 for item in z.infolist():
  p=PurePosixPath(item.filename)
  if not (len(p.parts)>=3 and p.parts[0]=="lib" and p.suffix==".so"): continue
  if p.is_absolute() or any(x in ("",".","..") for x in p.parts): raise SystemExit("Unsafe native library path: "+item.filename)
  target=dst.joinpath(*p.parts[1:]); target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(z.read(item)); count+=1
print(f"Extracted {count} native library file(s)")
PY

# Round-trip build is best effort: decoder output remains useful when a vendor resource cannot rebuild.
APKTOOL_BUILD_STATUS="failed"
UNSIGNED="$OUT_DIR/outputs/apktool-rebuilt-unsigned.apk"
ALIGNED="$OUT_DIR/outputs/apktool-rebuilt-aligned-unsigned.apk"
set +e
java -jar "$APKTOOL_JAR" b "$OUT_DIR/apktool-project" -o "$UNSIGNED" > "$OUT_DIR/logs/apktool-build.log" 2>&1
BUILD_EXIT=$?
set -e
if [[ "$BUILD_EXIT" -eq 0 && -s "$UNSIGNED" ]]; then
  set +e
  "$BUILD_TOOLS/zipalign" -f -p 4 "$UNSIGNED" "$ALIGNED" >> "$OUT_DIR/logs/apktool-build.log" 2>&1
  ALIGN_EXIT=$?
  set -e
  if [[ "$ALIGN_EXIT" -eq 0 && -s "$ALIGNED" ]]; then
    APKTOOL_BUILD_STATUS="success"
    echo "Unsigned round-trip only; signing is performed by the separate protected rebuild workflow." > "$OUT_DIR/analysis/signature-result.txt"
  else APKTOOL_BUILD_STATUS="alignment-failed"; fi
fi
printf '%s\n' "$APKTOOL_BUILD_STATUS" > "$OUT_DIR/analysis/apktool-build-status.txt"

if [[ -x "$BUILD_TOOLS/aapt" ]]; then "$BUILD_TOOLS/aapt" dump badging "$INPUT_APK" > "$OUT_DIR/analysis/aapt-badging.txt" 2>&1 || true; fi
set +e
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$INPUT_APK" > "$OUT_DIR/analysis/input-signature.txt" 2>&1
INPUT_SIGNATURE_STATUS=$?
set -e
if [[ "$INPUT_SIGNATURE_STATUS" -eq 0 ]]; then
  grep -m1 'Signer #1 certificate SHA-256 digest:' "$OUT_DIR/analysis/input-signature.txt" > "$OUT_DIR/analysis/input-certificate-sha256.txt" || true
else echo "Input certificate could not be verified by apksigner." > "$OUT_DIR/analysis/input-certificate-sha256.txt"; fi

JAVA_COUNT="$(find "$OUT_DIR/jadx-gradle-export" -type f -name '*.java' 2>/dev/null | wc -l | tr -d ' ')"
SMALI_COUNT="$(find "$OUT_DIR/apktool-project" -type f -name '*.smali' 2>/dev/null | wc -l | tr -d ' ')"
RESOURCE_COUNT="$(find "$OUT_DIR/apktool-project/res" -type f 2>/dev/null | wc -l | tr -d ' ')"
NATIVE_COUNT="$(find "$OUT_DIR/native-libs" -type f -name '*.so' 2>/dev/null | wc -l | tr -d ' ')"

cat > "$OUT_DIR/README.md" <<EOF
# APK reverse-engineering workspace
Generated from the APK you supplied.

## Contents
- apktool-project/: editable Smali, decoded AndroidManifest.xml, resources and assets. This is the closest reconstruction and the round-trip build input.
- jadx-gradle-export/: readable best-effort Java/Gradle reconstruction. Decompiled Java is not the original source and may need repair before it compiles.
- native-libs/: extracted lib/*.so files. Native binaries are not converted back into original C/C++ source.
- analysis/: input hash, package metadata and signing information.
- logs/: decoder, builder and signer logs.
- outputs/: an aligned unsigned round-trip APK when the project builds successfully.

## Results
- Apktool decode: success
- JADX export exit code: $JADX_STATUS
- Apktool round-trip: $APKTOOL_BUILD_STATUS
- Java files: $JAVA_COUNT
- Smali files: $SMALI_COUNT
- Resource files: $RESOURCE_COUNT
- Native libraries: $NATIVE_COUNT
- Input SHA-256: $INPUT_SHA256
- Apktool: $APKTOOL_VERSION
- JADX: $JADX_VERSION

## Edit and rebuild
Edit Smali/resources in apktool-project, then run the APK Factory workflow "Build Reconstructed APK" against the folder containing apktool.yml and AndroidManifest.xml. The separate build workflow signs the edited project with the existing APK Factory key. Java edits in jadx-gradle-export are best effort: original source, comments, names and build configuration may not be recoverable.

## Signing compatibility
An update requires the same package ID and an Android-accepted signing certificate. The original private key cannot be extracted from an APK. The APK Factory key does not automatically match the original app's certificate. Check analysis/input-signature.txt and the signature verification report from the build workflow before attempting an update. A different certificate normally requires uninstalling the old app first, which can remove local data.

## Limitations
- One complete APK is supported. Split APK sets, XAPK/APKS bundles, inaccessible backend services, and some heavily protected/obfuscated apps need extra work.
- JADX cannot restore the exact original project. Behavior may depend on APIs, servers, certificates, Play Integrity, licensing, native code or dynamic modules.
- Build success proves packaging, not app behavior. Test on an authorized emulator/device before distribution.
EOF

cat > "$OUT_DIR/analysis/result.txt" <<EOF
apktool_decode=success
jadx_export_exit_code=$JADX_STATUS
apktool_round_trip=$APKTOOL_BUILD_STATUS
java_source_files=$JAVA_COUNT
smali_files=$SMALI_COUNT
resource_files=$RESOURCE_COUNT
native_libraries=$NATIVE_COUNT
input_sha256=$INPUT_SHA256
apktool_version=$APKTOOL_VERSION
jadx_version=$JADX_VERSION
EOF

echo "Reverse engineering completed. JADX=$JADX_STATUS Apktool round-trip=$APKTOOL_BUILD_STATUS"
