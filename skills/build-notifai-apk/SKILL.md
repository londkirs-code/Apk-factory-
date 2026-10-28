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
3. Use the manual workflow dispatch, which intentionally forces a rebuild.
4. If no manual-dispatch action is available, use the supported fallback trigger: post the exact comment `/build-notifai` on issue #1, [APK Factory — הודעות על בנייה ועדכון APK](https://github.com/londkirs-code/Apk-factory-/issues/1). The workflow listens for this exact command and ignores all other issue comments. This is a real trigger attempt, unlike editing the workflow file.
5. Verify that a new workflow run appears after dispatch/comment before claiming it started. If the trigger does not create a run, diagnose the trigger/permissions configuration rather than editing the workflow repeatedly or saying the build started. If no supported trigger can be executed with available permissions, state that limitation plainly.
6. The repository's GitHub Contents API commits may not trigger Actions in some token/event configurations. Never rely on a workflow-file commit as the only way to force a build; use `workflow_dispatch` or the exact issue-comment fallback.
7. Wait for the run to complete. Inspect its status and relevant job steps/logs. If it fails, identify the first failed step and causal error, make the smallest targeted fix, and retry; do not rerun the same broken build unchanged.
8. Confirm the build used the latest `main` commit from `londkirs-code/nofitAI`.
9. Verify the successful signed APK artifact and the published `downloads/NotifAI.apk`; confirm the published metadata SHA-256 matches the file where possible.
10. Return the stable direct download URL only after verifying publication. If any check cannot be completed, state exactly what remains unverified and do not claim success.

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
## Failure diagnosis and prevention

- Distinguish a failed build from a failed trigger. A successful old run is not evidence that a new run started. A workflow-file commit is also not proof of dispatch.
- For run `40753948211` (2026-10-28), GitHub reported the build, signing, artifact upload, and standalone APK publication all succeeded. The subsequent issue was inability to verify/start a fresh run from the available dispatch interface—not a Gradle failure in that successful run.
- For future failures, inspect the latest run's job steps/logs first and fix the earliest causal error. Do not infer failure from a non-fatal native-library strip warning.
- Keep explicit builds forced: the freshness skip is allowed only for scheduled runs. Manual dispatch and the `/build-notifai` issue-comment trigger must build even when the source SHA matches the previous build.

## Build email notifications

- On build completion, the workflow comments on issue #1: https://github.com/londkirs-code/Apk-factory-/issues/1 and mentions the triggering GitHub account, matching APK Factory's established notification pattern.
- Success messages include the stable APK link, versionCode, source commit, and workflow-run logs. Failure messages include the run link and state that the previous APK must not be treated as newly built.
- GitHub sends email based on the account's notification/watch settings for that issue. Do not promise email delivery unless the user's GitHub notification subscription/settings support it; the workflow comment itself is the auditable notification record.
- The workflow needs `issues: write` permission for this notification step. Never add SMTP passwords or other mail secrets unless the repository is explicitly configured for a separate SMTP provider.
