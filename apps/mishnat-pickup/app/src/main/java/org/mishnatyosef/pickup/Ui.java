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

/** ריכוז מלא של צבעים, מידות, אייקונים ורכיבי ממשק. */
final class Ui {
    private Ui() {}

    // פלטת סגול נקייה: רקע לבנדר בהיר מאוד, משטחים לבנים, סגול עמוק לפעולות ולכותרות.
    static final int P25=0xFFFFFFFF, P50=0xFFF6F3FB, P100=0xFFEEE8F8, P200=0xFFE0D7F1,
            P300=0xFFC8B8E8, P400=0xFFA488D8, P500=0xFF8662C7, P600=0xFF6B45AE,
            P700=0xFF56348F, P800=0xFF412670, P900=0xFF2C1A4D;
    static final int INK=0xFF1C1529, MUTED=0xFF4B4459;
    static final int BG=P50, SURFACE=P25, LINE=P200, FOCUS=P700, ON_DARK=0xFFFFFFFF;
    static final int IMAGE_FOCUS_OUTER=2, IMAGE_FOCUS_INSET=4, IMAGE_FOCUS_INNER=1;
    static final int SCRIM=0xEB2A1D45;

    static final int S1=4, S2=8, S3=12, S4=16, S5=20, S6=24, S8=32;
    static final int R_CARD=16, R_BUTTON=14, R_IMAGE=12, R_DIALOG=22;
    // גדלי טקסט בסיסיים. בפועל הם מותאמים לרוחב המסך (textScale), כדי שגם במסך קטן של
    // טלפון מקשים הטקסט יהיה גדול וברור אבל ייכנס בלי להיחתך.
    static final float T_TITLE=24, T_SECTION=16, T_BODY=18, T_NAME=19, T_AMOUNT=26,
            T_NOTE=16, T_BUTTON=17;
    static final int TAP_MIN=60, PERCENT_WIDTH=64, DIALOG_MAX_WIDTH=400,
            DIALOG_SIDE_MARGIN=24, IMAGE_SAMPLE_MIN=78, MAX_NAME_LINES=3, MAX_NOTE_LINES=2;
    /** גודל אחיד לכל כפתורי האייקון בכותרות. במסכים צרים (מכשירי מקשים) הכפתורים קטנים מעט. */
    static final int ICON_BUTTON=48, ICON_GLYPH=24;

