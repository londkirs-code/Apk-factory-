---
name: build-mishnat-pickup-apk
description: Build and verify the standalone Mishnat Yosef pickup checklist app through APK Factory, using its persistent signing key and publishing a stable APK.
---

# Build משנת יוסף — רשימת איסוף

## Canonical paths
- APK Factory: `londkirs-code/Apk-factory-`
- App ID: `mishnat-pickup`
- Android package: `org.mishnatyosef.pickup`
- Unpacked app source at build time: `apps/mishnat-pickup/`
- Source archive in Git: `build-sources/mishnat-pickup.tar.gz`
- Build workflow: `.github/workflows/build-mishnat-pickup.yml`
- Published APK: `downloads/mishnat-pickup/app.apk`
- Direct APK URL: `https://raw.githubusercontent.com/londkirs-code/Apk-factory-/main/downloads/mishnat-pickup/app.apk`
- Build metadata: `downloads/mishnat-pickup/build-info.txt`
- Actions artifact: `Mishnat-Pickup-signed-APK`
- Manual fallback: comment exactly `/build-mishnat-pickup` on APK Factory issue #1.

## App behavior to preserve
- Independent Android app. Never modify `apps/mishnat-yosef/` as part of this app's work.
- Login uses the official Mishnat Yosef sign-in page and supports email/password and SMS one-time-code entry.
- Never save the account password. Store only the access token encrypted with Android Keystore.
- Load the latest non-cancelled order from `GET /api/orders`, then load it from `GET /api/orders/{id}`.
- The uploaded CRX v6.2 establishes the order detail shape: `order.products`, `order.items`, product `item_salesID`, `full_name`, `amount`, and `featured_image`; item details are linked by `item_salesID`.
- Product image URL format: `https://images.mishnatyosef.org/images/items/{featured_image}`.
- A tap anywhere on a product card toggles collection status. Collected products move out of the pending list into the bottom `נאספו` section. Tapping a collected card moves it back.
- Save checklist state separately per order ID. Do not change the website order or send any item update to the server.

## Build procedure
1. Read this skill, app README, changelog, Gradle file, manifest, and current Java source before editing.
2. Edit the unpacked app source, increment/document changes in Hebrew, and re-create the archive at `build-sources/mishnat-pickup.tar.gz`.
3. Preserve package `org.mishnatyosef.pickup` and the existing APK Factory signing configuration. Never create or replace the keystore or expose signing secrets.
4. For an explicit build request, post exactly `/build-mishnat-pickup` to issue #1. Verify a fresh run exists and wait until it completes.
5. Verify Gradle Release build, APK manifest package/version, signature verification and continuity with any previously published APK, Actions artifact upload, publication to `downloads/mishnat-pickup/app.apk`, matching build metadata, and the notification comment.
6. Do not provide the stable URL as a new build until publication and metadata are confirmed.
7. State clearly when physical-device installation or live-account authentication has not been tested.

## Security and testing
- Never ask the user to send a password or SMS code in chat.
- Never log, commit, or display access tokens.
- Treat failures in login, API refresh, order parsing, and product-image loading separately. Do not turn an API/schema failure into an empty successful checklist.
- A build succeeding does not prove that real-account login or actual pickup flow was tested.
