# Hagaha signing and release checks

The Hagaha Android package must keep using the persistent APK Factory release key so later APKs can update the installed app.

## Required repository Actions secrets

- `APK_SIGNING_KEY_BASE64`
- `APK_SIGNING_STORE_PASSWORD`
- `APK_SIGNING_KEY_ALIAS`
- `APK_SIGNING_KEY_PASSWORD`

Never commit the keystore, its Base64 value, or any password to this repository.

## Mandatory preflight before every build

The workflow must complete all of these checks before Gradle starts:

1. Confirm all four secrets are present.
2. Remove accidental whitespace from the alias only.
3. Decode the Base64 keystore and confirm the file is non-empty.
4. Open the keystore with the store password and verify that the alias is a `PrivateKeyEntry`.
5. Perform a non-destructive certificate-request operation to verify the private-key password.
6. Calculate the certificate SHA-256 fingerprint and compare it with the trusted APK Factory fingerprint:
   `5C:53:56:16:2C:02:40:E5:C8:B3:3C:53:D2:49:DE:DE:C3:3C:25:AE:F1:7B:DE:EE:01:1C:2C:3F:83:6E:E8:71`.
7. After building, run `apksigner verify --print-certs` and confirm that the produced APK carries the same fingerprint before publishing it.

## Recovery policy

- Prefer restoring the existing APK Factory key and its correct secrets. Do not create a new key merely because a build failed.
- Never generate a temporary per-run key: it would make later updates incompatible.
- If the keystore, passwords, alias, or fingerprint do not validate, stop before Gradle and report the exact failed check. Restore the original secure backup and rerun preflight.
- A deliberate key rotation requires an explicit decision, secure backup of the new key, updating the trusted fingerprint, and documenting the compatibility impact before publishing.

## Verified current release

Build run: [44047451693](https://github.com/londkirs-code/Apk-factory-/actions/runs/44047451693)

The Hagaha APK passed the signing preflight and APK signature verification. Its certificate fingerprint matched the APK Factory key used by the successful NotifAI build.
