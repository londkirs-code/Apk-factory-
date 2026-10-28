---
name: build-notifai-apk
description: Build the latest NotifAI Android APK through APK Factory using its existing signing key, diagnose build failures, verify the artifact, and return a working direct download link.
---

# Skill: Build NotifAI APK through APK Factory

## Purpose

Repeatably build and deliver an up-to-date signed NotifAI APK. Prefer the existing APK Factory workflow over ad-hoc local builds so updates remain compatible with the installed app.

## Canonical repositories and workflow

- APK Factory: https://github.com/londkirs-code/Apk-factory-
- NotifAI source fork: https://github.com/londkirs-code/nofitAI
- Signing infrastructure reference: https://github.com/londkirs-code/base-64
- Workflow: `.github/workflows/build-nofitai-signed.yml`
- Expected workflow artifact name: `NotifAI-signed-APK`
- Workflow runs: https://github.com/londkirs-code/Apk-factory-/actions/workflows/build-nofitai-signed.yml

Use the exact repository names above; do not confuse `nofitAI` with differently capitalized or similarly named repositories.

## Non-negotiable safety and compatibility rules

1. **Never generate a new signing key or replace the existing keystore.** The workflow must use APK Factory's existing signing secrets and alias. Never print, copy into chat, commit, or expose secret values.
2. Preserve the app's application ID/package name. Before claiming an in-place update is compatible, verify the APK uses the expected package and same signing certificate as the existing signing configuration where feasible.
3. Ensure the generated versionCode is higher than the last shipped build. Do not lower it or rely only on a display version string.
4. Never call a build successful based only on a workflow starting. Require completed workflow conclusion `success`, successful build/signing and artifact-upload steps, and a non-empty APK inside the artifact.
5. Never provide a stale artifact as the latest build. Tie the APK to the exact source commit checked out by the workflow.
6. Do not expose signing secrets, keystore files, passwords, access tokens, or private credentials in logs or responses.

## Procedure

### 1. Check current source and workflow

- Read the current default-branch commit of `londkirs-code/nofitAI`.
- Read `.github/workflows/build-nofitai-signed.yml` in APK Factory.
- Inspect recent workflow runs and identify the latest successful run, the source commit it built, the artifact, and the versionCode/version information if available.
- If the latest source commit is already successfully built and its artifact is still downloadable, reuse that exact artifact rather than rebuilding needlessly.
- Otherwise trigger the established APK Factory workflow. If it has no manual trigger, use the repository's supported trigger mechanism; do not make unrelated source changes merely to force a run. Any trigger-only change must be minimal, clearly documented, and committed to the workflow repository.

### 2. Preserve and validate signing

Confirm the workflow validates and prepares the existing APK Factory signing key. The expected secrets are referenced only by name in workflow configuration:
- `APK_SIGNING_KEY_BASE64`
- `APK_SIGNING_STORE_PASSWORD`
- `APK_SIGNING_KEY_ALIAS`
- `APK_SIGNING_KEY_PASSWORD`

Do not read or reveal their values. If key validation fails, diagnose the workflow/secrets safely and stop before replacing or regenerating any key.

### 3. Monitor the whole build

Wait until the run is completed and inspect every important step, especially:
- Checkout APK Factory shared signing configuration
- Checkout latest NotifAI source
- Skip-if-already-built guard
- Validate and prepare existing signing key
- Set up Android SDK and Gradle
- AndroidX Fragment compatibility fix and runtime-version validation
- Set increasing versionCode and build signed Release APK
- Publish signed APK artifact
- Record successfully built source commit

If the run fails, retrieve the complete failed-step logs, identify the first causal error, make the smallest targeted correction in the workflow or source, and run again. Do not repeatedly trigger identical builds without a fix.

## Known historical failure patterns and mitigations

- **Missing Gradle wrapper:** source checkout may not include `gradlew`. Install/use a compatible Gradle distribution in the workflow rather than assuming a wrapper exists.
- **AndroidX Fragment / Activity Result lint failure:** ensure the actual resolved runtime Fragment dependency is compatible with Activity Result APIs. Verify the resolved dependency version from Gradle output; do not trust a textual dependency declaration alone.
- **Shell quoting/line continuation failure:** carefully validate Bash/YAML quoting and continuation syntax in the compatibility patch step.
- **Release packaging failure:** inspect the full `:app:packageRelease` exception and the first relevant cause; do not assume it is the same as a lint failure.
- **Signing-key validation failure:** verify the configured secrets and alias without exposing values; retain the original key.
- **Missing artifact:** ensure the workflow uploads the actual signed Release APK, not only logs or an unsigned/intermediate build.

Warnings about native libraries being packaged without stripping are not automatically fatal. Base decisions on the final job conclusion and actual build error.

## 4. Verify the artifact before delivery

- Fetch artifacts for the completed successful run and select the exact `NotifAI-signed-APK` artifact.
- Download the artifact ZIP using the GitHub Actions artifact download tool.
- Inspect the ZIP and confirm it contains the expected `.apk` file. If possible, validate file size, package/application ID, versionCode, and APK signature. If the artifact publishes a checksum, compare it against the APK's checksum.
- If the user needs a direct APK link, extract the APK into the current conversation's file workspace and verify the exact output path exists before linking it.
- If a raw APK direct link cannot be produced, give the real GitHub Actions run/artifact page and explicitly state that it downloads a ZIP; do not label a ZIP as an APK.
- Do not reuse a `sandbox:` path from a previous session unless the file is present and verified in the current session.

## 5. Final response

Reply in Hebrew, concise and action-oriented. Include:
- A direct clickable APK download link, only if the file was created and verified in the current session.
- The successful GitHub Actions run link.
- Confirmation that the workflow used the existing APK Factory signing configuration.
- Any remaining limitation, such as not having installed the APK on the user's physical device.

Never claim the app was device-tested unless it actually was. An APK built with the existing signing key is intended to support an in-place update only if its package ID matches and the installed app was signed with that same key.

## Current known-good reference (as of 2026-10-28)

- Successful APK Factory run #22: https://github.com/londkirs-code/Apk-factory-/actions/runs/40659314923
- Run ID: `40659314923`
- Artifact ID: `12802274064`
- Artifact: `NotifAI-signed-APK`
- Artifact size reported by GitHub: `15,749,560` bytes
- The workflow's signing validation, Gradle setup, AndroidX Fragment compatibility/version check, signed Release build, artifact publication, and source-commit recording all completed successfully.

Treat this as historical reference only. Always check the latest source and latest run before reusing it.
