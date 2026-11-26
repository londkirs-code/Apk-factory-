package com.londkirs.chullin.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.londkirs.chullin.core.Completion;
import com.londkirs.chullin.core.DailyPage;
import com.londkirs.chullin.core.HebrewCalendar;
import com.londkirs.chullin.core.Masechet;
import com.londkirs.chullin.core.Masechtot;
import com.londkirs.chullin.core.Perakim;
import com.londkirs.chullin.data.Backup;
import com.londkirs.chullin.data.Catalog;
import com.londkirs.chullin.data.ProgressStore;
import com.londkirs.chullin.data.Settings;
import com.londkirs.chullin.data.TosafotImporter;
import com.londkirs.chullin.model.Amud;
import com.londkirs.chullin.model.AmudRecord;
import com.londkirs.chullin.model.MasechetInfo;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * הממסך הראשי: לימוד, סקירה ופרקים, בעיצוב של ה-HTML המקורי (צבעים, כרטיסים, פינות מעוגלות).
 * הכל נבנה בקוד. גופן השרת (Assistant / Frank Ruhl) מוחלף בגופן המערכת עם serif לכותרות.
 */
public class MainActivity extends Activity {

    private static final int REQ_IMPORT_TOS = 1;
    private static final int REQ_EXPORT = 2;
    private static final int REQ_IMPORT_BACKUP = 3;

    private Settings settings;
    private Catalog catalog;
    private MasechetInfo info;
    private List<Amud> amudim = new ArrayList<>();
    private Map<String, List<String>> tosDb;
    private ProgressStore store;
    private int curIdx;
    private String tab = "study";
    private LocalDate lastDay;
    private P p;

    private FrameLayout content;
    private LinearLayout bottomNav;
    private LinearLayout root;

