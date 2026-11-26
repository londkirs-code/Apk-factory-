package com.londkirs.chullin.data;

import android.content.Context;

import com.londkirs.chullin.model.MasechetInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * קטלוג המסכתות: קודם assets/masechtot/catalog.json, ואחר כך קבצים שיובאו במכשיר
 * (filesDir/tosafot/catalog.json) שדורסים לפי key. נתיב dataFile מוחלט = קובץ במכשיר.
 */
public final class Catalog {

    private final Map<String, MasechetInfo> masechtot = new LinkedHashMap<>();

    public Catalog(Context context) throws IOException, JSONException {
        readCatalog(readAsset(context, "masechtot/catalog.json"));
        File local = new File(context.getFilesDir(), "tosafot/catalog.json");
        if (local.isFile()) readCatalog(readFile(local));
    }

    private void readCatalog(String text) throws JSONException {
        JSONArray arr = new JSONArray(text);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            MasechetInfo info = new MasechetInfo(
                    o.getString("key"),
                    o.getString("heName"),
                    o.getInt("dafFrom"),
                    o.getInt("dafTo"),
                    o.optBoolean("lastDafSideAOnly", false),
                    o.getString("dataFile")
            );
            masechtot.put(info.key, info);
        }
    }

    public List<MasechetInfo> all() {
        return new ArrayList<>(masechtot.values());
    }

    public MasechetInfo find(String key) {
        MasechetInfo m = masechtot.get(key);
        if (m != null) return m;
        return masechtot.isEmpty() ? null : masechtot.values().iterator().next();
    }

    /** טוען את מאגר התוספות של המסכת: מפתח כמו "2a" לרשימת הד"ה. */
    public static Map<String, List<String>> loadTosafot(Context context, MasechetInfo info)
            throws IOException, JSONException {
        String text = info.dataFile.startsWith("/")
                ? readFile(new File(info.dataFile))
                : readAsset(context, info.dataFile);
        Map<String, List<String>> db = new HashMap<>();
        JSONObject root = new JSONObject(text);
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String ref = keys.next();
            JSONArray arr = root.getJSONArray(ref);
            List<String> list = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
            db.put(ref, list);
        }
        return db;
    }

    private static String readAsset(Context context, String path) throws IOException {
        try (InputStream in = context.getAssets().open(path)) {
            return readAll(in);
        }
    }

    private static String readFile(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
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
