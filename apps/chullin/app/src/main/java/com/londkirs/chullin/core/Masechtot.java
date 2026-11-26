package com.londkirs.chullin.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** שמות 37 מסכתות הש"ס: מפתח ספריא (כמו בסקריפט הבנייה) לשם בעברית. */
public final class Masechtot {

    private static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        String[][] rows = {
            {"Berakhot", "ברכות"}, {"Shabbat", "שבת"}, {"Eruvin", "עירובין"}, {"Pesachim", "פסחים"},
            {"Yoma", "יומא"}, {"Sukkah", "סוכה"}, {"Beitzah", "ביצה"}, {"Rosh_Hashanah", "ראש השנה"},
            {"Taanit", "תענית"}, {"Megillah", "מגילה"}, {"Moed_Katan", "מועד קטן"}, {"Chagigah", "חגיגה"},
            {"Yevamot", "יבמות"}, {"Ketubot", "כתובות"}, {"Nedarim", "נדרים"}, {"Nazir", "נזיר"},
            {"Sotah", "סוטה"}, {"Gittin", "גיטין"}, {"Kiddushin", "קידושין"}, {"Bava_Kamma", "בבא קמא"},
            {"Bava_Metzia", "בבא מציעא"}, {"Bava_Batra", "בבא בתרא"}, {"Sanhedrin", "סנהדרין"},
            {"Makkot", "מכות"}, {"Shevuot", "שבועות"}, {"Avodah_Zarah", "עבודה זרה"}, {"Horayot", "הוריות"},
            {"Zevachim", "זבחים"}, {"Menachot", "מנחות"}, {"Chullin", "חולין"}, {"Bekhorot", "בכורות"},
            {"Arakhin", "ערכין"}, {"Temurah", "תמורה"}, {"Keritot", "כריתות"}, {"Meilah", "מעילה"},
            {"Tamid", "תמיד"}, {"Niddah", "נידה"},
        };
        for (String[] r : rows) NAMES.put(r[0], r[1]);
    }

    private Masechtot() {}

    public static Map<String, String> all() {
        return NAMES;
    }

    public static String heName(String key) {
        String h = NAMES.get(key);
        return h == null ? key : h;
    }

    /** מזהה מסכת מתוך שם קובץ, למשל "Tosafot_on_Chullin_-_he_-_Vilna_Edition.plain.txt" → Chullin. */
    public static String keyFromFileName(String name) {
        for (String key : NAMES.keySet()) {
            if (name.contains("Tosafot_on_" + key + "_") || name.contains("Tosafot_on_" + key + ".")) {
                return key;
            }
        }
        return null;
    }
}
