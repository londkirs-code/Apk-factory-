package com.londkirs.chullin.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

/** גיבוי ושחזור בפורמט JSON בלבד: {progress: {key: {...}}, lastIdx: {key: n}, settings: {...}}. */
public final class Backup {

    public static final String PREFS = "chullin";
    public static final String SETTINGS_KEY = "chullin_settings";
    static final String PROGRESS_PREFIX = "progress_";
    static final String IDX_PREFIX = "last_idx_";

    private Backup() {}

    public static void export(Context c, Uri uri, List<String> keys) throws IOException, JSONException {
        SharedPreferences prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONObject progress = new JSONObject();
        JSONObject lastIdx = new JSONObject();
        for (String key : keys) {
            String raw = prefs.getString(PROGRESS_PREFIX + key, null);
            if (raw != null) progress.put(key, new JSONObject(raw));
            lastIdx.put(key, prefs.getInt(IDX_PREFIX + key, 0));
        }
        JSONObject root = new JSONObject();
        root.put("progress", progress);
        root.put("lastIdx", lastIdx);
        root.put("settings", new JSONObject(prefs.getString(SETTINGS_KEY, "{}")));
        try (OutputStream out = c.getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IOException("לא ניתן לכתוב לקובץ");
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** קורא גיבוי ושומר אותו. זורק חריגה אם ה-JSON לא תקין, בלי לשנות דבר. */
    public static void importBackup(Context c, Uri uri) throws IOException, JSONException {
        JSONObject root = new JSONObject(readUri(c, uri));
        SharedPreferences.Editor ed = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        JSONObject progress = root.optJSONObject("progress");
        if (progress != null) {
            Iterator<String> it = progress.keys();
            while (it.hasNext()) {
                String k = it.next();
                ed.putString(PROGRESS_PREFIX + k, progress.getJSONObject(k).toString());
            }
        }
        JSONObject lastIdx = root.optJSONObject("lastIdx");
        if (lastIdx != null) {
            Iterator<String> it = lastIdx.keys();
            while (it.hasNext()) {
                String k = it.next();
                ed.putInt(IDX_PREFIX + k, lastIdx.optInt(k, 0));
            }
        }
        JSONObject settings = root.optJSONObject("settings");
        if (settings != null) ed.putString(SETTINGS_KEY, settings.toString());
        ed.apply();
    }

    /** מוחק את כל ההתקדמות של המסכות (לא את ההגדרות). */
    public static void resetProgress(Context c, List<String> keys) {
        SharedPreferences.Editor ed = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (String k : keys) {
            ed.remove(PROGRESS_PREFIX + k).putInt(IDX_PREFIX + k, 0);
        }
        ed.apply();
    }

    static String readUri(Context c, Uri uri) throws IOException {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("לא ניתן לפתוח את הקובץ");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }
}
