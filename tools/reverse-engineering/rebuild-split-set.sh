#!/usr/bin/env bash
# Rebuild every APK in a split set. Signing is deliberately performed in a separate job.
set -euo pipefail
INPUT_SPLITS="${1:?usage: rebuild-split-set.sh INPUT_APKS BASE_PROJECT OUT_DIR}"
BASE_PROJECT="${2:?missing base Apktool project}"
OUT_DIR="${3:?missing output directory}"
APKTOOL_JAR="${APKTOOL_JAR:-$HOME/apktool_3.0.3.jar}"
BUILD_TOOLS="${ANDROID_HOME:?ANDROID_HOME is required}/build-tools/36.0.0"
AAPT="$BUILD_TOOLS/aapt"
ZIPALIGN="$BUILD_TOOLS/zipalign"
mkdir -p "$OUT_DIR"/{unsigned,aligned,work,analysis,logs}
[[ -s "$INPUT_SPLITS/base.apk" ]] || { echo "::error::base.apk is missing"; exit 1; }
[[ -s "$BASE_PROJECT/apktool.yml" && -s "$BASE_PROJECT/AndroidManifest.xml" ]] || { echo "::error::Edited base Apktool project is incomplete"; exit 1; }
[[ -s "$APKTOOL_JAR" && -x "$AAPT" && -x "$ZIPALIGN" ]] || { echo "::error::Apktool or Android build-tools are missing"; exit 1; }
mapfile -t ORIGINALS < <(find "$INPUT_SPLITS" -maxdepth 1 -type f -name '*.apk' -printf '%f\n' | sort)
(( ${#ORIGINALS[@]} >= 2 )) || { echo "::error::Split rebuild requires at least two APK files"; exit 1; }

BASE_BADGING="$("$AAPT" dump badging "$INPUT_SPLITS/base.apk")"
PACKAGE="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<< "$BASE_BADGING" | head -n1)"
OLD_VERSION="$(sed -n "s/^package:.*versionCode='\([0-9][0-9]*\)'.*/\1/p" <<< "$BASE_BADGING" | head -n1)"
[[ -n "$PACKAGE" && "$OLD_VERSION" =~ ^[0-9]+$ ]] || { echo "::error::Cannot read base package/versionCode"; exit 1; }
(( OLD_VERSION < 2100000000 )) || { echo "::error::versionCode reached Android maximum"; exit 1; }
NEW_VERSION=$((OLD_VERSION + 1))
printf 'package=%s\nprevious_versionCode=%s\nnew_versionCode=%s\n' "$PACKAGE" "$OLD_VERSION" "$NEW_VERSION" > "$OUT_DIR/analysis/version-report.txt"

for filename in "${ORIGINALS[@]}"; do
  stem="${filename%.apk}"
  project="$OUT_DIR/work/$stem"
  if [[ "$filename" == "base.apk" ]]; then
    cp -a "$BASE_PROJECT" "$project"
  else
    java -jar "$APKTOOL_JAR" d --force --all-src -o "$project" "$INPUT_SPLITS/$filename" > "$OUT_DIR/logs/decode-$stem.log" 2>&1
  fi
  python3 tools/reverse-engineering/set-apk-version.py "$project" "$NEW_VERSION"
  unsigned="$OUT_DIR/unsigned/$filename"
  aligned="$OUT_DIR/aligned/$filename"
  java -jar "$APKTOOL_JAR" b "$project" -o "$unsigned" > "$OUT_DIR/logs/build-$stem.log" 2>&1
  test -s "$unsigned"
  "$ZIPALIGN" -f -p 4 "$unsigned" "$aligned"
  "$AAPT" dump badging "$aligned" > "$OUT_DIR/analysis/$stem-badging.txt"
  actual_package="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" "$OUT_DIR/analysis/$stem-badging.txt" | head -n1)"
  actual_version="$(sed -n "s/^package:.*versionCode='\([0-9][0-9]*\)'.*/\1/p" "$OUT_DIR/analysis/$stem-badging.txt" | head -n1)"
  [[ "$actual_package" == "$PACKAGE" ]] || { echo "::error::$filename package ID changed"; exit 1; }
  [[ "$actual_version" == "$NEW_VERSION" ]] || { echo "::error::$filename versionCode $actual_version != $NEW_VERSION"; exit 1; }
  printf '%s\t%s\t%s\n' "$filename" "$actual_package" "$actual_version" >> "$OUT_DIR/analysis/rebuilt-splits.tsv"
done
{
  echo -e "filename\tpackage\tversionCode"
  cat "$OUT_DIR/analysis/rebuilt-splits.tsv"
} > "$OUT_DIR/analysis/rebuilt-splits-with-header.tsv"
rm "$OUT_DIR/analysis/rebuilt-splits.tsv"
echo "All ${#ORIGINALS[@]} APK splits rebuilt, aligned and validated at versionCode $NEW_VERSION."
