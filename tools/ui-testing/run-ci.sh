#!/usr/bin/env bash
# Runs inside the booted emulator (or with any adb device connected).
# Tests one or more apps and writes results under $UI_OUT_ROOT/<app>/<run-name>/.
# Never exits non-zero for test failures: writes $UI_OUT_ROOT/status (passed|failed)
# so later workflow steps can still publish screenshots before failing the job.
#
# Environment:
#   UI_APPS             space-separated app ids (folders under apps/ or downloads/)
#   UI_MODE             auto | scenario | explore | both     (default auto)
#   UI_SCENARIO         optional scenario path (overrides auto-discovery)
#   UI_EXPLORE_ACTIONS  max taps in explore mode (default 30)
#   UI_APK_URL          optional HTTPS URL of the APK (single app only)
#   UI_APK_DIR          optional directory with downloaded build artifact APKs
#   UI_LOCALE           optional locale, e.g. he-IL
#   UI_OUT_ROOT         output root (default ui-test-results)
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DRIVER="$ROOT/tools/ui-testing/ui_driver.py"
OUT_ROOT="${UI_OUT_ROOT:-ui-test-results}"
MODE="${UI_MODE:-auto}"
EXPLORE_ACTIONS="${UI_EXPLORE_ACTIONS:-30}"
mkdir -p "$OUT_ROOT"
OUT_ROOT="$(cd "$OUT_ROOT" && pwd)"
SUMMARY="$OUT_ROOT/summary.md"
STATUS=passed

log() { printf '\n=== %s\n' "$*"; }

adb wait-for-device
until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do sleep 2; done

if [[ -n "${UI_LOCALE:-}" ]]; then
  if [[ "$UI_LOCALE" =~ ^[a-z]{2,3}(-[A-Z]{2})?$ ]]; then
    log "Switching emulator locale to $UI_LOCALE"
    adb root >/dev/null 2>&1 || true
    sleep 2
    adb wait-for-device
    adb shell "setprop persist.sys.locale $UI_LOCALE; setprop ctl.restart zygote" || true
    sleep 5
    adb wait-for-device
    until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do sleep 2; done
    sleep 5
  else
    echo "::warning::Ignoring invalid locale '$UI_LOCALE'"
  fi
fi

