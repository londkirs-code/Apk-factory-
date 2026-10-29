---
name: build-netzhachama-apk
description: Always perform a fresh signed build of the latest Netz Hachama source when requested, publish the APK as a stable standalone file inside APK Factory, and verify the published build before sharing the direct link.
---

# Skill: Build נץ החמה APK through APK Factory

## Canonical locations
- APK Factory: https://github.com/londkirs-code/Apk-factory-
- Source repository: https://github.com/londkirs-code/-
- Source branch: main
- Build workflow: `.github/workflows/build-netzhachama-signed.yml`
- Stable APK in repository: `downloads/NetzHachama.apk`
- Stable direct download: https://raw.githubusercontent.com/londkirs-code/Apk-factory-/main/downloads/NetzHachama.apk
- Build metadata: `downloads/NetzHachama-build-info.txt`
- Actions artifact: `NetzHachama-signed-APK`
- Build runs: https://github.com/londkirs-code/Apk-factory-/actions/workflows/build-netzhachama-signed.yml
- Manual trigger fallback: comment exactly `/build-netzhachama` on https://github.com/londkirs-code/Apk-factory-/issues/1

## Non-negotiable rules
1. An explicit request such as “תפעיל את הסקיל”, “תבנה APK”, “תעדכן”, or “תכין APK חדש” means start a fresh build immediately. Never answer by only explaining the process.
2. Do not skip a user-requested build because the source SHA, timestamps, or an existing APK appear current. Freshness skipping is allowed only for scheduled runs.
3. Never create or replace the signing key. Use the existing APK Factory secrets: `APK_SIGNING_KEY_BASE64`, `APK_SIGNING_STORE_PASSWORD`, `APK_SIGNING_KEY_ALIAS`, and `APK_SIGNING_KEY_PASSWORD`. Never print or expose secret values.
4. Preserve the app's package/application ID and signing certificate. Use the existing shared signing key so upgrades remain compatible.
5. Keep `versionCode` increasing; never reset it or publish a lower version.
6. A started workflow, a successful compile, or an uploaded artifact alone is not a successful delivery. The stable APK must be committed to the repository and metadata must match the exact run.
7. Never use an old APK or a temporary sandbox file as a substitute for the newly published repository APK.

## Required steps for every explicit APK request
1. Read this skill and the current workflow from GitHub.
2. Check the latest commit on source repository `londkirs-code/-:main`. This is the user's own repository, so there is no separate upstream-fork synchronization step unless the user explicitly changes the source relationship.
3. Trigger a fresh build. Prefer `workflow_dispatch` if a supported action exists. If not, post exactly `/build-netzhachama` to issue #1 in APK Factory. A workflow-file commit alone is not proof a run started.
4. Verify a new workflow run appeared after the trigger. If it did not, diagnose the trigger and permissions instead of repeatedly editing files or claiming it started.
5. Poll that exact run until it is completed. Inspect failed step names and logs if it fails; fix the earliest causal issue and retry. Do not stop while publishing is still in progress.
6. Confirm the workflow checked out the latest source commit and record its exact SHA.
7. Verify the existing signing key validation, Release build, APK signature verification, and Actions artifact upload succeeded.
8. Verify publication succeeded for `downloads/NetzHachama.apk` and `downloads/NetzHachama-build-info.txt`. Metadata must have the current `workflow_run_id`, `source_sha`, `version_code`, recent `published_utc`, and the SHA-256 of the exact published APK.
9. Re-read the metadata from remote `main`; its `workflow_run_id` must match the run being delivered. Confirm the APK path exists on remote `main`. Do not infer file availability from an artifact alone.
10. Return the stable direct APK URL and the exact workflow run URL only after all checks pass. State clearly that installation on a physical device was not tested unless it actually was.

## Stable repository publication
The workflow must publish a standalone APK tracked in Git, not only a 30-day Actions artifact. Each successful build replaces `downloads/NetzHachama.apk` with the newest signed APK and updates `downloads/NetzHachama-build-info.txt`. Keep the source SHA and build state in `external-build-state/netzhachama.sha`.
The publication step must use safe fetch/rebase/push retries and must never force-push. After pushing, fetch remote `main` and assert that remote metadata matches this run. If a real rebase conflict occurs, stop and diagnose rather than overwriting concurrent changes.
The workflow should notify issue #1 subscribers after completion with either the stable APK link and metadata (success) or the failed run link (failure). An issue comment is an auditable notification, not proof that an email reached the inbox.

## Response behavior
Reply in Hebrew, concise and action-oriented. Do not say “יש APK חדש” until build, signature, artifact, stable repository publication, and metadata checks all pass. If a check cannot be completed, say exactly which one remains unverified and do not present the old stable link as newly built.
