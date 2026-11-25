package com.londkirs.chullin.core;

import com.londkirs.chullin.model.Amud;
import com.londkirs.chullin.model.MasechetInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** בונה את רשימת העמודים של מסכת כלשהי מתוך הקטלוג ומאגר התוספות שלה. */
public final class Masechet {

    public static final String NO_TOSAFOT_TEXT = "אין תוספות בעמוד זה";

    private Masechet() {}

    /** מספר העמודים הכולל של המסכת. */
    public static int countAmudim(MasechetInfo info) {
        int n = 0;
        for (int daf = info.dafFrom; daf <= info.dafTo; daf++) {
            n += sidesOf(info, daf).length;
        }
        return n;
    }

    private static char[] sidesOf(MasechetInfo info, int daf) {
        if (daf == info.dafTo && info.lastDafSideAOnly) return new char[]{'a'};
        return new char[]{'a', 'b'};
    }

    /** בונה את העמודים. מאגר התוספות הוא מפתח כמו "2a" לרשימת הד"ה. */
    public static List<Amud> build(MasechetInfo info, Map<String, List<String>> tosafotDb) {
        List<Amud> out = new ArrayList<>();
        int idx = 0;
        for (int daf = info.dafFrom; daf <= info.dafTo; daf++) {
            for (char side : sidesOf(info, daf)) {
                String ref = daf + String.valueOf(side);
                List<String> list = tosafotDb.get(ref);
                boolean hasReal = list != null && !list.isEmpty();
                if (!hasReal) list = Collections.singletonList(NO_TOSAFOT_TEXT);
                String hebDaf = HebrewCalendar.hebrewLetters(daf);
                String sideName = side == 'a' ? "ע\"א" : "ע\"ב";
                out.add(new Amud(
                        idx,
                        daf,
                        side,
                        ref,
                        "דף " + hebDaf + " " + sideName,
                        hebDaf + (side == 'a' ? "." : ":"),
                        Collections.unmodifiableList(new ArrayList<>(list)),
                        hasReal
                ));
                idx++;
            }
        }
        return out;
    }

    /**
     * מספר התוספות הנדרש לעמוד לפי האחוז שנבחר. מעוגל כלפי מעלה, מינימום 1.
     * עמוד בלי תוספות מחזיר 0.
     */
    public static int requiredTosafot(Amud amud, int pct) {
        if (!amud.hasRealTos) return 0;
        return Math.max(1, (int) Math.ceil(amud.tosafot.size() * (pct / 100.0)));
    }
}
