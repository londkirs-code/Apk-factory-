package com.londkirs.chullin.model;

import java.util.List;

/** עמוד אחד במסכת (למשל 2a). האובייקט לא משתנה לאחר היצירה. */
public final class Amud {
    public final int index;          // 0..280
    public final int daf;            // 2..142
    public final char side;          // 'a' או 'b'
    public final String ref;         // "2a"
    public final String title;       // "דף ב׳ ע\"א"
    public final String shortTitle;  // "ב׳." או "ב׳:"
    public final List<String> tosafot;
    public final boolean hasRealTos;

    public Amud(int index, int daf, char side, String ref, String title, String shortTitle,
                List<String> tosafot, boolean hasRealTos) {
        this.index = index;
        this.daf = daf;
        this.side = side;
        this.ref = ref;
        this.title = title;
        this.shortTitle = shortTitle;
        this.tosafot = tosafot;
        this.hasRealTos = hasRealTos;
    }

    public int totalTos() {
        return hasRealTos ? tosafot.size() : 0;
    }
}
