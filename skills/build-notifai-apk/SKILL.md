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


## Lessons from the 2026-10-28 run — mandatory operational checklist

This section is a permanent rule, not optional advice.

### What actually happened in run 40760523413
- The workflow was triggered successfully by posting `/build-notifai` to issue #1 because the available GitHub connector did not expose a direct `workflow_dispatch` action. That is a limitation of the available action interface, **not proof that the user has not granted GitHub access**.
- Run #31 completed successfully. The signed Release build, artifact upload, standalone APK publication, and notification step all succeeded.
- Published metadata now reports:
  - `workflow_run_id=40760523413`
  - `version_code=31`
  - `source_sha=61c688ed64a426648cefe72f10a8953196457cae`
  - `published_utc=2026-10-28T20:11:20Z`
  - `sha256=f26d07cf330a1775031f2422256b8972cdbeaaab9ed7b0056c0d874b787f4465`
- The artifact `NotifAI-signed-APK` exists for that run (15,709,909 bytes), the tracked `downloads/NotifAI.apk` exists, and the success notification comment was posted to issue #1.
- Therefore, the delay/error in the conversation was **not a Gradle/build failure**. The assistant checked while the workflow was still publishing, then answered too early that publication was not yet confirmed. Do not repeat that premature stop: continue polling and re-check all final evidence before replying.

### Access: diagnose the exact missing capability, never make a blanket claim
- The configured GitHub connector can read repository files, inspect runs and jobs, read artifacts metadata, create issue comments, and commit file changes where its enabled actions permit.
- A missing direct `workflow_dispatch` operation means only that this particular action is unavailable through the current tool interface. It does not mean repository access is absent. Use the tested `/build-notifai` issue-comment fallback and verify that it created a new run.
- When the user asks to change the skill, edit `skills/build-notifai-apk/SKILL.md` in this repository using the GitHub file update action, then re-read the committed file and report the commit. Do not merely explain what should be changed.
- When a requested GitHub operation is unavailable, identify the precise unavailable operation and use another supported route where possible. Do not tell the user to configure access again unless an actual permission/API error demonstrates that access is missing.
- Do not confuse tool discovery or a missing operation with a 403/401 permission failure. Record the exact error and endpoint/action before diagnosing permissions.

### Mandatory wait-and-verify gate — do not respond mid-publication
After starting a build:
1. Poll the exact run until `status=completed`; do not stop at the Release build or artifact-upload step.
2. Confirm the run conclusion is `success`, and these steps all succeeded: signed Release build, `Publish signed APK artifact`, `Publish standalone APK to repository`, and `Notify APK Factory issue subscribers about the NotifAI build`.
3. Re-read `downloads/NotifAI-build-info.txt` from the default branch. Its `workflow_run_id` must equal the run just checked, its `version_code` must be newer than the prior shipped version, and `published_utc` must be recent.
4. Confirm `downloads/NotifAI.apk` exists on the default branch and its GitHub file/blob SHA changed with the publication commit. The metadata SHA-256 is calculated by the workflow from the exact APK bytes immediately before commit. Do not claim an independent binary re-hash unless the binary was actually downloaded and hashed.
5. Confirm the Actions artifact exists for the same run and the success comment appears on issue #1. A comment is proof the notification step ran, **not** proof an email reached the inbox; email delivery depends on GitHub settings.
6. If the metadata still shows an older run, keep checking the publication step/run. Do not provide the direct link as the newly updated build until metadata matches. If a step fails, retrieve logs and fix the earliest causal error.
7. Only after these checks, answer in Hebrew with the direct APK URL and the run URL. State plainly that physical-device installation has not been tested unless it was actually tested.

### Learn from the successful Mishnat Yosef APK workflow
- Reuse the same proven APK Factory pattern: checkout source, use the existing shared APK Factory signing key, build a signed Release APK, upload a named Actions artifact, copy the APK to a stable repository path, write metadata/checksum, commit/push, and notify subscribers.
- Never generate a new key for convenience. A successful Gradle build alone is not delivery; stable-file publication and metadata verification are required.
- If a build takes several minutes, the user-facing response should reflect actual stage evidence. It is acceptable to give a brief accurate interim status, but not to say the APK is unavailable without polling the workflow through completion.

## Mandatory upstream synchronization before every requested APK build

This ordering is required. **Building from the fork's current files without first completing a successful upstream sync is a process failure, even if the APK build itself succeeds.**

Canonical source relationship:
- Upstream source of truth: https://github.com/ofirver-png/nofitAI (branch `main`)
- User's fork to synchronize: https://github.com/londkirs-code/nofitAI (branch `main`)
- Fork sync workflow: `.github/workflows/sync-and-build.yml` in `londkirs-code/nofitAI`
- APK Factory signed build workflow: `.github/workflows/build-nofitai-signed.yml` in `londkirs-code/Apk-factory-`

For every explicit user request to build/update NotifAI, perform these stages in this exact order:

1. **Start a fresh upstream-sync run first.** Trigger the fork's `Sync nofitAI source from upstream` workflow using `workflow_dispatch` if available. If no dispatch action is available, use a supported trigger that causes that workflow to run (for example, a harmless temporary file commit outside `.github/workflows/sync-and-build.yml`, because the workflow's push trigger ignores only its own workflow file). Do not start the APK build yet.
2. **Wait for the sync workflow to finish.** Verify its run is `completed` with conclusion `success`. A run merely starting, a scheduled run from earlier, or a commit being created is not sufficient.
3. **Verify fork HEAD equals upstream HEAD.** Read the latest commit SHA of `ofirver-png/nofitAI:main` and `londkirs-code/nofitAI:main` after the sync has completed. They must match exactly. Also verify the successful sync log shows it fetched upstream `main` and completed the force-sync/push, or explicitly reports the fork already matched upstream. If SHAs do not match, diagnose and retry synchronization; do not build.
4. **Only then trigger a fresh signed APK build** in APK Factory. Never use an APK build that began before the verified sync as the requested result. The APK workflow must check out the newly synchronized fork HEAD.
5. **Tie the APK to the synchronized SHA.** After the build finishes, compare `source_sha` in `downloads/NotifAI-build-info.txt` with the exact fork/upstream SHA verified in step 3. They must match. If the source moved again during the build, repeat the sync/verify/build sequence against the new HEAD.
6. Complete all existing build validation requirements: successful signed Release build, artifact upload, publication of `downloads/NotifAI.apk`, current metadata/checksum, and success notification. Do not return the stable APK link as the newly updated APK until all checks pass.
7. If sync fails, **do not build**. Inspect the first failed sync step, fix the smallest causal problem, and rerun sync. Report no new APK until sync succeeds and the downstream build is verified.

### Required workflow hardening
- Keep the source-sync and APK-build workflows as separate stages with a strict sync-success gate between them.
- Prefer a single orchestration workflow that triggers/awaits sync and then builds only after SHA equality is verified, if reliable cross-repository dispatch permissions are available. Do not assume the default `GITHUB_TOKEN` can dispatch workflows in another repository; confirm the actual configured mechanism before relying on it.
- At minimum, the operator/skill must explicitly trigger and verify the sync workflow before every user-requested build. The 15-minute schedule is not a substitute for a fresh, verified sync on demand.
- Never interpret “the APK workflow succeeded” as proof it used current upstream source. The source SHA in the build metadata must equal the upstream SHA verified immediately before the build.
- In the final response, state both the synchronized source SHA and the APK build run. If the source synchronization could not be proven, clearly state that and do not present an old APK as fresh.
