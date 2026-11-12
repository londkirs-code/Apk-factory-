#!/usr/bin/env bash
set -eo pipefail
PROJECT_DIR="$1"
OUT_DIR="$2"
APKTOOL_JAR="$HOME/apktool_3.0.3.jar"
BUILD_TOOLS="$ANDROID_HOME/build-tools/36.0.0"
PROJECT_DIR="$(realpath "$PROJECT_DIR")"
mkdir -p "$OUT_DIR"/{logs,outputs,analysis}
[[ -s "$PROJECT_DIR/apktool.yml" && -s "$PROJECT_DIR/AndroidManifest.xml" ]] || { echo "::error::This is not a decoded Apktool project."; exit 1; }
[[ -s "$APKTOOL_JAR" ]] || { echo "::error::Pinned Apktool JAR is missing."; exit 1; }
[[ -x "$BUILD_TOOLS/apksigner" && -x "$BUILD_TOOLS/zipalign" ]] || { echo "::error::Android build-tools are missing."; exit 1; }
[[ -s "$APK_FACTORY_KEYSTORE" ]] || { echo "::error::APK Factory signing key was not configured; refusing to publish an unsigned release."; exit 1; }

UNSIGNED="$OUT_DIR/outputs/reconstructed-unsigned.apk"
ALIGNED="$OUT_DIR/outputs/reconstructed-aligned-unsigned.apk"
SIGNED="$OUT_DIR/outputs/reconstructed-apkfactory-signed.apk"
java -jar "$APKTOOL_JAR" b "$PROJECT_DIR" -o "$UNSIGNED" 2>&1 | tee "$OUT_DIR/logs/apktool-build.log"
test -s "$UNSIGNED"
"$BUILD_TOOLS/zipalign" -f -p 4 "$UNSIGNED" "$ALIGNED"
"$BUILD_TOOLS/apksigner" sign --ks "$APK_FACTORY_KEYSTORE" --ks-key-alias "$APK_FACTORY_KEY_ALIAS" \
  --ks-pass env:APK_FACTORY_STORE_PASSWORD --key-pass env:APK_FACTORY_KEY_PASSWORD \
  --out "$SIGNED" "$ALIGNED" 2>&1 | tee "$OUT_DIR/logs/apksigner.log"
test -s "$SIGNED"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$SIGNED" | tee "$OUT_DIR/analysis/output-signature.txt"
sha256sum "$SIGNED" | tee "$OUT_DIR/analysis/SHA256SUMS.txt"
echo "Build and APK Factory signing completed."
