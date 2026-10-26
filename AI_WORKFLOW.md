# Required GitHub development workflow for Mishnat Yosef

Read this file at the start of every coding task in this repository, before editing any file.

## Before changing code
1. Read the current `main` branch state from GitHub. Treat `apps/mishnat-yosef/app/build.gradle` and `downloads/mishnat-yosef/update.json` as the authoritative version sources; never infer a version from chat history.
2. Read the relevant source methods and their callers, related UI components/styles, and the current `RELEASE_NOTES.md` and `CHANGELOG.md`. Search for every call site before changing a shared method.
3. Identify which workflows run for the files being changed. A failed unrelated workflow is not an APK build failure; inspect its name, status, and job/step logs before drawing conclusions.
4. Create/use a focused feature branch. Do not push application edits directly to `main`. Keep the patch limited to the requested behavior.

## Mandatory release-note gate for every app-source change

This rule is mandatory even for a tiny one-file fix, a revert, a visual adjustment, or a change that seems self-explanatory. Never commit a change to any `apps/mishnat-yosef/**/*.java` or `.kt` file until BOTH files below are updated in the same commit:
- `apps/mishnat-yosef/CHANGELOG.md`: prepend a dated entry describing what the new code actually does.
- `apps/mishnat-yosef/RELEASE_NOTES.md`: replace the current user-facing notes so they describe the behavior in the APK being built now, not a previous or reverted implementation.

Before committing, explicitly run the equivalent of:
`git diff --name-only BASE..HEAD`
and confirm that both documentation paths are in the same change range as the source edit. Then inspect the actual text of both documents against the final code diff. A file merely existing is not enough: its content must be accurate. In particular, when reverting a feature, remove/replace release-note claims that describe the feature being reverted; do not leave stale instructions or promises about behavior that no longer exists.

The repository preflight script `scripts/preflight-repository.sh` enforces that both documentation files are changed whenever Java/Kotlin app source changes. Treat this as a pre-commit requirement, not as a surprise CI check. Run/review the guard logic before pushing, and do not trigger an APK build until the guard is expected to pass.

If a workflow fails, inspect the failed job and exact failed step logs before explaining the cause. Distinguish preflight/metadata/documentation failures from compilation failures: if the build step was skipped, do not describe it as a Java/Gradle compile error and do not claim an APK was produced.

## Before opening or merging a PR
1. Review the full diff for accidental unrelated changes, duplicate declarations, malformed YAML, stale references, and UI behavior regressions.
2. For every user-visible app change, update `apps/mishnat-yosef/RELEASE_NOTES.md` (latest update only) and prepend a dated entry to `apps/mishnat-yosef/CHANGELOG.md`.
3. Run whitespace/source sanity checks. Verify all edited workflow YAML parses. Check that release notes have a title and meaningful details, and that `downloads/mishnat-yosef/update.json` is valid JSON.
4. Follow the actual UI flow and all callback paths. Prefer targeted updates that preserve existing view identity, focus, scroll, and state over rebuilding whole screens after a tap.
5. Open a PR with a concise description and explicit test evidence. Do not merge until the diff and available checks have been inspected.

## After merge / APK publishing
1. Inspect the specific **Build Android APKs** workflow run for the merged commit. Confirm the build, APK manifest version, signature continuity, artifact upload, and publishing steps succeeded.
2. Re-read `downloads/mishnat-yosef/update.json`. Only call an APK published when its `buildCommit`, `versionCode`, `versionName`, and checksum match the successful build and latest repository state.
3. The APK build workflow increments `versionCode` and `versionName` automatically. Do not manually guess or pre-announce the next version.
4. If any check fails, stop and inspect the failed step's logs before making another change. Avoid repeated blind commits or triggering builds for half-finished patches.
5. In the final user update, distinguish clearly between: code merged, build successful, and APK verified/published. Never claim a fix works on-device without a test or user confirmation.

## Workflow ownership
- `.github/workflows/build-apk.yml` builds and publishes the app APK.
- `.github/workflows/import-external-android-app.yml` is a separate manual utility for importing an unrelated external Android project. It must not run on ordinary pushes to this repository.
