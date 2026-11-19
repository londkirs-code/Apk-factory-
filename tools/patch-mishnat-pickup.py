from pathlib import Path

p = Path("apps/mishnat-pickup/app/src/main/java/org/mishnatyosef/pickup/MainActivity.java")
s = p.read_text()
s = s.replace("import android.app.Activity;", "import android.app.Activity;\nimport android.app.AlertDialog;")
s = s.replace("    private LinearLayout page;", "    private LinearLayout page;\n    private TextView progressPercent;\n    private Button resetButton;")
start = s.index("    private void showHomeScreen(boolean loadData) {")
end = s.index("\n    private void loadLatestOrder() {", start)
replacement = """    private void showHomeScreen(boolean loadData) {
        LinearLayout root = verticalRoot();
        root.setBackgroundColor(BG);
        LinearLayout sticky = new LinearLayout(this);
        sticky.setOrientation(LinearLayout.VERTICAL);
        sticky.setPadding(dp(16), dp(7), dp(16), dp(7));
        sticky.setBackgroundColor(Color.WHITE);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        progressText = text("נאספו 0 מתוך 0", 13, INK, true);
        head.addView(progressText, new LinearLayout.LayoutParams(0, -2, 1f));
        progressPercent = text("0%", 14, GREEN, true);
        head.addView(progressPercent);
        sticky.addView(head);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        if (Build.VERSION.SDK_INT >= 21) progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(4));
        bp.topMargin = dp(5);
        sticky.addView(progressBar, bp);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        resetButton = smallButton("איפוס איסוף", Color.WHITE, MUTED);
        resetButton.setOnClickListener(v -> confirmResetCollection());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, dp(29));
        rp.topMargin = dp(2);
        actions.addView(resetButton, rp);
        sticky.addView(actions);
        root.addView(sticky, new LinearLayout.LayoutParams(-1, -2));

        pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.setBackgroundColor(BG);
        page = verticalRoot();
        page.setPadding(dp(16), dp(16), dp(16), dp(24));
        pageScroll.addView(page);
        root.addView(pageScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        page.addView(text("רשימת איסוף", 25, INK, true));
        addTop(page, text("לוחצים על מוצר כדי להעביר אותו בין הרשימות", 13, MUTED, false), 3);
        messageText = text("", 13, MUTED, false);
        messageText.setVisibility(View.GONE);
        page.addView(messageText);
        addTop(page, sectionLabel("ממתינים לאיסוף", ""), 18);
        pendingList = verticalRoot();
        addTop(page, pendingList, 8);
        collectedSection = verticalRoot();
        collectedSection.addView(sectionLabel("נאספו", ""));
        collectedList = verticalRoot();
        addTop(collectedSection, collectedList, 8);
        addTop(page, collectedSection, 20);
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        Button logout = smallButton("התנתקות", Color.WHITE, MUTED);
        logout.setOnClickListener(v -> logout());
        bottom.addView(logout);
        addTop(page, bottom, 22);
        setContentView(root);
        if (loadData) loadLatestOrder(); else renderProducts(false);
    }

    private void confirmResetCollection() {
        if (collected.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("לאפס את האיסוף?")
                .setMessage("כל המוצרים יחזרו לרשימת הממתינים. ההזמנה באתר לא תשתנה.")
                .setNegativeButton("ביטול", (d, w) -> d.dismiss())
                .setPositiveButton("איפוס", (d, w) -> {
                    collected.clear();
                    saveCollectedState();
                    renderProducts(false);
                }).show();
    }
"""
s = s[:start] + replacement + s[end:]
old = """        if (progressText != null) progressText.setText("נאספו " + done + " מתוך " + products.size());
        if (progressBar != null) progressBar.setProgress(products.isEmpty() ? 0 : (int) Math.round(done * 100.0 / products.size()));
        if (scrollToCollected && pageScroll != null && collectedSection != null) {
            pageScroll.post(() -> pageScroll.smoothScrollTo(0, collectedSection.getTop()));
        }"""
new = """        int percent = products.isEmpty() ? 0 : (int) Math.round(done * 100.0 / products.size());
        if (progressText != null) progressText.setText("נאספו " + done + " מתוך " + products.size());
        if (progressPercent != null) progressPercent.setText(percent + "%");
        if (progressBar != null) progressBar.setProgress(percent);
        if (resetButton != null) resetButton.setEnabled(done > 0);"""
