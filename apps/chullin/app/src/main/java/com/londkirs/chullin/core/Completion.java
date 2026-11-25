package com.londkirs.chullin.core;

import com.londkirs.chullin.model.Amud;
import com.londkirs.chullin.model.AmudRecord;

/** הכללים של "מתי עמוד נחשב גמור" וחישוב החזרות. זהה ללוגיקה של ה-HTML המקורי. */
public final class Completion {

    public enum Mode {
        /** גמרא + רש"י + אחוז מהתוספות */
        FULL("full"),
        /** גמרא + רש"י בלבד */
        GR("gr"),
        /** גמרא בלבד */
        G("g");

        public final String key;

        Mode(String key) {
            this.key = key;
        }

        public static Mode fromKey(String key) {
            for (Mode m : values()) {
                if (m.key.equals(key)) return m;
            }
            return FULL;
        }
    }

    private Completion() {}

    public static boolean isComplete(Mode mode, int pct, Amud amud, AmudRecord rec) {
        switch (mode) {
            case G:
                return rec.gemara;
            case GR:
                return rec.gemara && rec.rashi;
            default:
                if (!amud.hasRealTos) return rec.gemara && rec.rashi;
                int required = Masechet.requiredTosafot(amud, pct);
                return rec.gemara && rec.rashi && rec.checkedTosafot() >= required;
        }
    }

    /** מספר החזרות לתצוגה: 0 אם העמוד לא גמור, אחרת לפחות 1. */
    public static int chazaraOf(Mode mode, int pct, Amud amud, AmudRecord rec) {
        if (!isComplete(mode, pct, amud, rec)) return 0;
        return Math.max(1, rec.chazara);
    }

    /**
     * מעדכן חזרות אחרי שינוי סימון. מחזיר true אם העמוד הפך לגמור כרגע (כדי להציג חגיגה).
     * wasComplete הוא מצב העמוד לפני השינוי.
     */
    public static boolean syncAfterChange(Mode mode, int pct, Amud amud, AmudRecord rec, boolean wasComplete) {
        boolean nowComplete = isComplete(mode, pct, amud, rec);
        if (!wasComplete && nowComplete) {
            if (rec.chazara < 1) rec.chazara = 1;
            return true;
        }
        if (wasComplete && !nowComplete) {
            rec.chazara = 0;
        }
        return false;
    }

    /** הגבלת חזרות בין 1 ל-10 (כמו stepChazara). */
    public static int stepChazara(Mode mode, int pct, Amud amud, AmudRecord rec, int delta) {
        if (!isComplete(mode, pct, amud, rec)) return rec.chazara;
        int cur = chazaraOf(mode, pct, amud, rec);
        rec.chazara = Math.max(1, Math.min(10, cur + delta));
        return rec.chazara;
    }
}
