package com.londkirs.chullin.data;

import android.content.Context;
import android.net.Uri;

import com.londkirs.chullin.core.Masechtot;
import com.londkirs.chullin.core.TosafotParser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * מייבא קובץ תוספות של מסכת (TXT מספריא או JSON של {"2a": [...]}), ושומר אותו
 * כקובץ קומפקטי שמכיל רק את תחילות הדיבורים, בתוך filesDir/tosafot/.
 * הקטלוג של הקבצים שיובאו נשמר ב-filesDir/tosafot/catalog.json ודורס את ה-assets לפי key.
 */
public final class TosafotImporter {

    public static final class Result {
        public final String key;
        public final int amudim;
        public final int dafTo;
        public final boolean lastDafSideAOnly;

        Result(String key, int amudim, int dafTo, boolean lastDafSideAOnly) {
            this.key = key;
            this.amudim = amudim;
            this.dafTo = dafTo;
            this.lastDafSideAOnly = lastDafSideAOnly;
        }
    }

    private TosafotImporter() {}

    /** קורא קובץ מ-URI (דרך SAF) ומייבא אותו עבור מסכת נתונה. */
    public static Result importFromUri(Context context, Uri uri, String masechetKey) throws IOException {
        String text = readText(context, uri);
        return importText(context, text, masechetKey);
    }

    public static Result importText(Context context, String text, String masechetKey) throws IOException {
        if (text.indexOf('\u0000') >= 0) {
            throw new IOException("הקובץ פגום (מכיל תווי NUL). יש להוריד אותו מחדש מספריא.");
        }
        if (Masechtot.heName(masechetKey).equals(masechetKey)) {
            throw new IOException("מסכת לא מוכרת: " + masechetKey);
        }
        Map<String, List<String>> db;
        String trimmed = text.trim();
        if (trimmed.startsWith("{")) {
            db = parseCompactJson(trimmed);
        } else {
            db = TosafotParser.parseSefariaText(text);
        }
        db = TosafotParser.dropEmpty(db);
        if (db.isEmpty()) {
            throw new IOException("לא נמצאו דיבורים בקובץ. ודא שזה קובץ תוספות של המסכת הנכונה.");
        }
        int[] range = TosafotParser.range(db);
        int amudim = 0;
        for (List<String> l : db.values()) amudim++;

        File dir = new File(context.getFilesDir(), "tosafot");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("לא ניתן ליצור תיקייה");
        writeJson(new File(dir, masechetKey + ".json"), db);
        updateCatalog(dir, masechetKey, range[0], range[1] == 1);
        return new Result(masechetKey, amudim, range[0], range[1] == 1);
    }

    private static Map<String, List<String>> parseCompactJson(String text) throws IOException {
        Map<String, List<String>> db = new java.util.LinkedHashMap<>();
        try {
            JSONObject o = new JSONObject(text);
            java.util.Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String ref = keys.next();
                JSONArray arr = o.optJSONArray(ref);
                if (arr == null) continue;
                List<String> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i, "").trim();
                    if (!s.isEmpty()) list.add(s);
                }
                db.put(ref, list);
            }
        } catch (org.json.JSONException e) {
            throw new IOException("קובץ JSON לא תקין: " + e.getMessage(), e);
        }
        return db;
    }

    private static void writeJson(File file, Map<String, List<String>> db) throws IOException {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, List<String>> e : db.entrySet()) {
                JSONArray arr = new JSONArray();
                for (String s : e.getValue()) arr.put(s);
                root.put(e.getKey(), arr);
            }
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
        writeFile(file, root.toString());
    }

    /** מעדכן את הקטלוג המקומי: שורה אחת לכל מסכת שיובאה. */
    private static void updateCatalog(File dir, String key, int dafTo, boolean sideAOnly) throws IOException {
        File cat = new File(dir, "catalog.json");
        JSONArray arr = new JSONArray();
        try {
            if (cat.isFile()) {
                JSONArray old = new JSONArray(readFile(cat));
                for (int i = 0; i < old.length(); i++) {
                    JSONObject o = old.getJSONObject(i);
                    if (!key.equals(o.optString("key"))) arr.put(o);
                }
            }
            JSONObject o = new JSONObject();
            o.put("key", key);
            o.put("heName", Masechtot.heName(key));
            o.put("dafFrom", 2);
            o.put("dafTo", dafTo);
            o.put("lastDafSideAOnly", sideAOnly);
            o.put("dataFile", new File(dir, key + ".json").getAbsolutePath());
            arr.put(o);
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
        writeFile(cat, arr.toString());
    }

    private static void writeFile(File file, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readFile(File file) throws IOException {
        try (InputStream in = new java.io.FileInputStream(file)) {
            return readAll(in);
        }
    }

    private static String readText(Context context, Uri uri) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("לא ניתן לפתוח את הקובץ");
            return readAll(in);
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }
}
