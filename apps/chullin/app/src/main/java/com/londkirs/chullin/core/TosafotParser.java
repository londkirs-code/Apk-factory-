package com.londkirs.chullin.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * מפרסר קובץ תוספות גולמי (TXT מספריא) ומוציא ממנו רק את תחילת כל דיבור (ד"ה).
 * זהה ל-tools/build_masechtot.py ול-parseSefariaTxt של ה-HTML המקורי. אין כאן תלויות ב-Android.
 */
public final class TosafotParser {

    private static final Pattern DAF_RE = Pattern.compile("^Daf\\s+(\\d+)([ab])$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINE_RE = Pattern.compile("^Line\\s+\\d+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG_RE = Pattern.compile("<[^>]+>");

    private TosafotParser() {}

    /** מחזיר {"2a": ["דיבור", ...], ...} לפי סדר העמודים. */
    public static Map<String, List<String>> parseSefariaText(String text) {
        Map<String, List<String>> db = new LinkedHashMap<>();
        String current = null;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            Matcher m = DAF_RE.matcher(line);
            if (m.matches()) {
                current = Integer.parseInt(m.group(1)) + m.group(2).toLowerCase();
                db.putIfAbsent(current, new ArrayList<>());
                continue;
            }
            if (LINE_RE.matcher(line).matches()) continue;
            if (line.startsWith("http") || line.startsWith("Tosafot on")
                    || line.startsWith("תוספות על") || line.equals("Vilna Edition")) continue;
            if (current == null) continue;
            String dh = extractDh(TAG_RE.matcher(line).replaceAll("").trim());
            if (!dh.isEmpty()) db.get(current).add(dh);
        }
        return db;
    }

    /** מחלץ את תחילת הדיבור מהשורה הנקייה: לפני " - " או " – " או מקף, ואם אין - ששה המילים הראשונות. */
    public static String extractDh(String clean) {
        int dash = clean.indexOf(" - ");
        int endash = clean.indexOf(" – ");
        if (dash >= 0 && dash < 95) return clean.substring(0, dash).trim();
        if (endash >= 0 && endash < 95) return clean.substring(0, endash).trim();
        int plain = clean.indexOf('-');
        if (plain > 2 && plain < 80) return clean.substring(0, plain).trim();
        String[] words = clean.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(6, words.length); i++) {
            if (words[i].isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(words[i]);
        }
        return sb.toString();
    }

    /** מחזיר רק עמודים עם תוספות, ממוין לפי דף ואז ע"א/ע"ב. */
    public static Map<String, List<String>> dropEmpty(Map<String, List<String>> db) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : db.entrySet()) {
            if (!e.getValue().isEmpty()) out.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        return out;
    }

    /** מוציא את מספר הדף הגבוה ביותר והאם הדף האחרון הוא ע"א בלבד. */
    public static int[] range(Map<String, List<String>> db) {
        int max = 0;
        boolean maxHasB = false;
        for (String ref : db.keySet()) {
            int daf = Integer.parseInt(ref.substring(0, ref.length() - 1));
            char side = ref.charAt(ref.length() - 1);
            if (daf > max) {
                max = daf;
                maxHasB = side == 'b';
            } else if (daf == max && side == 'b') {
                maxHasB = true;
            }
        }
        return new int[]{max, maxHasB ? 0 : 1};
    }
}
