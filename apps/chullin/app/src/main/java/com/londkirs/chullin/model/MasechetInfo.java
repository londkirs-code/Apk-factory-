package com.londkirs.chullin.model;

/** פרטי מסכת מתוך קטלוג: שם, טווח הדפים, והאם העמוד האחרון הוא ע"א בלבד. */
public final class MasechetInfo {
    public final String key;       // שם באנגלית לפי ספריא, למשל "Chullin"
    public final String heName;    // שם בעברית, למשל "חולין"
    public final int dafFrom;      // בדרך כלל 2
    public final int dafTo;        // הדף האחרון במסכת
    public final boolean lastDafSideAOnly; // true אם הדף האחרון יש לו רק ע"א
    public final String dataFile;  // נתיב ה-JSON של התוספות בתוך assets

    public MasechetInfo(String key, String heName, int dafFrom, int dafTo,
                        boolean lastDafSideAOnly, String dataFile) {
        this.key = key;
        this.heName = heName;
        this.dafFrom = dafFrom;
        this.dafTo = dafTo;
        this.lastDafSideAOnly = lastDafSideAOnly;
        this.dataFile = dataFile;
    }
}
