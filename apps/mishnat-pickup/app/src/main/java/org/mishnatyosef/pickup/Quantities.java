package org.mishnatyosef.pickup;

import java.util.Locale;

/** פענוח ועיצוב כמויות: "2,5" ו-"2.5" הם 2.5; 3.0 מוצג כ-"3"; כמות לא תקינה היא 0. */
final class Quantities {
    private Quantities() {}

    static double parse(String value) {
        try { return Math.max(0,Double.parseDouble((value==null?"":value).replace(',','.'))); }
        catch(Exception ignored) { return 0; }
    }

    static String format(double value) {
        if(Math.abs(value-Math.rint(value))<0.000001)return String.valueOf((long)Math.rint(value));
        String s=String.format(Locale.US,"%.3f",value);
        while(s.contains(".")&&s.endsWith("0"))s=s.substring(0,s.length()-1);
        if(s.endsWith("."))s=s.substring(0,s.length()-1);
        return s;
    }
}
