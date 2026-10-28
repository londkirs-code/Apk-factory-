---
name: build-notifai-apk
description: Always attempt a fresh signed NotifAI APK build when requested, publish it as a stable standalone file in APK Factory, and return its direct download link.
---

# Skill: Build NotifAI APK through APK Factory

## Purpose
Whenever the user asks to build, update, recreate, or provide the latest NotifAI APK, **always attempt a fresh signed build**. Do not decide to skip a requested build based on timestamps, matching SHAs, metadata, or an existing APK. This rule exists because a source update can be missed by a stale or incomplete freshness check.

## Canonical repositories and workflow
- APK Factory: https://github.com/londkirs-code/Apk-factory-
- NotifAI source fork: https://github.com/londkirs-code/nofitAI
- Signing infrastructure reference: https://github.com/londkirs-code/base-64
- Workflow: `.github/workflows/build-nofitai-signed.yml`
- Stable standalone APK in repository: `downloads/NotifAI.apk`
- Direct APK URL: https://raw.githubusercontent.com/londkirs-code/Apk-factory-/main/downloads/NotifAI.apk
- Build metadata: `downloads/NotifAI-build-info.txt`
- Workflow artifact name: `NotifAI-signed-APK`
- Workflow runs: https://github.com/londkirs-code/Apk-factory-/actions/workflows/build-nofitai-signed.yml

Use the exact repository names above; do not confuse `nofitAI` with differently capitalized or similarly named repositories.

## Non-negotiable safety and compatibility rules
1. **Never generate a new signing key or replace the existing keystore.** Use APK Factory's existing signing secrets and alias. Never print, commit, or expose secret values.
2. Preserve the app's application ID/package name. Before claiming an in-place update is compatible, verify the APK package and signing certificate where feasible.
3. Ensure generated versionCode is higher than the last shipped build. Do not lower it or rely only on a display version string.
4. Never call a build successful just because a workflow started. Require successful build/signing, artifact publication, and repository APK publication.
5. Never provide a stale APK as the latest build. Tie each APK to its exact source commit via build metadata.
6. Do not expose signing secrets, keystore files, passwords, access tokens, or private credentials in logs or responses.

## Mandatory build behavior when the user asks for an APK
1. **A command such as “תפעיל את הסקיל”, “תפעיל בנייה ידנית”, “תבנה APK”, or any equivalent explicit instruction means: start a new build immediately.** Do not interpret “run the skill” as merely reading, reviewing, or editing the skill.
2. Always initiate a fresh workflow run for each explicit request to build/update/recreate the APK, even if the existing APK appears current.
3. Use the manual workflow dispatch, which intentionally forces a rebuild. If dispatch is unavailable, use an equivalent safe trigger that starts the workflow; do not claim that a build was started unless a run is verifiably present.
4. If the manual-dispatch API/action is not available in the connected tools, immediately use a supported equivalent trigger that causes a new workflow run; verify the run appears in Actions before saying it started. If no trigger can be executed with available permissions, state that limitation plainly and provide the workflow page as the next step—never pretend that editing the skill or workflow itself is the same as dispatching a run.
5. Wait for the run to complete. Inspect its status and relevant job steps/logs. If it fails, identify the first causal error, make the smallest targeted fix, and retry instead of repeatedly running the same broken build.
6. Confirm the build used the latest `main` commit from `londkirs-code/nofitAI`.
7. Verify the successful signed APK artifact and the published `downloads/NotifAI.apk`; confirm the published metadata SHA-256 matches the file where possible.
8. Return the stable direct download URL only after verifying publication. If any check cannot be completed, state exactly what remains unverified and do not claim success.

## Automatic scheduled freshness checks
The workflow may continue checking every 15 minutes and use its freshness guard to avoid unnecessary scheduled builds. **That optimization must never override an explicit user request**: an explicit request always requires a forced fresh build.

## Preserve and validate signing
The expected secrets are referenced only by name:
- `APK_SIGNING_KEY_BASE64`
- `APK_SIGNING_STORE_PASSWORD`
- `APK_SIGNING_KEY_ALIAS`
- `APK_SIGNING_KEY_PASSWORD`

Do not read or reveal their values. If key validation fails, diagnose the workflow/secrets safely and stop before replacing or regenerating any key.

## Monitor and diagnose builds
Wait for a completed workflow and inspect the relevant steps:
- Checkout shared signing configuration and latest NotifAI source
- Freshness guard (must not skip a user-requested build)
- Validate and prepare existing signing key
- Set up Android SDK and Gradle
- AndroidX Fragment compatibility fix and runtime-version validation
- Set increasing versionCode and build signed Release APK
- Publish Actions artifact
- Publish standalone APK to `downloads/NotifAI.apk` and update metadata

Known failure patterns:
- Source may not include `gradlew`; install/use compatible Gradle.
- AndroidX Fragment / Activity Result lint failure; verify the resolved runtime dependency.
- Shell quoting or line-continuation failures; validate Bash/YAML.
- Release packaging failures; inspect the full `:app:packageRelease` exception.
- Signing validation failures; verify configured secrets and alias without exposing values.
- Native library strip warnings are not automatically fatal.

## Verify and deliver
- For a successful build, verify the stable repository APK exists, is non-empty, and matches `sha256` in build metadata.
- Prefer the stable direct APK link over the temporary Actions artifact ZIP: https://raw.githubusercontent.com/londkirs-code/Apk-factory-/main/downloads/NotifAI.apk
- Include the successful workflow run link if useful.
- State clearly if physical-device installation was not tested.
- Never reuse an old sandbox path unless it exists and is verified in the current session.

## Response style
Reply in Hebrew, concise and action-oriented. If a new build is requested, wait for success before claiming the APK was updated.