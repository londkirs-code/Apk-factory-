package com.londkirs.chullin.core;

import java.time.LocalDate;

/**
 * לוח עברי: המרה בין תאריך לועזי לעברי. תרגום ישיר של האלגוריתם מקובץ ה-HTML המקורי.
 * ימים נספרים מ-RD (Rata Die). UNIX_EPOCH_RD הוא מספר היום של 1.1.1970 בספירה הזו.
 */
public final class HebrewCalendar {

    public static final long UNIX_EPOCH_RD = 719163L;

    /** תאריך עברי: יום, חודש (7=תשרי ... 6=אלול, 12=אדר או אדר א׳, 13=אדר ב׳), שנה. */
    public static final class HDate {
        public final int year;
        public final int month;
        public final int day;

        public HDate(int year, int month, int day) {
            this.year = year;
            this.month = month;
            this.day = day;
        }
    }

    private HebrewCalendar() {}

    // ---------- ליבת החישוב ----------

    static boolean isLeap(int y) {
        return Math.floorMod(7L * y + 1, 19) < 7;
    }

    /** מספר הימים מ-1 תשרי שנה 1 עד תחילת השנה (לפני תיקון אורך השנה). */
    static long elapsedDays(int y) {
        long months = Math.floorDiv(235L * y - 234, 19);
        long parts = 12084 + 13753 * months;
        long days = 29 * months + Math.floorDiv(parts, 25920);
        return Math.floorMod(3 * (days + 1), 7) < 3 ? days + 1 : days;
    }

    /** תיקון אורך השנה (שנה שלמה / חסרה) לפי השנים הסמוכות. */
    static long yearLengthCorrection(int y) {
        long ny0 = elapsedDays(y - 1);
        long ny1 = elapsedDays(y);
        long ny2 = elapsedDays(y + 1);
        if (ny2 - ny1 == 356) return 2;
        if (ny1 - ny0 == 382) return 1;
        return 0;
    }

    /** RD של 1 תשרי השנה. */
    static long newYearRD(int y) {
        return -1373427L + elapsedDays(y) + yearLengthCorrection(y);
    }

    static int yearLength(int y) {
        return (int) (newYearRD(y + 1) - newYearRD(y));
    }

    static int[] monthOrder(int y) {
        return isLeap(y)
                ? new int[]{7, 8, 9, 10, 11, 12, 13, 1, 2, 3, 4, 5, 6}
                : new int[]{7, 8, 9, 10, 11, 12, 1, 2, 3, 4, 5, 6};
    }

    public static int lastDayOfMonth(int m, int y) {
        if (m == 2 || m == 4 || m == 6 || m == 10 || m == 13) return 29;
        if (m == 12) return isLeap(y) ? 30 : 29;
        if (m == 8) return (yearLength(y) % 10 == 5) ? 30 : 29;
        if (m == 9) return (yearLength(y) % 10 == 3) ? 29 : 30;
        return 30;
    }

    /** RD של יום מסוים בלוח העברי. */
    static long toRD(int y, int m, int d) {
        long rd = newYearRD(y) + d - 1;
        for (int mon : monthOrder(y)) {
            if (mon == m) break;
            rd += lastDayOfMonth(mon, y);
        }
        return rd;
    }

    static HDate fromRD(long rd) {
        int y = (int) Math.floor((rd + 1373427) / 365.2468) ;
        while (newYearRD(y + 1) <= rd) y++;
        while (newYearRD(y) > rd) y--;
        for (int mon : monthOrder(y)) {
            long start = toRD(y, mon, 1);
            if (rd < start + lastDayOfMonth(mon, y)) {
                int d = (int) (rd - start + 1);
                return new HDate(y, mon, d);
            }
        }
        throw new IllegalStateException("תאריך לא נמצא בלוח");
    }

    // ---------- ממשק נוח לתאריכים לועזיים ----------

    /** מחזיר את היום הלועזי (epoch days) בהתאם ל-RD. */
    public static HDate fromGregorian(LocalDate date) {
        long epochDays = date.toEpochDay();
        return fromRD(epochDays + UNIX_EPOCH_RD);
    }

    public static LocalDate toGregorian(HDate h) {
        long rd = toRD(h.year, h.month, h.day);
        return LocalDate.ofEpochDay(rd - UNIX_EPOCH_RD);
    }

    public static boolean isLeapYear(int y) {
        return isLeap(y);
    }

    // ---------- שמות ומספרים בעברית ----------

    public static String monthName(int m, int y) {
        switch (m) {
            case 7: return "תשרי";
            case 8: return "מרחשוון";
            case 9: return "כסלו";
            case 10: return "טבת";
            case 11: return "שבט";
            case 12: return isLeap(y) ? "אדר א׳" : "אדר";
            case 13: return "אדר ב׳";
            case 1: return "ניסן";
            case 2: return "אייר";
            case 3: return "סיוון";
            case 4: return "תמוז";
            case 5: return "אב";
            case 6: return "אלול";
            default: return "";
        }
    }

    /** מספר בעברית עם גרשיים (למשל ב׳, טו״ז). */
    public static String hebrewLetters(int n) {
        String[] ones = {"", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט"};
        String[] tens = {"", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ"};
        String[] hundreds = {"", "ק", "ר", "ש", "ת"};
        StringBuilder s = new StringBuilder();
        while (n >= 400) {
            s.append('ת');
            n -= 400;
        }
        if (n >= 100) {
            s.append(hundreds[n / 100]);
            n %= 100;
        }
        if (n == 15) return s + "טו";
        if (n == 16) return s + "טז";
        if (n >= 10) {
            s.append(tens[n / 10]);
            n %= 10;
        }
        if (n > 0) s.append(ones[n]);
        return s.toString();
    }

    static String quote(String s) {
        return s.length() <= 1 ? s + "׳" : s.substring(0, s.length() - 1) + "״" + s.substring(s.length() - 1);
    }

    public static String dayString(int d) {
        return quote(hebrewLetters(d));
    }

    public static String yearString(int y) {
        return quote(hebrewLetters(y % 1000));
    }

    /** תאריך מלא בעברית, למשל: ח׳ בטבת תשפ״ו. */
    public static String fullHebrew(LocalDate date) {
        HDate h = fromGregorian(date);
        return dayString(h.day) + " ב" + monthName(h.month, h.year) + " " + yearString(h.year);
    }
}