    // ---------- מחזור חיים ----------

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        android.content.SharedPreferences prefs = getSharedPreferences(Backup.PREFS, MODE_PRIVATE);
        settings = Settings.fromJson(prefs.getString(Backup.SETTINGS_KEY, null));
        try {
            catalog = new Catalog(this);
            loadMasechet(settings.masechetKey);
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בטעינת הנתונים: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        buildShell();
        applyDaily();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (catalog != null && !LocalDate.now().equals(lastDay)) {
            applyDaily();
            render();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (store != null) store.save();
    }

    /** טוען מסכת: מאגר התוספות, העמודים והתקדמות המסכת. */
    private void loadMasechet(String key) throws IOException, org.json.JSONException {
        info = catalog.find(key);
        settings.masechetKey = info.key;
        tosDb = Catalog.loadTosafot(this, info);
        amudim = Masechet.build(info, tosDb);
        store = new ProgressStore(this, info.key);
        curIdx = Math.max(0, Math.min(store.getLastIdx(), amudim.size() - 1));
        saveSettings();
    }

    /** העמוד היומי לפי תאריך ההתחלה (אם הוגדר). */
    private void applyDaily() {
        lastDay = LocalDate.now();
        if (settings.startDate != null && !amudim.isEmpty()) {
            curIdx = DailyPage.indexFor(settings.startDate, lastDay, amudim.size());
            store.setLastIdx(curIdx);
            store.save();
        }
    }

    private void saveSettings() {
        try {
            getSharedPreferences(Backup.PREFS, MODE_PRIVATE).edit()
                    .putString(Backup.SETTINGS_KEY, settings.toJson().toString()).apply();
        } catch (org.json.JSONException ignored) {
            // ההגדרות בנויות מערכים בטוחים בלבד
        }
    }

    // ---------- בניית המסגרת ----------

    private void buildShell() {
        p = P.of(settings.theme == Settings.Theme.DARK);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        content = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(header(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setBackgroundColor(p.card);
        bottomNav.addView(navTab("לימוד", "study"), weighted());
        bottomNav.addView(navTab("סקירה", "overview"), weighted());
        bottomNav.addView(navTab("פרקים", "perakim"), weighted());
        root.addView(bottomNav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private LinearLayout header() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(16), dp(12), dp(16), dp(12));
        h.setBackgroundColor(p.card);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(info.heName, 22, true, p.text);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        titles.addView(title);
        titles.addView(text("חולין • עמוד יומי", 13, false, p.muted));
        h.addView(titles, weighted());
        h.addView(button("⚙", p.muted, p.bg, this::openSettings));
        h.setTag("header");
        return h;
    }

    private TextView navTab(String label, String id) {
        boolean on = id.equals(tab);
        TextView t = text(label, 15, true, on ? p.accentText : p.muted);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(14), dp(8), dp(14));
        t.setBackgroundColor(on ? p.accentBg : p.card);
        t.setOnClickListener(v -> {
            tab = id;
            render();
        });
        return t;
    }

    // ---------- רינדור ----------

    private void render() {
        if (content == null) return;
        content.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), dp(12), dp(12), dp(24));
        switch (tab) {
            case "overview": buildOverview(col); break;
            case "perakim": buildPerakim(col); break;
            default: buildStudy(col); break;
        }
        content.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < bottomNav.getChildCount(); i++) {
            TextView t = (TextView) bottomNav.getChildAt(i);
            boolean on = i == idxOfTab(tab);
            t.setTextColor(on ? p.accentText : p.muted);
            t.setBackgroundColor(on ? p.accentBg : p.card);
        }
    }

    private int idxOfTab(String t) {
        switch (t) {
            case "overview": return 1;
            case "perakim": return 2;
            default: return 0;
        }
    }

    private Amud amud() {
        return amudim.get(curIdx);
    }

    private AmudRecord rec(Amud a) {
        return store.get(a.ref, a.totalTos());
    }

    private boolean complete(Amud a, AmudRecord r) {
        return Completion.isComplete(settings.completionMode, settings.tosPct, a, r);
    }

    private String perekName(Amud a) {
        Perakim.Perek pk = Perakim.containing(Perakim.forKey(info.key), a.index);
        return pk == null ? "" : "פרק " + HebrewCalendar.hebrewLetters(pk.id) + "' - " + pk.name;
    }

    private void buildStudy(LinearLayout col) {
        Amud a = amud();
        AmudRecord r = rec(a);
        boolean done = complete(a, r);

        // כותרת העמוד
        LinearLayout card = card();
        TextView titleView = text(a.title, 26, true, p.text);
        titleView.setTypeface(Typeface.SERIF, Typeface.BOLD);
        card.addView(titleView);
        String pk = perekName(a);
        if (!pk.isEmpty()) card.addView(text(pk, 15, true, p.muted));
        card.addView(text("תאריך עברי: " + HebrewCalendar.fullHebrew(LocalDate.now()), 13, false, p.muted));
        col.addView(card, matchWrap());

        // עמוד יומי
        int today = dailyIndex();
        if (today >= 0 && today == curIdx) {
            col.addView(banner("📅 זה העמוד היומי להיום", p.accentBg, p.accentText, null), matchWrap());
        } else if (today >= 0) {
            col.addView(banner("↩ עבור לעמוד היומי", p.accent, 0xffffffff, () -> {
                curIdx = today;
                saveIdx();
                render();
            }), matchWrap());
        }

        // סטטוס
        String status = done ? "✓ עמוד גמור" : "טרם הושלם";
        int chz = Completion.chazaraOf(settings.completionMode, settings.tosPct, a, r);
        String cText = chz > 0 ? "חזרה " + chz : "";
        LinearLayout statusRow = row();
        statusRow.addView(badge(status, done ? p.doneBg : p.card, done ? p.doneText : p.muted));
        if (!cText.isEmpty()) statusRow.addView(badge(cText, p.accentBg, p.accentText));
        col.addView(statusRow, matchWrap());

        // גמרא ורש"י
        LinearLayout toggles = row();
        toggles.addView(toggle("גמרא", r.gemara, () -> change(a, r, () -> r.gemara = !r.gemara)), weighted());
        toggles.addView(toggle("רש\"י", r.rashi, () -> change(a, r, () -> r.rashi = !r.rashi)), weighted());
        col.addView(toggles, matchWrap());

        // תוספות
        LinearLayout tos = card();
        tos.addView(text("תוספות", 18, true, p.text));
        if (a.hasRealTos) {
            tos.addView(text("נלמדו " + r.checkedTosafot() + " מתוך " + a.totalTos()
                    + " (יעד: " + Masechet.requiredTosafot(a, settings.tosPct) + ")", 13, false, p.muted));
            LinearLayout quick = row();
            quick.addView(button("50% ראשונות", p.accent, 0xffffffff, () -> quickTos(a, r, "fifty")), weighted());
            quick.addView(button("100%", p.indigo, 0xffffffff, () -> quickTos(a, r, "all")), weighted());
            quick.addView(button("נקה", p.border, p.text, () -> quickTos(a, r, "clear")), weighted());
            tos.addView(quick, matchWrap());
            for (int i = 0; i < a.tosafot.size(); i++) {
                final int itemIdx = i;
                boolean on = r.tosafot[i];
                tos.addView(tosRow(i + 1, a.tosafot.get(i), on, () -> change(a, r, () -> r.tosafot[itemIdx] = !r.tosafot[itemIdx])), matchWrap());
            }
        } else {
            tos.addView(text(Masechet.NO_TOSAFOT_TEXT, 15, false, p.muted));
        }
        col.addView(tos, matchWrap());

        // חזרה
        LinearLayout chazara = row();
        chazara.addView(button("−", p.border, p.text, () -> stepChazara(a, r, -1)), weighted());
        chazara.addView(text("חזרה: " + chz, 16, true, p.text), weighted());
        chazara.addView(button("+", p.border, p.text, () -> stepChazara(a, r, 1)), weighted());
        col.addView(chazara, matchWrap());

        // הערות
        LinearLayout notesCard = card();
        notesCard.addView(text("הערות לעמוד", 15, true, p.text));
        EditText notes = new EditText(this);
        notes.setText(r.note);
        notes.setHint("כתוב הערה...");
        notes.setTextColor(p.text);
        notes.setHintTextColor(p.muted);
        notes.setMinLines(2);
        notes.setBackground(rounded(p.bg, p.border, 12));
        notes.setPadding(dp(10), dp(8), dp(10), dp(8));
        notes.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a1, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a1, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                r.note = s.toString();
                store.save();
            }
        });
        notesCard.addView(notes, matchWrap());
        col.addView(notesCard, matchWrap());

        // ניווט
        LinearLayout nav = row();
        nav.addView(button("הבא ←", p.border, p.text, () -> move(1)), weighted());
        nav.addView(button("בחר דף", p.accent, 0xffffffff, this::pickDaf), weighted());
        nav.addView(button("→ קודם", p.border, p.text, () -> move(-1)), weighted());
        col.addView(nav, matchWrap());
        col.addView(text("מקשים: 4 / ← הבא • 6 / → קודם • 1 גמרא • 2 רש\"י • 3 50% • 7 / 8 חזרה",
                12, false, p.muted), matchWrap());
    }

    private void buildOverview(LinearLayout col) {
        int total = amudim.size();
        int doneCount = 0;
        int tosLearned = 0;
        int tosTotal = 0;
        for (Amud a : amudim) {
            AmudRecord r = rec(a);
            if (complete(a, r)) doneCount++;
            tosLearned += r.checkedTosafot();
            tosTotal += a.totalTos();
        }
        int pct = total == 0 ? 0 : (int) Math.round(doneCount * 100.0 / total);
        LinearLayout stats = card();
        stats.addView(text("סקירה כללית", 20, true, p.text));
        stats.addView(text("גמורים: " + doneCount + " מתוך " + total + " (" + pct + "%)", 15, false, p.text));
        stats.addView(text("תוספות שנלמדו: " + tosLearned + " מתוך " + tosTotal, 15, false, p.text));
        col.addView(stats, matchWrap());

        LinearLayout legend = card();
        legend.addView(text("מקרא: לפי מספר החזרות", 14, true, p.text));
        legend.addView(text("ריק = לא נלמד • 1 = נלמד • 2 = חזרה אחת • 3 • 4+", 13, false, p.muted));
        col.addView(legend, matchWrap());

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout line = null;
        for (int i = 0; i < amudim.size(); i++) {
            if (i % 10 == 0) {
                line = row();
                grid.addView(line, matchWrap());
            }
            Amud a = amudim.get(i);
            AmudRecord r = rec(a);
            int chz = Completion.chazaraOf(settings.completionMode, settings.tosPct, a, r);
            final int idx = i;
            TextView cell = text(a.shortTitle, 12, chz > 0, tierText(chz));
            cell.setGravity(Gravity.CENTER);
            cell.setBackground(rounded(tierBg(chz), tierBorder(chz), 8));
            cell.setOnClickListener(v -> {
                curIdx = idx;
                saveIdx();
                tab = "study";
                render();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            line.addView(cell, lp);
        }
        col.addView(grid, matchWrap());
    }

    private void buildPerakim(LinearLayout col) {
        List<Perakim.Perek> list = Perakim.forKey(info.key);
        if (list.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("אין פרקים מוגדרים למסכת זו עדיין.", 15, false, p.muted));
            col.addView(empty, matchWrap());
            return;
        }
        for (Perakim.Perek pk : list) {
            LinearLayout card = card();
            int done = 0;
            int count = 0;
            for (int i = pk.start; i <= pk.end && i < amudim.size(); i++) {
                count++;
                if (complete(amudim.get(i), rec(amudim.get(i)))) done++;
            }
            int pct = count == 0 ? 0 : done * 100 / count;
            TextView head = text("פרק " + HebrewCalendar.hebrewLetters(pk.id) + "' - " + pk.name
                    + "  •  " + pct + "%", 17, true, p.text);
            head.setOnClickListener(v -> {
                expanded = expanded == pk.id ? -1 : pk.id;
                render();
            });
            card.addView(head, matchWrap());
            card.addView(text("גמורים: " + done + " מתוך " + count, 13, false, p.muted));
            if (expanded == pk.id) {
                for (int i = pk.start; i <= pk.end && i < amudim.size(); i++) {
                    Amud a = amudim.get(i);
                    AmudRecord r = rec(a);
                    final int idx = i;
                    LinearLayout line = row();
                    line.addView(text(a.title + (complete(a, r) ? "  ✓" : ""), 14, false, p.text), weighted());
                    line.addView(button("פתח", p.border, p.text, () -> {
                        curIdx = idx;
                        saveIdx();
                        tab = "study";
                        render();
                    }));
                    line.addView(button("תוספות", p.accent, 0xffffffff, () -> showTosDialog(a)));
                    card.addView(line, matchWrap());
                }
            }
            col.addView(card, matchWrap());
        }
    }

    private int expanded = -1;

    // ---------- פעולות ----------

    private void change(Amud a, AmudRecord r, Runnable mutation) {
        boolean was = complete(a, r);
        mutation.run();
        boolean now = Completion.syncAfterChange(settings.completionMode, settings.tosPct, a, r, was);
        store.save();
        if (now) celebrate();
        render();
    }

    private void quickTos(Amud a, AmudRecord r, String action) {
        if (!a.hasRealTos) return;
        if ("clear".equals(action)) {
            new AlertDialog.Builder(this).setMessage("האם למחוק את כל סימוני התוספות בעמוד זה?")
                    .setPositiveButton("מחק", (d, w) -> change(a, r, () -> clearTos(r)))
                    .setNegativeButton("ביטול", null).show();
            return;
        }
        change(a, r, () -> {
            if ("all".equals(action)) {
                for (int i = 0; i < r.tosafot.length; i++) r.tosafot[i] = true;
            } else {
                int half = (int) Math.ceil(a.totalTos() / 2.0);
                for (int i = 0; i < r.tosafot.length; i++) r.tosafot[i] = i < half;
            }
        });
    }

    private void clearTos(AmudRecord r) {
        for (int i = 0; i < r.tosafot.length; i++) r.tosafot[i] = false;
    }

    private void stepChazara(Amud a, AmudRecord r, int delta) {
        if (!complete(a, r)) {
            Toast.makeText(this, "החזרות מתחילות אחרי שהעמוד גמור", Toast.LENGTH_SHORT).show();
            return;
        }
        Completion.stepChazara(settings.completionMode, settings.tosPct, a, r, delta);
        store.save();
        render();
    }

    private void move(int delta) {
        int next = Math.max(0, Math.min(amudim.size() - 1, curIdx + delta));
        if (next == curIdx) return;
        curIdx = next;
        saveIdx();
        render();
    }

    private void saveIdx() {
        store.setLastIdx(curIdx);
        store.save();
    }

    private int dailyIndex() {
        if (settings.startDate == null || amudim.isEmpty()) return -1;
        return DailyPage.indexFor(settings.startDate, LocalDate.now(), amudim.size());
    }

    private void celebrate() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            long[] pattern = {0, 50, 40, 90};
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                v.vibrate(pattern, -1);
            }
        }
        Toast.makeText(this, "🎉 עמוד גמור!", Toast.LENGTH_SHORT).show();
    }

    private void pickDaf() {
        String[] labels = new String[amudim.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = amudim.get(i).title;
        new AlertDialog.Builder(this).setTitle("בחר דף").setItems(labels, (d, which) -> {
            curIdx = which;
            saveIdx();
            render();
        }).show();
    }

    private void showTosDialog(Amud a) {
        if (!a.hasRealTos) {
            Toast.makeText(this, Masechet.NO_TOSAFOT_TEXT, Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog[] holder = new AlertDialog[1];
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout quick = row();
        quick.addView(button("50%", p.accent, 0xffffffff, () -> { quickTos(a, rec(a), "fifty"); refresh(holder, a); }), weighted());
        quick.addView(button("הכל", p.indigo, 0xffffffff, () -> { quickTos(a, rec(a), "all"); refresh(holder, a); }), weighted());
        quick.addView(button("נקה", p.border, p.text, () -> { quickTos(a, rec(a), "clear"); refresh(holder, a); }), weighted());
        col.addView(quick, matchWrap());
        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        AmudRecord r = rec(a);
        for (int i = 0; i < a.tosafot.size(); i++) {
            final int itemIdx = i;
            CheckBox cb = new CheckBox(this);
            cb.setText((i + 1) + ". " + a.tosafot.get(i));
            cb.setTextColor(p.text);
            cb.setChecked(r.tosafot[i]);
            cb.setOnCheckedChangeListener((btn, on) -> {
                if (rec(a).tosafot[itemIdx] == on) return;
                change(a, rec(a), () -> rec(a).tosafot[itemIdx] = on);
            });
            list.addView(cb, matchWrap());
        }
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));
        holder[0] = new AlertDialog.Builder(this).setTitle("תוספות • " + a.title).setView(col)
                .setPositiveButton("סיום", null).show();
    }

    private void refresh(AlertDialog[] holder, Amud a) {
        if (holder[0] != null) holder[0].dismiss();
        showTosDialog(a);
    }

    // ---------- הגדרות וגיבוי ----------

    private void openSettings() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(8), dp(16), dp(8));

        col.addView(text("ערכת נושא", 15, true, p.text), matchWrap());
        LinearLayout theme = row();
        theme.addView(button("בהיר", settings.theme == Settings.Theme.LIGHT ? p.accent : p.border,
                settings.theme == Settings.Theme.LIGHT ? 0xffffffff : p.text, () -> setTheme(Settings.Theme.LIGHT)), weighted());
        theme.addView(button("כהה", settings.theme == Settings.Theme.DARK ? p.accent : p.border,
                settings.theme == Settings.Theme.DARK ? 0xffffffff : p.text, () -> setTheme(Settings.Theme.DARK)), weighted());
        col.addView(theme, matchWrap());

        col.addView(text("תאריך התחלה ללימוד יומי", 15, true, p.text), matchWrap());
        LocalDate start = settings.startDate;
        col.addView(text(start == null ? "לא הוגדר" : HebrewCalendar.fullHebrew(start), 14, false, p.muted), matchWrap());
        LinearLayout dateRow = row();
        dateRow.addView(button("בחר תאריך", p.accent, 0xffffffff, this::pickStartDate), weighted());
        dateRow.addView(button("נקה", p.border, p.text, () -> {
            settings.startDate = null;
            saveSettings();
            applyDaily();
            render();
        }), weighted());
        col.addView(dateRow, matchWrap());

        col.addView(text("מצב השלמת עמוד", 15, true, p.text), matchWrap());
        LinearLayout modes = row();
        for (Completion.Mode m : Completion.Mode.values()) {
            boolean on = settings.completionMode == m;
            String label = m == Completion.Mode.FULL ? "מלא" : m == Completion.Mode.GR ? "גמרא+רש\"י" : "גמרא";
            modes.addView(button(label, on ? p.accent : p.border, on ? 0xffffffff : p.text, () -> {
                settings.completionMode = m;
                saveSettings();
                dismissAndRender();
            }), weighted());
        }
        col.addView(modes, matchWrap());

        col.addView(text("אחוז תוספות (למצב מלא): " + settings.tosPct + "%", 15, true, p.text), matchWrap());
        LinearLayout pcts = row();
        int[] options = {25, 50, 75, 100};
        for (int o : options) {
            boolean on = settings.tosPct == o;
            pcts.addView(button(o + "%", on ? p.accent : p.border, on ? 0xffffffff : p.text, () -> {
                settings.tosPct = o;
                saveSettings();
                dismissAndRender();
            }), weighted());
        }
        col.addView(pcts, matchWrap());

        col.addView(text("מסכת", 15, true, p.text), matchWrap());
        col.addView(button("החלף מסכת: " + info.heName, p.border, p.text, this::pickMasechet), matchWrap());
        col.addView(button("ייבוא קובץ תוספות למסכת " + info.heName, p.border, p.text, this::pickTosFile), matchWrap());

        col.addView(text("גיבוי ושחזור (JSON)", 15, true, p.text), matchWrap());
        LinearLayout backup = row();
        backup.addView(button("ייצוא גיבוי", p.accent, 0xffffffff, this::exportBackup), weighted());
        backup.addView(button("שחזור", p.border, p.text, this::importBackupPick), weighted());
        col.addView(backup, matchWrap());
        col.addView(button("אפס את כל ההתקדמות", 0xffb91c1c, 0xffffffff, () ->
                new AlertDialog.Builder(this).setMessage("למחוק את כל ההתקדמות בכל המסכות? ההגדרות יישארו.")
                        .setPositiveButton("מחק", (d, w) -> {
                            List<String> keys = new ArrayList<>();
                            for (MasechetInfo m : catalog.all()) keys.add(m.key);
                            Backup.resetProgress(this, keys);
                            reloadAfterChange();
                        }).setNegativeButton("ביטול", null).show()), matchWrap());

        settingsDialog = new AlertDialog.Builder(this).setTitle("הגדרות").setView(wrapScroll(col))
                .setPositiveButton("סגור", null).show();
    }

    private AlertDialog settingsDialog;

    private ScrollView wrapScroll(View v) {
        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        return sv;
    }

    private void dismissAndRender() {
        if (settingsDialog != null) settingsDialog.dismiss();
        applyDaily();
        render();
        openSettings();
    }

    private void setTheme(Settings.Theme t) {
        settings.theme = t;
        saveSettings();
        recreate();
    }

    private void pickStartDate() {
        LocalDate d = settings.startDate != null ? settings.startDate : LocalDate.now();
        new DatePickerDialog(this, (view, y, m, day) -> {
            settings.startDate = LocalDate.of(y, m + 1, day);
            saveSettings();
            applyDaily();
            render();
        }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
    }

    private void pickMasechet() {
        List<MasechetInfo> all = catalog.all();
        String[] labels = new String[all.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = all.get(i).heName;
        new AlertDialog.Builder(this).setTitle("בחר מסכת").setItems(labels, (d, which) -> {
            try {
                store.save();
                loadMasechet(all.get(which).key);
                reloadAfterChange();
            } catch (Exception e) {
                Toast.makeText(this, "לא ניתן לטעון: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }).show();
    }

    private void pickTosFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_IMPORT_TOS);
    }

    private void exportBackup() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "chullin-backup.json");
        startActivityForResult(i, REQ_EXPORT);
    }

    private void importBackupPick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_IMPORT_BACKUP);
    }

    private void reloadAfterChange() {
        try {
            settings = Settings.fromJson(getSharedPreferences(Backup.PREFS, MODE_PRIVATE)
                    .getString(Backup.SETTINGS_KEY, null));
            loadMasechet(settings.masechetKey);
            applyDaily();
            render();
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (req == REQ_IMPORT_TOS) {
                String name = queryName(uri);
                String key = name == null ? null : Masechtot.keyFromFileName(name);
                if (key == null) key = info.key;
                TosafotImporter.Result r = TosafotImporter.importFromUri(this, uri, key);
                Toast.makeText(this, "יובאו תוספות למסכת " + Masechtot.heName(r.key)
                        + " (" + r.amudim + " עמודים)", Toast.LENGTH_LONG).show();
                if (r.key.equals(info.key)) reloadAfterChange();
            } else if (req == REQ_EXPORT) {
                List<String> keys = new ArrayList<>();
                for (MasechetInfo m : catalog.all()) keys.add(m.key);
                Backup.export(this, uri, keys);
                Toast.makeText(this, "הגיבוי נשמר", Toast.LENGTH_SHORT).show();
            } else if (req == REQ_IMPORT_BACKUP) {
                Backup.importBackup(this, uri);
                reloadAfterChange();
                Toast.makeText(this, "השחזור הסתיים", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String queryName(Uri uri) {
        android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
        if (c == null) return uri.getLastPathSegment();
        try {
            if (c.moveToFirst()) {
                int i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } finally {
            c.close();
        }
        return uri.getLastPathSegment();
    }

    // ---------- מקשים ----------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!"study".equals(tab)) return super.onKeyDown(keyCode, event);
        Amud a = amud();
        AmudRecord r = rec(a);
        switch (keyCode) {
            case KeyEvent.KEYCODE_4:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                move(1); return true;
            case KeyEvent.KEYCODE_6:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                move(-1); return true;
            case KeyEvent.KEYCODE_1:
                change(a, r, () -> r.gemara = !r.gemara); return true;
            case KeyEvent.KEYCODE_2:
                change(a, r, () -> r.rashi = !r.rashi); return true;
            case KeyEvent.KEYCODE_3:
                quickTos(a, r, "fifty"); return true;
            case KeyEvent.KEYCODE_7:
                stepChazara(a, r, 1); return true;
            case KeyEvent.KEYCODE_8:
                stepChazara(a, r, -1); return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    // ---------- כלי עזר לבנייה ----------

    private int tierBg(int chz) {
        if (chz <= 0) return p.tier0;
        if (chz == 1) return p.tier1;
        if (chz == 2) return p.tier2;
        if (chz == 3) return p.tier3;
        return p.tier4;
    }

    private int tierBorder(int chz) {
        return chz <= 0 ? p.border : p.accent;
    }

    private int tierText(int chz) {
        return chz <= 0 ? p.muted : p.text;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.setBackground(rounded(p.card, p.border, 16));
        LinearLayout.LayoutParams lp = matchWrap();
        c.setLayoutParams(lp);
        return c;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private LinearLayout banner(String msg, int bg, int fg, Runnable onClick) {
        TextView t = text(msg, 15, true, fg);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        t.setBackground(rounded(bg, bg, 12));
        if (onClick != null) t.setOnClickListener(v -> onClick.run());
        LinearLayout w = new LinearLayout(this);
        w.addView(t, matchWrap());
        return w;
    }

    private TextView badge(String s, int bg, int fg) {
        TextView t = text(s, 13, true, fg);
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        t.setBackground(rounded(bg, bg, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), dp(8));
        t.setLayoutParams(lp);
        return t;
    }

    private TextView toggle(String label, boolean on, Runnable action) {
        TextView t = text((on ? "✓ " : "") + label, 16, true, on ? 0xffffffff : p.text);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(14), dp(10), dp(14));
        t.setBackground(rounded(on ? p.doneBg : p.card, on ? p.done : p.border, 14));
        t.setOnClickListener(v -> action.run());
        return t;
    }

    private LinearLayout tosRow(int n, String dh, boolean on, Runnable action) {
        LinearLayout r = row();
        r.setPadding(dp(10), dp(10), dp(10), dp(10));
        r.setBackground(rounded(on ? p.accentBg : p.bg, p.border, 12));
        TextView box = text(on ? "✓" : "", 15, true, 0xffffffff);
        box.setGravity(Gravity.CENTER);
        box.setBackground(rounded(on ? p.accent : p.card, on ? p.accent : p.border, 8));
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
        r.addView(box);
        TextView label = text(dh, 17, true, on ? p.accentText : p.text);
        label.setPadding(dp(10), 0, 0, 0);
        r.addView(label, weighted());
        r.addView(text("#" + n, 12, false, p.muted));
        r.setOnClickListener(v -> action.run());
        return r;
    }

    private TextView button(String label, int bg, int fg, Runnable action) {
        TextView t = text(label, 15, true, fg);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(44));
        t.setPadding(dp(10), dp(10), dp(10), dp(10));
        t.setBackground(rounded(bg, bg == p.border ? p.border : bg, 12));
        t.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));
        t.setLayoutParams(lp);
        return t;
    }

    private TextView text(String s, float sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(1), stroke);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        return lp;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** פלטת הצבעים: זהה לזו של ה-HTML (dark slate ומצב בהיר). */
    private static final class P {
        int bg, card, border, text, muted, accent, accentText, accentBg, indigo;
        int done, doneText, doneBg, tier0, tier1, tier2, tier3, tier4;

        static P of(boolean dark) {
            P p = new P();
            if (dark) {
                p.bg = 0xff0f172a; p.card = 0xff1e293b; p.border = 0xff334155;
                p.text = 0xfff1f5f9; p.muted = 0xff94a3b8; p.accent = 0xff2563eb;
                p.accentText = 0xff93c5fd; p.accentBg = 0xff172554; p.indigo = 0xff4f46e5;
                p.done = 0xff059669; p.doneText = 0xff6ee7b7; p.doneBg = 0xff064e3b;
                p.tier0 = 0xff1e293b; p.tier1 = 0xff064e3b; p.tier2 = 0xff1e3a8a;
                p.tier3 = 0xff581c87; p.tier4 = 0xff7c2d12;
            } else {
                p.bg = 0xfff8fafc; p.card = 0xffffffff; p.border = 0xffcbd5e1;
                p.text = 0xff0f172a; p.muted = 0xff475569; p.accent = 0xff2563eb;
                p.accentText = 0xff1d4ed8; p.accentBg = 0xffeff6ff; p.indigo = 0xff4f46e5;
                p.done = 0xff10b981; p.doneText = 0xff065f46; p.doneBg = 0xffecfdf5;
                p.tier0 = 0xfff8fafc; p.tier1 = 0xffecfdf5; p.tier2 = 0xffeff6ff;
                p.tier3 = 0xfffaf5ff; p.tier4 = 0xfffff7ed;
            }
            return p;
        }
    }
}
