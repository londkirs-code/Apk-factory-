---
name: build-notifai-apk
description: Build and publish the latest signed NotifAI APK as a stable standalone file in APK Factory, skip unnecessary builds when the repository APK is verified current, and return its direct download link.
---

# Skill: Build NotifAI APK through APK Factory

## Purpose
Repeatably build and deliver an up-to-date signed NotifAI APK. Prefer the established APK Factory workflow so updates remain compatible with the installed app.

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

## Fast path: check before building
1. Check the latest commit on `londkirs-code/nofitAI` main.
2. Check the repository's `downloads/NotifAI.apk` and `downloads/NotifAI-build-info.txt`.
3. Compare the APK's last Git modification timestamp with the latest source commit timestamp. A newer APK timestamp is only a freshness heuristic, not proof by itself.
4. Verify `source_sha` in build metadata equals the current source SHA and `external-build-state/nofitAI.sha` also matches. The workflow's skip guard additionally requires the APK commit timestamp to be newer than the source commit timestamp.
5. If all checks pass, **do not rebuild**. Return the stable direct APK URL immediately: https://raw.githubusercontent.com/londkirs-code/Apk-factory-/main/downloads/NotifAI.apk
6. If the APK is absent, the metadata/state is missing or mismatched, the source SHA changed, or the APK timestamp is not newer, trigger/allow the established workflow and wait for it to finish.

The workflow checks every 15 minutes and also supports manual dispatch. A manual dispatch intentionally forces a rebuild. When the workflow builds successfully, it replaces `downloads/NotifAI.apk` in the repository with the new signed APK and updates build metadata and the successful source-SHA marker in the same commit. The Actions artifact remains available as an additional backup.

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
- Freshness guard
- Validate and prepare existing signing key
- Set up Android SDK and Gradle
- AndroidX Fragment compatibility fix and runtime-version validation
- Set increasing versionCode and build signed Release APK
- Publish Actions artifact
- Publish standalone APK to `downloads/NotifAI.apk` and update metadata

If a run fails, inspect the first causal error and make the smallest targeted correction before retrying. Do not repeatedly trigger identical builds without a fix.

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
Reply in Hebrew, concise and action-oriented. If the stable APK is already current, simply give the direct APK link and say a rebuild was unnecessary. If a new build is needed, wait for success before claiming the APK was updated.
