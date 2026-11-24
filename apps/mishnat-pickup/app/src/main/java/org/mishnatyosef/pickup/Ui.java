package org.mishnatyosef.pickup;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
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

/** ריכוז מלא של צבעים, מידות ורכיבי ממשק. */
final class Ui {
    private Ui() {}

    static final int P25=0xFFFDFBFF, P50=0xFFF9F7FC, P100=0xFFF1EBFA, P200=0xFFE5DEEF,
            P300=0xFFCFC0E8, P400=0xFFA98BD6, P500=0xFF8765C0, P600=0xFF69489D,
            P700=0xFF533881, P800=0xFF3E2A63, P900=0xFF2A1D45;
    static final int INK=0xFF19161F, MUTED=0xFF443F4C;
    static final int BG=P50, SURFACE=P25, LINE=P200, FOCUS=P700, ON_DARK=P25;
    static final int IMAGE_FOCUS_OUTER=2, IMAGE_FOCUS_INSET=4, IMAGE_FOCUS_INNER=1;
    static final int SCRIM=0xEB2A1D45;

    static final int S1=4, S2=8, S3=12, S4=16, S5=20, S6=24, S8=32;
    static final int R_CARD=20, R_BUTTON=16, R_IMAGE=16, R_DIALOG=24;
    static final float T_TITLE=26, T_SECTION=15, T_BODY=18, T_NAME=20, T_AMOUNT=34,
            T_NOTE=15, T_BUTTON=18;
    static final int TAP_MIN=60, PERCENT_WIDTH=64, DIALOG_MAX_WIDTH=360,
            DIALOG_SIDE_MARGIN=40, IMAGE_SAMPLE_MIN=78, MAX_NAME_LINES=3, MAX_NOTE_LINES=2;

