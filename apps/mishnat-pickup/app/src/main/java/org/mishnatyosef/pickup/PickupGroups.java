package org.mishnatyosef.pickup;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * רשימות איסוף: רשימה אחת עם שם, ובתוכה משתמשים. כל משתמש הוא רשימה שמורה (הזמנה) עם שם תצוגה משלו.
 * לכל רשימת איסוף הגדרות משלה: תצוגה מאוחדת או לפי משתמש, וכמות לפי משתמש בכל מוצר.
 *
 * מצב האיסוף (מה נאסף, נאסף חלקית) נשמר בכל רשימה שמורה עצמה (SavedListStore.setProgress),
 * ולכן הוא נשאר גם כשעוברים לרשימת איסוף אחרת או לרשימה בודדת, וחוזר כשחוזרים.
 * המעבר בין רשימות לא מוחק אף רשימת איסוף: רק "מחיקה" מפורשת.
 */
final class PickupGroups {
    static final String VIEW_COMBINED = "COMBINED_SUM", VIEW_BY_USER = "COMBINED_BY_SOURCE";
    private static final String KEY_GROUPS = "pickup_groups", KEY_ACTIVE = "pickup_group_active";

    static final class Group {
        String id = "", name = "";
        final List<String> members = new ArrayList<>();
        final Map<String, String> labels = new LinkedHashMap<>();
        String view = VIEW_COMBINED;
        boolean breakdown = true;
        long created;

        String label(String entryId, String fallback) {
            String custom = labels.get(entryId);
            return custom == null || custom.trim().isEmpty() ? fallback : custom.trim();
        }

        JSONObject toJson() throws Exception {
            JSONArray m = new JSONArray();
            for (String id : members) m.put(id);
            JSONObject l = new JSONObject();
            for (Map.Entry<String, String> e : labels.entrySet()) l.put(e.getKey(), e.getValue());
            return new JSONObject().put("id", id).put("name", name).put("members", m).put("labels", l)
                    .put("view", view).put("breakdown", breakdown).put("created", created);
        }

        static Group fromJson(JSONObject o) {
            Group g = new Group();
            g.id = o.optString("id", "");
            g.name = o.optString("name", "");
            JSONArray m = o.optJSONArray("members");
            if (m != null) for (int i = 0; i < m.length(); i++) {
                String id = m.optString(i, "");
                if (!id.isEmpty() && !g.members.contains(id)) g.members.add(id);
            }
            JSONObject l = o.optJSONObject("labels");
            if (l != null) {
                JSONArray names = l.names();
                if (names != null) for (int i = 0; i < names.length(); i++) {
                    String key = names.optString(i, "");
                    g.labels.put(key, l.optString(key, ""));
                }
            }
            g.view = VIEW_BY_USER.equals(o.optString("view")) ? VIEW_BY_USER : VIEW_COMBINED;
            g.breakdown = o.optBoolean("breakdown", true);
            g.created = o.optLong("created", 0);
            return g;
        }
    }

    private final SharedPreferences prefs;

    PickupGroups(SharedPreferences prefs) { this.prefs = prefs; }

    synchronized List<Group> all() {
        List<Group> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString(KEY_GROUPS, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) { Group g = Group.fromJson(o); if (!g.id.isEmpty()) out.add(g); }
            }
        } catch (Exception ignored) { }
        return out;
    }

    synchronized Group find(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Group g : all()) if (g.id.equals(id)) return g;
        return null;
    }

    synchronized Group active() { return find(prefs.getString(KEY_ACTIVE, "")); }

    synchronized void setActive(String id) { prefs.edit().putString(KEY_ACTIVE, id == null ? "" : id).apply(); }

    synchronized Group create(String name, List<String> members) {
        Group g = new Group();
        g.id = "g" + System.currentTimeMillis();
        g.name = name == null || name.trim().isEmpty() ? "רשימת איסוף" : name.trim();
        g.created = System.currentTimeMillis();
        if (members != null) for (String id : members) if (!g.members.contains(id)) g.members.add(id);
        save(g);
        return g;
    }

    synchronized void save(Group group) {
        List<Group> list = all();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(group.id)) { list.set(i, group); replaced = true; }
        if (!replaced) list.add(group);
        write(list);
    }

    synchronized void delete(String id) {
        List<Group> list = all();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(id)) list.remove(i);
        write(list);
        if (id.equals(prefs.getString(KEY_ACTIVE, ""))) setActive("");
    }

    /** רשימה שמורה שנמחקה יוצאת מכל רשימות האיסוף. */
    synchronized void removeMemberEverywhere(String entryId) {
        List<Group> list = all();
        for (Group g : list) { g.members.remove(entryId); g.labels.remove(entryId); }
        write(list);
    }

    private void write(List<Group> list) {
        JSONArray a = new JSONArray();
        for (Group g : list) { try { a.put(g.toJson()); } catch (Exception ignored) { } }
        prefs.edit().putString(KEY_GROUPS, a.toString()).apply();
    }
}
