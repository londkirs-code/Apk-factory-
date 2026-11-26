package com.londkirs.chullin.model;

/** ההתקדמות של עמוד אחד. שמות השדות זהים לאלה שב-HTML המקורי, כדי שגיבויים ישנים ייקראו. */
public final class AmudRecord {
    public boolean gemara;
    public boolean rashi;
    public boolean[] tosafot;
    public int chazara;   // 0..10
    public String note = "";

    public AmudRecord(int tosafotCount) {
        this.tosafot = new boolean[tosafotCount];
    }

    /** מתאים את אורך מערך התוספות לרשימה הנוכחית, ושומר את הסימונים שנשארו. */
    public void resize(int tosafotCount) {
        if (tosafot == null || tosafot.length != tosafotCount) {
            boolean[] old = tosafot == null ? new boolean[0] : tosafot;
            boolean[] next = new boolean[tosafotCount];
            for (int i = 0; i < Math.min(old.length, tosafotCount); i++) {
                next[i] = old[i];
            }
            tosafot = next;
        }
        if (note == null) note = "";
    }

    public int checkedTosafot() {
        int n = 0;
        for (boolean b : tosafot) {
            if (b) n++;
        }
        return n;
    }
}
