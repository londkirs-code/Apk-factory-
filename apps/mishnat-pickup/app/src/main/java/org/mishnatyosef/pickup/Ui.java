package org.mishnatyosef.pickup;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * ערכת העיצוב של האפליקציה: צבעים, מידות, טיפוגרפיה, אייקונים ורכיבים.
 * כל המסכים נבנים רק מהרכיבים שכאן, כדי שהמראה יהיה אחיד בכל מקום.
 *
 * עקרונות:
 * - משטחים נקיים (לבן / אפור כהה), צבע מותג אחד (סגול-אינדיגו) לפעולות ולמצב פעיל.
 * - כל כפתור מקבל אייקון וקטורי לפי המשמעות שלו (לא אימוג׳י), כך שהמראה חד בכל מכשיר.
 * - פוקוס של מקלדת תמיד ברור: מסגרת עבה בצבע המותג ומילוי כהה יותר.
 * - פינות מעוגלות ואחידות: כפתורים 16, כרטיסים 20, חלונות 28.
 */
final class Ui {
    private Ui() {}

    // ---------------------------------------------------------------------
    // פלטות צבע. כל הממשק משתמש בתפקידים (P25 משטח ... P900 כותרות, INK טקסט, MUTED טקסט משני),
    // ולכן מצב לילה הוא פשוט פלטה אחרת לאותם תפקידים.
    //   בהיר: רקע אפור-קריר בהיר, משטחים לבנים, סגול-אינדיגו לפעולות.
    //   כהה: רקע כמעט שחור, משטחים כהים, סגול בהיר לפעולות (ניגודיות AA לפחות).
    // ---------------------------------------------------------------------
    private static final int[] LIGHT = {
            /* P25  משטח */ 0xFFFFFFFF, /* P50  רקע */ 0xFFF4F4F8, /* P100 גוון רך */ 0xFFEEEBFC,
            /* P200 קו */ 0xFFE2DEF3, /* P300 */ 0xFFCBC2EF, /* P400 */ 0xFFA697EA,
            /* P500 */ 0xFF836DE4, /* P600 מותג */ 0xFF6548D9, /* P700 פוקוס */ 0xFF5233C2,
            /* P800 */ 0xFF3F2699, /* P900 כותרת */ 0xFF1E1938,
            /* INK */ 0xFF17151F, /* MUTED */ 0xFF5E5B6E, /* ON_DARK */ 0xFFFFFFFF};
    private static final int[] DARK = {
            0xFF1C1B24, 0xFF111017, 0xFF282539, 0xFF322F45, 0xFF433D62, 0xFF5F5596,
            0xFF7F6FD6, 0xFF9C8BF3, 0xFFB5A8FF, 0xFFCFC6FF, 0xFFF1EEFF,
            /* INK */ 0xFFEDEBF5, /* MUTED */ 0xFFA9A5BA, /* ON_DARK */ 0xFF130F24};

    static boolean dark = false;
    static int P25, P50, P100, P200, P300, P400, P500, P600, P700, P800, P900;
    static int INK, MUTED;
    /** BG רקע מסך, SURFACE כרטיסים וחלונות, LINE קווים, FOCUS מסגרת פוקוס, ON_DARK טקסט על כפתור ראשי. */
    static int BG, SURFACE, LINE, FOCUS, ON_DARK;
    /** צבעים למשמעות: הצלחה (נאסף) ואזהרה (מחיקה). */
    static int SUCCESS, DANGER;
    static { applyTheme(false); }

    /** מחליף את הפלטה. מסכים שנבנים מעכשיו משתמשים בצבעים החדשים. */
    static void applyTheme(boolean night) {
        int[] p = night ? DARK : LIGHT;
        dark = night;
        P25 = p[0]; P50 = p[1]; P100 = p[2]; P200 = p[3]; P300 = p[4]; P400 = p[5];
        P500 = p[6]; P600 = p[7]; P700 = p[8]; P800 = p[9]; P900 = p[10];
        INK = p[11]; MUTED = p[12]; ON_DARK = p[13];
        BG = P50; SURFACE = P25; LINE = P200; FOCUS = P700;
        SUCCESS = night ? 0xFF5FD08A : 0xFF1F8A4C;
        DANGER = night ? 0xFFFF8A80 : 0xFFC62828;
    }
    static final int IMAGE_FOCUS_OUTER=2, IMAGE_FOCUS_INSET=4, IMAGE_FOCUS_INNER=1;
    static final int SCRIM=0xEB1E1938;

    static final int S1=4, S2=8, S3=12, S4=16, S5=20, S6=24, S8=32;
    /** רווח בין כפתורים גדולים ברשת (שורות ועמודות): מספיק כדי שלא ייראו צמודים, בלי לבזבז מקום. */
    static final int GRID_GAP=10;
    static final int R_CARD=20, R_BUTTON=16, R_IMAGE=12, R_DIALOG=28;
    // גדלי טקסט בסיסיים. בפועל הם מותאמים לרוחב המסך (textScale), כדי שגם במסך קטן של
    // טלפון מקשים הטקסט יהיה גדול וברור אבל ייכנס בלי להיחתך.
    static final float T_TITLE=24, T_SECTION=17, T_BODY=18, T_NAME=19, T_AMOUNT=26,
            T_NOTE=16, T_BUTTON=17, T_HINT=15;
    static final int TAP_MIN=60, PERCENT_WIDTH=64, DIALOG_MAX_WIDTH=420,
            DIALOG_SIDE_MARGIN=24, IMAGE_SAMPLE_MIN=78, MAX_NAME_LINES=3, MAX_NOTE_LINES=2;
    /** גודל אחיד לכל כפתורי האייקון בכותרות. במסכים צרים (מכשירי מקשים) הכפתורים קטנים מעט. */
    static final int ICON_BUTTON=48, ICON_GLYPH=24;
    /** עובי מסגרת הפוקוס (dp). */
    static final int FOCUS_STROKE=3;

    static int iconButtonDp(Context c) {
        int w=c.getResources().getConfiguration().screenWidthDp;
        return w < 300 ? 38 : (w < 340 ? 42 : ICON_BUTTON);
    }

    /** האם יש מספיק רוחב לכותרת ולכפתורי האייקון באותה שורה. */
    static boolean wideHeader(Context c) {
        return c.getResources().getConfiguration().screenWidthDp >= 420;
    }

    static int dp(Context c, int v) {
        return Math.round(v*c.getResources().getDisplayMetrics().density);
    }

    /** מקדם לגודל הטקסט לפי רוחב המסך: מעט קטן יותר במסכים צרים מאוד, אף פעם לא זעיר. */
    static float textScale(Context c) {
        int w=c.getResources().getConfiguration().screenWidthDp;
        return w<280?0.86f:(w<330?0.92f:(w<380?0.97f:1f));
    }

    /** שוליים צדדיים של מסך: צרים יותר במסכים קטנים, כדי להשאיר מקום לתוכן. */
    static int screenPad(Context c) {
        int w=c.getResources().getConfiguration().screenWidthDp;
        return w<330?S3:S4;
    }

    // פונטים: Sans (ב-Android לעברית - Noto Sans Hebrew) רגיל לגוף, בינוני לכפתורים, מודגש לכותרות.
    static final Typeface FONT_REGULAR=Typeface.create("sans-serif",Typeface.NORMAL);
    static final Typeface FONT_MEDIUM=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    static final Typeface FONT_BOLD=Typeface.create("sans-serif",Typeface.BOLD);

    static int imageSize(Context c) {
        int w=c.getResources().getConfiguration().screenWidthDp;
        return dp(c,w>=400?56:(w>=340?48:36));
    }

