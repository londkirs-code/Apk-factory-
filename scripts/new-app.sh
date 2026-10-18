#!/usr/bin/env bash
set -euo pipefail
APP_ID="${1:-}"
PACKAGE="${2:-}"
if [[ ! "$APP_ID" =~ ^[a-zA-Z0-9_-]+$ ]]; then
  echo "Usage: $0 <app-id> <com.example.package>"; exit 2
fi
if [[ ! "$PACKAGE" =~ ^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$ ]]; then
  echo "Package must look like com.example.myapp"; exit 2
fi
if [[ -e "apps/$APP_ID" ]]; then echo "apps/$APP_ID already exists"; exit 1; fi
mkdir -p apps
cp -R templates/android-basic "apps/$APP_ID"
PACKAGE_PATH="${PACKAGE//./\/}"
mkdir -p "apps/$APP_ID/app/src/main/java/$PACKAGE_PATH"
mv "apps/$APP_ID/app/src/main/java/com/example/myapp/MainActivity.java" "apps/$APP_ID/app/src/main/java/$PACKAGE_PATH/MainActivity.java"
rm -rf "apps/$APP_ID/app/src/main/java/com"
find "apps/$APP_ID" -type f \( -name '*.java' -o -name '*.xml' -o -name '*.gradle' \) -print0 |
  xargs -0 sed -i "s/com\.example\.myapp/$PACKAGE/g"
sed -i "s/rootProject.name = \"AndroidApp\"/rootProject.name = \"$APP_ID\"/" "apps/$APP_ID/settings.gradle"
sed -i "s/My App/$APP_ID/g" "apps/$APP_ID/app/src/main/AndroidManifest.xml"
# Every new app starts with its own change log, so documentation is part of creation.
cp templates/APP_CHANGELOG_TEMPLATE.md "apps/$APP_ID/CHANGELOG.md"
sed -i "s/{{APP_ID}}/$APP_ID/g; s/{{PACKAGE}}/$PACKAGE/g; s/YYYY-MM-DD/$(date +%F)/" "apps/$APP_ID/CHANGELOG.md"
echo "Created apps/$APP_ID/CHANGELOG.md from the documentation template"
echo "Created apps/$APP_ID"
echo "Commit the files, then run Actions → Build Android APKs."
