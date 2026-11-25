---
name: ui-test-apk
description: Optionally verify an Android app end-to-end on an emulator - navigate, tap, type, take screenshots, detect crashes - and inspect the screenshots before reporting that a change works. Works for any app in APK Factory or any APK URL.
---

# Skill: UI-test an APK on an emulator (optional)

Use this skill when the user asks to check, test, verify or screenshot an app, or when you need visual proof that a UI change works. This step is **optional**: it adds about 4–8 minutes. Do not run it on every build unless the user asked for it or enabled `UI_TEST_AFTER_BUILD`.

Full reference (in Hebrew), covering actions, selectors and limitations: `docs/UI_TESTING.md`.

## Procedure
1. **Pick the APK.** The default is `downloads/<app>/app.apk` (or `downloads/<App>.apk`) on the branch being run. If you just built a new APK, wait until the build has published it. Otherwise pass `apk_url`.
2. **Write or update a scenario** in `ui-tests/<app>/<name>.json`. Prefer this over `apps/<app>/ui-tests/`, because changes under `apps/` trigger an APK rebuild on main.
   - Read the app's layouts and strings first, and select elements by real `text`, `id` or `desc`.
   - Add a `screenshot` step at every point the user cares about.
   - Use `optional: true` for dialogs that may not appear.
   - If you don't know the UI yet, run `mode=explore` first. Then read the `ui/*.xml` dumps and screenshots, and write the scenario from them.
3. **Trigger the test** in one of these ways:
   - Workflow dispatch of `.github/workflows/ui-test-apk.yml` with `app`, `mode` and an optional `scenario`.
   - Or post the exact comment `/ui-test <app> [auto|scenario|explore|both|path.json] [N]` on issue #1. This only works when posted from the owner's account.
   - Or push the scenario change to a development branch (not main). That tests the app automatically.
   - Then confirm that a new run appeared.
4. **Wait for the run to finish, then read the results.** They are published to the `ui-test-results` branch:
   - `latest/<app>.md`
   - `runs/<run-id>-<attempt>/summary.md`
   - `runs/<run-id>-<attempt>/<app>/<scenario>/report.md`, `report.json` and `screenshots/*.png`
   - `ui/*.xml` (dumps of the screen hierarchy)
   - `logcat.txt`, which is in the run artifact only

   Fetch the branch (`git fetch origin ui-test-results`) or read the files through the GitHub API. **Open the PNG files and look at them.** A passed status alone does not prove the screen looks right.
5. **If something is wrong** (failed step, crash in `report.json` under `crashes`, or a wrong-looking screenshot):
   - Inspect the failing step's error, its automatic `-FAILED` screenshot, and the UI dump.
   - Fix the app code, or the scenario if the selector was wrong.
   - Rebuild and test again.
   - Iterate, but never in blind loops: every retry must follow a concrete diagnosis.
6. **Report honestly.** Say "verified on emulator (API NN), see screenshots: <link>". Do not claim a physical-device test. Name the steps that were skipped or optional, and the limitations, such as Hebrew typing that needs ADBKeyBoard or Canvas or WebView screens.

## Safety rules
- Explore mode by default avoids destructive or outbound buttons (delete, send, pay, logout and their Hebrew equivalents). Do not remove that guard for apps that talk to real backends or real users.
- The repository is public, and so are the screenshots. Do not test with real personal data or credentials.
- Never commit screenshots to `main`. Results belong on the `ui-test-results` branch only. The publisher never force-pushes.