    static int dp(Context c, int v) {
        return Math.round(v*c.getResources().getDisplayMetrics().density);
    }

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
        s.addState(new int[]{android.R.attr.state_focused},shape(c,fillFocus,radiusDp,3,borderFocus));
        s.addState(new int[]{android.R.attr.state_pressed},shape(c,fillFocus,radiusDp,3,borderFocus));
        s.addState(new int[]{},shape(c,fill,radiusDp,1,border));
        return s;
    }

    static LinearLayout.LayoutParams row(Context c,int top,int bottom) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(c,top),0,dp(c,bottom)); return p;
    }

    static TextView text(Context c,String value,float sp,int color,boolean bold) {
        TextView t=new TextView(c); t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp); t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        t.setIncludeFontPadding(false); t.setLineSpacing(0f,1.15f); return t;
    }

    static LinearLayout screen(Context c) {
        LinearLayout l=column(c); l.setBackgroundColor(BG);
        l.setPadding(dp(c,S5),dp(c,S5),dp(c,S5),dp(c,S5)); return l;
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
        TextView t=text(c,value,T_SECTION,P600,true);
        t.setLayoutParams(row(c,S6,S2)); return t;
    }

    static TextView banner(Context c,String value) {
        TextView t=text(c,value,T_NOTE,P800,false);
        t.setBackground(shape(c,P100,R_BUTTON,1,P300));
        t.setPadding(dp(c,S4),dp(c,S3),dp(c,S4),dp(c,S3));
        t.setLayoutParams(row(c,0,S3)); return t;
    }

    static void styleProgress(Context c,ProgressBar p) {
        GradientDrawable track=shape(c,P200,6,0,0);
        ClipDrawable fill=new ClipDrawable(shape(c,P600,6,0,0),Gravity.RIGHT,ClipDrawable.HORIZONTAL);
        LayerDrawable layers=new LayerDrawable(new Drawable[]{track,fill});
        layers.setId(0,android.R.id.background); layers.setId(1,android.R.id.progress);
        p.setProgressDrawable(layers);
        p.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(c,S3)));
    }

    enum Kind { PRIMARY, SOFT, STRONG }

    static Button button(Context c,String label,Kind kind) {
        int fill,border,focusFill,focusBorder,color;
        switch(kind) {
            case PRIMARY: fill=P600;border=P600;focusFill=P700;focusBorder=P900;color=ON_DARK;break;
            case STRONG: fill=P900;border=P900;focusFill=P800;focusBorder=P400;color=ON_DARK;break;
            default: fill=P100;border=P300;focusFill=P200;focusBorder=P700;color=P800;break;
        }
        Button b=new Button(c); b.setId(View.generateViewId()); b.setText(label);
        b.setAllCaps(false); b.setTextSize(TypedValue.COMPLEX_UNIT_SP,T_BUTTON);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); b.setTextColor(color);
        if(Build.VERSION.SDK_INT>=21) b.setStateListAnimator(null);
        b.setFocusable(true); b.setFocusableInTouchMode(false);
        b.setBackground(interactive(c,fill,border,focusFill,focusBorder,R_BUTTON));
        // Compact control dimensions while preserving the original 20sp label size.
        b.setMinHeight(dp(c,44)); b.setMinimumHeight(dp(c,44));
        // Default breathing room for every vertically stacked action button.
        // Call sites in horizontal button groups provide their own side margins.
        b.setLayoutParams(row(c,S2,S2));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c,S3),dp(c,S1),dp(c,S3),dp(c,S1));
        return b;
    }

    static final class CardViews {
        final LinearLayout root;
        final ImageView image;
        final TextView name,note,amount;
        CardViews(LinearLayout r,ImageView i,TextView n,TextView d,TextView a) {
            root=r;image=i;name=n;note=d;amount=a;
        }
    }

    static CardViews productCard(Context c) { return productCard(c, 3); }

    /** mode 0: dense checklist; modes 1-4: scaled cards; mode 5: three-column grid. */
    static CardViews productCard(Context c, int mode) {
        boolean compact = mode == 0;
        boolean grid = mode == 5;
        float scale = mode == 1 ? .70f : mode == 2 ? .85f : mode == 4 ? 1.15f : 1f;
        LinearLayout root=new LinearLayout(c);
        root.setOrientation(grid ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); root.setFocusable(true);
        root.setFocusableInTouchMode(false); root.setClickable(true);
        root.setBackground(interactive(c,SURFACE,LINE,P100,FOCUS,R_CARD));
        int horizontalPad = compact ? S2 : Math.max(S2, Math.round(S4*scale));
        int verticalPad = compact ? 2 : Math.max(2, Math.round(S2*scale));
        root.setPadding(dp(c,horizontalPad),dp(c,verticalPad),dp(c,horizontalPad),dp(c,verticalPad));
        root.setMinimumHeight(dp(c, compact ? 34 : 0));
        root.setLayoutParams(row(c,0,compact ? S1 : Math.max(2,Math.round(S3*scale))));

        ImageView image=new RoundedImageView(c,dp(c, grid ? 12 : R_IMAGE));
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(shape(c,P100,R_IMAGE,0,0));
        int size = compact ? 0 : grid ? dp(c,48) : Math.max(dp(c,28),Math.round(imageSize(c)*scale));
        image.setLayoutParams(grid
                ? new LinearLayout.LayoutParams(size,size)
                : new LinearLayout.LayoutParams(size,size));
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
        TextView name=text(c,"",compact?14:T_NAME*scale,INK,true);
        name.setMaxLines(grid?2:(compact?1:MAX_NAME_LINES));
        TextView note=text(c,"",compact?12:T_NOTE*scale,MUTED,false);
        note.setMaxLines(grid?2:MAX_NOTE_LINES);
        note.setLayoutParams(row(c,1,0));
        details.addView(name); details.addView(note);

        TextView amount=text(c,"",grid?19:(compact?17:T_AMOUNT*scale),P800,true);
        amount.setGravity(Gravity.CENTER);
        amount.setMinWidth(dp(c,grid?0:(compact?34:Math.max(40,Math.round(56*scale)))));
        amount.setBackground(shape(c,P100,R_BUTTON,0,0));
        amount.setPadding(dp(c,compact?S2:Math.max(2,Math.round(S3*scale))),
                dp(c,1),dp(c,compact?S2:Math.max(2,Math.round(S3*scale))),dp(c,1));
        if (grid) {
            LinearLayout.LayoutParams imageLp=(LinearLayout.LayoutParams)image.getLayoutParams();
            imageLp.gravity=Gravity.CENTER_HORIZONTAL;
            image.setLayoutParams(imageLp);
            amount.setLayoutParams(row(c,S1,0));
        }
        root.addView(image); root.addView(details); root.addView(amount);
        return new CardViews(root,image,name,note,amount);
    }

    static void setImageFocus(Context c, CardViews v, boolean focused) {
        if (v.image instanceof RoundedImageView) ((RoundedImageView) v.image).setRing(focused);
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
        l.setPadding(dp(c,S6),dp(c,S6),dp(c,S6),dp(c,S4)); return l;
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
