# APK Reverse Engineering Studio

APK Factory includes an Actions-based reverse-engineering pipeline for APKs that you own or are explicitly authorized to inspect and modify.

## What it produces

The workflow **Reverse Engineer Android APK** accepts one complete APK at a direct HTTPS URL and creates a downloadable workspace:

- **apktool-project/** — Apktool's editable reconstruction: decoded manifest, resources, assets and Smali source. This is the most reliable route for rebuilding the same package layout after small changes.
- **jadx-gradle-export/** — JADX's best-effort readable Java/Gradle export. This is useful for understanding application logic and searching methods/classes. It is not the original source tree and may need significant repair before it compiles.
- **native-libs/** — safely extracted .so files, organized by ABI, for separate native analysis (for example with Ghidra).
- **analysis/** — input SHA-256, manifest/badging, signer information and result summaries.
- **logs/** — full Apktool, JADX and build logs.
- **outputs/** — an aligned unsigned Apktool round-trip APK when Apktool can rebuild the project.

JADX's Gradle export is also tested with a best-effort assembleDebug. The result is recorded as success, failure, or not available. A failed Gradle export does not discard the Apktool project.

## Start a reverse-engineering run

1. Open **Actions → Reverse Engineer Android APK → Run workflow**.
2. Enter a direct HTTPS URL to one complete APK. The URL may be a public raw GitHub URL or another HTTPS download URL.
3. Choose a short output slug (letters, numbers, underscore and hyphen).
4. Confirm both required checkboxes: you own the app or have permission to modify it, and you understand the artifact is public in this repository.
5. Optionally enable **Run MobSF static security analysis**. This downloads the large MobSF Docker image and adds a JSON security report; it is not dynamic analysis and does not run the app in an emulator.
6. Start the workflow, open its run, and download the apk-reverse-engineering artifact for your slug.

The owner can also use the existing issue-notification thread (issue #1) by posting one exact command:

    /reverse-engineer-authorized https://host/path/app.apk optional-slug

Only the repository owner can invoke this comment trigger. The command is an explicit authorization/public-artifact attestation. A workflow_dispatch run is available when the APK URL or analysis options need to be entered separately.

## Make changes and build again

For the most faithful reconstruction, edit files in **apktool-project/** (manifest, resources, assets and smali directories). Commit that folder into a GitHub repository/branch you control. Then:

1. Open **Actions → Build Reconstructed APK → Run workflow**.
2. Enter the repository, ref and project path containing both **apktool.yml** and **AndroidManifest.xml**.
3. For a private source repository, configure **SOURCE_REPO_TOKEN** in the APK Factory repository with read access to that source. Do not paste a token in an input field or source file.
4. Supply the input APK's signer SHA-256 fingerprint from **analysis/input-signature.txt** when you want the workflow to compare update compatibility.
5. Confirm the ownership/permission checkbox and the public-artifact checkbox, then start the workflow.
6. Download **reconstructed-signed-apk**. The APK is signed with the existing APK Factory key and its versionCode is incremented in the temporary build copy. The source project in your repository is not modified by the build.

The rebuild is split into two jobs. The first checks out the source and runs Apktool without signing secrets. Only after assembly succeeds does a separate job load the existing APK Factory key and sign the output.

## Signing and update compatibility

Android accepts an in-place update only when the package/application ID matches and the signing certificate is compatible with the installed application. APK Factory does not and cannot extract an app's original private signing key from an APK.

The final rebuild uses the existing APK_SIGNING_* repository secrets already used by APK Factory. It never generates or rotates a key. The workflow reports whether the resulting signer fingerprint matches the supplied original fingerprint:

- **true**: the certificate fingerprints matched; package ID and device policy still need to be checked.
- **false**: the output is signed with a different certificate, so Android will reject it as an update over that installation. Uninstalling the old app may be necessary and can erase its local data.
- **not-verified**: no original fingerprint was supplied; do not assume update compatibility.

A successful APK build is not proof that runtime behavior is correct. Install and test on an authorized emulator/device before distribution.

## Important public-repository warning

londkirs-code/Apk-factory- is public. GitHub Actions artifacts produced by this repository are therefore visible to people who can view the workflow run. The reconstruction includes decompiled code, resources and a copy of the input APK for reference. Do not upload proprietary, confidential, customer or otherwise private APKs here. Use a private copy of APK Factory with private Actions artifacts for confidential work. The required public-artifact checkbox is a deliberate guard, not a privacy setting.

Private source checkout is supported with SOURCE_REPO_TOKEN, but it does not make artifacts in this public repository private.

## Scope, limits and troubleshooting

- This pipeline takes one complete .apk. It does not yet accept an .aab, XAPK or a set of split APKs. For split apps, provide the complete base and all splits only after adding a dedicated bundle-handling process.
- JADX decompiles DEX bytecode into an approximation. Original Kotlin/Java source, comments, variable names, build scripts and exact project structure usually cannot be recovered.
- Apktool preserves a buildable Smali/resource representation, not the original Java/Kotlin source.
- Obfuscation, missing framework resources, encrypted assets, packers, Play Integrity, server-side logic, dynamic modules and native .so libraries can require manual work.
- Ghidra is a separate option for deeper native binary analysis; this workflow extracts .so files but does not claim to reconstruct their original C/C++ source.
- MobSF is optional static security analysis. Its findings need human review; the workflow does not perform runtime/dynamic analysis.
- A decoded project that round-trips successfully can still crash or behave differently. Review the build logs and test the resulting APK on a device/emulator.
- Use the first failing log under logs/ to diagnose errors. For missing vendor framework resources, install the appropriate framework package before decoding/building again.
- Respect the app's license, terms, access controls and applicable law. This workflow is intended for apps you own or have explicit permission to analyze and modify; it does not attempt to remove DRM, licensing checks, authentication or other access controls.
