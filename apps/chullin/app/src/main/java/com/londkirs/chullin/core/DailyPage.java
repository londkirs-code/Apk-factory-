package com.londkirs.chullin.core;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** העמוד היומי לפי תאריך תחילת לימוד: יום 1 = העמוד הראשון (ב׳ ע"א). */
public final class DailyPage {

    private DailyPage() {}

    /** מחזיר אינדקס עמוד להיום, או -1 אם לא הוגדר תאריך. לפני התחלה: 0. אחרי הסיום: האחרון. */
    public static int indexFor(LocalDate start, LocalDate today, int total) {
        if (start == null) return -1;
        long diff = ChronoUnit.DAYS.between(start, today);
        if (diff < 0) return 0;
        if (diff >= total) return total - 1;
        return (int) diff;
    }
}
