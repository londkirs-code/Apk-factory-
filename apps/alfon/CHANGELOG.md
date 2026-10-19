# Alfon — Changelog

## Compatibility update (Android 5.0+ and Android 15+)
- Lowered minSdk to 21 (Android 5.0) to broaden device compatibility.
- Added Android 15+ edge-to-edge insets so the embedded HTML remains clear of status/navigation bars and display cutouts.
- Hardened the local WebView by disabling universal file-URL access and mixed content, and enabled Safe Browsing on supported Android versions.
- Preserved the existing bundled HTML, visual design, contact search, call/SMS, and contact-card sharing behavior.

## 1.1
- Keep the app as a minimal Android WebView wrapper that displays the bundled HTML file directly.
- No native redesign or extra app screens; HTML, styling, search, and contact behavior remain in the embedded HTML.

## 1.0
- Bundled the complete Alfon HTML and contact list locally for offline use.
- Enabled name/phone search and direct call and SMS actions.
- Added Android handling for contact-card export/share.
