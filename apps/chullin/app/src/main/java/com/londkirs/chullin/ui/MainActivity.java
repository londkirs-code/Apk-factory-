package com.londkirs.chullin.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.londkirs.chullin.core.Completion;
import com.londkirs.chullin.core.DailyPage;
import com.londkirs.chullin.core.HebrewCalendar;
import com.londkirs.chullin.core.Masechet;
import com.londkirs.chullin.data.Catalog;
import com.londkirs.chullin.data.ProgressStore;
import com.londkirs.chullin.data.Settings;
import com.londkirs.chullin.model.Amud;
import com.londkirs.chullin.model.AmudRecord;
import com.londkirs.chullin.model.MasechetInfo;

import org.json.JSONObject;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * מסך הלימוד היומי. ה-UI נבנה בקוד (ללא XML) כדי לשמור על כל הלוגיקה בקבצי Java פשוטים.
 * כל הכללים (מצבי גמר, חזרות, עמוד יומי, לוח עברי) נמצאים בחבילת core.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "chullin";
    private static final String KEY_SETTINGS = "chullin_settings";
    private static final int[] PCT_CHOICES = {25, 50, 75, 100};

    private Catalog catalog;
    private Settings settings;
    private MasechetInfo info;
    private List<Amud> amudim;
    private Map<String, List<String>> tosafotDb;
    private ProgressStore store;
    private int idx = 0;
    private LocalDate lastDailyDay = null;

    private TextView titleView;
    private TextView dayView;
    private TextView statusView;
    private TextView chazaraView;
    private Button btnGemara;
    private Button btnRashi;
    private Button btnFifty;
    private Button btnAll;
    private Button btnModeView;
    private Button btnPctView;
    private LinearLayout tosList;
    private EditText notesView;
    private boolean updatingNotes = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = loadSettings();
        try {
            catalog = new Catalog(this);
            openMasechet(catalog.find(settings.masechetKey));
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בטעינת הנתונים: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(buildUi());
        applyDailyPage();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // כמו ב-HTML: כשהיום משתנה, קופצים לעמוד היומי החדש.
        if (amudim != null && settings.startDate != null && !LocalDate.now().equals(lastDailyDay)) {
            applyDailyPage();
            render();
        }
    }

    // ---------- טעינה ושמירה ----------

    private void openMasechet(MasechetInfo next) {
        try {
            info = next;
            tosafotDb = Catalog.loadTosafot(this, info);
            amudim = Masechet.build(info, tosafotDb);
            store = new ProgressStore(this, info.key);
            idx = Math.max(0, Math.min(amudim.size() - 1, store.getLastIdx()));
            settings.masechetKey = info.key;
            saveSettings();
        } catch (Exception e) {
            Toast.makeText(this, "לא ניתן לטעון את המסכת: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void applyDailyPage() {
        if (settings.startDate == null || amudim == null) return;
        lastDailyDay = LocalDate.now();
        idx = DailyPage.indexFor(settings.startDate, lastDailyDay, amudim.size());
        store.setLastIdx(idx);
        store.save();
    }

    private Settings loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return Settings.fromJson(prefs.getString(KEY_SETTINGS, null));
    }

    private void saveSettings() {
        try {
            JSONObject json = settings.toJson();
            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_SETTINGS, json.toString()).apply();
        } catch (Exception ignored) {
            // ההגדרות אינן קריטיות; ברירות המחדל יחזרו
        }
    }

    // ---------- בניית המסך ----------

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        titleView = text(20, true);
        dayView = text(14, false);
        statusView = text(14, false);
        root.addView(titleView);
        root.addView(dayView);
        root.addView(statusView);

        LinearLayout nav = row();
        nav.addView(button("מסכת", v -> chooseMasechet()));
        nav.addView(button("היום", v -> { applyDailyPage(); render(); }));
        nav.addView(button("קודם", v -> changeAmud(-1)));
        nav.addView(button("הבא", v -> changeAmud(1)));
        root.addView(nav);

        LinearLayout marks = row();
        btnGemara = button("גמרא", v -> toggleField("gemara"));
        btnRashi = button("רש\"י", v -> toggleField("rashi"));
        marks.addView(btnGemara);
        marks.addView(btnRashi);
        root.addView(marks);

        LinearLayout tosHead = row();
        btnFifty = button("50% ראשונות", v -> quickTosafot(false));
        btnAll = button("100%", v -> quickTosafot(true));
        tosHead.addView(btnFifty);
        tosHead.addView(btnAll);
        root.addView(tosHead);

        ScrollView scroll = new ScrollView(this);
        tosList = new LinearLayout(this);
        tosList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(tosList);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout chazara = row();
        chazara.addView(button("-", v -> stepChazara(-1)));
        chazaraView = text(16, true);
        chazaraView.setGravity(Gravity.CENTER);
        chazara.addView(chazaraView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        chazara.addView(button("+", v -> stepChazara(1)));
        root.addView(chazara);

        LinearLayout modes = row();
        btnModeView = button("", v -> cycleMode());
        btnPctView = button("", v -> cyclePct());
        modes.addView(btnModeView);
        modes.addView(btnPctView);
        modes.addView(button("תאריך התחלה", v -> pickStartDate()));
        root.addView(modes);

        notesView = new EditText(this);
        notesView.setHint("הערות לעמוד");
        notesView.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        notesView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (updatingNotes) return;
                AmudRecord rec = currentRecord();
                rec.note = s.toString();
                store.save();
            }
        });
        root.addView(notesView);
        return root;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return r;
    }

    private TextView text(int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setPadding(dp(4), dp(4), dp(4), dp(4));
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        b.setOnClickListener(listener);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------- רינדור ----------

    private AmudRecord currentRecord() {
        Amud amud = amudim.get(idx);
        return store.get(amud.ref, amud.tosafot.size());
    }

    private void render() {
        Amud amud = amudim.get(idx);
        AmudRecord rec = currentRecord();
        Completion.Mode mode = settings.completionMode;
        int pct = settings.tosPct;
        boolean complete = Completion.isComplete(mode, pct, amud, rec);

        titleView.setText(info.heName + " • " + amud.title);
        dayView.setText("עמוד " + (idx + 1) + " מתוך " + amudim.size());
        String status = complete ? "הושלם ✓" : ((rec.gemara || rec.rashi || rec.checkedTosafot() > 0) ? "בלימוד..." : "טרם נלמד");
        String startText = settings.startDate == null
                ? "לא הוגדר תאריך התחלה"
                : HebrewCalendar.fullHebrew(settings.startDate);
        statusView.setText(status + "  •  תחילת לימוד: " + startText);

        btnGemara.setText((rec.gemara ? "✓ " : "○ ") + "גמרא");
        btnRashi.setText((rec.rashi ? "✓ " : "○ ") + "רש\"י");

        boolean hasTos = amud.hasRealTos;
        btnFifty.setEnabled(hasTos);
        btnAll.setEnabled(hasTos);

        chazaraView.setText(Completion.chazaraOf(mode, pct, amud, rec) + " חזרות");

        btnModeView.setText("מצב: " + modeLabel(mode));
        btnPctView.setText(mode == Completion.Mode.FULL ? "אחוז תוספות: " + pct + "%" : "תוספות: לא חובה");

        tosList.removeAllViews();
        for (int i = 0; i < amud.tosafot.size(); i++) {
            final int itemIdx = i;
            boolean checked = hasTos && rec.tosafot[i];
            Button row = button((checked ? "✓ " : "○ ") + (i + 1) + ". " + amud.tosafot.get(i),
                    v -> toggleTosafot(itemIdx));
            row.setTextSize(17);
            row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            tosList.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        if (!rec.note.equals(notesView.getText().toString())) {
            updatingNotes = true;
            notesView.setText(rec.note);
            notesView.setSelection(rec.note.length());
            updatingNotes = false;
        }
    }

    private static String modeLabel(Completion.Mode mode) {
        switch (mode) {
            case GR: return "גמרא + רש\"י";
            case G: return "גמרא בלבד";
            default: return "גמרא + רש\"י + תוספות";
        }
    }

    // ---------- פעולות ----------

    private void changeAmud(int delta) {
        int next = idx + delta;
        if (next < 0 || next >= amudim.size()) return;
        idx = next;
        store.setLastIdx(idx);
        store.save();
        render();
    }

    private void toggleField(String field) {
        Amud amud = amudim.get(idx);
        AmudRecord rec = currentRecord();
        boolean was = Completion.isComplete(settings.completionMode, settings.tosPct, amud, rec);
        if ("gemara".equals(field)) rec.gemara = !rec.gemara;
        else rec.rashi = !rec.rashi;
        afterChange(amud, rec, was);
    }

    private void toggleTosafot(int itemIdx) {
        Amud amud = amudim.get(idx);
        if (!amud.hasRealTos) return;
        AmudRecord rec = currentRecord();
        boolean was = Completion.isComplete(settings.completionMode, settings.tosPct, amud, rec);
        rec.tosafot[itemIdx] = !rec.tosafot[itemIdx];
        afterChange(amud, rec, was);
    }

    private void quickTosafot(boolean all) {
        Amud amud = amudim.get(idx);
        if (!amud.hasRealTos) return;
        AmudRecord rec = currentRecord();
        boolean was = Completion.isComplete(settings.completionMode, settings.tosPct, amud, rec);
        int target = all ? amud.tosafot.size() : (int) Math.ceil(amud.tosafot.size() / 2.0);
        for (int i = 0; i < rec.tosafot.length; i++) rec.tosafot[i] = i < target;
        afterChange(amud, rec, was);
    }

    private void stepChazara(int delta) {
        Amud amud = amudim.get(idx);
        AmudRecord rec = currentRecord();
        Completion.stepChazara(settings.completionMode, settings.tosPct, amud, rec, delta);
        store.save();
        render();
    }

    private void afterChange(Amud amud, AmudRecord rec, boolean wasComplete) {
        Completion.syncAfterChange(settings.completionMode, settings.tosPct, amud, rec, wasComplete);
        store.save();
        render();
    }

    private void cycleMode() {
        Completion.Mode[] modes = Completion.Mode.values();
        settings.completionMode = modes[(settings.completionMode.ordinal() + 1) % modes.length];
        saveSettings();
        render();
    }

    private void cyclePct() {
        int pos = 0;
        for (int i = 0; i < PCT_CHOICES.length; i++) {
            if (PCT_CHOICES[i] == settings.tosPct) pos = i;
        }
        settings.tosPct = PCT_CHOICES[(pos + 1) % PCT_CHOICES.length];
        saveSettings();
        render();
    }

    private void pickStartDate() {
        LocalDate base = settings.startDate != null ? settings.startDate : LocalDate.now();
        new DatePickerDialog(this, (view, y, m, d) -> {
            settings.startDate = LocalDate.of(y, m + 1, d);
            saveSettings();
            applyDailyPage();
            render();
        }, base.getYear(), base.getMonthValue() - 1, base.getDayOfMonth()).show();
    }

    private void chooseMasechet() {
        List<MasechetInfo> all = catalog.all();
        String[] names = new String[all.size()];
        for (int i = 0; i < names.length; i++) names[i] = all.get(i).heName;
        new AlertDialog.Builder(this)
                .setTitle("בחירת מסכת")
                .setItems(names, (dialog, which) -> {
                    openMasechet(all.get(which));
                    applyDailyPage();
                    render();
                })
                .show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (getCurrentFocus() instanceof EditText) return super.onKeyDown(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_4:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                changeAmud(1);
                return true;
            case KeyEvent.KEYCODE_6:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                changeAmud(-1);
                return true;
            case KeyEvent.KEYCODE_1:
                toggleField("gemara");
                return true;
            case KeyEvent.KEYCODE_2:
                toggleField("rashi");
                return true;
            case KeyEvent.KEYCODE_3:
                quickTosafot(false);
                return true;
            case KeyEvent.KEYCODE_7:
                stepChazara(1);
                return true;
            case KeyEvent.KEYCODE_8:
                stepChazara(-1);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }
}