assert old in s
s = s.replace(old, new)
old = """        TextView check = text(isCollected ? "✓" : "", 21, Color.WHITE, true);
        check.setGravity(Gravity.CENTER);
        check.setBackground(shape(isCollected ? GREEN : Color.WHITE, 24, isCollected ? GREEN : Color.rgb(190, 198, 209)));
        row.addView(check, new LinearLayout.LayoutParams(dp(34), dp(34)));

        row.setOnClickListener(v -> {
            if (collected.contains(product.key)) {
                collected.remove(product.key);
                saveCollectedState();
                renderProducts(false);
                if (pageScroll != null) pageScroll.post(() -> pageScroll.smoothScrollTo(0, 0));
            } else {
                collected.add(product.key);
                saveCollectedState();
                renderProducts(true);
            }
        });"""
new = """        TextView check = text(isCollected ? "✓" : "＋", 22, isCollected ? GREEN : Color.rgb(174, 184, 197), true);
        check.setGravity(Gravity.CENTER);
        check.setBackgroundColor(Color.TRANSPARENT);
        row.addView(check, new LinearLayout.LayoutParams(dp(28), dp(40)));

        row.setOnClickListener(v -> {
            int oldScrollY = pageScroll == null ? 0 : pageScroll.getScrollY();
            if (collected.contains(product.key)) collected.remove(product.key);
            else collected.add(product.key);
            saveCollectedState();
            renderProducts(false);
            if (pageScroll != null) pageScroll.post(() -> pageScroll.scrollTo(0, oldScrollY));
        });"""
assert old in s
s = s.replace(old, new)
p.write_text(s)

gradle = Path("apps/mishnat-pickup/app/build.gradle")
g = gradle.read_text().replace("#E97828", "#6D28D9")
gradle.write_text(g)

# Final checklist UI and supplied launcher-icon integration.
import shutil

main_activity = Path("apps/mishnat-pickup/app/src/main/java/org/mishnatyosef/pickup/MainActivity.java")
ui = main_activity.read_text()

old_progress = """        progressText = text("נאספו 0 מתוך 0", 13, INK, true);
        head.addView(progressText, new LinearLayout.LayoutParams(0, -2, 1f));
        progressPercent = text("0%", 14, GREEN, true);
        head.addView(progressPercent);"""
new_progress = """        // Keep the Hebrew progress label at the far right and the larger
        // percentage at the far left, with deliberate breathing room.
        head.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        progressPercent = text("0%", 22, GREEN, true);
        progressPercent.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams percentParams = new LinearLayout.LayoutParams(-2, -2);
        percentParams.rightMargin = dp(18);
        head.addView(progressPercent, percentParams);
        progressText = text("נאספו 0 מתוך 0", 13, INK, true);
        progressText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        head.addView(progressText, new LinearLayout.LayoutParams(0, -2, 1f));"""
if old_progress not in ui:
    raise SystemExit("Could not find the progress-header block to reposition and enlarge the percentage.")
ui = ui.replace(old_progress, new_progress, 1)

old_marker = """        TextView check = text(isCollected ? "✓" : "＋", 22, isCollected ? GREEN : Color.rgb(174, 184, 197), true);
        check.setGravity(Gravity.CENTER);
        check.setBackgroundColor(Color.TRANSPARENT);
        row.addView(check, new LinearLayout.LayoutParams(dp(28), dp(40)));
"""
if old_marker not in ui:
    raise SystemExit("Could not find the per-item plus/check marker to remove.")
ui = ui.replace(old_marker, "", 1)
if "＋" in ui:
    raise SystemExit("A plus marker remains in the item-list UI after removal.")
main_activity.write_text(ui)

icon_source = Path("assets/mishnat-pickup/app-icon.webp")
if not icon_source.is_file() or icon_source.stat().st_size == 0:
    raise SystemExit("The supplied purple launcher icon asset is missing from the repository.")
icon_target = Path("apps/mishnat-pickup/app/src/main/res/drawable-nodpi/ic_app_launcher.webp")
icon_target.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(icon_source, icon_target)

manifest = Path("apps/mishnat-pickup/app/src/main/AndroidManifest.xml")
manifest_text = manifest.read_text()
import re
manifest_text, icon_count = re.subn(r'android:icon="[^"]+"', 'android:icon="@drawable/ic_app_launcher"', manifest_text, count=1)
if icon_count == 0:
    manifest_text = manifest_text.replace("<application", '<application android:icon="@drawable/ic_app_launcher"', 1)
round_text, round_count = re.subn(r'android:roundIcon="[^"]+"', 'android:roundIcon="@drawable/ic_app_launcher"', manifest_text, count=1)
if round_count == 0:
    manifest_text = manifest_text.replace('android:icon="@drawable/ic_app_launcher"', 'android:icon="@drawable/ic_app_launcher" android:roundIcon="@drawable/ic_app_launcher"', 1)
if 'android:icon="@drawable/ic_app_launcher"' not in manifest_text:
    raise SystemExit("Could not set the launcher icon to the uploaded purple app icon.")
manifest.write_text(manifest_text)