resolve_apk() {
  local app="$1" lower candidate
  lower="$(printf '%s' "$app" | tr '[:upper:]' '[:lower:]')"
  if [[ -n "${UI_APK_URL:-}" ]]; then
    candidate="$OUT_ROOT/.apk/$app.apk"
    mkdir -p "$OUT_ROOT/.apk"
    [[ "$UI_APK_URL" =~ ^https:// ]] || { echo "apk_url must be HTTPS" >&2; return 1; }
    curl -fsSL --retry 3 -o "$candidate" "$UI_APK_URL" || return 1
    echo "$candidate"; return 0
  fi
  local dirs=()
  [[ -n "${UI_APK_DIR:-}" ]] && dirs+=("$UI_APK_DIR/$app" "$UI_APK_DIR/downloads/$app")
  dirs+=("$ROOT/downloads/$app")
  for d in "${dirs[@]}"; do
    for name in app.apk "$app.apk"; do
      [[ -s "$d/$name" ]] && { echo "$d/$name"; return 0; }
    done
    if [[ -d "$d" ]]; then
      candidate="$(find "$d" -maxdepth 1 -name '*.apk' ! -name 'app-debug.apk' | sort | head -n1)"
      [[ -n "$candidate" ]] && { echo "$candidate"; return 0; }
    fi
  done
  # Stable single-file downloads such as downloads/NotifAI.apk
  candidate="$(find "$ROOT/downloads" ${UI_APK_DIR:+"$UI_APK_DIR"} -maxdepth 1 -iname "$lower.apk" 2>/dev/null | head -n1)"
  [[ -n "$candidate" ]] && { echo "$candidate"; return 0; }
  return 1
}

scenarios_for() {
  local app="$1"
  if [[ -n "${UI_SCENARIO:-}" ]]; then
    echo "$ROOT/$UI_SCENARIO"; return
  fi
  find "$ROOT/apps/$app/ui-tests" "$ROOT/ui-tests/$app" -maxdepth 1 -name '*.json' 2>/dev/null | sort
}

{
  echo "# UI test results"
  echo
  echo "| App | Run | Result | Steps | Screenshots | Crashes |"
  echo "|---|---|---|---|---|---|"
} > "$SUMMARY"

run_driver() {
  local app="$1" name="$2" apk="$3"; shift 3
  local out="$OUT_ROOT/$app/$name" rc
  mkdir -p "$out"
  log "$app :: $name"
  local pkg_args=()
  [[ -n "${APP_PACKAGE:-}" ]] && pkg_args=(--package "$APP_PACKAGE")
  python3 "$DRIVER" --apk "$apk" --app "$app" --out "$out" "${pkg_args[@]}" "$@" 2>&1 | tee "$out/driver.log"
  rc=${PIPESTATUS[0]}
  # Remember the detected package so later runs of the same app (already installed) reuse it.
  if [[ -z "${APP_PACKAGE:-}" && -f "$out/report.json" ]]; then
    APP_PACKAGE="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["package"])' "$out/report.json" 2>/dev/null || true)"
  fi
  if [[ -f "$out/report.json" ]]; then
    python3 - "$out/report.json" "$app" "$name" >> "$SUMMARY" <<'PY'
import json, sys
r = json.load(open(sys.argv[1], encoding="utf-8"))
res = "✅ passed" if r["passed"] else "❌ failed"
print(f"| {sys.argv[2]} | [{sys.argv[3]}]({sys.argv[2]}/{sys.argv[3]}/report.md) | {res} | {len(r['steps'])} | {len(r['screenshots'])} | {len(r['crashes'])} |")
PY
  else
    echo "| $app | $name | ❌ setup error (exit $rc) | – | – | – |" >> "$SUMMARY"
  fi
  [[ $rc -eq 0 ]] || STATUS=failed
}

for app in ${UI_APPS:-}; do
  if ! [[ "$app" =~ ^[A-Za-z0-9_-]+$ ]]; then
    echo "::error::Invalid app id: $app"; STATUS=failed; continue
  fi
  if ! APK="$(resolve_apk "$app")"; then
    echo "::error::No APK found for $app (looked in build artifact, downloads/$app/, downloads/$app.apk)"
    echo "| $app | – | ❌ APK not found | – | – | – |" >> "$SUMMARY"
    STATUS=failed; continue
  fi
  echo "APK for $app: $APK"
  APP_PACKAGE=""
  mapfile -t SCENARIOS < <(scenarios_for "$app")
  case "$MODE" in
    explore) SCENARIOS=() ;;
    scenario)
      if [[ ${#SCENARIOS[@]} -eq 0 ]]; then
        echo "::error::No scenario for $app (expected apps/$app/ui-tests/*.json or ui-tests/$app/*.json)"
        echo "| $app | – | ❌ no scenario | – | – | – |" >> "$SUMMARY"
        STATUS=failed; continue
      fi ;;
  esac
  for sc in "${SCENARIOS[@]}"; do
    [[ -f "$sc" ]] || { echo "::error::Scenario not found: $sc"; STATUS=failed; continue; }
    run_driver "$app" "$(basename "$sc" .json)" "$APK" --scenario "$sc"
  done
  if [[ "$MODE" == "explore" || "$MODE" == "both" || ( "$MODE" == "auto" && ${#SCENARIOS[@]} -eq 0 ) ]]; then
    run_driver "$app" "explore" "$APK" --explore "$EXPLORE_ACTIONS"
  fi
done

echo "$STATUS" > "$OUT_ROOT/status"
log "Overall: $STATUS"
cat "$SUMMARY"
exit 0
