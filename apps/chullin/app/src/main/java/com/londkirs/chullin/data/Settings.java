package com.londkirs.chullin.data;

import com.londkirs.chullin.core.Completion;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;

/** הגדרות המשתמש. ברירות המחדל זהות ל-HTML המקורי (מצב יום, עברי, מלא, 50%). */
public final class Settings {

    public enum Theme { LIGHT, DARK }

    public enum InputMode { HEBREW, GREGORIAN }

    public Theme theme = Theme.LIGHT;
    public LocalDate startDate = null;
    public InputMode startInputMode = InputMode.HEBREW;
    public Completion.Mode completionMode = Completion.Mode.FULL;
    public int tosPct = 50;
    public String masechetKey = "Chullin";

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("theme", theme == Theme.LIGHT ? "light" : "dark");
        o.put("startDate", startDate == null ? JSONObject.NULL : startDate.toString());
        o.put("startInputMode", startInputMode == InputMode.GREGORIAN ? "greg" : "heb");
        o.put("completionMode", completionMode.key);
        o.put("tosPct", tosPct);
        o.put("masechet", masechetKey);
        return o;
    }

    /** קורא הגדרות מ-JSON, כולל הגדרות ישנות מה-HTML. ערכים לא תקינים מקבלים ברירת מחדל. */
    public static Settings fromJson(String text) {
        Settings s = new Settings();
        if (text == null) return s;
        try {
            JSONObject o = new JSONObject(text);
            if ("dark".equals(o.optString("theme"))) s.theme = Theme.DARK;
            if (o.has("startDate") && !o.isNull("startDate")) {
                try {
                    s.startDate = LocalDate.parse(o.getString("startDate"));
                } catch (RuntimeException ignored) {
                    s.startDate = null;
                }
            }
            if ("greg".equals(o.optString("startInputMode"))) s.startInputMode = InputMode.GREGORIAN;
            s.completionMode = Completion.Mode.fromKey(o.optString("completionMode", "full"));
            int p = o.optInt("tosPct", 50);
            if (p >= 1 && p <= 100) s.tosPct = p;
            String m = o.optString("masechet", "");
            if (!m.isEmpty()) s.masechetKey = m;
        } catch (JSONException ignored) {
            // JSON פגום: נשארים עם ברירות המחדל
        }
        return s;
    }
}
