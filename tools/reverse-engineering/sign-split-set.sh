#!/usr/bin/env bash
# Sign and validate every APK in a rebuilt split set using one existing APK Factory key.
set -euo pipefail
ALIGNED_DIR="${1:?usage: sign-split-set.sh ALIGNED_DIR OUTPUT_DIR [EXPECTED_ORIGINAL_CERT_SHA256]}"
OUT_DIR="${2:?missing output directory}"
EXPECTED_CERT="${3:-}"
BUILD_TOOLS="${ANDROID_HOME:?ANDROID_HOME is required}/build-tools/36.0.0"
APK_SIGNER="$BUILD_TOOLS/apksigner"
AAPT="$BUILD_TOOLS/aapt"
mkdir -p "$OUT_DIR"/{signed,analysis}
: "${APK_FACTORY_KEYSTORE:?Existing APK Factory keystore path required}"
: "${APK_FACTORY_KEY_ALIAS:?Existing APK Factory key alias required}"
: "${APK_FACTORY_STORE_PASSWORD:?Existing APK Factory store password required}"
: "${APK_FACTORY_KEY_PASSWORD:?Existing APK Factory key password required}"
mapfile -t FILES < <(find "$ALIGNED_DIR" -maxdepth 1 -type f -name '*.apk' -printf '%f\n' | sort)
(( ${#FILES[@]} >= 2 )) || { echo "::error::Expected a complete base + split set"; exit 1; }
: > "$OUT_DIR/analysis/signed-splits.tsv"
reference_package=""
reference_version=""
reference_cert=""
for filename in "${FILES[@]}"; do
  input="$ALIGNED_DIR/$filename"
  signed="$OUT_DIR/signed/$filename"
  "$APK_SIGNER" sign --ks "$APK_FACTORY_KEYSTORE" --ks-key-alias "$APK_FACTORY_KEY_ALIAS" \
    --ks-pass env:APK_FACTORY_STORE_PASSWORD --key-pass env:APK_FACTORY_KEY_PASSWORD \
    --out "$signed" "$input"
  "$APK_SIGNER" verify --verbose --print-certs "$signed" > "$OUT_DIR/analysis/$filename-signature.txt"
  cert="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$OUT_DIR/analysis/$filename-signature.txt" | head -n1 | tr -d ':' | tr '[:lower:]' '[:upper:]')"
  [[ -n "$cert" ]] || { echo "::error::Could not read signer certificate for $filename"; exit 1; }
  badging="$("$AAPT" dump badging "$signed")"
  package="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<< "$badging" | head -n1)"
  version="$(sed -n "s/^package:.*versionCode='\([0-9][0-9]*\)'.*/\1/p" <<< "$badging" | head -n1)"
  [[ -n "$package" && "$version" =~ ^[0-9]+$ ]] || { echo "::error::Cannot validate package/version for $filename"; exit 1; }
  if [[ -z "$reference_package" ]]; then
    reference_package="$package"; reference_version="$version"; reference_cert="$cert"
  fi
  [[ "$package" == "$reference_package" ]] || { echo "::error::Package ID differs across signed splits"; exit 1; }
  [[ "$version" == "$reference_version" ]] || { echo "::error::versionCode differs across signed splits"; exit 1; }
  [[ "$cert" == "$reference_cert" ]] || { echo "::error::Signer certificate differs across split APKs"; exit 1; }
  printf '%s\t%s\t%s\t%s\n' "$filename" "$package" "$version" "$cert" >> "$OUT_DIR/analysis/signed-splits.tsv"
done
EXPECTED_CERT="$(printf '%s' "$EXPECTED_CERT" | tr -d ':' | tr '[:lower:]' '[:upper:]')"
if [[ -n "$EXPECTED_CERT" && "$reference_cert" == "$EXPECTED_CERT" ]]; then
  compatibility="true"
elif [[ -n "$EXPECTED_CERT" ]]; then
  compatibility="false"
  echo "::warning::APK Factory signer differs from original app certificate; Android will reject in-place update."
else
  compatibility="not-verified"
  echo "::warning::Original certificate not supplied; update compatibility is unverified."
fi
printf 'package=%s\nversionCode=%s\nsplit_signer_sha256=%s\noriginal_signer_match=%s\n' \
  "$reference_package" "$reference_version" "$reference_cert" "$compatibility" > "$OUT_DIR/analysis/split-signing-result.txt"
# XAPK is a transport archive; Android installation must install the APK set as one session.
python3 - "$OUT_DIR/signed" "$OUT_DIR" "$reference_package" "$reference_version" <<'PY'
import json,sys,zipfile
from pathlib import Path
signed,out,pkg,version=Path(sys.argv[1]),Path(sys.argv[2]),sys.argv[3],int(sys.argv[4])
files=sorted(signed.glob("*.apk"))
manifest={"package_name":pkg,"version_code":version,"split_apks":[p.name for p in files]}
(out/"manifest.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
with zipfile.ZipFile(out/"reconstructed-signed-split-set.zip","w",zipfile.ZIP_DEFLATED) as z:
    z.write(out/"manifest.json","manifest.json")
    for p in files: z.write(p,p.name)
PY
cat > "$OUT_DIR/install-with-adb.sh" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)/signed"
command -v adb >/dev/null || { echo "adb is required"; exit 1; }
mapfile -t APKS < <(find "$DIR" -maxdepth 1 -type f -name '*.apk' | sort)
(( ${#APKS[@]} >= 2 )) || { echo "Complete split APK set not found"; exit 1; }
adb install-multiple -r "${APKS[@]}"
SH
chmod +x "$OUT_DIR/install-with-adb.sh"
sha256sum "$OUT_DIR"/signed/*.apk "$OUT_DIR/reconstructed-signed-split-set.zip" > "$OUT_DIR/analysis/SHA256SUMS.txt"
echo "All split APKs signed by one key and verified. Original update compatibility: $compatibility"