    static GradientDrawable shape(Context c,int fill,int radiusDp,int strokeDp,int stroke) {
        GradientDrawable g=new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(c,radiusDp));
        if(strokeDp>0) g.setStroke(dp(c,strokeDp),stroke);
        return g;
    }

    /**
     * רקע לרכיב לחיץ. בפוקוס ובלחיצה: מילוי כהה יותר ומסגרת עבה בצבע הפוקוס, כך שתמיד רואים
     * איפה המקלדת נמצאת. רכיב מושבת: אפור שקט.
     */
    static StateListDrawable interactive(Context c,int fill,int border,int fillFocus,int borderFocus,int radiusDp) {
        StateListDrawable s=new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused},shape(c,fillFocus,radiusDp,FOCUS_STROKE,borderFocus));
        s.addState(new int[]{android.R.attr.state_pressed},shape(c,fillFocus,radiusDp,FOCUS_STROKE,borderFocus));
        s.addState(new int[]{-android.R.attr.state_enabled},shape(c,P50,radiusDp,1,P200));
        s.addState(new int[]{},shape(c,fill,radiusDp,border==fill?0:1,border));
        return s;
    }

    /** רקע לשדות טקסט: מסגרת דקה במנוחה, מסגרת עבה בצבע המותג כשהשדה בפוקוס. */
    static StateListDrawable inputBackground(Context c) {
        StateListDrawable s=new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused},shape(c,SURFACE,R_BUTTON,FOCUS_STROKE,P700));
        s.addState(new int[]{},shape(c,SURFACE,R_BUTTON,1,P300));
        return s;
    }

    /** מסגרת פוקוס לרכיבים שאינם כפתורים (מתגים ומחוונים), כדי שיהיה ברור איפה המקלדת נמצאת. */
    static StateListDrawable focusFrame(Context c) {
        StateListDrawable s=new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused},shape(c,P100,R_BUTTON,FOCUS_STROKE,P700));
        s.addState(new int[]{android.R.attr.state_pressed},shape(c,P100,R_BUTTON,0,0));
        s.addState(new int[]{},new ColorDrawable(Color.TRANSPARENT));
        return s;
    }

    static LinearLayout.LayoutParams row(Context c,int top,int bottom) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(c,top),0,dp(c,bottom)); return p;
    }

    static TextView text(Context c,String value,float sp,int color,boolean bold) {
        TextView t=new TextView(c); t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp*textScale(c)); t.setTextColor(color);
        t.setTypeface(bold?FONT_BOLD:FONT_REGULAR);
        // ריווח שורות נדיב ושוליים פנימיים של הפונט, כדי שעברית עם ניקוד ותווים גבוהים לא תיחתך.
        t.setIncludeFontPadding(true); t.setLineSpacing(0f,1.12f);
        t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        return t;
    }

    static LinearLayout screen(Context c) {
        LinearLayout l=column(c); l.setBackgroundColor(BG);
        int pad=dp(c,screenPad(c));
        l.setPadding(pad,dp(c,S3),pad,dp(c,S6)); return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l=new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); return l;
    }

    static TextView title(Context c,String value) {
        TextView t=text(c,value,T_TITLE,P900,true);
        t.setLayoutParams(row(c,S2,S4)); return t;
    }

    /** כותרת קטע: טקסט מודגש כהה עם מרווח מעל, בלי קווים וקישוטים. */
    static TextView section(Context c,String value) {
        TextView t=text(c,stripEmoji(value),T_SECTION,P900,true);
        t.setLayoutParams(row(c,S5,S2)); return t;
    }

    /** הודעה בולטת: כרטיס בגוון רך עם אייקון מידע. */
    static TextView banner(Context c,String value) {
        TextView t=text(c,"",T_NOTE,INK,false);
        t.setText(withIcon(c,IC_INFO,value,20));
        t.setBackground(shape(c,P100,R_BUTTON,0,0));
        t.setPadding(dp(c,S4),dp(c,S3),dp(c,S4),dp(c,S3));
        t.setLayoutParams(row(c,0,S3)); return t;
    }

    /**
     * הסבר קצר בתוך המסך: שורה או שתיים בטקסט משני, עם אייקון מידע קטן.
     * מופיע מתחת לכותרות כדי שיהיה ברור מה עושים במסך, בלי חלונות עזרה.
     */
    static TextView hint(Context c,String value) {
        TextView t=text(c,"",T_HINT,MUTED,false);
        t.setText(withIcon(c,IC_INFO,value,16));
        t.setLayoutParams(row(c,0,S2));
        return t;
    }

    /** כרטיס: משטח לבן עם פינות מעוגלות וקו עדין, לקיבוץ תוכן. */
    static LinearLayout card(Context c) {
        LinearLayout l=column(c);
        l.setBackground(shape(c,SURFACE,R_CARD,1,LINE));
        l.setPadding(dp(c,S4),dp(c,S3),dp(c,S4),dp(c,S3));
        return l;
    }

    static void styleProgress(Context c,ProgressBar p) {
        GradientDrawable track=shape(c,P200,6,0,0);
        ClipDrawable fill=new ClipDrawable(shape(c,P600,6,0,0),Gravity.RIGHT,ClipDrawable.HORIZONTAL);
        LayerDrawable layers=new LayerDrawable(new Drawable[]{track,fill});
        layers.setId(0,android.R.id.background); layers.setId(1,android.R.id.progress);
        p.setProgressDrawable(layers);
        // בממשק מימין לשמאל אנדרואיד הופך את הפס ואז הוא מתמלא משמאל. נועלים את הכיוון,
        // כך שהמילוי צמוד לימין ומתקדם שמאלה בכל המכשירים.
        if(android.os.Build.VERSION.SDK_INT>=17)p.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        p.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(c,8)));
    }

    static final int IC_SETTINGS=1, IC_RESET=2, IC_LIST_SINGLE=3, IC_LIST_STACK=4,
            IC_BACK=5, IC_CLOSE=6, IC_SEARCH=7, IC_PHOTO=8, IC_HISTORY=9, IC_INFO=10,
            IC_MAP=11, IC_PHONE=12, IC_CHEVRON_DOWN=13, IC_MORE=14,
            IC_PLUS=15, IC_MERGE=16, IC_CLOUD=17, IC_SHARE=18,
            IC_BOX=19, IC_BASKET=20, IC_DOWNLOAD=21, IC_QR=22, IC_BLUETOOTH=23, IC_CLIPBOARD=24,
            IC_FILE=25, IC_USER=26, IC_TRASH=27, IC_EDIT=28, IC_CHECK=29, IC_PLAY=30, IC_EYE=31,
            IC_SYNC=32, IC_SUN=33, IC_MOON=34, IC_KEYBOARD=35, IC_SPARKLE=36, IC_LOGOUT=37,
            IC_KEY=38, IC_SAVE=39, IC_MAIL=40, IC_CHAT=41, IC_MENU=42, IC_CHEVRON_FORWARD=43,
            IC_FOLDER=44, IC_SEND=45, IC_ARROW_UP=46, IC_ARROW_DOWN=47, IC_MINUS=48, IC_PALETTE=49,
            IC_COPY=50, IC_HOME=51;

    enum Kind { PRIMARY, SOFT, STRONG }

    /** כפתור רגיל: אייקון לפי המשמעות ואחריו הטקסט, במרכז. */
    static Button button(Context c,String label,Kind kind) {
        Button b=styleButton(c,new Button(c),"",kind);
        String clean=stripEmoji(label);
        int icon=iconFor(clean);
        b.setText(icon==0?clean:withIcon(c,icon,clean,20));
        b.setContentDescription(clean);
        return b;
    }

    /**
     * כפתור לתפריט בשתי עמודות, כמו מסך אפליקציות: אייקון גדול למעלה והשם מתחת, ממורכז.
     */
    static Button gridButton(Context c,String label,Kind kind) {
        Button b=styleButton(c,new Button(c),"",kind);
        String clean=stripEmoji(label);
        int icon=iconFor(clean);
        b.setText(icon==0?clean:iconAbove(c,icon,clean,28));
        b.setContentDescription(clean);
        b.setGravity(Gravity.CENTER);
        b.setMaxLines(Integer.MAX_VALUE); // טקסט לעולם לא נחתך
        b.setMinHeight(dp(c,84));
        b.setMinimumHeight(dp(c,84));
        b.setPadding(dp(c,S3),dp(c,S3),dp(c,S3),dp(c,S3));
        b.setLineSpacing(dp(c,3),1f);
        return b;
    }

    /**
     * אייקון לכל כפתור לפי מילות מפתח בטקסט שלו (הכלל הראשון שמתאים קובע).
     * "סגירה" ו"ביטול" נשארים בלי אייקון, כי הם מוחלפים אוטומטית בכפתור X.
     */
    private static final Object[][] BUTTON_ICONS = {
            {"מחיקת המסומנות", IC_TRASH}, {"סיום", IC_CHECK}, {"הבנתי", IC_CHECK}, {"אישור", IC_CHECK},
            {"פתיחה לאיסוף", IC_PLAY}, {"לאיסוף", IC_PLAY}, {"פתיחת רשימת", IC_PLAY},
            {"רשימת איסוף", IC_BASKET}, {"רשימות איסוף", IC_BASKET}, {"לרשימת", IC_BASKET}, {"לרשימה", IC_BASKET},
            {"יציאה", IC_LOGOUT}, {"מאוחדת", IC_MERGE}, {"איחוד", IC_MERGE}, {"מיזוג", IC_MERGE}, {"מזג", IC_MERGE},
            {"מחיקת", IC_TRASH}, {"מחיקה", IC_TRASH}, {"מחק", IC_TRASH}, {"הסר", IC_TRASH},
            {"הוצאה", IC_MINUS}, {"הבאת", IC_CLOUD}, {"ענן", IC_CLOUD},
            {"בלוטות'", IC_BLUETOOTH}, {"הדבקת", IC_CLIPBOARD}, {"הדבקה", IC_CLIPBOARD}, {"העתקה", IC_COPY},
            {"קודמת", IC_HISTORY}, {"קודמות", IC_HISTORY}, {"מהעבר", IC_HISTORY},
            {"QR", IC_QR}, {"מה חדש", IC_SPARKLE}, {"תצוגה", IC_PALETTE}, {"לברירת המחדל", IC_RESET},
            {"בהיר", IC_SUN}, {"כהה", IC_MOON}, {"לפי המכשיר", IC_PHONE},
            {"וואטסאפ", IC_CHAT}, {"SMS", IC_CHAT}, {"מייל", IC_MAIL}, {"שמירה במכשיר", IC_SAVE},
            {"אפליקציה אחרת", IC_SEND}, {"שליחת", IC_SEND}, {"קובץ טקסט", IC_FILE}, {"TXT", IC_FILE},
            {"ZIP", IC_FOLDER}, {"מקובץ", IC_FILE}, {"קובץ", IC_FILE},
            {"מקשים", IC_KEYBOARD}, {"הגדרות", IC_SETTINGS},
            {"ייבוא", IC_DOWNLOAD}, {"ייבא", IC_DOWNLOAD},
            {"התחבר מחדש", IC_KEY}, {"התנתק", IC_LOGOUT}, {"התחבר", IC_KEY}, {"התחברות", IC_KEY},
            {"חיבורים", IC_USER}, {"חשבון", IC_USER}, {"מנותק", IC_USER}, {"מחובר", IC_USER},
            {"סנכר", IC_SYNC}, {"לנסות שוב", IC_SYNC}, {"עדכן", IC_SYNC}, {"החלפה", IC_SYNC},
            {"שיתוף", IC_SHARE}, {"ייצוא", IC_SHARE},
            {"הוספת הזמנ", IC_PLUS}, {"הזמנה חדשה", IC_PLUS}, {"חדשה", IC_PLUS}, {"יצירת", IC_PLUS},
            {"הוסף", IC_PLUS}, {"הוספה", IC_PLUS}, {"צירוף", IC_PLUS},
            {"הזמנות", IC_BOX}, {"הזמנה", IC_BOX},
            {"שינוי שם", IC_EDIT}, {"שם", IC_EDIT}, {"עריכ", IC_EDIT},
            {"המיקום", IC_MAP}, {"חיוג", IC_PHONE}, {"הצגת", IC_EYE}, {"פתיחת", IC_PLAY}, {"פתיחה", IC_PLAY},
            {"שמירה", IC_SAVE}, {"שמור", IC_SAVE}, {"איפוס", IC_RESET},
            {"למעלה", IC_ARROW_UP}, {"למטה", IC_ARROW_DOWN}, {"פעולות", IC_MORE}, {"תמונות", IC_PHOTO},
            {"חזרה", IC_BACK}};

    /** האייקון שמתאים לטקסט של כפתור, או 0. */
    static int iconFor(String label) {
        if (label == null) return 0;
        String clean = label.trim();
        if (clean.isEmpty() || "סגירה".equals(clean) || "ביטול".equals(clean)) return 0;
        for (Object[] rule : BUTTON_ICONS) if (clean.contains((String) rule[0])) return (Integer) rule[1];
        return 0;
    }

    /** מסיר אימוג׳י או סמל מתחילת טקסט (הכפתורים מקבלים אייקון וקטורי במקומם). */
    static String stripEmoji(String label) {
        if (label == null) return "";
        String s = label.trim();
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (Character.isSurrogate(ch) || (ch >= 0x2000 && ch <= 0x2BFF) || ch == 0xFE0F || ch == 0x200D) { i++; continue; }
            break;
        }
        return i == 0 ? s : s.substring(i).trim();
    }

    /** תאימות: בעבר הוסיף אימוג׳י; היום מחזיר את הטקסט הנקי (האייקון מצויר בכפתור עצמו). */
    static String withEmoji(String label) { return stripEmoji(label); }

    /** טקסט עם אייקון וקטורי בתחילתו, באותו צבע של הטקסט. */
    static CharSequence withIcon(Context c, int icon, String text, int sizeDp) {
        android.text.SpannableStringBuilder s = new android.text.SpannableStringBuilder("￼  ");
        s.setSpan(new IconSpan(new IconDrawable(c, icon, INK, sizeDp, 1f), dp(c, sizeDp)), 0, 1,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.append(text == null ? "" : text);
        return s;
    }

    /** אייקון בשורה משלו ומתחתיו הטקסט (לאריחים ולכפתורי רשת). */
    static CharSequence iconAbove(Context c, int icon, String text, int sizeDp) {
        android.text.SpannableStringBuilder s = new android.text.SpannableStringBuilder("￼\n");
        s.setSpan(new IconSpan(new IconDrawable(c, icon, INK, sizeDp, 1f), dp(c, sizeDp)), 0, 1,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.append(text == null ? "" : text);
        return s;
    }

    /** צבעי טקסט לכפתור: רגיל, ומושבת (חיוור). האייקון בטקסט מקבל את אותו צבע. */
    private static ColorStateList buttonText(int color) {
        int disabled = (color & 0x00FFFFFF) | 0x66000000;
        return new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}}, new int[]{disabled, color});
    }

    private static Button styleButton(Context c,Button b,String label,Kind kind) {
        int fill,border,focusFill,focusBorder,color;
        switch(kind) {
            case PRIMARY: fill=P600;border=P600;focusFill=P700;focusBorder=P900;color=ON_DARK;break;
            case STRONG: fill=P800;border=P800;focusFill=P900;focusBorder=P400;color=ON_DARK;break;
            default: fill=P100;border=P100;focusFill=P200;focusBorder=FOCUS;color=P800;break;
        }
        b.setId(View.generateViewId()); b.setText(label);
        b.setAllCaps(false); b.setTextSize(TypedValue.COMPLEX_UNIT_SP,T_BUTTON*textScale(c));
        b.setTypeface(FONT_MEDIUM); b.setTextColor(buttonText(color));
        b.setIncludeFontPadding(true); b.setLineSpacing(0f,1.08f);
        if(Build.VERSION.SDK_INT>=21) b.setStateListAnimator(null);
        b.setFocusable(true); b.setFocusableInTouchMode(false);
        b.setBackground(interactive(c,fill,border,focusFill,focusBorder,R_BUTTON));
        // גובה מינימלי נוח ללחיצה ולפוקוס במקלדת; טקסט ארוך יורד שורה ולא נחתך.
        b.setMinHeight(dp(c,50)); b.setMinimumHeight(dp(c,50));
        // Default breathing room for every vertically stacked action button.
        // Call sites in horizontal button groups provide their own side margins.
        b.setLayoutParams(row(c,S2,S2));
        b.setGravity(Gravity.CENTER);
        // טקסט בכפתור לעולם לא נחתך: אין מגבלת שורות ואין קיצור, רוחב מינימלי של ערכת המערכת
        // מבוטל (הוא דחף כפתורים צמודים מעבר לשטח), והריפוד הצדדי קטן יותר במסכים צרים.
        b.setSingleLine(false);
        b.setMaxLines(Integer.MAX_VALUE);
        b.setEllipsize(null);
        b.setHorizontallyScrolling(false);
        b.setMinWidth(0); b.setMinimumWidth(0);
        if(Build.VERSION.SDK_INT>=23) b.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_BALANCED);
        int side=c.getResources().getConfiguration().screenWidthDp<360?S2+2:S4;
        b.setPadding(dp(c,side),dp(c,S2),dp(c,side),dp(c,S2));
        return b;
    }

    // ---------------------------------------------------------------------
    // אייקונים וקטוריים. כל האייקונים מצוירים על משטח 24×24 עם עובי קו אחיד,
    // ולכן הם חדים בכל גודל ובכל צפיפות מסך, ואינם תלויים בגופן האימוג׳י של המכשיר.
    // ---------------------------------------------------------------------


    /** אייקון כטקסט בתוך שורה: ממורכז לגובה השורה ובצבע הטקסט שמסביבו. */
    static final class IconSpan extends android.text.style.ReplacementSpan {
        private final IconDrawable icon;
        private final int size;
        IconSpan(IconDrawable icon, int sizePx) { this.icon = icon; this.size = sizePx; }
        @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm != null) {
                Paint.FontMetricsInt p = paint.getFontMetricsInt();
                int textHeight = p.descent - p.ascent;
                int extra = Math.max(0, size - textHeight);
                fm.ascent = p.ascent - extra / 2; fm.top = Math.min(p.top, fm.ascent);
                fm.descent = p.descent + (extra - extra / 2); fm.bottom = Math.max(p.bottom, fm.descent);
            }
            return size;
        }
        @Override public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                                   int top, int y, int bottom, Paint paint) {
            Paint.FontMetricsInt p = paint.getFontMetricsInt();
            float centerY = y + (p.ascent + p.descent) / 2f;
            int l = Math.round(x), t = Math.round(centerY - size / 2f);
            icon.setColor(paint.getColor());
            icon.setBounds(l, t, l + size, t + size);
            icon.draw(canvas);
        }
    }

    /** עיגול בגוון רך עם אייקון באמצע: לתחילת שורות ברשימה. */
    static final class IconBubble extends Drawable {
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final IconDrawable icon;
        private final int size;
        IconBubble(Context c, int iconId, int bgColor, int iconColor, int sizeDp) {
            bg.setColor(bgColor);
            icon = new IconDrawable(c, iconId, iconColor, 0, 0.56f);
            size = dp(c, sizeDp);
        }
        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), Math.min(b.width(), b.height()) / 2f, bg);
            icon.setBounds(b);
            icon.draw(canvas);
        }
        @Override public void setAlpha(int alpha) { bg.setAlpha(alpha); icon.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { bg.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
    }

    static final class IconDrawable extends Drawable {
        private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path=new Path();
        private final Path cut=new Path();
        private final RectF box=new RectF();
        private final int icon, intrinsic;
        private final float fraction;

        /** sizeDp&lt;=0 יוצר אייקון ללא גודל טבעי, שממלא את הגבולות שהוא מקבל. */
        IconDrawable(Context c,int icon,int color,int sizeDp,float fraction) {
            this.icon=icon; this.intrinsic=sizeDp<=0?-1:dp(c,sizeDp);
            this.fraction=fraction<=0?1f:fraction;
            stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND); stroke.setColor(color);
            fill.setStyle(Paint.Style.FILL); fill.setColor(color);
        }

        void setColor(int color) {
            if (stroke.getColor() == color) return;
            stroke.setColor(color); fill.setColor(color); invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            Rect b=getBounds();
            if(b.isEmpty()) return;
            float side=Math.min(b.width(),b.height())*fraction;
            int save=canvas.save();
            canvas.translate(b.exactCenterX()-side/2f,b.exactCenterY()-side/2f);
            canvas.scale(side/24f,side/24f);
            stroke.setStrokeWidth(2f);
            switch(icon) {
                case IC_SETTINGS: drawGear(canvas); break;
                case IC_RESET: drawReset(canvas); break;
                case IC_LIST_SINGLE: drawSingleList(canvas); break;
                case IC_LIST_STACK: drawStackedLists(canvas); break;
                case IC_BACK:
                    canvas.drawLine(5f,12f,19f,12f,stroke);
                    path.reset(); path.moveTo(13f,6f); path.lineTo(19f,12f); path.lineTo(13f,18f);
                    canvas.drawPath(path,stroke); break;
                case IC_CLOSE:
                    canvas.drawLine(6.5f,6.5f,17.5f,17.5f,stroke);
                    canvas.drawLine(17.5f,6.5f,6.5f,17.5f,stroke); break;
                case IC_SEARCH:
                    canvas.drawCircle(10.5f,10.5f,6.2f,stroke);
                    canvas.drawLine(15.2f,15.2f,20f,20f,stroke); break;
                case IC_PHOTO: drawPhoto(canvas); break;
                case IC_HISTORY: drawHistory(canvas); break;
                case IC_INFO:
                    canvas.drawCircle(12f,12f,9f,stroke);
                    canvas.drawLine(12f,11f,12f,16.8f,stroke);
                    canvas.drawCircle(12f,7.6f,1.25f,fill); break;
                case IC_MAP:
                    path.reset(); path.moveTo(12f,21f);
                    path.cubicTo(7f,15.5f,5f,12.5f,5f,9.5f);
                    path.cubicTo(5f,5.6f,8.1f,2.8f,12f,2.8f);
                    path.cubicTo(15.9f,2.8f,19f,5.6f,19f,9.5f);
                    path.cubicTo(19f,12.5f,17f,15.5f,12f,21f);
                    canvas.drawPath(path,stroke); canvas.drawCircle(12f,9.5f,2.4f,stroke); break;
                case IC_PLUS:
                    canvas.drawLine(12f,5f,12f,19f,stroke); canvas.drawLine(5f,12f,19f,12f,stroke); break;
                case IC_MINUS:
                    canvas.drawCircle(12f,12f,9f,stroke); canvas.drawLine(8f,12f,16f,12f,stroke); break;
                case IC_MERGE:
                    path.reset();
                    path.moveTo(6f,3.5f); path.lineTo(6f,9f); path.lineTo(12f,15f); path.lineTo(12f,20.5f);
                    path.moveTo(18f,3.5f); path.lineTo(18f,9f); path.lineTo(12f,15f);
                    canvas.drawPath(path,stroke);
                    path.reset(); path.moveTo(9f,18f); path.lineTo(12f,21f); path.lineTo(15f,18f);
                    canvas.drawPath(path,stroke); break;
                case IC_SHARE:
                    canvas.drawCircle(17.5f,5.5f,2.6f,stroke); canvas.drawCircle(6.5f,12f,2.6f,stroke); canvas.drawCircle(17.5f,18.5f,2.6f,stroke);
                    canvas.drawLine(8.8f,10.7f,15.2f,6.8f,stroke); canvas.drawLine(8.8f,13.3f,15.2f,17.2f,stroke); break;
                case IC_CLOUD:
                    path.reset();
                    path.moveTo(7f,18.5f); path.lineTo(17.5f,18.5f);
                    path.cubicTo(20.8f,18.5f,21.6f,14f,18.6f,12.8f);
                    path.cubicTo(18.4f,8.6f,13.6f,7f,11.4f,9.8f);
                    path.cubicTo(9.2f,7.4f,5.2f,8.8f,5.6f,12.4f);
                    path.cubicTo(2.6f,13.2f,3.2f,18.5f,7f,18.5f);
                    path.close(); canvas.drawPath(path,stroke); break;
                case IC_MORE:
                    canvas.drawCircle(12f,5.5f,1.9f,fill); canvas.drawCircle(12f,12f,1.9f,fill);
                    canvas.drawCircle(12f,18.5f,1.9f,fill); break;
                case IC_CHEVRON_DOWN:
                    path.reset(); path.moveTo(6f,9.5f); path.lineTo(12f,15.5f); path.lineTo(18f,9.5f);
                    stroke.setStrokeWidth(2.4f); canvas.drawPath(path,stroke); break;
                case IC_CHEVRON_FORWARD:
                    // "קדימה" בעברית: חץ שמאלה.
                    path.reset(); path.moveTo(14.5f,6f); path.lineTo(8.5f,12f); path.lineTo(14.5f,18f);
                    stroke.setStrokeWidth(2.4f); canvas.drawPath(path,stroke); break;
                case IC_PHONE:
                    box.set(7f,2.5f,17f,21.5f); canvas.drawRoundRect(box,2.2f,2.2f,stroke);
                    canvas.drawLine(10.5f,18.3f,13.5f,18.3f,stroke); break;
                case IC_BOX:
                    stroke.setStrokeWidth(1.8f);
                    path.reset(); path.moveTo(3.5f,7.5f); path.lineTo(12f,3f); path.lineTo(20.5f,7.5f);
                    path.lineTo(20.5f,16.5f); path.lineTo(12f,21f); path.lineTo(3.5f,16.5f); path.close();
                    path.moveTo(3.5f,7.5f); path.lineTo(12f,12f); path.lineTo(20.5f,7.5f);
                    path.moveTo(12f,12f); path.lineTo(12f,21f);
                    path.moveTo(7.8f,5.2f); path.lineTo(16.2f,9.8f);
                    canvas.drawPath(path,stroke); break;
                case IC_BASKET:
                    stroke.setStrokeWidth(1.8f);
                    path.reset(); path.moveTo(2.8f,9.5f); path.lineTo(21.2f,9.5f); path.lineTo(19f,19.8f);
                    path.lineTo(5f,19.8f); path.close();
                    path.moveTo(7.5f,9.5f); path.lineTo(10.5f,4f);
                    path.moveTo(16.5f,9.5f); path.lineTo(13.5f,4f);
                    path.moveTo(9f,12.8f); path.lineTo(9.4f,16.6f);
                    path.moveTo(12f,12.8f); path.lineTo(12f,16.6f);
                    path.moveTo(15f,12.8f); path.lineTo(14.6f,16.6f);
                    canvas.drawPath(path,stroke); break;
                case IC_DOWNLOAD:
                    path.reset(); path.moveTo(12f,3.5f); path.lineTo(12f,14.5f);
                    path.moveTo(7.5f,10f); path.lineTo(12f,14.5f); path.lineTo(16.5f,10f);
                    path.moveTo(4f,15.5f); path.lineTo(4f,19.5f); path.lineTo(20f,19.5f); path.lineTo(20f,15.5f);
                    canvas.drawPath(path,stroke); break;
                case IC_QR:
                    stroke.setStrokeWidth(1.7f);
                    box.set(3.5f,3.5f,9.5f,9.5f); canvas.drawRoundRect(box,1.2f,1.2f,stroke);
                    box.set(14.5f,3.5f,20.5f,9.5f); canvas.drawRoundRect(box,1.2f,1.2f,stroke);
                    box.set(3.5f,14.5f,9.5f,20.5f); canvas.drawRoundRect(box,1.2f,1.2f,stroke);
                    canvas.drawRect(5.6f,5.6f,7.4f,7.4f,fill); canvas.drawRect(16.6f,5.6f,18.4f,7.4f,fill);
                    canvas.drawRect(5.6f,16.6f,7.4f,18.4f,fill);
                    canvas.drawRect(14.5f,14.5f,16.5f,16.5f,fill); canvas.drawRect(18.5f,14.5f,20.5f,16.5f,fill);
                    canvas.drawRect(16.5f,16.5f,18.5f,18.5f,fill); canvas.drawRect(14.5f,18.5f,16.5f,20.5f,fill);
                    canvas.drawRect(18.5f,18.5f,20.5f,20.5f,fill); break;
                case IC_BLUETOOTH:
                    path.reset(); path.moveTo(7f,7.5f); path.lineTo(17f,16.5f); path.lineTo(12f,21f);
                    path.lineTo(12f,3f); path.lineTo(17f,7.5f); path.lineTo(7f,16.5f);
                    canvas.drawPath(path,stroke); break;
                case IC_CLIPBOARD:
                    stroke.setStrokeWidth(1.8f);
                    box.set(5f,4.5f,19f,21f); canvas.drawRoundRect(box,2f,2f,stroke);
                    box.set(9f,2.5f,15f,6.5f); canvas.drawRoundRect(box,1.2f,1.2f,stroke);
                    canvas.drawLine(8.5f,11f,15.5f,11f,stroke); canvas.drawLine(8.5f,15f,13.5f,15f,stroke); break;
                case IC_COPY:
                    stroke.setStrokeWidth(1.8f);
                    box.set(8f,8f,20f,20f); canvas.drawRoundRect(box,2f,2f,stroke);
                    path.reset(); path.moveTo(16f,8f); path.lineTo(16f,5.5f); path.quadTo(16f,4f,14.5f,4f);
                    path.lineTo(5.5f,4f); path.quadTo(4f,4f,4f,5.5f); path.lineTo(4f,14.5f); path.quadTo(4f,16f,5.5f,16f); path.lineTo(8f,16f);
                    canvas.drawPath(path,stroke); break;
                case IC_FILE:
                    stroke.setStrokeWidth(1.8f);
                    path.reset(); path.moveTo(6f,2.5f); path.lineTo(14f,2.5f); path.lineTo(19f,7.5f);
                    path.lineTo(19f,21.5f); path.lineTo(6f,21.5f); path.close();
                    path.moveTo(14f,2.5f); path.lineTo(14f,7.5f); path.lineTo(19f,7.5f);
                    canvas.drawPath(path,stroke);
                    canvas.drawLine(9f,12.5f,16f,12.5f,stroke); canvas.drawLine(9f,16.5f,14f,16.5f,stroke); break;
                case IC_FOLDER:
                    stroke.setStrokeWidth(1.8f);
                    path.reset(); path.moveTo(3f,6f); path.lineTo(9.5f,6f); path.lineTo(11.5f,8.5f);
                    path.lineTo(21f,8.5f); path.lineTo(21f,19f); path.lineTo(3f,19f); path.close();
                    canvas.drawPath(path,stroke); break;
                case IC_USER:
                    canvas.drawCircle(12f,8f,4f,stroke);
                    path.reset(); path.moveTo(4.5f,20.5f); path.cubicTo(5f,15.6f,8.4f,13.6f,12f,13.6f);
                    path.cubicTo(15.6f,13.6f,19f,15.6f,19.5f,20.5f); canvas.drawPath(path,stroke); break;
                case IC_TRASH:
                    stroke.setStrokeWidth(1.8f);
                    canvas.drawLine(4f,6.5f,20f,6.5f,stroke);
                    path.reset(); path.moveTo(9.5f,6.5f); path.lineTo(9.5f,3.8f); path.lineTo(14.5f,3.8f); path.lineTo(14.5f,6.5f);
                    path.moveTo(6f,6.5f); path.lineTo(7f,20.5f); path.lineTo(17f,20.5f); path.lineTo(18f,6.5f);
                    canvas.drawPath(path,stroke);
                    canvas.drawLine(10f,10.5f,10f,16.5f,stroke); canvas.drawLine(14f,10.5f,14f,16.5f,stroke); break;
                case IC_EDIT:
                    stroke.setStrokeWidth(1.8f);
                    path.reset(); path.moveTo(4f,20f); path.lineTo(4.8f,16.2f); path.lineTo(15.5f,5.5f);
                    path.lineTo(18.5f,8.5f); path.lineTo(7.8f,19.2f); path.close();
                    path.moveTo(13.3f,7.7f); path.lineTo(16.3f,10.7f);
                    canvas.drawPath(path,stroke); break;
                case IC_CHECK:
                    path.reset(); path.moveTo(5f,12.5f); path.lineTo(10f,17.5f); path.lineTo(19f,7f);
                    stroke.setStrokeWidth(2.4f); canvas.drawPath(path,stroke); break;
                case IC_PLAY:
                    path.reset(); path.moveTo(8f,5f); path.lineTo(19f,12f); path.lineTo(8f,19f); path.close();
                    canvas.drawPath(path,stroke); break;
                case IC_EYE:
                    stroke.setStrokeWidth(1.8f);
                    path.reset(); path.moveTo(2.5f,12f); path.cubicTo(5f,7f,8.5f,5f,12f,5f);
                    path.cubicTo(15.5f,5f,19f,7f,21.5f,12f); path.cubicTo(19f,17f,15.5f,19f,12f,19f);
                    path.cubicTo(8.5f,19f,5f,17f,2.5f,12f); path.close();
                    canvas.drawPath(path,stroke); canvas.drawCircle(12f,12f,3f,stroke); break;
                case IC_SYNC:
                    drawArrowArc(canvas,-160f,150f); drawArrowArc(canvas,20f,150f); break;
                case IC_SUN:
                    canvas.drawCircle(12f,12f,4f,stroke);
                    for(int k=0;k<8;k++){double a=Math.toRadians(k*45);
                        canvas.drawLine((float)(12+7*Math.cos(a)),(float)(12+7*Math.sin(a)),
                                (float)(12+9.5*Math.cos(a)),(float)(12+9.5*Math.sin(a)),stroke);} break;
                case IC_MOON:
                    path.reset(); path.addCircle(11f,13f,8f,Path.Direction.CW);
                    cut.reset(); cut.addCircle(16.5f,8f,6.5f,Path.Direction.CW);
                    if(Build.VERSION.SDK_INT>=19) path.op(cut,Path.Op.DIFFERENCE);
                    canvas.drawPath(path,stroke); break;
                case IC_KEYBOARD:
                    stroke.setStrokeWidth(1.8f);
                    box.set(2.5f,6.5f,21.5f,18f); canvas.drawRoundRect(box,2f,2f,stroke);
                    for(int k=0;k<5;k++){canvas.drawCircle(6f+k*3f,10f,0.9f,fill);canvas.drawCircle(6f+k*3f,12.8f,0.9f,fill);}
                    canvas.drawLine(8.5f,15.4f,15.5f,15.4f,stroke); break;
                case IC_SPARKLE:
                    stroke.setStrokeWidth(1.8f);
                    path.reset(); path.moveTo(11f,3f); path.lineTo(12.8f,9.2f); path.lineTo(19f,11f);
                    path.lineTo(12.8f,12.8f); path.lineTo(11f,19f); path.lineTo(9.2f,12.8f); path.lineTo(3f,11f);
                    path.lineTo(9.2f,9.2f); path.close(); canvas.drawPath(path,stroke);
                    canvas.drawLine(19f,16f,19f,21f,stroke); canvas.drawLine(16.5f,18.5f,21.5f,18.5f,stroke); break;
                case IC_LOGOUT:
                    path.reset(); path.moveTo(13f,4f); path.lineTo(5f,4f); path.lineTo(5f,20f); path.lineTo(13f,20f);
                    path.moveTo(10f,12f); path.lineTo(20f,12f); path.moveTo(16.5f,8.5f); path.lineTo(20f,12f); path.lineTo(16.5f,15.5f);
                    canvas.drawPath(path,stroke); break;
                case IC_KEY:
                    canvas.drawCircle(8f,12f,4f,stroke);
                    path.reset(); path.moveTo(12f,12f); path.lineTo(21f,12f); path.moveTo(18f,12f); path.lineTo(18f,15.5f);
                    path.moveTo(15f,12f); path.lineTo(15f,14.5f); canvas.drawPath(path,stroke); break;
                case IC_SAVE:
                    stroke.setStrokeWidth(1.8f);
                    path.reset(); path.moveTo(4f,4f); path.lineTo(17f,4f); path.lineTo(20f,7f); path.lineTo(20f,20f);
                    path.lineTo(4f,20f); path.close(); path.moveTo(8f,4f); path.lineTo(8f,9f); path.lineTo(15f,9f); path.lineTo(15f,4f);
                    canvas.drawPath(path,stroke); box.set(7f,13.5f,17f,20f); canvas.drawRect(box,stroke); break;
                case IC_MAIL:
                    stroke.setStrokeWidth(1.8f);
                    box.set(3f,5.5f,21f,18.5f); canvas.drawRoundRect(box,2f,2f,stroke);
                    path.reset(); path.moveTo(3.8f,6.8f); path.lineTo(12f,13f); path.lineTo(20.2f,6.8f);
                    canvas.drawPath(path,stroke); break;
                case IC_CHAT:
                    stroke.setStrokeWidth(1.8f);
                    path.reset(); path.moveTo(7f,4f); path.lineTo(17f,4f); path.quadTo(21f,4f,21f,8f); path.lineTo(21f,12.5f);
                    path.quadTo(21f,16.5f,17f,16.5f); path.lineTo(11.5f,16.5f); path.lineTo(6.5f,20.5f); path.lineTo(7f,16.5f);
                    path.quadTo(3f,16.5f,3f,12.5f); path.lineTo(3f,8f); path.quadTo(3f,4f,7f,4f); path.close();
                    canvas.drawPath(path,stroke); break;
                case IC_MENU:
                    canvas.drawLine(4f,6.5f,20f,6.5f,stroke); canvas.drawLine(4f,12f,20f,12f,stroke);
                    canvas.drawLine(4f,17.5f,20f,17.5f,stroke); break;
                case IC_SEND:
                    stroke.setStrokeWidth(1.8f);
                    path.reset(); path.moveTo(3f,11f); path.lineTo(21f,3f); path.lineTo(14f,21f); path.lineTo(11f,13.5f); path.close();
                    path.moveTo(11f,13.5f); path.lineTo(21f,3f); canvas.drawPath(path,stroke); break;
                case IC_ARROW_UP:
                    path.reset(); path.moveTo(12f,19f); path.lineTo(12f,5f); path.moveTo(6.5f,10.5f); path.lineTo(12f,5f); path.lineTo(17.5f,10.5f);
                    canvas.drawPath(path,stroke); break;
                case IC_ARROW_DOWN:
                    path.reset(); path.moveTo(12f,5f); path.lineTo(12f,19f); path.moveTo(6.5f,13.5f); path.lineTo(12f,19f); path.lineTo(17.5f,13.5f);
                    canvas.drawPath(path,stroke); break;
                case IC_PALETTE:
                    stroke.setStrokeWidth(1.8f);
                    path.reset(); path.moveTo(12f,3f); path.cubicTo(6.5f,3f,3f,7f,3f,12f); path.cubicTo(3f,17f,7f,21f,11.5f,21f);
                    path.cubicTo(13f,21f,13.5f,19.8f,12.8f,18.6f); path.cubicTo(12f,17.2f,12.8f,15.6f,14.5f,15.6f);
                    path.lineTo(17f,15.6f); path.cubicTo(19.4f,15.6f,21f,14f,21f,11.5f); path.cubicTo(21f,6.8f,17f,3f,12f,3f); path.close();
                    canvas.drawPath(path,stroke);
                    canvas.drawCircle(7.8f,11f,1.3f,fill); canvas.drawCircle(10.5f,7.2f,1.3f,fill); canvas.drawCircle(15.5f,7.8f,1.3f,fill); break;
                case IC_HOME:
                    stroke.setStrokeWidth(1.8f);
                    path.reset(); path.moveTo(3.5f,11f); path.lineTo(12f,3.8f); path.lineTo(20.5f,11f);
                    path.moveTo(6f,9f); path.lineTo(6f,20f); path.lineTo(18f,20f); path.lineTo(18f,9f);
                    canvas.drawPath(path,stroke); break;
                default: break;
            }
            canvas.restoreToCount(save);
        }

        /** קשת עם ראש חץ בסופה (לסנכרון). */
        private void drawArrowArc(Canvas canvas,float start,float sweep) {
            float r=7.6f;
            box.set(12-r,12-r,12+r,12+r);
            canvas.drawArc(box,start,sweep,false,stroke);
            double end=Math.toRadians(start+sweep);
            float px=(float)(12+r*Math.cos(end)), py=(float)(12+r*Math.sin(end));
            float tx=(float)-Math.sin(end), ty=(float)Math.cos(end);
            float nx=(float)Math.cos(end), ny=(float)Math.sin(end);
            path.reset();
            path.moveTo(px+tx*3f,py+ty*3f);
            path.lineTo(px+nx*2.7f-tx*0.5f,py+ny*2.7f-ty*0.5f);
            path.lineTo(px-nx*2.7f-tx*0.5f,py-ny*2.7f-ty*0.5f);
            path.close();
            canvas.drawPath(path,fill);
        }

        private void drawGear(Canvas canvas) {
            stroke.setStrokeWidth(1.8f);
            path.reset();
            for(int k=0;k<8;k++) {
                double a=Math.toRadians(k*45-90);
                double[] angles={a-Math.toRadians(17),a-Math.toRadians(9),a+Math.toRadians(9),a+Math.toRadians(17)};
                double[] radii={7.3,9.8,9.8,7.3};
                for(int j=0;j<4;j++) {
                    float x=(float)(12+radii[j]*Math.cos(angles[j]));
                    float y=(float)(12+radii[j]*Math.sin(angles[j]));
                    if(k==0&&j==0) path.moveTo(x,y); else path.lineTo(x,y);
                }
            }
            path.close();
            canvas.drawPath(path,stroke);
            canvas.drawCircle(12f,12f,3.1f,stroke);
        }

        private void drawReset(Canvas canvas) {
            float r=7.6f, start=-25f, sweep=285f;
            box.set(12-r,12-r,12+r,12+r);
            canvas.drawArc(box,start,sweep,false,stroke);
            double end=Math.toRadians(start+sweep);
            float px=(float)(12+r*Math.cos(end)), py=(float)(12+r*Math.sin(end));
            float tx=(float)-Math.sin(end), ty=(float)Math.cos(end);   // כיוון התנועה
            float nx=(float)Math.cos(end), ny=(float)Math.sin(end);    // כיוון רדיאלי
            path.reset();
            path.moveTo(px+tx*3.4f,py+ty*3.4f);
            path.lineTo(px+nx*3.1f-tx*0.6f,py+ny*3.1f-ty*0.6f);
            path.lineTo(px-nx*3.1f-tx*0.6f,py-ny*3.1f-ty*0.6f);
            path.close();
            canvas.drawPath(path,fill);
        }

        private void drawSingleList(Canvas canvas) {
            stroke.setStrokeWidth(1.8f);
            box.set(5.5f,3f,18.5f,21f);
            canvas.drawRoundRect(box,2.2f,2.2f,stroke);
            canvas.drawLine(9f,8.5f,15f,8.5f,stroke);
            canvas.drawLine(9f,12f,15f,12f,stroke);
            canvas.drawLine(11f,15.5f,15f,15.5f,stroke);
        }

        private void drawStackedLists(Canvas canvas) {
            stroke.setStrokeWidth(1.7f);
            box.set(3f,8.5f,14.5f,21.5f);
            canvas.drawRoundRect(box,1.8f,1.8f,stroke);
            canvas.drawLine(6f,12.8f,11.5f,12.8f,stroke);
            canvas.drawLine(6f,16.3f,11.5f,16.3f,stroke);
            path.reset();
            path.moveTo(7f,8.5f); path.lineTo(7f,5.5f); path.lineTo(18f,5.5f);
            path.lineTo(18f,18f); path.lineTo(14.5f,18f);
            path.moveTo(11f,5.5f); path.lineTo(11f,2.5f); path.lineTo(21.5f,2.5f);
            path.lineTo(21.5f,14.5f); path.lineTo(18f,14.5f);
            canvas.drawPath(path,stroke);
        }

        private void drawPhoto(Canvas canvas) {
            stroke.setStrokeWidth(1.6f);
            box.set(3f,5f,21f,19f);
            canvas.drawRoundRect(box,2.5f,2.5f,stroke);
            canvas.drawCircle(8.5f,10f,1.7f,stroke);
            path.reset();
            path.moveTo(4f,17.5f); path.lineTo(9.5f,12.5f); path.lineTo(13.5f,16f);
            path.lineTo(16.5f,13.5f); path.lineTo(20f,16.5f);
            canvas.drawPath(path,stroke);
        }

        private void drawHistory(Canvas canvas) {
            stroke.setStrokeWidth(1.8f);
            canvas.drawCircle(12f,12f,8.5f,stroke);
            path.reset(); path.moveTo(12f,7.5f); path.lineTo(12f,12f); path.lineTo(15.5f,14f);
            canvas.drawPath(path,stroke);
        }

        @Override public void setAlpha(int alpha) { stroke.setAlpha(alpha); fill.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            stroke.setColorFilter(filter); fill.setColorFilter(filter); invalidateSelf();
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return intrinsic; }
        @Override public int getIntrinsicHeight() { return intrinsic; }
    }


    /** כפתור שמצייר אייקון וקטורי במרכזו, עם אותו רקע, פוקוס והודעת פעולה כמו שאר הכפתורים. */
    static final class IconButton extends Button {
        private final IconDrawable icon;
        private final int iconPx;
        IconButton(Context c,IconDrawable icon,int iconDp) {
            super(c); this.icon=icon; this.iconPx=dp(c,iconDp); icon.setCallback(this);
        }
        IconDrawable icon() { return icon; }
        @Override protected boolean verifyDrawable(Drawable who) { return who==icon||super.verifyDrawable(who); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int l=(getWidth()-iconPx)/2, t=(getHeight()-iconPx)/2;
            icon.setBounds(l,t,l+iconPx,t+iconPx);
            icon.setAlpha(isEnabled()?255:110);
            icon.draw(canvas);
        }
    }

    /** כפתור אייקון עגול בגודל אחיד. active מסמן מצב בחירה (למשל מצב התצוגה הפעיל). */
    static Button iconButton(Context c,int icon,String description,boolean active) {
        IconButton b=new IconButton(c,new IconDrawable(c,icon,active?ON_DARK:P800,ICON_GLYPH,1f),ICON_GLYPH);
        styleButton(c,b,"",Kind.SOFT);
        b.setPadding(0,0,0,0);
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setContentDescription(description);
        int size=iconButtonDp(c);
        b.setBackground(interactive(c,active?P600:P100,active?P600:P100,
                active?P700:P200,active?P900:FOCUS,size/2));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c,size),dp(c,size)));
        return b;
    }

    /**
     * אריח ריבועי גדול (מסך הבית כשאין עדיין הזמנות): מעבר צבעים, פינות מעוגלות, צל, אייקון שנושם בעדינות,
     * והגדלה קלה עם מסגרת בהירה כשהוא בפוקוס או נלחץ.
     */
    static View heroTile(Context c, String emoji, String title, String subtitle) {
        TextView icon = text(c, emoji, 56, 0xFFFFFFFF, false);
        icon.setGravity(Gravity.CENTER);
        return heroTileWith(c, icon, title, subtitle);
    }

    static View heroTile(Context c, int iconId, String title, String subtitle) {
        ImageView icon = new ImageView(c);
        icon.setImageDrawable(new IconDrawable(c, iconId, 0xFFFFFFFF, 0, 1f));
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 60), dp(c, 60)));
        return heroTileWith(c, icon, title, subtitle);
    }

    private static View heroTileWith(Context c, View icon, String title, String subtitle) {
        final LinearLayout tile = new LinearLayout(c);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true); tile.setClickable(true);
        tile.setContentDescription(title + ". " + subtitle);
        int pad = dp(c, S4);
        tile.setPadding(pad, pad, pad, pad);
        GradientDrawable normal = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{P500, P600, P800});
        normal.setCornerRadius(dp(c, 32));
        GradientDrawable focused = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{lighten(P500, .15f), P600, P700});
        focused.setCornerRadius(dp(c, 32));
        focused.setStroke(dp(c, 4), dark ? P900 : 0xFF1E1938);
        GradientDrawable pressed = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{P600, P700, darken(P800, .2f)});
        pressed.setCornerRadius(dp(c, 32));
        pressed.setStroke(dp(c, 4), lighten(P300, .3f));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        tile.setBackground(states);
        if (Build.VERSION.SDK_INT >= 21) tile.setElevation(dp(c, 8));

        if (icon.getLayoutParams() instanceof LinearLayout.LayoutParams)
            ((LinearLayout.LayoutParams) icon.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        tile.addView(icon, icon.getLayoutParams() == null ? new LinearLayout.LayoutParams(-2, -2) : icon.getLayoutParams());
        TextView name = text(c, title, 21, 0xFFFFFFFF, true);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, -2);
        nameLp.topMargin = dp(c, S2);
        tile.addView(name, nameLp);
        TextView sub = text(c, subtitle, 13, 0xE6FFFFFF, false);
        sub.setGravity(Gravity.CENTER);
        tile.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        // האייקון "נושם" לאט, כדי למשוך את העין לכפתור.
        android.animation.ObjectAnimator sx = android.animation.ObjectAnimator.ofFloat(icon, "scaleX", 1f, 1.08f);
        android.animation.ObjectAnimator sy = android.animation.ObjectAnimator.ofFloat(icon, "scaleY", 1f, 1.08f);
        for (android.animation.ObjectAnimator a : new android.animation.ObjectAnimator[]{sx, sy}) {
            a.setDuration(1200); a.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            a.setRepeatMode(android.animation.ValueAnimator.REVERSE); a.start();
        }
        tile.setOnFocusChangeListener((v, has) -> v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(160).start());
        tile.setOnKeyListener((v, keyCode, event) -> {
            boolean confirm = keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER
                    || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == android.view.KeyEvent.KEYCODE_5;
            if (!confirm) return false;
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) v.performClick();
            return true;
        });
        return tile;
    }

    /** אריח פעולה: אייקון ומתחתיו תווית קצרה. כמה אריחים בשורה אחת מקבצים פעולות קבועות בראש מסך. */
    static Button tile(Context c,int icon,String label) {
        Button b=styleButton(c,new Button(c),"",Kind.SOFT);
        String clean=label==null?"":label.trim();
        b.setText(iconAbove(c,icon,clean,22));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP,13.5f*textScale(c));
        b.setLineSpacing(dp(c,2),1f);
        b.setPadding(dp(c,2),dp(c,S2),dp(c,2),dp(c,S2));
        b.setMinHeight(dp(c,64)); b.setMinimumHeight(dp(c,64));
        b.setContentDescription(clean);
        return b;
    }

    /** שורת רשימה: שם בולט ומתחתיו פרטים בגופן קטן יותר, על כרטיס לבן. */
    static Button listRow(Context c,String name,String details,boolean highlighted) {
        return listItem(c,0,name,details,highlighted,false);
    }

    /**
     * פריט ברשימה: עיגול אייקון בהתחלה, שם מודגש ומתחתיו שורת פרטים, וחץ "קדימה" בסוף כשהלחיצה פותחת מסך.
     * פריט מודגש (למשל הרשימה הפתוחה) מקבל רקע בגוון המותג.
     */
    static Button listItem(Context c,int icon,String name,String details,boolean highlighted,boolean chevron) {
        Button b=styleButton(c,new Button(c),"",Kind.SOFT);
        String title=name==null?"":name;
        android.text.SpannableStringBuilder text=new android.text.SpannableStringBuilder(rtlParagraph(title));
        text.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),0,text.length(),0);
        text.setSpan(new android.text.style.ForegroundColorSpan(INK),0,text.length(),0);
        if(details!=null&&!details.isEmpty()){
            text.append("\n");
            int start=text.length();
            text.append(rtlParagraph(details));
            text.setSpan(new android.text.style.RelativeSizeSpan(0.84f),start,text.length(),0);
            text.setSpan(new android.text.style.ForegroundColorSpan(MUTED),start,text.length(),0);
        }
        b.setText(text);
        b.setTypeface(FONT_REGULAR);
        b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        b.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        b.setBackground(interactive(c,highlighted?P100:SURFACE,highlighted?P300:LINE,P100,FOCUS,R_CARD));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP,17*textScale(c));
        b.setLineSpacing(dp(c,2),1f);
        b.setPadding(dp(c,S3),dp(c,S3),dp(c,S3),dp(c,S3));
        b.setMinHeight(dp(c,60)); b.setMinimumHeight(dp(c,60));
        Drawable start=null,end=null;
        if(icon!=0){
            start=new IconBubble(c,icon,highlighted?P600:P100,highlighted?ON_DARK:P700,40);
            start.setBounds(0,0,start.getIntrinsicWidth(),start.getIntrinsicHeight());
        }
        if(chevron){
            end=new IconDrawable(c,IC_CHEVRON_FORWARD,MUTED,18,1f);
            end.setBounds(0,0,dp(c,18),dp(c,18));
        }
        b.setCompoundDrawablesRelative(start,null,end,null);
        b.setCompoundDrawablePadding(dp(c,S3));
        b.setContentDescription(title+(details==null||details.isEmpty()?"":", "+details));
        return b;
    }

    /** מתג (פעיל / כבוי) לשורות הגדרה. בעברית: פעיל - העיגול משמאל. */
    static final class ToggleDrawable extends Drawable {
        private final Paint track=new Paint(Paint.ANTI_ALIAS_FLAG), knob=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box=new RectF();
        private final boolean on;
        private final int w,h;
        ToggleDrawable(Context c,boolean on){
            this.on=on; w=dp(c,40); h=dp(c,24);
            track.setColor(on?P600:P300); knob.setColor(on?ON_DARK:SURFACE);
        }
        @Override public void draw(Canvas canvas){
            Rect b=getBounds();
            float cy=b.exactCenterY();
            box.set(b.left,cy-h/2f,b.left+w,cy+h/2f);
            canvas.drawRoundRect(box,h/2f,h/2f,track);
            float r=h/2f-dp2(3);
            float cx=on?box.left+h/2f:box.right-h/2f;
            canvas.drawCircle(cx,cy,r,knob);
        }
        private float dp2(int v){return v*(h/24f);}
        @Override public void setAlpha(int alpha){track.setAlpha(alpha);knob.setAlpha(alpha);}
        @Override public void setColorFilter(android.graphics.ColorFilter f){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        @Override public int getIntrinsicWidth(){return w;}
        @Override public int getIntrinsicHeight(){return h;}
    }

    /** שורת הגדרה עם מתג בסופה: אייקון, שם, הסבר קצר, ומצב פעיל/כבוי. */
    static Button toggleItem(Context c,int icon,String name,String details,boolean on) {
        Button b=listItem(c,icon,name,details,false,false);
        Drawable[] current=b.getCompoundDrawablesRelative();
        Drawable toggle=new ToggleDrawable(c,on);
        toggle.setBounds(0,0,toggle.getIntrinsicWidth(),toggle.getIntrinsicHeight());
        b.setCompoundDrawablesRelative(current[0],null,toggle,null);
        b.setContentDescription(name+", "+(on?"פעיל":"כבוי")+(details==null||details.isEmpty()?"":". "+details));
        return b;
    }

    /** מצב ריק: עיגול אייקון גדול, כותרת והסבר, ממורכזים. */
    static LinearLayout emptyState(Context c,int icon,String title,String body) {
        LinearLayout l=column(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(dp(c,S4),dp(c,S5),dp(c,S4),dp(c,S5));
        ImageView bubble=new ImageView(c);
        bubble.setImageDrawable(new IconBubble(c,icon,P100,P600,72));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(c,72),dp(c,72));
        lp.gravity=Gravity.CENTER_HORIZONTAL;
        l.addView(bubble,lp);
        TextView t=text(c,title,T_NAME,P900,true);
        t.setGravity(Gravity.CENTER);
        l.addView(t,row(c,S3,S1));
        if(body!=null&&!body.isEmpty()){
            TextView d=text(c,body,T_HINT,MUTED,false);
            d.setGravity(Gravity.CENTER);
            l.addView(d,row(c,0,0));
        }
        return l;
    }

    /** פס פעולות בתחתית מסך: משטח עם קו עליון דק. */
    static LinearLayout bottomBar(Context c) {
        LinearLayout bar=new LinearLayout(c);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        LayerDrawable bg=new LayerDrawable(new Drawable[]{new ColorDrawable(LINE),new ColorDrawable(SURFACE)});
        bg.setLayerInset(1,0,dp(c,1),0,0);
        bar.setBackground(bg);
        int pad=dp(c,screenPad(c));
        bar.setPadding(pad,dp(c,S2),pad,dp(c,S2));
        return bar;
    }

    /** תג קטן (מספר או מצב) ליד כותרת קטע. */
    static TextView chip(Context c,String value) {
        TextView t=text(c,value,13,P800,true);
        t.setBackground(shape(c,P100,12,0,0));
        t.setPadding(dp(c,S2),dp(c,2),dp(c,S2),dp(c,2));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    /** תמונת מקום למוצר שאין לו תמונה: מסגרת תמונה עדינה בגוון סגול בהיר. */
    static Drawable photoPlaceholder(Context c) {
        return new IconDrawable(c,IC_PHOTO,P400,0,0.55f);
    }

    /** כניסה רכה למסך חדש. מכבדת את ביטול האנימציות בהגדרות המכשיר. */
    static void fadeIn(View view) {
        if(view==null) return;
        float scale=1f;
        try { scale=Settings.Global.getFloat(view.getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE,1f); } catch(Exception ignored) { }
        if(scale<=0f) { view.setAlpha(1f); return; }
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(170L).start();
    }

    static final class CardViews {
        final LinearLayout root;
        final ImageView image;
        final TextView name,note,amount;
        /** פס צבע המחלקה בצד הכרטיס (או בראשו במצב רשת). */
        final View accent;
        CardViews(LinearLayout r,ImageView i,TextView n,TextView d,TextView a,View accent) {
            root=r;image=i;name=n;note=d;amount=a;this.accent=accent;
        }
    }

    // ---------------------------------------------------------------------
    // צבעי מחלקות: צבע קבוע לכל מחלקה, כך שקל לזהות אותה בכל רשימה.
    // ---------------------------------------------------------------------

    private static final int[] CATEGORY_PALETTE = {
            0xFF2E7D32, 0xFF1565C0, 0xFFC62828, 0xFFEF6C00, 0xFF6A1B9A, 0xFF00838F,
            0xFFAD1457, 0xFF5D4037, 0xFF827717, 0xFF283593, 0xFF00695C, 0xFFD84315};
    private static final String[][] CATEGORY_HINTS = {
            {"ירק", "0"}, {"עלים", "0"}, {"פיר", "3"}, {"ביצ", "8"}, {"חלב", "1"}, {"גבינ", "1"},
            {"טיפוח", "5"}, {"נקיון", "5"}, {"ניקיון", "5"}, {"ביגוד", "4"}, {"גרב", "6"},
            {"קפוא", "9"}, {"מאפ", "7"}, {"לחם", "7"}, {"קטניות", "8"}, {"שימור", "8"},
            {"דגנ", "11"}, {"ממתק", "6"}, {"יסוד", "10"}, {"חשמל", "2"}, {"חנוכה", "2"},
            {"פעמי", "10"}, {"תזונה", "11"}, {"בשר", "2"}, {"עוף", "2"}, {"דג", "1"}};

    /** צבע המחלקה: לפי מילות מפתח בשם (ירקות ירוק, חלב כחול...), ואחרת לפי השם עצמו. 0 כשאין מחלקה. */
    static int categoryColor(String category) {
        if (category == null || category.trim().isEmpty()) return 0;
        String name = category.trim();
        for (String[] hint : CATEGORY_HINTS)
            if (name.contains(hint[0])) return CATEGORY_PALETTE[Integer.parseInt(hint[1])];
        int hash = 0;
        for (int i = 0; i < name.length(); i++) hash = hash * 31 + name.charAt(i);
        return CATEGORY_PALETTE[Math.abs(hash % CATEGORY_PALETTE.length)];
    }

    /** גוון בהיר של צבע (לרקע הכותרת). amount=0.88 כמעט לבן. */
    static int lighten(int color, float amount) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        r = Math.round(r + (255 - r) * amount); g = Math.round(g + (255 - g) * amount);
        b = Math.round(b + (255 - b) * amount);
        return Color.rgb(r, g, b);
    }

    /** גוון כהה של צבע (לטקסט הכותרת). */
    static int darken(int color, float amount) {
        return Color.rgb(Math.round(Color.red(color) * (1 - amount)),
                Math.round(Color.green(color) * (1 - amount)), Math.round(Color.blue(color) * (1 - amount)));
    }

    /** כותרת מחלקה ברשימה: פס צבע, שם המחלקה ומספר המוצרים. */
    static TextView categoryHeader(Context c, String category, int count) {
        int color = categoryColor(category);
        // במצב לילה: רקע כהה מגוון הקטגוריה וטקסט בהיר; ביום: רקע בהיר וטקסט כהה.
        TextView t = text(c, category + (count > 0 ? "  ·  " + count : ""), T_SECTION + 1,
                dark ? lighten(color, 0.55f) : darken(color, 0.15f), true);
        GradientDrawable bg = shape(c, dark ? darken(color, 0.72f) : lighten(color, 0.86f), 10, 0, 0);
        t.setBackground(bg);
        t.setCompoundDrawablePadding(dp(c, S2));
        GradientDrawable bar = shape(c, color, 3, 0, 0);
        bar.setSize(dp(c, 5), dp(c, 18));
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(bar, null, null, null);
        t.setPadding(dp(c, S3), dp(c, S2), dp(c, S3), dp(c, S2));
        t.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        t.setFocusable(false);
        t.setClickable(false);
        return t;
    }

    /** כותרת משתמש (חשבון או רשימה) בתצוגה לפי משתמש: פס סגול כהה עם השם והמספר. */
    static TextView userHeader(Context c, String name, int count) {
        TextView t = text(c, "👤  " + name + (count > 0 ? "  ·  " + count : ""), T_NAME, P25, true);
        t.setBackground(shape(c, P700, 10, 0, 0));
        t.setPadding(dp(c, S3), dp(c, S2), dp(c, S3), dp(c, S2));
        t.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        t.setFocusable(false);
        t.setClickable(false);
        return t;
    }

    static CardViews productCard(Context c) { return productCard(c, 3); }

    /** mode 0: dense checklist; modes 1-4: scaled cards; mode 5: three-column grid. */
    static CardViews productCard(Context c, int mode) {
        boolean compact = mode == 0;
        boolean grid = mode == 5;
        float scale = mode == 1 ? .70f : mode == 2 ? .85f : mode == 4 ? 1.15f : 1f;
        LinearLayout root=new LinearLayout(c);
        root.setId(View.generateViewId());
        root.setOrientation(grid ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        // ברשת: הכל מתחיל מלמעלה, כך שפס הקטגוריה תמיד באותו מקום גם בכרטיס שנמתח.
        root.setGravity(grid ? Gravity.TOP | Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); root.setFocusable(true);
        root.setFocusableInTouchMode(false); root.setClickable(true);
        root.setBackground(interactive(c,SURFACE,LINE,P100,FOCUS,R_CARD));
        int horizontalPad = compact ? S2 : Math.max(S2, Math.round(S4*scale));
        int verticalPad = compact ? 6 : Math.max(S2, Math.round(S3*scale));
        root.setPadding(dp(c,horizontalPad),dp(c,verticalPad),dp(c,horizontalPad),dp(c,verticalPad));
        root.setMinimumHeight(dp(c, compact ? 44 : (grid ? 150 : 0)));
        root.setLayoutParams(row(c,0,compact ? S1 : Math.max(2,Math.round(S3*scale))));

        ImageView image=new RoundedImageView(c,dp(c, grid ? 12 : R_IMAGE));
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(shape(c,P100,R_IMAGE,0,0));
        int size = compact ? 0 : grid ? dp(c,48) : Math.max(dp(c,28),Math.round(imageSize(c)*scale));
        image.setLayoutParams(new LinearLayout.LayoutParams(size,size));
        if (compact) image.setVisibility(View.GONE);

        LinearLayout details=column(c);
        LinearLayout.LayoutParams detailsLp = grid
                ? new LinearLayout.LayoutParams(-1,0,1f)
                : new LinearLayout.LayoutParams(0,-2,1f);
        if (grid) {
            detailsLp.topMargin = dp(c,S1);
            details.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        } else {
            detailsLp.setMarginStart(dp(c,compact?S2:Math.round(S4*scale)));
            detailsLp.setMarginEnd(dp(c,compact?S2:Math.round(S4*scale)));
        }
        details.setLayoutParams(detailsLp);
        TextView name=text(c,"",compact?16:Math.max(15,T_NAME*scale),INK,true);
        // שם המוצר וההערה מוצגים במלואם, בכמה שורות שצריך, בלי חיתוך.
        if (grid) name.setGravity(Gravity.CENTER);
        TextView note=text(c,"",compact?14:Math.max(14,T_NOTE*scale),MUTED,false);
        if (grid) note.setGravity(Gravity.CENTER);
        note.setLayoutParams(row(c,1,0));
        details.addView(name); details.addView(note);

        TextView amount=text(c,"",grid?20:(compact?18:Math.max(18,T_AMOUNT*scale)),P800,true);
        amount.setGravity(Gravity.CENTER);
        amount.setMinWidth(dp(c,grid?0:(compact?34:Math.max(40,Math.round(56*scale)))));
        amount.setBackground(shape(c,P100,R_BUTTON,0,0));
        amount.setPadding(dp(c,compact?S2:Math.max(2,Math.round(S3*scale))),
                dp(c,1),dp(c,compact?S2:Math.max(2,Math.round(S3*scale))),dp(c,1));
        if (grid) {
            LinearLayout.LayoutParams imageLp=(LinearLayout.LayoutParams)image.getLayoutParams();
            imageLp.gravity=Gravity.CENTER_HORIZONTAL;
            image.setLayoutParams(imageLp);
            LinearLayout.LayoutParams amountLp=new LinearLayout.LayoutParams(-2,-2);
            amountLp.topMargin=dp(c,S1); amountLp.gravity=Gravity.CENTER_HORIZONTAL;
            amount.setMinWidth(dp(c,36));
            amount.setLayoutParams(amountLp);
        }
        View accent=new View(c);
        accent.setVisibility(grid ? View.INVISIBLE : View.GONE);
        LinearLayout.LayoutParams accentLp=grid
                ? new LinearLayout.LayoutParams(-1,dp(c,4))
                : new LinearLayout.LayoutParams(dp(c,compact?4:5),-1);
        if(grid) { accentLp.bottomMargin=dp(c,S1); accentLp.gravity=Gravity.TOP; } else accentLp.setMarginEnd(dp(c,compact?S2:S3));
        accent.setLayoutParams(accentLp);
        root.addView(accent); root.addView(image); root.addView(details); root.addView(amount);
        return new CardViews(root,image,name,note,amount,accent);
    }

    static void setImageFocus(Context c, CardViews v, boolean focused) {
        if (v.image instanceof RoundedImageView) ((RoundedImageView) v.image).setRing(focused);
    }

    /** צובע את פס המחלקה של הכרטיס; מוסתר כשאין מחלקה. */
    static void bindCategory(CardViews v,String category) {
        int color=categoryColor(category);
        // ברשת הפס נשאר תופס מקום (שקוף) גם בלי קטגוריה, כדי שכל הכרטיסים יתחילו באותו גובה.
        boolean gridCard=v.accent.getLayoutParams()!=null&&v.accent.getLayoutParams().width==-1;
        if(color==0){v.accent.setVisibility(gridCard?View.INVISIBLE:View.GONE);return;}
        v.accent.setBackground(shape(v.root.getContext(),dark?lighten(color,0.25f):color,3,0,0));
        v.accent.setVisibility(View.VISIBLE);
    }

    /**
     * A name/note that starts with Latin letters (e.g. "Pasta") would get a left-to-right paragraph:
     * left-aligned in the RTL card and with Hebrew parts ordered left-to-right. A leading invisible
     * RIGHT-TO-LEFT MARK makes it an RTL paragraph like every Hebrew line. Display only; data is unchanged.
     */
    static String rtlParagraph(String text) {
        return text == null || text.isEmpty() || text.charAt(0) == '\u200F' ? (text == null ? "" : text) : "\u200F" + text;
    }

    static void bindProduct(CardViews v,String name,String note,String amount,boolean collected) {
        Context c=v.root.getContext(); v.name.setText(rtlParagraph(name)); v.note.setText(rtlParagraph(note));
        v.note.setVisibility(note==null||note.isEmpty()?View.GONE:View.VISIBLE);
        v.amount.setText(amount);
        int flags=v.name.getPaintFlags();
        v.name.setPaintFlags(collected?(flags|Paint.STRIKE_THRU_TEXT_FLAG):(flags&~Paint.STRIKE_THRU_TEXT_FLAG));
        v.name.setTextColor(collected?MUTED:INK); v.amount.setTextColor(collected?P600:P800);
        v.image.setAlpha(collected?0.55f:1f);
        v.root.setBackground(interactive(c,collected?P100:SURFACE,collected?P200:LINE,P200,FOCUS,R_CARD));
        v.root.setContentDescription(name+", כמות "+amount+(collected?", לוקט":""));
    }

    static LinearLayout dialogPanel(Context c) {
        LinearLayout l=column(c); l.setBackground(shape(c,SURFACE,R_DIALOG,dark?1:0,LINE));
        l.setPadding(dp(c,S5),dp(c,S5),dp(c,S5),dp(c,S4)); return l;
    }

    static void systemBars(Activity a) {
        Window w=a.getWindow();
        int flags=0;
        w.setBackgroundDrawable(new ColorDrawable(BG));
        if(Build.VERSION.SDK_INT>=21){
            // גרסאות שלא תומכות בסמלים כהים בשורת המצב מקבלות צבע כהה, כדי שהסמלים הלבנים ייראו.
            w.setStatusBarColor(dark||Build.VERSION.SDK_INT>=23?BG:P800);
            w.setNavigationBarColor(dark||Build.VERSION.SDK_INT>=26?SURFACE:P800);
        }
        if(!dark&&Build.VERSION.SDK_INT>=23)flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if(!dark&&Build.VERSION.SDK_INT>=26)flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        w.getDecorView().setSystemUiVisibility(flags);
    }

    /** בועת הצלחה שמופיעה מתחת לשכבת הקונפטי. */
    static TextView celebrationCard(Context c, String msg) {
        TextView t = text(c, msg, T_TITLE, P800, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(shape(c, P25, R_DIALOG, 1, P300));
        t.setShadowLayer(dp(c, 3), 0f, dp(c, 2), 0x33000000);
        t.setPadding(dp(c, S5), dp(c, S3), dp(c, S5), dp(c, S3));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        lp.setMargins(dp(c, S5), 0, dp(c, S5), 0);
        t.setLayoutParams(lp);
        t.setFocusable(false);
        t.setClickable(false);
        return t;
    }

    static ColorStateList tint(int color) { return ColorStateList.valueOf(color); }
}
