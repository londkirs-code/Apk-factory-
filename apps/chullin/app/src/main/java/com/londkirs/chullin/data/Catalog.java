package com.londkirs.chullin.data;

import android.content.Context;

import com.londkirs.chullin.model.MasechetInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** קטלוג המסכתות (assets/masechtot/catalog.json) וטעינת התוספות של כל מסכת. */
public final class Catalog {

    private final List<MasechetInfo> masechtot = new ArrayList<>();

    public Catalog(Context context) throws IOException, JSONException {
        String text = readAsset(context, "masechtot/catalog.json");
        JSONArray arr = new JSONArray(text);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            masechtot.add(new MasechetInfo(
                    o.getString("key"),
                    o.getString("heName"),
                    o.getInt("dafFrom"),
                    o.getInt("dafTo"),
                    o.optBoolean("lastDafSideAOnly", false),
                    o.getString("dataFile")
            ));
        }
    }

    public List<MasechetInfo> all() {
        return masechtot;
    }

    public MasechetInfo find(String key) {
        for (MasechetInfo m : masechtot) {
            if (m.key.equals(key)) return m;
        }
        return masechtot.isEmpty() ? null : masechtot.get(0);
    }

    /** טוען את מאגר התוספות של המסכת: מפתח כמו "2a" לרשימת הד"ה. */
    public static Map<String, List<String>> loadTosafot(Context context, MasechetInfo info)
            throws IOException, JSONException {
        Map<String, List<String>> db = new HashMap<>();
        JSONObject root = new JSONObject(readAsset(context, info.dataFile));
        java.util.Iterator<String> keys = root.keys();
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
        try (InputStream in = context.getAssets().open(path);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }
}
