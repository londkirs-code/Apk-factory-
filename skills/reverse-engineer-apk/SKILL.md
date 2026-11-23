# Skill: Reverse Engineer and Modify an Android APK

## Purpose

Use APK Factory's reverse-engineering workflows to turn an APK the user owns or is explicitly authorized to modify into an editable reconstruction, inspect it, make focused changes, rebuild it and verify the resulting APK. This is a tool-assisted reconstruction workflow, not a promise to recover the original source.

## Required procedure

1. **Confirm lawful scope from the request.** Proceed only for an APK the user owns or has explicit permission to analyze and modify. Do not remove DRM, licensing, authentication, anti-cheat, payment or other access controls.
2. **Read the project instructions and this skill.** Inspect the current main state of the APK Factory workflows before invoking them.
3. **Run the Reverse Engineer Android APK workflow.**
   - Supply a direct HTTPS URL to one APK or a ZIP/XAPK/APKS bundle containing one base APK and its split APKs. The workflow validates package IDs, preserves every split and passes the full set to JADX; Apktool edits the base APK project; the split rebuild workflow separately rebuilds all original split APKs.
   - Use a safe project slug.
   - The workflow owner/comment trigger must provide the authorization and public-artifact confirmations.
   - Download the artifact from the exact successful run; do not use an older artifact.
4. **Register a permanent project workspace when future edits are expected.**
   - After a successful analysis run, use Actions → **Save Reverse-Engineered APK as Persistent Project**.
   - Supply that exact successful run ID and the same project slug used by the analysis workflow.
   - This repository is public. The workflow requires explicit confirmation that decompiled source/resources will be public; use a private APK Factory copy for confidential apps.
   - The workflow stores the Apktool project, JADX export, textual analysis and logs under `apps/reverse-engineered/<slug>/`. It refuses to overwrite an existing project and excludes APK/OBB binaries, generated APKs, ZIPs and native binary libraries.
   - Before subsequent edits, inspect `project.json` and run `python3 tools/reverse-engineering/project-manager.py validate <slug>`. Continue from the saved folder; do not re-decompile or start from a temporary artifact unless the user requests a fresh analysis.
5. **Inspect outputs before editing.**
   - Read analysis/result.txt, analysis/aapt-badging.txt, signer information and both tool logs.
   - Use jadx-gradle-export/ to understand class names, control flow, method calls and likely behavior.
   - Use apktool-project/ as the editable source of truth for manifest, resources, assets and Smali.
   - Inspect native-libs/ separately when native code matters. Do not claim that .so files were converted to C/C++.
   - Inspect analysis/jadx-ninepatch-repair.json; the reverse workflow repairs matching Nine-Patch images in the JADX export from Apktool resources before attempting Gradle compilation.
   - Record which parts decoded, which were reconstructed, and what failed.
5. **Choose the narrowest edit route.**
   - Prefer a resource/manifest edit for UI text, icons, theme resources or manifest metadata.
   - For executable changes, inspect the relevant JADX Java and corresponding Smali before patching. Smali is verbose and register changes are error-prone; keep patches small and inspect the full method.
   - If the requested feature is too extensive or the Gradle export is incomplete, recommend a clean Android project that recreates the permitted behavior instead of pretending a perfect source recovery occurred.
6. **Preserve project identity and behavior.**
   - Do not change the package/application ID unless the user explicitly asks for a distinct app.
   - Do not remove required permissions, signatures, license checks or authentication.
   - Keep original APK and analysis evidence unchanged. For bundles, retain all original files under input-apks/ and use analysis/split-inventory.tsv. Do not present a base-only rebuild as a complete split-set release.
   - Make edits in a working copy or a dedicated branch. Do not commit original private signing keys.
7. **Commit the edited Apktool project to a GitHub repository the user controls.** The build workflow needs a folder containing apktool.yml and AndroidManifest.xml. For a private source repository, use SOURCE_REPO_TOKEN in APK Factory; never expose the token in chat or code.
8. **Run Build Reconstructed APK.**
   - Provide repository, branch/ref and project path.
   - Provide the original signer SHA-256 from analysis/input-signature.txt when update compatibility matters.
   - The build workflow increments versionCode in its disposable checkout, assembles with Apktool and aligns the APK. When the source artifact contains multiple APKs, it rebuilds each split, enforces the same package ID and versionCode, signs every APK with the same existing APK Factory key, and validates the complete set.
   - A separate job signs the completed APK with the existing APK Factory signing key. Never create or rotate the key.
9. **Verify before reporting success.**
   - Confirm build-project succeeded, the signing job succeeded, apksigner verify succeeded, and the final artifact belongs to this run.
   - For split outputs, verify that every APK has the same signer, package ID and versionCode. The workflow packages all components and includes an adb install-multiple helper; never install only the base APK. Compare signer fingerprint with the original. false means it cannot update the installed app in place; not-verified means compatibility is unknown.
   - Compare package name and versionCode in badging.txt; ensure versionCode increased.
   - Report separately: decode success, Gradle export compile status, Apktool round-trip status, final signed APK status, signer compatibility status, and whether any real device/emulator runtime test was performed.
10. **Never claim more than the evidence supports.** Decompilation/packaging success is not runtime proof. Do not claim the original source was recovered or every feature works unless independently verified.

## Tool selection

- **JADX**: readable decompiled Java, navigation and best-effort Gradle export.
- **Apktool**: manifest/resource decode, assets, Smali disassembly and rebuildable project representation.
- **MobSF (optional)**: static security findings. Review findings manually; it is not dynamic testing.
- **Ghidra (optional)**: deeper native-library analysis after extracting .so binaries.
- **APK Factory Gradle importer**: use only when the source is already a real Gradle project. A JADX export may need repair and is not equivalent to the original project.

## User-facing honesty

Always explain that JADX cannot fully reconstruct original source, Apktool output is Smali/resources, native libraries are separate, and the APK Factory signing certificate will differ from the original unless it is the same certificate. Never guarantee direct update installation without a matching signature and package ID.


## AI integration boundary

These workflows produce and validate the reconstruction artifacts; they do not themselves call an LLM or include an autonomous AI chat/editor in the APK Factory app. The AI assistant must inspect the downloaded workspace, implement the requested targeted changes, and commit the edited project before the rebuild workflow. No model API key is consumed by the current workflows.


## Rebuild directly from a reverse-engineering Artifact

When the edited Apktool project remains in the workspace artifact and no source-repository commit is needed, use **Rebuild and Sign Reverse-Engineered APK**. Provide the successful reverse-engineering RUN_ID, exact PROJECT_SLUG, and original certificate fingerprint from analysis/input-signature.txt. Owner-triggered issue #1 command:

    /rebuild-reconstructed-authorized RUN_ID PROJECT_SLUG ORIGINAL_CERT_SHA256

The build workflow downloads the prior run artifact and copies apktool-project into a temporary workspace. For a split set it rebuilds every APK component, enforces one package ID and incremented versionCode, then passes the aligned set to a separate signing job that uses the existing APK Factory key. That job signs and verifies each component with the same certificate and publishes a complete split-set transport ZIP plus an adb install-multiple helper. Single APK inputs retain the single-APK path. Confirm the final artifact and apksigner report. Never call a build update-compatible unless the signer fingerprint matches the original and package ID is unchanged.
