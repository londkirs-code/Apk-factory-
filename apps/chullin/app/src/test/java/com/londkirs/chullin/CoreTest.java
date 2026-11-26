package com.londkirs.chullin;

import com.londkirs.chullin.core.Completion;
import com.londkirs.chullin.core.DailyPage;
import com.londkirs.chullin.core.HebrewCalendar;
import com.londkirs.chullin.core.Masechet;
import com.londkirs.chullin.model.Amud;
import com.londkirs.chullin.model.AmudRecord;
import com.londkirs.chullin.model.MasechetInfo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** בדיקות ליבה בלי Android: הרצה עם javac + java (ראו הסבר בסוף). */
public final class CoreTest {

    static int failures = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "OK   " : "FAIL ") + name);
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        // לוח עברי: פסח 5786 - ט״ו בניסן = 2 באפריל 2026
        LocalDate pesach = LocalDate.of(2026, 4, 2);
        HebrewCalendar.HDate h = HebrewCalendar.fromGregorian(pesach);
        check("פסח 2026 הוא ט״ו בניסן", h.month == 1 && h.day == 15 && h.year == 5786);
        check("המרה הפוכה חוזרת לאותו תאריך",
                HebrewCalendar.toGregorian(h).equals(pesach));

        // רצף ימים: כל יום מתקדם ב-1 בלוח העברי (בדיקת עקביות, כולל מעבר חודשים)
        boolean consistent = true;
        LocalDate d = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < 1000; i++, d = d.plusDays(1)) {
            HebrewCalendar.HDate a = HebrewCalendar.fromGregorian(d);
            HebrewCalendar.HDate b = HebrewCalendar.fromGregorian(d.plusDays(1));
            if (!(HebrewCalendar.toGregorian(a).equals(d))) consistent = false;
            if (b.day != 1 && !(b.day == a.day + 1 && b.month == a.month && b.year == a.year)) consistent = false;
        }
        check("רצף ימים עקבי (1000 ימים)", consistent);

        // מסכת: 281 עמודים ו-12 פרקים
        Map<String, List<String>> db = new HashMap<>();
        db.put("2a", new ArrayList<>(List.of("מתני' הכל שוחטין", "שמא יקלקלו כו'")));
        MasechetInfo chullin = new MasechetInfo("Chullin", "חולין", 2, 142, true, "masechtot/Chullin.json");
        List<Amud> amudim = Masechet.build(chullin, db);
        check("281 עמודים", amudim.size() == 281 && Masechet.countAmudim(chullin) == 281);
        check("העמוד הראשון הוא 2a", amudim.get(0).ref.equals("2a"));
        check("העמוד האחרון הוא 142a", amudim.get(280).ref.equals("142a"));
        MasechetInfo berakhot = new MasechetInfo("Berakhot", "ברכות", 2, 3, false, "x");
        check("מסכת עם שני צדדים בדף האחרון: 4 עמודים", Masechet.countAmudim(berakhot) == 4);
        check("תוספות נטענו לעמוד 2a", amudim.get(0).totalTos() == 2);
        check("עמוד בלי תוספות מסומן", !amudim.get(1).hasRealTos);

        // כלל ה-50%: 3 מתוך 5 (עיגול כלפי מעלה)
        Amud fake = new Amud(0, 2, 'a', "2a", "", "", List.of("1", "2", "3", "4", "5"), true);
        check("50% מ-5 תוספות = 3", Masechet.requiredTosafot(fake, 50) == 3);

        // מצבי גמר
        AmudRecord rec = new AmudRecord(fake.totalTos());
        rec.gemara = true;
        rec.rashi = true;
        check("מצב full: לא גמור בלי תוספות", !Completion.isComplete(Completion.Mode.FULL, 50, fake, rec));
        rec.tosafot[0] = rec.tosafot[1] = rec.tosafot[2] = true;
        check("מצב full: גמור עם 3 מתוך 5", Completion.isComplete(Completion.Mode.FULL, 50, fake, rec));
        check("מצב gr: גמור בלי תוספות", Completion.isComplete(Completion.Mode.GR, 50, fake, new AmudRecord(5)) == false);
        AmudRecord onlyG = new AmudRecord(5);
        onlyG.gemara = true;
        check("מצב g: גמרא בלבד מספיקה", Completion.isComplete(Completion.Mode.G, 50, fake, onlyG));

        // חזרות: מתחילות ב-1 כשהעמוד נגמר, וחוזרות ל-0 כשהוא לא גמור
        AmudRecord r2 = new AmudRecord(5);
        r2.gemara = true;
        r2.rashi = true;
        r2.tosafot[0] = r2.tosafot[1] = r2.tosafot[2] = true;
        boolean celebrated = Completion.syncAfterChange(Completion.Mode.FULL, 50, fake, r2, false);
        check("חזרה מתחילה ב-1 בהשלמה", r2.chazara == 1 && celebrated);
        r2.tosafot[0] = false;
        Completion.syncAfterChange(Completion.Mode.FULL, 50, fake, r2, true);
        check("חזרות מתאפסות כשהעמוד חוזר ללא גמור", r2.chazara == 0);

        // יום יומי
        LocalDate start = LocalDate.of(2026, 1, 1);
        check("לפני תחילת הלימוד: עמוד ראשון", DailyPage.indexFor(start, start.minusDays(5), 281) == 0);
        check("יום 10 = אינדקס 9", DailyPage.indexFor(start, start.plusDays(9), 281) == 9);
        check("אחרי הסיום: העמוד האחרון", DailyPage.indexFor(start, start.plusDays(1000), 281) == 280);
        check("בלי תאריך: -1", DailyPage.indexFor(null, start, 281) == -1);

        System.out.println(failures == 0 ? "\nכל הבדיקות עברו" : ("\nנכשלו: " + failures));
        System.exit(failures == 0 ? 0 : 1);
    }
}
