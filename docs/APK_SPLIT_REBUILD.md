# APK Split Rebuild and Signing

The reverse-engineering workspace retains the original APK set under `input-apks/`. For a multi-APK input, **Rebuild and Sign Reverse-Engineered APK** rebuilds the edited base Apktool project and separately decodes/rebuilds every original split. The workflow can now also start directly from a downloadable package URL, without requiring a previous reverse-engineering run.

## Start from a direct download URL

1. Open **Actions → Rebuild and Sign Reverse-Engineered APK → Run workflow**.
2. Enter `source_url` with a direct HTTPS download URL to an APK, APKM, XAPK, APKS, or ZIP containing the complete APK set. Leave `source_run_id` empty. Alternatively, leave `source_url` empty and provide the run ID from a previous reverse-engineering job.
3. Set a safe `project_slug` and provide an original certificate fingerprint only when using a previous workspace. For a direct URL, the workflow verifies every downloaded APK signature and automatically captures the original signer fingerprint.
4. Confirm ownership/permission and public-artifact disclosure, then start the run.

The URL is downloaded in the GitHub Actions runner (maximum 1 GiB, HTTPS redirects only). The package normalizer safely extracts the APK set and OBB files, checks package-ID consistency, and the workflow checks that all original split APKs are signed by the same certificate before attempting reconstruction. The signed output is still produced with the configured APK Factory key; automatic source-certificate detection does not recover that private key or make a different key update-compatible. This is intended only for apps you own or are explicitly authorized to modify. Do not use this rebuild-and-sign path to create modified copies of third-party proprietary apps without permission.

## Split-set rebuild guarantees

- Every APK in the original base + split set must be present; a multi-APK build does not silently fall back to base-only output.
- All components receive the same incremented numeric `versionCode` in both `apktool.yml` and `AndroidManifest.xml`.
- The package/application ID and version code are checked with Android `aapt` after each rebuild.
- Every rebuilt APK is zip-aligned, then all components are signed in the isolated signing job with the existing APK Factory keystore.
- The signing job verifies every APK with `apksigner` and requires one identical certificate across the complete set.
- The output includes the signed APK files, a transport ZIP, a SHA-256 manifest, and `install-with-adb.sh` for atomic split installation via `adb install-multiple -r`.

The transport ZIP is not an Android App Bundle `.aab` and is not a Play Store `.apks` archive. Install all APK components together as one install session; do not install the base APK alone.

## Update compatibility

The original certificate fingerprint is compared with the APK Factory signing certificate. A match is necessary (along with the same package ID and valid version progression) for an in-place update. A mismatch is explicitly reported as `false`; Android will reject an update over an installation signed by another key. APK Factory cannot recover the original private key from an APK. No workflow should claim update compatibility when the original fingerprint is absent or differs.

## Important limitations

Apktool must successfully decode and rebuild every split. Some vendor-specific split layouts, encrypted/protected assets, unsupported resources, dynamic feature dependencies, or signing schemes may prevent reconstruction. The workflow fails rather than publishing a partial split set. A successful package/signature check is not a runtime test; install the full set on an authorized test device and verify behavior.
