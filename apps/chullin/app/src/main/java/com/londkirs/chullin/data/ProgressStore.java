package com.londkirs.chullin.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.londkirs.chullin.model.AmudRecord;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * התקדמות של מסכת אחת, נשמרת ב-SharedPreferences לפי מפתח המסכת.
 * הפורמט (gemara, rashi, tosafot, chazara, note) זהה ל-HTML המקורי, כך שגיבויים ישנים נקראים.
 */
public final class ProgressStore {

    private static final String PREFS = "chullin";
    private static final String KEY_PREFIX = "progress_";
    private static final String IDX_PREFIX = "last_idx_";

    private final SharedPreferences prefs;
    private final String masechetKey;
    private final Map<String, AmudRecord> records = new HashMap<>();
    private int lastIdx = 0;

    public ProgressStore(Context context, String masechetKey) {
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.masechetKey = masechetKey;
        load();
    }

    /** מחזיר את הרשומה של העמוד, ויוצר אותה אם לא קיימת. */
    public AmudRecord get(String ref, int tosafotCount) {
        AmudRecord rec = records.get(ref);
        if (rec == null) {
            rec = new AmudRecord(tosafotCount);
            records.put(ref, rec);
        }
        rec.resize(tosafotCount);
        return rec;
    }

    public int getLastIdx() {
        return lastIdx;
    }

    public void setLastIdx(int idx) {
        lastIdx = idx;
    }

    public void save() {
        prefs.edit()
                .putString(KEY_PREFIX + masechetKey, toJson().toString())
                .putInt(IDX_PREFIX + masechetKey, lastIdx)
                .apply();
    }

    public void resetAll() {
        records.clear();
        save();
    }

    private void load() {
        String raw = prefs.getString(KEY_PREFIX + masechetKey, null);
        if (raw != null) {
            try {
                readJson(new JSONObject(raw));
            } catch (JSONException ignored) {
                // נתונים פגומים: מתחילים מחדש ולא קורסים
            }
        }
        lastIdx = prefs.getInt(IDX_PREFIX + masechetKey, 0);
    }

    JSONObject toJson() {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, AmudRecord> e : records.entrySet()) {
                AmudRecord r = e.getValue();
                JSONObject o = new JSONObject();
                o.put("gemara", r.gemara);
                o.put("rashi", r.rashi);
                JSONArray tos = new JSONArray();
                for (boolean b : r.tosafot) tos.put(b);
                o.put("tosafot", tos);
                o.put("chazara", r.chazara);
                o.put("note", r.note);
                root.put(e.getKey(), o);
            }
        } catch (JSONException ignored) {
            // שדות בסיסיים בלבד
        }
        return root;
    }

    void readJson(JSONObject root) {
        records.clear();
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String ref = keys.next();
            JSONObject o = root.optJSONObject(ref);
            if (o == null) continue;
            JSONArray tos = o.optJSONArray("tosafot");
            AmudRecord r = new AmudRecord(tos == null ? 0 : tos.length());
            r.gemara = o.optBoolean("gemara", false);
            r.rashi = o.optBoolean("rashi", false);
            for (int i = 0; tos != null && i < tos.length(); i++) {
                r.tosafot[i] = tos.optBoolean(i, false);
            }
            r.chazara = o.optInt("chazara", 0);
            r.note = o.optString("note", "");
            records.put(ref, r);
        }
    }
}
