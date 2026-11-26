package com.londkirs.chullin.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** פרקי המסכת (לשונית הפרקים). כרגע מוגדרים רק פרקי חולין, כמו ב-HTML המקורי. */
public final class Perakim {

    public static final class Perek {
        public final int id;
        public final String name;
        public final int start;  // אינדקס העמוד הראשון (0..)
        public final int end;    // אינדקס העמוד האחרון

        Perek(int id, String name, int start, int end) {
            this.id = id;
            this.name = name;
            this.start = start;
            this.end = end;
        }

        public boolean contains(int idx) {
            return idx >= start && idx <= end;
        }
    }

    private static final List<Perek> CHULLIN = Collections.unmodifiableList(java.util.Arrays.asList(
            new Perek(1, "הכל שוחטין", 0, 49), new Perek(2, "השוחט", 50, 79),
            new Perek(3, "אלו טרפות", 80, 131), new Perek(4, "בהמה המקשה", 132, 151),
            new Perek(5, "אותו ואת בנו", 152, 162), new Perek(6, "כסוי הדם", 163, 174),
            new Perek(7, "גיד הנשה", 175, 202), new Perek(8, "כל הבשר", 203, 231),
            new Perek(9, "העור והרוטב", 232, 256), new Perek(10, "הזרוע והלחיים", 257, 266),
            new Perek(11, "ראשית הגז", 267, 273), new Perek(12, "שילוח הקן", 274, 280)));

    private Perakim() {}

    public static List<Perek> forKey(String key) {
        return "Chullin".equals(key) ? CHULLIN : new ArrayList<Perek>();
    }

    /** הפרק שמכיל את העמוד; אם אין מידע מחזיר null. */
    public static Perek containing(List<Perek> list, int idx) {
        for (Perek p : list) {
            if (p.contains(idx)) return p;
        }
        return null;
    }
}