    static int iconButtonDp(Context c) {
        int w=c.getResources().getConfiguration().screenWidthDp;
        return w < 300 ? 36 : (w < 340 ? 40 : ICON_BUTTON);
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

    static StateListDrawable interactive(Context c,int fill,int border,int fillFocus,int borderFocus,int radiusDp) {
        StateListDrawable s=new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused},shape(c,fillFocus,radiusDp,6,borderFocus));
        s.addState(new int[]{android.R.attr.state_pressed},shape(c,fillFocus,radiusDp,6,borderFocus));
        s.addState(new int[]{-android.R.attr.state_enabled},shape(c,P50,radiusDp,1,P200));
        s.addState(new int[]{},shape(c,fill,radiusDp,1,border));
        return s;
    }

    /** רקע לשדות טקסט: מסגרת דקה במנוחה, מסגרת סגולה עבה כשהשדה בפוקוס. */
    static StateListDrawable inputBackground(Context c) {
        StateListDrawable s=new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused},shape(c,SURFACE,R_BUTTON,3,P700));
        s.addState(new int[]{},shape(c,SURFACE,R_BUTTON,1,P300));
        return s;
    }

    /** מסגרת פוקוס לרכיבים שאינם כפתורים (מתגים ומחוונים), כדי שיהיה ברור איפה המקלדת נמצאת. */
    static StateListDrawable focusFrame(Context c) {
        StateListDrawable s=new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused},shape(c,P100,R_BUTTON,3,P700));
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

    static TextView section(Context c,String value) {
        TextView t=text(c,value,T_SECTION,P700,true);
        t.setLayoutParams(row(c,S5,S2)); return t;
    }

    static TextView banner(Context c,String value) {
        TextView t=text(c,value,T_NOTE,P900,false);
        t.setBackground(shape(c,P100,R_BUTTON,0,0));
        t.setPadding(dp(c,S4),dp(c,S3),dp(c,S4),dp(c,S3));
        t.setLayoutParams(row(c,0,S3)); return t;
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
        p.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(c,10)));
    }

    enum Kind { PRIMARY, SOFT, STRONG }

    static Button button(Context c,String label,Kind kind) {
        return styleButton(c,new Button(c),withEmoji(label),kind);
    }

    /**
     * כפתור לתפריט בשתי עמודות, כמו מסך אפליקציות: אימוג׳י גדול למעלה והשם מתחת, ממורכז.
     */
    static Button gridButton(Context c,String label,Kind kind) {
        Button b=button(c,label,kind);
        String clean=label==null?"":label.trim();
        String decorated=withEmoji(clean);
        String emoji=decorated.equals(clean)?"":decorated.substring(0,decorated.indexOf("  "));
        if(emoji.isEmpty()) b.setText(clean);
        else {
            android.text.SpannableString text=new android.text.SpannableString(emoji+"\n"+clean);
            text.setSpan(new android.text.style.RelativeSizeSpan(1.6f),0,emoji.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setText(text);
        }
        b.setGravity(Gravity.CENTER);
        b.setMaxLines(Integer.MAX_VALUE); // טקסט לעולם לא נחתך
        b.setMinHeight(dp(c,88));
        b.setMinimumHeight(dp(c,88));
        b.setPadding(dp(c,S2),dp(c,S2),dp(c,S2),dp(c,S2));
        b.setLineSpacing(dp(c,2),1f);
        return b;
    }

    /**
     * אימוג׳י שמתאים לטקסט הכפתור, לפי מילות מפתח (הכלל הראשון שמתאים קובע).
     * "סגירה" ו"ביטול" נשארים בלי אימוג׳י, כי הם מוחלפים אוטומטית בכפתור X.
     */
    private static final String[][] BUTTON_EMOJI = {
            {"הזמנה קודמת", "🕘"}, {"רשימה ריקה", "📝"}, {"הוספה לרשימה קיימת", "📋"},
            {"וואטסאפ", "💬"}, {"SMS", "📱"}, {"במייל", "📧"}, {"שמירה במכשיר", "💾"}, {"אפליקציה אחרת", "📤"},
            {"קובץ JSON", "🗂️"}, {"קובץ טקסט", "📄"}, {"קובץ ZIP", "🗜️"},
            {"בענן", "☁️"}, {"מהענן", "☁️"}, {"שרת הענן", "☁️"}, {"שליחת המספר", "📨"}, {"מדריך המקשים", "⌨️"},
            {"חזרה להזמנה העדכנית", "🛒"}, {"ההזמנה העדכנית", "🛒"}, {"חזרה להגדרות", "⚙️"},
            {"ייבוא הזמנה", "🕘"}, {"הזמנות קודמות", "🕘"}, {"הוספת ההזמנה", "🧾"},
            {"התחבר מחדש", "🔑"}, {"התנתק", "🚪"}, {"התחבר", "🔐"}, {"ניהול חיבורים", "👥"},
            {"הפוך לחשבון הפעיל", "⭐"}, {"הוספת חשבון", "👤"}, {"הוסף חשבון", "👤"},
            {"סנכרן", "🔄"}, {"סנכרון", "🔄"}, {"לנסות שוב", "🔁"}, {"החלפה", "🔁"}, {"טוען", "⏳"},
            {"רשימות שמורות", "📚"}, {"כל הרשימות", "📚"}, {"הצגת הרשימה השמורה", "📋"},
            {"רשימה חדשה", "➕"}, {"יצירת", "➕"}, {"איחוד", "🔗"}, {"מיזוג", "🔀"}, {"מזג", "🔀"},
            {"ייצוא", "📤"}, {"מתיקיית", "📁"}, {"ייבוא", "📥"}, {"ייבא", "📥"},
{"מחיקת", "🗑"}, {"מחיקה", "🗑"}, {"מחק", "🗑"}, {"הסר", "🗑"}, {"שינוי שם", "✏️"},
            {"פתיחת המיקום", "📍"}, {"חיוג", "📞"}, {"פתיחה", "📂"}, {"פתיחת", "📂"},
            {"שמירה", "💾"}, {"שמור", "💾"}, {"אישור", "✅"}, {"איפוס", "♻️"},
            {"הוסף", "➕"}, {"הוספה", "➕"}, {"עדכן", "🔄"}, {"פעולות", "⋯"},
            {"הגדרות", "⚙️"}, {"מה חדש", "✨"}, {"מנותק", "👤"}, {"מחובר", "👤"}, {"פעיל", "👤"},
            {"מיובאת", "📄"}};

    static String withEmoji(String label) {
        if (label == null) return "";
        String clean = label.trim();
        if (clean.isEmpty() || "סגירה".equals(clean) || "ביטול".equals(clean)) return label;
        // כבר מתחיל באימוג׳י או בסמל - לא מוסיפים שוב.
        if (Character.isSurrogate(clean.charAt(0)) || clean.charAt(0) > 0x2000 && clean.charAt(0) < 0x2BFF) return label;
        for (String[] rule : BUTTON_EMOJI) if (clean.contains(rule[0])) return rule[1] + "  " + clean;
        return label;
    }

    private static Button styleButton(Context c,Button b,String label,Kind kind) {
        int fill,border,focusFill,focusBorder,color;
        switch(kind) {
            case PRIMARY: fill=P600;border=P600;focusFill=P700;focusBorder=P900;color=ON_DARK;break;
            case STRONG: fill=P900;border=P900;focusFill=P800;focusBorder=P400;color=ON_DARK;break;
            default: fill=P100;border=P300;focusFill=P200;focusBorder=P700;color=P800;break;
        }
        b.setId(View.generateViewId()); b.setText(label);
        b.setAllCaps(false); b.setTextSize(TypedValue.COMPLEX_UNIT_SP,T_BUTTON*textScale(c));
        b.setTypeface(FONT_MEDIUM); b.setTextColor(color);
        b.setIncludeFontPadding(true); b.setLineSpacing(0f,1.08f);
        if(Build.VERSION.SDK_INT>=21) b.setStateListAnimator(null);
        b.setFocusable(true); b.setFocusableInTouchMode(false);
        b.setBackground(interactive(c,fill,border,focusFill,focusBorder,R_BUTTON));
        // גובה מינימלי נוח ללחיצה ולפוקוס במקלדת; טקסט ארוך יורד שורה ולא נחתך.
        b.setMinHeight(dp(c,48)); b.setMinimumHeight(dp(c,48));
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

    static final int IC_SETTINGS=1, IC_RESET=2, IC_LIST_SINGLE=3, IC_LIST_STACK=4,
            IC_BACK=5, IC_CLOSE=6, IC_SEARCH=7, IC_PHOTO=8, IC_HISTORY=9, IC_INFO=10,
            IC_MAP=11, IC_PHONE=12, IC_CHEVRON_DOWN=13, IC_MORE=14,
            IC_PLUS=15, IC_MERGE=16, IC_CLOUD=17, IC_SHARE=18;

    static final class IconDrawable extends Drawable {
        private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path=new Path();
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

        void setColor(int color) { stroke.setColor(color); fill.setColor(color); invalidateSelf(); }

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
                case IC_PHONE:
                    box.set(7f,2.5f,17f,21.5f); canvas.drawRoundRect(box,2.2f,2.2f,stroke);
                    canvas.drawLine(10.5f,18.3f,13.5f,18.3f,stroke); break;
                default: break;
            }
            canvas.restoreToCount(save);
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

    /** כפתור אייקון בגודל אחיד. active מסמן מצב בחירה (למשל מצב התצוגה הפעיל). */
    static Button iconButton(Context c,int icon,String description,boolean active) {
        IconButton b=new IconButton(c,new IconDrawable(c,icon,active?P900:P700,ICON_GLYPH,1f),ICON_GLYPH);
        styleButton(c,b,"",Kind.SOFT);
        b.setPadding(0,0,0,0);
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setContentDescription(description);
        b.setBackground(interactive(c,active?P200:SURFACE,active?P600:P200,
                active?P300:P100,active?P900:P700,R_BUTTON));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c,iconButtonDp(c)),dp(c,iconButtonDp(c))));
        return b;
    }

    /** אריח פעולה: אייקון ומתחתיו תווית קצרה. כמה אריחים בשורה אחת מקבצים פעולות קבועות בראש מסך. */
    static Button tile(Context c,int icon,String label) {
        Button b=styleButton(c,new Button(c),label,Kind.SOFT);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP,13*textScale(c));
        IconDrawable glyph=new IconDrawable(c,icon,P700,20,1f);
        glyph.setBounds(0,0,dp(c,20),dp(c,20));
        b.setCompoundDrawables(null,glyph,null,null);
        b.setCompoundDrawablePadding(dp(c,1));
        b.setPadding(dp(c,2),dp(c,S1),dp(c,2),dp(c,S1));
        b.setMinHeight(dp(c,52)); b.setMinimumHeight(dp(c,52));
        b.setContentDescription(label);
        return b;
    }

    /** שורת רשימה: שם בולט ומתחתיו פרטים בגופן קטן יותר, על כרטיס לבן. */
    static Button listRow(Context c,String name,String details,boolean highlighted) {
        Button b=styleButton(c,new Button(c),"",Kind.SOFT);
        android.text.SpannableStringBuilder text=new android.text.SpannableStringBuilder(name);
        text.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),0,name.length(),0);
        text.setSpan(new android.text.style.ForegroundColorSpan(P900),0,name.length(),0);
        if(details!=null&&!details.isEmpty()){
            int start=text.length()+1;
            // הפרטים באותה שורה עם השם (יורדים שורה רק כשאין מקום) - כרטיס נמוך יותר.
            text.append("  ").append(details);
            text.setSpan(new android.text.style.RelativeSizeSpan(0.8f),start,text.length(),0);
            text.setSpan(new android.text.style.ForegroundColorSpan(MUTED),start,text.length(),0);
        }
        b.setText(text);
        b.setTypeface(FONT_REGULAR);
        b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        b.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        b.setBackground(interactive(c,highlighted?P100:SURFACE,highlighted?P400:LINE,P100,FOCUS,R_CARD));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP,16*textScale(c));
        b.setPadding(dp(c,S3),dp(c,6),dp(c,S3),dp(c,6));
        b.setMinHeight(dp(c,44)); b.setMinimumHeight(dp(c,44));
        b.setContentDescription(name);
        return b;
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
        TextView t = text(c, category + (count > 0 ? "  ·  " + count : ""), T_SECTION + 1, darken(color, 0.15f), true);
        GradientDrawable bg = shape(c, lighten(color, 0.86f), 10, 0, 0);
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
        root.setGravity(Gravity.CENTER_VERTICAL);
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
                ? new LinearLayout.LayoutParams(-1,-2)
                : new LinearLayout.LayoutParams(0,-2,1f);
        if (grid) {
            detailsLp.topMargin = dp(c,S1);
            details.setGravity(Gravity.CENTER);
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
        accent.setVisibility(View.GONE);
        LinearLayout.LayoutParams accentLp=grid
                ? new LinearLayout.LayoutParams(-1,dp(c,4))
                : new LinearLayout.LayoutParams(dp(c,compact?4:5),-1);
        if(grid) accentLp.bottomMargin=dp(c,S1); else accentLp.setMarginEnd(dp(c,compact?S2:S3));
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
        if(color==0){v.accent.setVisibility(View.GONE);return;}
        v.accent.setBackground(shape(v.root.getContext(),color,3,0,0));
        v.accent.setVisibility(View.VISIBLE);
    }

    static void bindProduct(CardViews v,String name,String note,String amount,boolean collected) {
        Context c=v.root.getContext(); v.name.setText(name); v.note.setText(note);
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
        LinearLayout l=column(c); l.setBackground(shape(c,SURFACE,R_DIALOG,1,LINE));
        l.setPadding(dp(c,S5),dp(c,S5),dp(c,S5),dp(c,S4)); return l;
    }

    static void systemBars(Activity a) {
        Window w=a.getWindow();
        int flags=0;
        if(Build.VERSION.SDK_INT>=21){
            w.setStatusBarColor(Build.VERSION.SDK_INT>=23?BG:P800);
            w.setNavigationBarColor(Build.VERSION.SDK_INT>=26?BG:P800);
        }
        if(Build.VERSION.SDK_INT>=23)flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if(Build.VERSION.SDK_INT>=26)flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        if(flags!=0)w.getDecorView().setSystemUiVisibility(flags);
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
