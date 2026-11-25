package org.mishnatyosef.pickup;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * מדריך המקשים: תמונה במסך מלא שאפשר להגדיל ולהזיז גם בלי מסך מגע.
 * אישור / 5: הגדלה (בהגדלה המקסימלית חוזר לגודל המסך) · 2/4/6/8 וחצים: תזוזה ·
 * כל מקש אחר (וגם חזרה): סגירה. במסך מגע יש כפתורי + ו-−, וגלילה רגילה.
 */
final class KeysHelpViewer {
    private static final float[] ZOOMS = {1f, 1.5f, 2f, 3f};

    private final Activity activity;
    private Dialog dialog;
    private Bitmap bitmap;
    private ScrollView vertical;
    private HorizontalScrollView horizontal;
    private ImageView image;
    private TextView zoomLabel;
    private int zoomIndex;
    private int baseWidth;

    KeysHelpViewer(Activity activity) { this.activity = activity; }

    void show() {
        if (dialog != null) return;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565; // חצי זיכרון; התמונה בלי שקיפות
        bitmap = BitmapFactory.decodeResource(activity.getResources(), R.drawable.keys_help, options);
        if (bitmap == null) return;

        dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(0xFF1B1230);

        vertical = new ScrollView(activity);
        vertical.setFillViewport(true);
        vertical.setFocusable(false);
        horizontal = new HorizontalScrollView(activity);
        horizontal.setFocusable(false);
        // כיוון קבוע משמאל לימין, כדי שגלילה אופקית תתנהג אותו דבר בכל גרסאות אנדרואיד.
        horizontal.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        // התמונה ממורכזת כשהיא צרה מהמסך.
        FrameLayout holder = new FrameLayout(activity);
        image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_XY);
        image.setImageBitmap(bitmap);
        image.setFocusable(false);
        image.setContentDescription("מדריך המקשים של משנת אוסף");
        holder.addView(image, new FrameLayout.LayoutParams(1, 1, Gravity.CENTER_HORIZONTAL));
        horizontal.addView(holder, new FrameLayout.LayoutParams(-2, -2));
        vertical.addView(horizontal, new FrameLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams scrollLp = new FrameLayout.LayoutParams(-1, -1);
        scrollLp.bottomMargin = Ui.dp(activity, 64);
        root.addView(vertical, scrollLp);

        // פס תחתון: הסבר קצר וכפתורי מגע. הכפתורים לא מקבלים פוקוס, כדי שהמקשים יזיזו את התמונה.
        LinearLayout bar = new LinearLayout(activity);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Ui.P900);
        int pad = Ui.dp(activity, Ui.S2);
        bar.setPadding(pad, pad, pad, pad);
        zoomLabel = Ui.text(activity, "", Ui.T_NOTE, Color.WHITE, true);
        zoomLabel.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        bar.addView(zoomLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(barButton("−", v -> zoomBy(-1)));
        bar.addView(barButton("+", v -> zoomBy(1)));
        bar.addView(barButton("✕", v -> dialog.dismiss()));
        root.addView(bar, new FrameLayout.LayoutParams(-1, Ui.dp(activity, 64), Gravity.BOTTOM));

        dialog.setContentView(root);
        dialog.setOnKeyListener((d, keyCode, event) -> onKey(keyCode, event));
        dialog.setOnDismissListener(d -> release());
        dialog.show();

        root.post(() -> {
            baseWidth = Math.max(1, root.getWidth() - Ui.dp(activity, Ui.S2) * 2);
            applyZoom(0, 0.5f, 0f);
        });
    }

    private Button barButton(String label, View.OnClickListener click) {
        Button b = new Button(activity);
        b.setText(label);
        b.setTextSize(22);
        b.setTextColor(Color.WHITE);
        b.setBackground(Ui.shape(activity, Ui.P700, 12, 0, 0));
        b.setFocusable(false);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(activity, 56), Ui.dp(activity, 48));
        lp.setMarginStart(Ui.dp(activity, Ui.S1));
        b.setLayoutParams(lp);
        return b;
    }

    private boolean onKey(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) return false; // סגירה רגילה
        if (dialog == null) return true;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        int stepY = vertical.getHeight() / 4, stepX = horizontal.getWidth() / 4;
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER: case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_5: case KeyEvent.KEYCODE_NUMPAD_5:
                if (down && event.getRepeatCount() == 0)
                    zoomBy(zoomIndex == ZOOMS.length - 1 ? -zoomIndex : 1); // אחרי המקסימום חוזר לגודל המסך
                return true;
            case KeyEvent.KEYCODE_2: case KeyEvent.KEYCODE_NUMPAD_2: case KeyEvent.KEYCODE_DPAD_UP:
                if (down) vertical.smoothScrollBy(0, -stepY);
                return true;
            case KeyEvent.KEYCODE_8: case KeyEvent.KEYCODE_NUMPAD_8: case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) vertical.smoothScrollBy(0, stepY);
                return true;
            case KeyEvent.KEYCODE_4: case KeyEvent.KEYCODE_NUMPAD_4: case KeyEvent.KEYCODE_DPAD_LEFT:
                if (down) horizontal.smoothScrollBy(-stepX, 0);
                return true;
            case KeyEvent.KEYCODE_6: case KeyEvent.KEYCODE_NUMPAD_6: case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) horizontal.smoothScrollBy(stepX, 0);
                return true;
            default:
                // כל מקש אחר סוגר את התמונה. הסגירה בשחרור המקש, כדי שהמקש לא יפעל גם במסך שמתחת.
                if (!down) dialog.dismiss();
                return true;
        }
    }

    private void zoomBy(int delta) {
        int next = Math.max(0, Math.min(ZOOMS.length - 1, zoomIndex + delta));
        if (next == zoomIndex && image != null && image.getWidth() > 1) return;
        // שומרים את נקודת המרכז, כדי שההגדלה תהיה סביב מה שרואים עכשיו.
        float cx = 0.5f, cy = 0f;
        if (image != null && image.getWidth() > 1 && image.getHeight() > 1) {
            cx = (horizontal.getScrollX() + horizontal.getWidth() / 2f) / image.getWidth();
            cy = (vertical.getScrollY() + vertical.getHeight() / 2f) / image.getHeight();
        }
        applyZoom(next, cx, cy);
    }

    private void applyZoom(int index, float cx, float cy) {
        if (image == null || bitmap == null) return;
        zoomIndex = index;
        int width = Math.round(baseWidth * ZOOMS[index]);
        int height = Math.round(width * (bitmap.getHeight() / (float) bitmap.getWidth()));
        ViewGroup.LayoutParams lp = image.getLayoutParams();
        lp.width = width; lp.height = height;
        image.setLayoutParams(lp);
        zoomLabel.setText(index == 0
                ? "אישור: הגדלה · 2/4/6/8: תזוזה · מקש אחר: סגירה"
                : "הגדלה ×" + trim(ZOOMS[index]) + " · מקש אחר: סגירה");
        final float fx = cx, fy = cy;
        image.post(() -> {
            if (image == null) return;
            horizontal.scrollTo(Math.max(0, Math.round(fx * width - horizontal.getWidth() / 2f)), 0);
            vertical.scrollTo(0, index == 0 && fy == 0f ? 0 : Math.max(0, Math.round(fy * height - vertical.getHeight() / 2f)));
        });
    }

    private static String trim(float value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    private void release() {
        dialog = null;
        if (image != null) image.setImageDrawable(null);
        image = null;
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        bitmap = null;
    }
}
