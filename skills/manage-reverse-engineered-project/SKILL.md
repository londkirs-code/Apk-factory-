# Skill: Manage Persistent APK Reverse-Engineering Projects

## Purpose
Maintain a durable, version-controlled workspace for each APK that the user owns or is authorized to modify. Do not restart from temporary files when a registered project already exists.

## Procedure
1. Check `apps/reverse-engineered/<slug>/project.json` and run:
   `python3 tools/reverse-engineering/project-manager.py validate <slug>`.
2. Use `apktool-project/` as the editable/buildable source of truth for Smali, manifest, resources and assets.
3. Use `jadx-gradle-export/` only to understand behavior and locate methods. Decompiled Java is approximate, not recovered original source.
4. Keep changes narrow, review the full affected Smali method, and commit one logical change at a time.
5. Never overwrite an existing persistent project during import. Create a new slug or explicitly review a separate migration plan.
6. Keep signing keys and passwords in GitHub Secrets only. Never copy a keystore into the repository, artifact, logs or chat.
7. Before rebuilding, validate the project, preserve package/application ID unless a distinct app is requested, increment versionCode only in the disposable build copy, and use the existing signed rebuild workflow.
8. Verify the output APK signature and compare its SHA-256 certificate fingerprint to the original. A different signer is not update-compatible. A missing original fingerprint means compatibility is unknown.
9. Report build success, signature verification, signer match, package/version checks, and runtime test status separately.

## Public repository safety
The current APK Factory repository is public. The persistent-project import workflow requires explicit confirmation that decompiled source and resources will become public. Never store confidential or proprietary apps there. Use a private APK Factory repository for private work.

## Importing an analyzed workspace
Run Actions → Save Reverse-Engineered APK as Persistent Project. Supply the exact successful reverse-engineering run ID and matching slug. The workflow imports editable source, JADX export, textual analysis and logs, but deliberately omits original APK/OBB binaries, generated APKs, ZIPs and native binary libraries. If the target folder already exists, import fails without overwriting it.

## Existing project commands
- List: `python3 tools/reverse-engineering/project-manager.py list`
- Inspect: `python3 tools/reverse-engineering/project-manager.py show <slug>`
- Validate: `python3 tools/reverse-engineering/project-manager.py validate <slug>`
