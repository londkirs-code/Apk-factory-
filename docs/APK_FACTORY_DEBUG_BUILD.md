# APK Factory — Debug rebuild automation

This workflow rebuilds an APK or complete split set from an authorized reverse-engineering workspace and signs the result with a **temporary DEBUG key generated for that run**.

## Workflow

- GitHub Actions: **Debug Build from Reverse-Engineered APK**
- File: `.github/workflows/debug-rebuild-reverse-engineered-apk.yml`
- Inputs: a direct HTTPS APK/APKM/XAPK/APKS/ZIP URL, or the run ID of a successful **Reverse Engineer Android APK** workflow, plus the exact project slug.
- Both ownership/permission and public-artifact confirmations are mandatory.
- The workflow rebuilds the base and every split APK, aligns them, generates an ephemeral JKS debug keystore, signs all components with the same key, verifies each signature and package/version consistency, and uploads the result as `debug-reconstructed-apk`.
- No APK Factory release signing secrets are read or used.

## Important DEBUG behavior

The debug keystore is generated inside the signing job and is not retained as a reusable signing identity. Each run receives a new certificate. Therefore:

- the result is a DEBUG-signed test build;
- it cannot update an installation signed by the original publisher or by a previous run's temporary key;
- installing it may require uninstalling the existing app first, which can erase local app data;
- all APK splits must be installed together; the workflow supplies a split-set archive and installation helper.

## Use

Open **Actions → Debug Build from Reverse-Engineered APK → Run workflow**. Supply either a direct HTTPS download URL or a successful reverse-engineering run ID and matching project slug. Confirm that you own the app or have explicit permission to modify it, and acknowledge that this public repository exposes its artifacts to repository viewers.

The issue-comment shortcut is available to the repository owner:

```
/debug-rebuild-authorized RUN_ID PROJECT_SLUG [ORIGINAL_CERT_SHA256]
```

## Limits

This is an automated Apktool reconstruction, not recovery of the original Java/Kotlin source tree. JADX output is an approximate readable export. Native libraries and runtime behavior may need separate work. A successful build and signature verification do not prove that the app launches or works; test on an authorized emulator/device.

Do not use this workflow to create modified builds of third-party applications unless you have explicit permission. Do not upload confidential APKs or data to this public repository.
